package org.tallison;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.txt.Icu4jEncodingDetector;
import org.apache.tika.parser.txt.UniversalEncodingDetector;

public class CharsetComparer {
    static Log logger = LogFactory.getLog(CharsetComparer.class);
    static AtomicInteger threadNum = new AtomicInteger();
    static AtomicInteger filesProcessed = new AtomicInteger(1);

    private static Path POISON = Paths.get("");

    private int numThreads = 10;

    public static void main(String[] args) throws Exception {
        CharsetComparer charsetComparer = new CharsetComparer();
        Path directory = Paths.get(args[0]);
        Path reportFile = Paths.get(args[1]);
        try {
            charsetComparer.execute(directory, reportFile);
        } catch (Throwable t) {
            logger.error(t);
        }
    }

    private void execute(Path startDir, Path outputDir) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(numThreads+1);

        ExecutorCompletionService executorCompletionService = new ExecutorCompletionService(ex);
        ArrayBlockingQueue<Path> queue = new ArrayBlockingQueue<>(1000);
        Crawler crawler = new Crawler(queue, startDir);
        executorCompletionService.submit(crawler);

        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit(new Comparer(queue, outputDir));
        }

        int completed = 0;
        while (completed < numThreads+1) {
            Future<Integer> future = executorCompletionService.poll(1, TimeUnit.SECONDS);
            if (future != null) {

                try {
                    future.get();
                } catch (Throwable e) {
                    logger.warn("bad future", e);
                }
                completed++;
            }
        }

        ex.shutdown();
        ex.shutdownNow();
    }

    private class Crawler implements Callable<Integer> {
        private final ArrayBlockingQueue<Path> queue;
        private final Path startDir;
        Crawler(ArrayBlockingQueue<Path> queue, Path startDir) {
            this.queue = queue;
            this.startDir = startDir;
        }

        @Override
        public Integer call() throws Exception {

            addDir(startDir);

            for (int i = 0; i < numThreads; i++) {
                try {
                    boolean added = false;
                    int j = 0;
                    while (added == false && j++ < 100) {
                        added = queue.offer(POISON, 1000, TimeUnit.MILLISECONDS);
                    }
                    if (added == false) {
                        logger.error("couldn't add poison in enough time");
                        throw new RuntimeException("quitting...can't add poison");
                    }
                } catch (InterruptedException e) {
                    logger.warn(e);
                }
            }
            return 0;
        }

        private void addDir(Path startDir) {
            for (File f : startDir.toFile().listFiles()) {
                if (f.isDirectory()) {
                    addDir(f.toPath());
                } else if (Files.isRegularFile(f.toPath())){
                    addFile(f.toPath());
                } else {
                    logger.warn("neither dir nor regular file: "+f);
                }
            }
        }

        private void addFile(Path path) {
            boolean added = false;
            for (int i = 0; i < 100; i++) {
                try {
                    added = queue.offer(path, 1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.warn(e);
                }
                if (added == true) {
                    break;
                }
            }
            if (added == false) {
                logger.error("took too long to add file; I'm quitting");
                throw new RuntimeException("too long to add file");
            }
        }
    }

    private class Comparer implements Callable<Integer> {
        private final ArrayBlockingQueue<Path> queue;
        private List<EncodingDetector> detectors = new ArrayList<>();
        private BufferedWriter writer;
        private final int myThreadNum = threadNum.getAndIncrement();

        Comparer(ArrayBlockingQueue<Path> queue, Path outputDirectory) throws Exception {
            this.queue = queue;
            detectors.add(new HtmlEncodingDetector());
            detectors.add(new UniversalEncodingDetector());
            detectors.add(new Icu4jEncodingDetector());
            detectors.add(new ScrapingHTMLEncodingDetector());
            detectors.add(new ScrapingUniversalEncodingDetector());
            detectors.add(new ScrapingICUEncodingDetector());
            writer = Files.newBufferedWriter(outputDirectory.resolve(
                    "charset-comparisons-"+myThreadNum+".txt"), StandardCharsets.UTF_8);
            writer.write("File\t");
            for (EncodingDetector detector : detectors) {
                writer.write(detector.getClass().getSimpleName()+"\t");
            }
            writer.write("\n");

        }

        @Override
        public Integer call() {
            while (true) {
                try {
                    Path p = queue.poll(1000, TimeUnit.MILLISECONDS);
                    if (p != null && p.equals(POISON)) {
                        break;
                    }
                    if (p != null) {
                        try {
                            processFile(p);
                            int i = filesProcessed.incrementAndGet();
                            if (i % 1000 == 0) {
                                logger.info("processed "+i);
                            }
                        } catch (Throwable t) {
                            logger.warn(p, t);
                        }
                    } else {
                        logger.info("queue empty");
                    }
                } catch (InterruptedException e) {
                    logger.warn(e);
                }
            }
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                logger.warn("on writer close", e);
            }
            logger.info("comparer " + myThreadNum+ " terminating");
            return 1;
        }

        private void processFile(Path path) throws IOException {
            Map<String, String> results = new HashMap<>();

            for (EncodingDetector detector : detectors) {
                try (InputStream is = TikaInputStream.get(path)) {
                    Charset detected = detector.detect(is, new Metadata());
                    if (detected == null) {
                        results.put(detector.getClass().getSimpleName(), "");
                    } else {
                        results.put(detector.getClass().getSimpleName(), detected.toString());
                    }
                } catch (IOException e) {
                    logger.warn(e.getMessage()+": "+path, e);
                }
            }
            writer.write(path.getFileName().toString()+"\t");
            for (EncodingDetector detector : detectors) {
                String r = results.get(detector.getClass().getSimpleName());
                writer.write(r+"\t");
            }
            writer.write("\n");
        }

    }

}
