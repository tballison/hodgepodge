import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileBuilder;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.profiles.LanguageProfileWriter;
import org.wikiclean.WikiClean;
import org.wikiclean.WikiCleanBuilder;
import org.wikiclean.WikipediaBz2DumpInputStream;

/**
 * Created by TALLISON on 2/7/2017.
 */
public class BuildOptimaizeLanguageModel {

    public static void main(String[] args) throws Exception {
        Path bzip = Paths.get(args[0]);
        LdLocale langCode = LdLocale.fromString(args[1]);
        Path langDir = Paths.get(args[2]);
        int minFreq = 100;
        int maxPages = 1000;
        if (args.length > 3) {
            minFreq = Integer.parseInt(args[3]);
        }
        if (args.length > 4) {
            maxPages = Integer.parseInt(args[4]);
        }

        LanguageProfileBuilder languageProfileBuilder = new LanguageProfileBuilder(langCode);
        languageProfileBuilder.minimalFrequency(minFreq);

        WikiClean cleaner = new WikiCleanBuilder()
                .withLanguage(WikiClean.WikiLanguage.EN).withTitle(false)
                .withFooter(false).build();

        WikipediaBz2DumpInputStream stream =
                new WikipediaBz2DumpInputStream(bzip.toAbsolutePath().toString());

        List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();

        //build language detector:
        LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();


        String page;
        while ((page = stream.readNext()) != null) {
            if (page.contains("<ns>") && !page.contains("<ns>0</ns>")) {
                continue;
            }

            String s = cleaner.clean(page).replaceAll("\\n+", " ");
            //System.out.println("S: " + pages + " : "+s);
            if (s.startsWith("#REDIRECT")) {
                continue;
            }

            if (s.startsWith("#")) {
                continue;
            }

            languageProfileBuilder.addText(page);
        }


        LanguageProfile languageProfile = languageProfileBuilder.build();
        LanguageProfileWriter languageProfileWriter = new LanguageProfileWriter();
        languageProfileWriter.writeToDirectory(languageProfile, langDir.toFile());
    }
}
