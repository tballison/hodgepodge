import java.io.File;
import java.util.List;

import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import org.apache.commons.io.FileUtils;

/**
 * Created by TALLISON on 2/7/2017.
 */
public class OptimaizeDev {
    public static void main(String[] args) throws Exception {
        String s = "尼泊尔卢比，符號RS;代碼是NPR，是尼泊尔的货币单位，1盧比=100派薩。现时与印度卢比掛钩，币值为印度卢比的三分之二";

        s = FileUtils.readFileToString(new File("C:/data/test.txt"), "UTF-8");
        List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();

        //build language detector:
        LanguageDetector languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(languageProfiles)
                .build();

        for (DetectedLanguage detectedLanguage : languageDetector.getProbabilities(s)) {
            System.out.println(detectedLanguage.getLocale().toString() + " : " + detectedLanguage.getProbability());
        }

        System.out.println("OVERALL: " + languageDetector.detect(s));

    }
}
