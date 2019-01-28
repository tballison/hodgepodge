import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Optional;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.eval.tokens.AnalyzerManager;
import org.wikiclean.WikiClean;
import org.wikiclean.WikipediaArticlesDump;

public class IndexWikiBzip2 {

    private WikiClean cleaner = new WikiClean.Builder()
            .withLanguage(WikiClean.WikiLanguage.EN).withTitle(false)
            .withFooter(false).build();

    private Matcher redirectMatcher = Pattern.compile("<text[^>]*>#").matcher("");


    public static void main(String[] args) throws IOException {
        Path bzip = Paths.get(args[0]);
        Path indexDir = Paths.get(args[1]);
        String field = args[2];
        int maxPages = -1;
        if (args.length > 3) {
            maxPages = Integer.parseInt(args[3]);
        }

        IndexWikiBzip2 indexer = new IndexWikiBzip2();
        indexer.execute(bzip, indexDir, field, maxPages);
    }

    private void execute(Path bzipDir, Path indexDir,
                         String fieldName, int maxPages) throws IOException {
        IndexWriter indexWriter = getIndexWriter(indexDir);

        int pages = 0;

        int totalReports = countReports(bzipDir, fieldName);
        double samplingRate = (double)(maxPages+1000)/(double)totalReports;
        samplingRate = (samplingRate > 1.0) ? -1.0 : samplingRate;
        System.err.println("finished counting reports: "+ totalReports + " with a sampling rate: " + samplingRate);
        Random random = new Random();
        for (File bzip : bzipDir.toFile().listFiles()) {
            if (!bzip.getName().startsWith(fieldName)) {
                continue;
            }
            if (maxPages > -1 && pages > maxPages) {
                break;
            }
            WikipediaArticlesDump stream = new WikipediaArticlesDump(bzip);

            List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();

            //build language detector:
            LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                    .withProfiles(languageProfiles)
                    .build();

            //create a text object factory
            TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();


            for (String page : stream) {
                if (maxPages > -1 && pages > maxPages) {
                    break;
                }
                if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                    continue;
                }

                if (redirectMatcher.reset(page).find()) {
                    continue;
                }

                if (samplingRate > -1.0 &&
                        random.nextDouble() > samplingRate) {
                    continue;
                }
                String s = cleaner.clean(page).replaceAll("\\n+", " ");

                TextObject textObject = textObjectFactory.forText(s);

                Optional<LdLocale> detectedLang = languageDetector.detect(textObject);
                String detectedLangString = "";
                if (detectedLang.isPresent()) {
                    detectedLangString = detectedLang.get().toString();
                } else {
                    continue;
                }
                detectedLangString = detectedLangString.toLowerCase(Locale.US);
                if (!detectedLangString.equals(fieldName) &&
                        !detectedLangString.startsWith(fieldName+"-")) {
                    System.out.println("skipping: " + detectedLangString + " : " + s);
                    continue;
                }
//                System.out.println("indexing: "+s);
                Document document = new Document();
                document.add(new TextField(detectedLangString, s, Field.Store.NO));
                indexWriter.addDocument(document);
                pages++;

            }
        }

        indexWriter.flush();
        indexWriter.close();


    }

    private int countReports(Path bzipDir, String fieldName) throws IOException {
        int total = 0;
        long start = new Date().getTime();
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
            System.out.println("max pfilname: "+ maxPFileName);
            return (int)(maxPFileName*0.70);
            //return maxPFileName;
        }
        int skipped =0;
        for (File bzip : bzipDir.toFile().listFiles()) {
            if (!bzip.getName().startsWith(fieldName)) {
                continue;
            }

            WikipediaArticlesDump stream =
                    new WikipediaArticlesDump(bzip);
            for (String page : stream) {
                total++;

                if (total %1000 == 0) {
                    double elapsed = (new Date().getTime()-start)/1000;
                    System.err.println("still counting: "+total + " : with "+skipped +
                            " skipped in " + elapsed +" seconds");
                }
                if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                    skipped++;
                    continue;
                }

                String s = page;
                if (redirectMatcher.reset(s).find()) {
                    skipped++;
                    continue;
                }

            }
        }
        return total;
    }

    private IndexWriter getIndexWriter(Path indexDir) throws IOException {
        AnalyzerManager analyzerManager = AnalyzerManager.newInstance(1000000);

        IndexWriterConfig iwConfig = new IndexWriterConfig(analyzerManager.getCommonTokensAnalyzer());
        iwConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        IndexWriter writer = new IndexWriter(FSDirectory.open(indexDir), iwConfig);
        return writer;
    }
}
