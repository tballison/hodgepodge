import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.wikiclean.WikiClean;
import org.wikiclean.WikiCleanBuilder;
import org.wikiclean.WikipediaBz2DumpInputStream;

public class IndexWikiBzip2 {

    private WikiClean cleaner = new WikiCleanBuilder()
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

        String page;
        int pages = 0;

        maxPages = -1;
        int totalReports = 10000000;//countReports(bzipDir, fieldName);
        double samplingRate = (double)(maxPages+1000)/(double)totalReports;
        System.err.println("finished counting reports: "+ totalReports + " with a sampling rate: " + samplingRate);
        Random random = new Random();
        for (File bzip : bzipDir.toFile().listFiles()) {
            if (!bzip.getName().startsWith(fieldName)) {
                continue;
            }
            WikipediaBz2DumpInputStream stream =
                    new WikipediaBz2DumpInputStream(bzip.getAbsolutePath().toString());

            List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();

            //build language detector:
            LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                    .withProfiles(languageProfiles)
                    .build();

            //create a text object factory
            TextObjectFactory textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();


            while ((page = stream.readNext()) != null) {
                if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                    continue;
                }

                if (redirectMatcher.reset(page).find()) {
                    continue;
                }

                if (random.nextDouble() > samplingRate) {
                    continue;
                }
                String s = cleaner.clean(page).replaceAll("\\n+", " ");

                TextObject textObject = textObjectFactory.forText(s);

                Optional<LdLocale> lang = languageDetector.detect(textObject);
                String langString = "";
                if (lang.isPresent()) {
                    langString = lang.get().toString();
                }
                if (!langString.equals(fieldName)) {
                    System.out.println("skipping: " + langString + " : " + s);
                    continue;
                }
                System.out.println("indexing: "+s);
                Document document = new Document();
                document.add(new TextField(fieldName, s, Field.Store.NO));
                indexWriter.addDocument(document);
                pages++;
                if (maxPages > -1 && pages > maxPages) {
                    break;
                }
            }
        }

        indexWriter.flush();
        indexWriter.close();


    }

    private int countReports(Path bzipDir, String fieldName) throws IOException {
        int total = 0;
        long start = new Date().getTime();
        for (File bzip : bzipDir.toFile().listFiles()) {
            if (!bzip.getName().startsWith(fieldName)) {
                continue;
            }

            WikipediaBz2DumpInputStream stream =
                    new WikipediaBz2DumpInputStream(bzip.getAbsolutePath().toString());
            String page;
            while ((page = stream.readNext()) != null) {

                if (total %1000 == 0) {
                    long elapsed = new Date().getTime()-start;
                    System.err.println("still counting: "+total + " : "+elapsed);
                }
                total++;
                if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                    continue;
                }

                String s = page;
                if (redirectMatcher.reset(s).find()) {
                    continue;
                }


            }
        }
        return total;
    }

    private IndexWriter getIndexWriter(Path indexDir) throws IOException {
        Analyzer analyzer = new StandardAnalyzer(CharArraySet.EMPTY_SET);

        IndexWriterConfig iwConfig = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(FSDirectory.open(indexDir), iwConfig);
        return writer;
    }
}
