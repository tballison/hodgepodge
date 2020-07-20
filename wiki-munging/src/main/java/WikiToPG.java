
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.Sentence;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.wikiclean.WikiClean;
import org.wikiclean.WikipediaArticlesDump;
import org.wikiclean.languages.Chinese;
import org.wikiclean.languages.English;
import org.wikiclean.languages.German;
import org.wikiclean.languages.Language;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copied/pasted from WikiTableSimple.
 *
 * Instead of storing digests in memory, this writes to a database
 * with the digest as a primary key.
 *
 * This crashed PG on my laptop when I was also writing to PG
 * on a different project.  Use with caution.
 *
 */
public class WikiToPG {

    static Pattern BYTES_FLIPPER = Pattern.compile("<text bytes=\"(\\d+)\" xml:space=\\\"preserve\\\">");
    static Pattern NS_PATTERN = Pattern.compile("<ns>(\\d+)</ns>");
    static Options OPTIONS;
    private static final int MIN_SENT_LENGTH = 10;
    private static final Path POISON = Paths.get("");

    static {
        Option inputDir = new Option("i", "input",
                true, "directory with *-pages-articles-*.xml.bz2 files");
        inputDir.setRequired(true);

        Option outputFile = new Option("db", "output",
                true, "database connection");
        outputFile.setRequired(true);

        Option targetLang = new Option("l", "lang",
                true, "target language");
        outputFile.setRequired(true);

        OPTIONS = new Options()
                .addOption(inputDir)
                .addOption(outputFile)
                .addOption(targetLang)
                .addOption("maxPages", true, "maximum number of pages to process")
                .addOption("minLength", true, "minimum length of article");
    }

    public static void USAGE() {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp(
                80,
                "java -jar munging-q.r-s.t.jar <options>",
                "GramReaper",
                OPTIONS,
                "");
    }

    private final Analyzer analyzer = new StandardAnalyzer();
    Set<String> digests = new HashSet<>();

    private Matcher redirectMatcher = Pattern.compile("<text[^>]*>#").matcher("");


    public static void main(String[] args) throws Exception {
        DefaultParser parser = new DefaultParser();
        CommandLine commandLine = null;
        try {
            commandLine = parser.parse(OPTIONS, args);
        } catch (ParseException e) {
            USAGE();
            return;
        }
        Path bzip = Paths.get(commandLine.getOptionValue('i'));
        Connection connection = DriverManager.getConnection(commandLine.getOptionValue("db"));
        String targLang = commandLine.getOptionValue('l');

        int maxPages = -1;
        if (commandLine.hasOption("maxPages")) {
            maxPages = Integer.parseInt(commandLine.getOptionValue("maxPages"));
        }

        int minPageLength = -1;
        if (commandLine.hasOption("minLength")) {
            minPageLength = Integer.parseInt(commandLine.getOptionValue("minLength"));
        }

        WikiToPG wikiToTable = new WikiToPG();
        wikiToTable.execute(targLang, bzip, connection, maxPages, minPageLength);

    }



    private static Language getLanguage(String targLang) {
        if ("de".equals(targLang)) {
            return new German();
        } else if ("zh".equals(targLang)) {
            return new Chinese();
        } else if ("ar".equals(targLang)) {
            return new Arabic();
        } else {
            //default
            return new English();
        }
    }

    public static String flipBytes(String s) {
        Matcher m = BYTES_FLIPPER.matcher(s);
        return m.replaceFirst("<text xml:space=\"preserve\" bytes=\"$1\">");
    }

    private void execute(String lang, Path bzipDir, Connection connection, int maxPages, int minPageLength) throws Exception {

        String sql = "drop table if exists wiki";
        connection.createStatement().execute(sql);
        sql = "create table wiki (digest varchar(64) primary key, fileId int, sentId int, sent varchar(64000))";
        connection.createStatement().execute(sql);


        File[] files = bzipDir.toFile().listFiles();
        int numThreads = 12;
        numThreads = numThreads > files.length ? files.length : numThreads;
        System.out.println("num threads: "+numThreads);
        ArrayBlockingQueue<Path> bzips = new ArrayBlockingQueue<>(files.length+numThreads);


        for (File f : bzipDir.toFile().listFiles()) {
            bzips.add(f.toPath());
        }

        for (int i = 0; i < numThreads; i++) {
            bzips.add(POISON);
        }
        ExecutorService es = Executors.newFixedThreadPool(numThreads);
        ExecutorCompletionService completionService = new ExecutorCompletionService(es);

        for (int i = 0; i < numThreads; i++) {
            completionService.submit(new BzipProcessor(bzips, connection, minPageLength, lang));
        }

        int finished = 0;
        int pages = 0;
        long start = System.currentTimeMillis();
        while (finished < numThreads) {
            Future<Integer> future = completionService.take();
            if (future != null) {
                finished++;
                pages += future.get();
            }
        }
        es.shutdownNow();
        System.out.println("Finished " + pages + " pages in " + (System.currentTimeMillis() - start) + " ms");
    }

    private static class BzipProcessor implements Callable<Integer> {

        private static AtomicInteger FILES_PROCESSED = new AtomicInteger(0);
        private final Analyzer analyzer = new StandardAnalyzer();
        private final PreparedStatement insert;
        private final ArrayBlockingQueue<Path> bzips;
        private final WikiClean cleaner;
        private final int minPageLength;
        private int fileId = 0;//per file
        private int sentenceId = 0;//per file
        private Matcher redirectMatcher = Pattern.compile("<text[^>]*>#").matcher("");

        BzipProcessor(ArrayBlockingQueue bzips, Connection connection, int minPageLength, String lang) throws Exception {

            Language language = getLanguage(lang);
            cleaner = new WikiClean.Builder().withTitle(true)
                    .withFooter(false).withLanguage(language).build();
            this.bzips = bzips;
            String sql = "insert into wiki values (?,?,?,?) on conflict do nothing";
            this.insert = connection.prepareStatement(sql);
            this.minPageLength = minPageLength;
        }

        @Override
        public Integer call() throws Exception {
            int pagesSoFar = 0;
            SkipCounter skipCounter = new SkipCounter();
            while (true) {
                Path p = bzips.take();
                if (p.equals(POISON)) {
                    insert.executeBatch();
                    return pagesSoFar;
                }
                fileId = FILES_PROCESSED.incrementAndGet();
                sentenceId = 0;
                int pages = processDump(p, pagesSoFar, skipCounter);
                pagesSoFar += pages;
            }
        }


        private int processDump(Path bzip, int pagesSoFar, SkipCounter skipCounter) throws Exception {
            WikipediaArticlesDump dump = new WikipediaArticlesDump(bzip.toFile());

            int localPages = 0;
            int totalPages = pagesSoFar;
            long started = System.currentTimeMillis();

            for (String page : dump) {
                if (shouldProcess(page, skipCounter, totalPages)) {
                    int processed = processPage(page, skipCounter);
                    localPages += processed;
                    totalPages += processed;
                    if (totalPages > 0 && totalPages % 1000 == 0) {
                        long elapsed = System.currentTimeMillis() - started;
                        System.out.println("wrote " + totalPages +
                                " pages total and skipped " + skipCounter.getSkipped() +
                                " (for this file) in " + elapsed + "ms (for " +
                                bzip.getFileName() + ")");
                        System.out.println(skipCounter);
                        insert.executeBatch();
                    }
                }
            }
            return localPages;
        }


        private boolean shouldProcess(String page, SkipCounter skipCounter, int totalPages) {

            if (minPageLength > -1 && page.trim().length() < minPageLength) {
                skipCounter.minPageLength++;
                return false;
            }
            //require main space: https://en.wikipedia.org/wiki/Wikipedia:Namespace
            if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                skipCounter.ns++;
                Matcher m = NS_PATTERN.matcher(page);
                if (m.find()) {
                    //    System.out.println(m.group(0));
                }
                return false;
            }

            if (redirectMatcher.reset(page).find()) {
                skipCounter.redirect++;
                return false;
            }

            return true;

        }

        public int processPage(String page, SkipCounter skipCounter) throws IOException, SQLException {

            page = flipBytes(page);
            String s = cleaner.clean(page);
            if (s.trim().length() > 10) {
                Document doc = new Document(s);
                for (Sentence sent : doc.sentences()) {
                    String sentString = sent.toString();

                    //some sentences have lists in them; these sents
                    //contain new lines for each entry in the list.
                    //let's split out the list items into separate rows
                    for (String sentBit : sentString.split("[\\r\\n]+")) {
                        sentBit = cleanSent(sentBit);
                        if (sentBit.length() < MIN_SENT_LENGTH) {
                            continue;
                        }
                        String md5 = hash(sentBit);
                        insert.setString(1, md5);
                        insert.setInt(2, fileId);
                        insert.setInt(3, sentenceId++);
                        insert.setString(4, sentBit);
                        insert.addBatch();
                    }
                }
            } else {
                skipCounter.emptyContent++;
                return 0;
            }
            return 1;
        }

        private String hash(String sent) throws IOException {
            StringBuilder sb = new StringBuilder();
            try (TokenStream ts = analyzer.tokenStream("f", sent)) {
                CharTermAttribute charTermAttribute = ts.getAttribute(CharTermAttribute.class);
                ts.reset();
                while (ts.incrementToken()) {
                    sb.append(charTermAttribute.toString()).append(" ");
                }
            }
            return Base64.encodeBase64String(DigestUtils.sha256(sb.toString()));
        }


        private static String cleanSent(String sent) {
            //strip out list markup
            sent = sent.replaceFirst("^\\s*[*#\u2022]+\\s*", "");
            return sent.trim();
        }

        private class SkipCounter {
            int sampled = 0;
            int ns = 0;
            int redirect = 0;
            int emptyContent = 0;
            int minPageLength = 0;

            @Override
            public String toString() {
                return "SkipCounter{" +
                        "sampled=" + sampled +
                        ", ns=" + ns +
                        ", redirect=" + redirect +
                        ", emptyContent=" + emptyContent +
                        ", minPageLength=" + minPageLength +
                        '}';
            }

            public int getSkipped() {
                return sampled + ns + redirect + emptyContent + minPageLength;
            }
        }
    }
}