import org.wikiclean.languages.Language;

import java.util.List;
import java.util.regex.Pattern;

public class Arabic extends Language {

    public Arabic() {
        super("ar");
    }

    protected List<Pattern> footerPatterns() {
        return this.footerPatterns(new String[]{
                "مصادر",//sources
                "المصادر",//sources
                "وصلات خارجية",//external links
                "مراجع",//reference
                "روابط أضافية",//additional links
                "اقرأ أيضاً",//read also
                "مواقع إلكترونية",//websites
                "موضوعات متعلقة",//related topics
                "انظر أيضًا", //see also
                "الوصلات الخارجية", //external links
                "روابط خارجية",//external links
                "مزيد من القراءة",//further reading
                "طالع أيضا",//see also
                "انظر أيضاً",//see also
                "المراجع والروابط الخارجية", //external references and links

                "See also", "References", "Further reading", "External Links", "Related pages"});
    }

    protected List<Pattern> categoryLinkPatterns() {
        return this.categoryLinkPatterns(new String[]{
                "تصنيف",//references
        });
    }
}