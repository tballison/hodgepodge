package org.tallison.crawler;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;


public class URLCrawler {
    static final String POISON = "";
    static final int numThreads = 10;
    static Logger logger = Logger.getLogger(URLCrawler.class);

    static Options options = new Options();
    static {

        options.addOption(Option.builder("c")
                .hasArg()
                .longOpt("connectionString")
                .desc("db connection string")
                .required()
                .build());


        options.addOption(Option.builder("i")
                .hasArg()
                .longOpt("inputFile")
                .desc("list of urls")
                .required()
                .build());
        options.addOption(Option.builder("docs")
                .hasArg()
                .longOpt("documents")
                .desc("root for downloaded documents")
                .required()
                .build());
        options.addOption(Option.builder("ph")
                .longOpt("proxyHost")
                .hasArg()
                .desc("proxy host")
                .build());
        options.addOption(Option.builder("pp")
                .longOpt("proxyPort")
                .hasArg()
                .desc("proxy port")
                .build());
        options.addOption(Option.builder("f")
                .longOpt("freshstart")
                .hasArg(false)
                .desc("drop table before appending new data")
                .build());

    }

    private static boolean SHUTDOWN = false;
    private int proxyPort = -1;
    private String proxyHost = null;
    private final Connection connection;
    private final Path urlListFile;
    private final Path docRoot;
    private final boolean freshStart;

    public URLCrawler(Connection connection, Path urlListFile, Path docRoot) {
        this(connection, urlListFile, docRoot, true);
    }
    public URLCrawler(Connection connection, Path urlListFile, Path docRoot, boolean freshStart) {
        this.connection = connection;
        this.urlListFile = urlListFile;
        this.docRoot = docRoot;
        this.freshStart = freshStart;
    }

    static void usage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -cp \"bin/*\" org.mitre.jget.URIGetter", options);
    }

    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(options, args);
        String connectionString = commandLine.getOptionValue("c");
        Connection connection = DriverManager.getConnection(connectionString);


        Path urlListFile = null;
        if (commandLine.hasOption("i")) {
            urlListFile = Paths.get(commandLine.getOptionValue("i"));
        } else {
            throw new IllegalArgumentException("Must specify a 's'tart url or a 'ccIndexFile'");
        }


        Path docRoot = Paths.get(commandLine.getOptionValue("docs"));
        boolean freshStart = commandLine.hasOption("f") ? true : false;
        URLCrawler crawler = new URLCrawler(connection, urlListFile, docRoot, freshStart);

        if (commandLine.hasOption("pp")) {
            crawler.setProxyPort(Integer.parseInt(commandLine.getOptionValue("pp")));
        }
        if (commandLine.hasOption("ph")) {
            crawler.setProxyHost(commandLine.getOptionValue("ph"));
        }

        Files.createDirectories(docRoot);
        try {
            crawler.execute();
        } finally {
            connection.commit();
            connection.close();
        }
    }

    private void execute() {
        ArrayBlockingQueue<String> urls = new ArrayBlockingQueue<>(10);
        List<URIGetter> uriGetters = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            URIGetter uriGetter = null;
            try {
                uriGetter = new URIGetter(urls, docRoot, new DBReporter(connection, freshStart));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            if (proxyHost != null) {
                uriGetter.setProxyHost(proxyHost);
            }
            if (proxyPort > -1) {
                uriGetter.setProxyPort(proxyPort);
            }
            uriGetters.add(uriGetter);
        }
        ExecutorService ex = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<>(ex);

        completionService.submit(new URLReader(urlListFile, urls));

        for (URIGetter uriGetter : uriGetters) {
            completionService.submit(uriGetter);
        }
        int finished = 0;
        while (finished < numThreads+1) {
            Future<String> future = null;
            try {
                future = completionService.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                logger.warn(e);
            }

            if (future != null && future.isDone()) {
                try {
                    System.out.println(future.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    logger.warn(e);
                }
                finished++;
            }
        }
        ex.shutdown();
        ex.shutdownNow();
        System.out.println("completed");

    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    private static class URLReader implements Callable<String> {
        private final ArrayBlockingQueue<String> queue;
        private final Path file;
        URLReader(Path file, ArrayBlockingQueue<String> queue) {
            this.file = file;
            this.queue = queue;
        }

        @Override
        public String call() throws Exception {
            System.out.println("about to open "+file.toAbsolutePath());
            try(BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line = reader.readLine();
                while (line != null) {
                    boolean keepTrying = true;
                    if (line.trim().length() != 0) {
                        System.out.println("trying to add: " + line);
                        while (keepTrying) {
                            keepTrying = !queue.offer(line.trim(), 1, TimeUnit.SECONDS);
                        }
                    }
                    line = reader.readLine();
                }
            } finally {
                SHUTDOWN = true;
                for (int i = 0; i < numThreads; i++) {
                    System.out.println("adding poison");
                    //this might fail to add POISON!!!
                    queue.offer(POISON, 1, TimeUnit.SECONDS);
                }
            }
            return "crawler done";
        }
    }
}
