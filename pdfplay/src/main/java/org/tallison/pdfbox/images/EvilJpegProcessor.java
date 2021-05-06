package org.tallison.pdfbox.images;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fuzzing.general.ByteDeleter;
import org.apache.tika.fuzzing.general.ByteFlipper;
import org.apache.tika.fuzzing.general.ByteInjector;
import org.apache.tika.fuzzing.general.GeneralTransformer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

//Copied nearly verbatim from PDFBox's example code
public class EvilJpegProcessor {

    static Map<Long, ProcessTuple> TUPLES = new HashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        final File[] jpegs = Paths.get(args[0]).toFile().listFiles();
        final AtomicInteger processed = new AtomicInteger(0);
        Path outDir = Paths.get(args[1]);
        if (!Files.isDirectory(outDir)) {
            Files.createDirectories(outDir);
        }
        ByteFlipper byteFlipper = new ByteFlipper();
        ByteInjector byteInjector = new ByteInjector();
        byteFlipper.setPercentCorrupt(0.0001f);
        ByteDeleter byteDeleter = new ByteDeleter();

        Path beasties = Paths.get("/home/tallison/Desktop/jpegs/beasties");
        Files.createDirectories(beasties);
        Path exceptions = Paths.get("/home/tallison/Desktop/jpegs/exceptions");
        Files.createDirectories(exceptions);

        GeneralTransformer transformer = new GeneralTransformer(1, byteFlipper,
                byteInjector, byteDeleter);

        int numThreads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<Integer> executorCompletionService = new ExecutorCompletionService<>(executorService);
        for (int i = 0; i < numThreads; i++) {
            executorCompletionService.submit((Callable<Integer>) () -> {
                for (int t = 0; t < 100; t++) {
                    for (File f : jpegs) {
                        try {
                            processDoc(t, f, transformer, outDir, beasties, exceptions);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                }
                return 1;
            });
        }
        int finished = 0;
        while (finished < numThreads) {
            Future<Integer> future = executorCompletionService.take();
            future.get();
            finished++;
        }
        FileUtils.deleteDirectory(outDir.toFile());
        executorService.shutdownNow();
    }

    private static void processDoc(int i, File jpeg,
                                   GeneralTransformer transformer, Path outDir,
                                   Path beasties, Path exceptions) throws IOException {
        if (jpeg.isDirectory()) {
            for (File f : jpeg.listFiles()) {
                processDoc(i, f, transformer, outDir, beasties, exceptions);
            }
            return;
        }

        int cnt = COUNTER.getAndIncrement();
        if (cnt % 1000 == 0) {
            System.err.println("processed " + cnt);
        }
        Path out = outDir.resolve("fuzzed-" + i + "-" + jpeg.getName());
        try (InputStream is = Files.newInputStream(jpeg.toPath());
            OutputStream os = Files.newOutputStream(out)) {
            transformer.transform(is, os);
        } catch (Throwable e) {
            System.out.println(jpeg.toPath() + " : "+Files.size(jpeg.toPath())+
                    " : "+e.getMessage());
            e.printStackTrace();
        }

        String[] pArgs = {
                //"djpeg", "-fast", out.toAbsolutePath().toString()};
                //"tesseract", out.toAbsolutePath().toString(),
                //"/usr/bin/gdk-pixbuf-thumbnailer", "-s", "256",
                "file",
                out.toAbsolutePath().toString(),
//                Files.createTempFile(outDir, "", "").toAbsolutePath().toString()
        };
        long start = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(pArgs);
        Path tmpOut = Files.createTempFile(outDir, "out-", "");
        Path tmpErr = Files.createTempFile(outDir, "err-", "");
        pb.redirectOutput(tmpOut.toFile());
        pb.redirectError(tmpErr.toFile());
        String err = "";
        Process p = pb.start();
        boolean finished = false;
        long processId = p.pid();
        try {
            finished = p.waitFor(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {

        } finally {
            p.destroyForcibly();
            try {
                p.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                //
            }
            if (Files.isRegularFile(tmpErr)) {
                try {
                    err = FileUtils.readFileToString(tmpErr.toFile(), StandardCharsets.UTF_8).trim();
                } catch (IOException e) {
                    //swallow
                    err = "err ioexception";
                }
            }

            if (finished == false) {
                mv(out, beasties, "timeout");
            } else if (p.exitValue() > 1 || p.exitValue() < 0) {
                mv(out, beasties, "crash "+p.exitValue());
            }
           /* for (File f : outDir.toFile().listFiles()) {
/*                try {
                    //Files.delete(f.toPath());
                } catch (NoSuchFileException e) {

                }
            }*/
        }
        long elapsed = System.currentTimeMillis() - start;

        //TUPLES.put(processId, new ProcessTuple(processId, out, elapsed, p.exitValue(),
          //      err.replaceAll("[\r\n]", " ")));
    }

    private static class ProcessTuple {
        private final long pid;
        private final Path path;
        private final long elapsedMs;
        private final int exitValue;
        private final String stderr;

        public ProcessTuple(long pid, Path path, long elapsedMs, int exitValue, String stderr) {
            this.pid = pid;
            this.path = path;
            this.elapsedMs = elapsedMs;
            this.exitValue = exitValue;
            this.stderr = stderr;
        }

        @Override
        public String toString() {
            return "ProcessTuple{" +
                    "pid=" + pid +
                    ", path=" + path +
                    ", elapsedMs=" + elapsedMs +
                    ", exitValue=" + exitValue +
                    ", stderr='" + stderr + '\'' +
                    '}';
        }
    }
    private static List<Long> getKilledPids() throws IOException {
        String[] args = {"dmesg"};
        List<Long> pids = new ArrayList<>();
        ProcessBuilder pb = new ProcessBuilder(args);
        Path tmpOut = Files.createTempFile("out-", "");
        Path tmpErr = Files.createTempFile("err-", "");
        try {
            pb.redirectOutput(tmpOut.toFile());
            pb.redirectError(tmpErr.toFile());
            String err = "";
            Process p = pb.start();
            boolean finished = p.waitFor(1000, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                p.waitFor(1, TimeUnit.SECONDS);
            }
            List<String> lines = Files.readAllLines(tmpOut);
            Matcher m = Pattern.compile("reaped process (\\d+)").matcher("");
            for (String line : lines) {
                if (m.reset(line).find()) {
                    pids.add(Long.parseLong(m.group(1)));
                }
            }

        } catch (InterruptedException e) {

        } finally {
            Files.delete(tmpOut);
            Files.delete(tmpErr);
        }
        return pids;
    }

    private static void mv(Path out, Path beasties, String msg) throws IOException {
        String digest = "";
        try (InputStream is = Files.newInputStream(out)) {
            digest = DigestUtils.sha256Hex(is);
        }
        Path target = beasties.resolve(digest);
        System.out.println(digest + "\t" + out.getFileName() + "\t" + msg);
        Files.copy(out, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
