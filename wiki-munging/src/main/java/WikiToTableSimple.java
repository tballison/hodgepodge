
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This hashes every sentence and only outputs uniquely hashed sentences.
 * This is not exceedingly efficient, and I needed to allocate 20GB -Xmx
 * to get it to process the full English wikipedia.
 */
public class WikiToTableSimple {

    static Pattern BYTES_FLIPPER = Pattern.compile("<text bytes=\"(\\d+)\" xml:space=\\\"preserve\\\">");
    static Pattern NS_PATTERN = Pattern.compile("<ns>(\\d+)</ns>");
    static Options OPTIONS;
    private static final int MIN_SENT_LENGTH = 10;

    static {
        Option inputDir = new Option("i", "input",
                true, "directory with *-pages-articles-*.xml.bz2 files");
        inputDir.setRequired(true);

        Option outputFile = new Option("o", "output",
                true, "output file");
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


    private final String targLang;
    private final WikiClean cleaner;
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
        Path tableFile = Paths.get(commandLine.getOptionValue('o'));
        String targLang = commandLine.getOptionValue('l');

        int maxPages = -1;
        if (commandLine.hasOption("maxPages")) {
            maxPages = Integer.parseInt(commandLine.getOptionValue("maxPages"));
        }

        int minPageLength = -1;
        if (commandLine.hasOption("minLength")) {
            minPageLength = Integer.parseInt(commandLine.getOptionValue("minLength"));
        }

        WikiToTableSimple wikiToTable = new WikiToTableSimple(targLang);
        wikiToTable.execute(bzip, tableFile, maxPages, minPageLength);
    }


    public WikiToTableSimple(String targLang) throws Exception {
        this.targLang = targLang;
        Language language = getLanguage(targLang);
        cleaner = new WikiClean.Builder().withTitle(true)
                .withFooter(false).withLanguage(language).build();
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

    private void execute(Path bzipDir, Path tableFile, int maxPages, int minPageLength) throws Exception {

        int pages = 0;
        double samplingRate = -1.0;
        if (maxPages > -1) {
            int totalReports = countReports(bzipDir, targLang, minPageLength);
            samplingRate = (double) (maxPages + 1000) / (double) totalReports;
            samplingRate = (samplingRate > 1.0) ? -1.0 : samplingRate;
            System.err.println("finished counting reports: " + totalReports + " with a sampling rate: " + samplingRate);
        }
        long start = System.currentTimeMillis();
        try (BufferedWriter writer =
                     new BufferedWriter(
                             new OutputStreamWriter(
                                     new GzipCompressorOutputStream(
                                             Files.newOutputStream(tableFile)
                                     ), StandardCharsets.UTF_8))) {
            SkipCounter skipCounter = new SkipCounter();
            File[] bzips = bzipDir.toFile().listFiles();
            Arrays.sort(bzips);
            for (File bzip : bzips) {
                if (!bzip.getName().startsWith(targLang)) {
                    System.out.println("skipping: " + bzip.getName());
                    continue;
                }
                if (maxPages > -1 && pages > maxPages) {
                    break;
                }
                System.out.println("processing: " + bzip.getName());
                WikipediaArticlesDump dump = new WikipediaArticlesDump(bzip);

                pages += processDump(bzip.getName(), pages, maxPages, minPageLength, samplingRate, targLang,
                        dump, writer, skipCounter);
            }
        }

        System.out.println("Finished " + pages + " pages in " + (System.currentTimeMillis() - start) + " ms");
    }

    private int processDump(String fileName, int pagesSoFar, int maxPages, int minPageLength,
                            double samplingRate, String targetLang,
                            WikipediaArticlesDump dump, BufferedWriter writer, SkipCounter skipCounter) throws Exception {
        if (maxPages > -1 && pagesSoFar > maxPages) {
            return 0;
        }

        Random random = new Random();
        int localPages = 0;
        int totalPages = pagesSoFar;
        long started = System.currentTimeMillis();

        for (String page : dump) {
            if (shouldProcess(page, skipCounter, minPageLength, maxPages, totalPages, samplingRate, random)) {
                int processed = processPage(page, writer, skipCounter);
                localPages += processed;
                totalPages += processed;
                if (totalPages > 0 && totalPages % 1000 == 0) {
                    long elapsed = System.currentTimeMillis() - started;
                    System.out.println("wrote " + totalPages +
                            " pages total and skipped " + skipCounter.getSkipped() +
                            " (for this file) in " + elapsed + "ms (for " +
                            fileName + ") hashes: "+digests.size());
                    System.out.println(skipCounter);
                }
            }
        }
        return localPages;
    }

    //this is an abomination
    private boolean shouldProcess(String page, SkipCounter skipCounter, int minPageLength,
                                  int maxPages, int totalPages, double samplingRate, Random random) {

        if (minPageLength > -1 && page.trim().length() < minPageLength) {
            skipCounter.minPageLength++;
            return false;
        }
        if (maxPages > -1 && totalPages > maxPages) {
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

        if (samplingRate > -1.0 &&
                random.nextDouble() > samplingRate) {
            skipCounter.sampled++;
            return false;
        }
        return true;

    }

    public int processPage(String page, BufferedWriter writer, SkipCounter skipCounter) throws IOException {

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
                    String digest = hash(sentBit);
                    if (digests.contains(digest)) {
                        continue;
                    }
                    digests.add(digest);
                    writer.write(sentBit);
                    writer.write("\n");
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


    private int countReports(Path bzipDir, String fieldName, int minPageLength) throws IOException {
        int total = 0;
        long start = System.currentTimeMillis();
        int maxPFileName = -1;
        for (File bzip : bzipDir.toFile().listFiles()) {
            if (!bzip.getName().startsWith(fieldName)) {
                continue;
            }
            Matcher m = Pattern.compile("p(\\d+)\\.bz2").matcher(bzip.getName());
            System.out.println(bzip.getName());
            if (m.find()) {
                Integer lastP = null;
                try {
                    lastP = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                if (lastP != null && lastP > maxPFileName) {
                    maxPFileName = lastP;
                }
            }
        }
        if (maxPFileName > -1) {
            System.out.println("max pfilname: " + maxPFileName);
            return (int) (maxPFileName * 0.70);
            //return maxPFileName;
        }
        int skipped = 0;
        for (File bzip : bzipDir.toFile().listFiles()) {
            if (!bzip.getName().startsWith(fieldName)) {
                continue;
            }

            WikipediaArticlesDump dump = new WikipediaArticlesDump(bzip);
            for (String page : dump) {
                if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                    skipped++;
                    continue;
                }

                String s = page;
                if (redirectMatcher.reset(s).find()) {
                    skipped++;
                    continue;
                }
                if (minPageLength > -1 && page.trim().length() < minPageLength) {
                    continue;
                }
                total++;

                if (total % 1000 == 0) {
                    double elapsed = (System.currentTimeMillis() - start) / 1000;
                    System.err.println("still counting: " + total + " : with " + skipped +
                            " skipped in " + elapsed + " seconds");
                }
            }
        }
        return total;
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