
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.wikiclean.WikiClean;
import org.wikiclean.WikipediaArticlesDump;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WikiToTableSimple {

    static Pattern BYTES_FLIPPER = Pattern.compile("<text bytes=\"(\\d+)\" xml:space=\\\"preserve\\\">");
    static Pattern NS_PATTERN = Pattern.compile("<ns>(\\d+)</ns>");
    static Options OPTIONS;

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


    private WikiClean cleaner = new WikiClean.Builder().withTitle(false)
            .withFooter(false).build();

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

        WikiToTableSimple wikiToTable = new WikiToTableSimple();
        wikiToTable.execute(bzip, tableFile, targLang, maxPages, minPageLength);
    }


    public WikiToTableSimple() throws Exception {
    }

    public static String flipBytes(String s) {
        Matcher m = BYTES_FLIPPER.matcher(s);
        return m.replaceFirst("<text xml:space=\"preserve\" bytes=\"$1\">");
    }

    private void execute(Path bzipDir, Path tableFile,
                         String targLang, int maxPages, int minPageLength) throws Exception {

        int pages = 0;
        double samplingRate = -1.0;
        if (maxPages > -1) {
            int totalReports = countReports(bzipDir, targLang, minPageLength);
            samplingRate = (double) (maxPages + 1000) / (double) totalReports;
            samplingRate = (samplingRate > 1.0) ? -1.0 : samplingRate;
            System.err.println("finished counting reports: " + totalReports + " with a sampling rate: " + samplingRate);
        }

        try (BufferedWriter writer =
                     new BufferedWriter(
                             new OutputStreamWriter(
                                     new GzipCompressorOutputStream(
                                             Files.newOutputStream(tableFile)
                                     ) , StandardCharsets.UTF_8))) {
            SkipCounter skipCounter = new SkipCounter();
            for (File bzip : bzipDir.toFile().listFiles()) {
                if (!bzip.getName().startsWith(targLang)) {
                    continue;
                }
                if (maxPages > -1 && pages > maxPages) {
                    break;
                }
                WikipediaArticlesDump dump = new WikipediaArticlesDump(bzip);

                pages += processDump(bzip.getName(), pages, maxPages, minPageLength, samplingRate, targLang,
                        dump, writer, skipCounter);
            }
        }
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
        int skipped = 0;
        long started = System.currentTimeMillis();
        for (String page : dump) {
            if (minPageLength > -1 && page.trim().length() < minPageLength) {
                skipped++;
                skipCounter.minPageLength++;
                continue;
            }
            if (maxPages > -1 && totalPages > maxPages) {
                break;
            }
            //require main space: https://en.wikipedia.org/wiki/Wikipedia:Namespace
            if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                skipped++;
                skipCounter.ns++;
                Matcher m = NS_PATTERN.matcher(page);
                if (m.find()) {
                //    System.out.println(m.group(0));
                }
                continue;
            }

            if (redirectMatcher.reset(page).find()) {
                skipped++;
                skipCounter.redirect++;
                continue;
            }

            if (samplingRate > -1.0 &&
                    random.nextDouble() > samplingRate) {
                skipped++;
                skipCounter.sampled++;
                continue;
            }

            page = flipBytes(page);
            String s = cleaner.clean(page);
            s = s.replaceAll("[\\t\\r\\n]+", " ");
            if (s.trim().length() > 10) {
                writer.write(s);
                writer.write("\n");
            } else {
                skipCounter.emptyContent++;
                skipped++;
            }
            localPages++;
            totalPages++;
            if (totalPages %1000 == 0) {
                long elapsed = System.currentTimeMillis()-started;
                System.out.println("wrote "+totalPages+
                        " pages total and skipped "+skipped +
                        " (for this file) in "+elapsed + "ms (for "+
                        fileName+")");
                System.out.println(skipCounter);
            }

        }
        return localPages;
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
                    double elapsed = (System.currentTimeMillis()- start) / 1000;
                    System.err.println("still counting: " + total + " : with " + skipped +
                            " skipped in " + elapsed + " seconds");
                }
            }
        }
        return total;
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
    }
}