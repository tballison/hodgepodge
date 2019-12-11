import org.apache.commons.io.FilenameUtils;
import org.junit.Test;
import org.tallison.selenium.URLNormalizer;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SimpleTest {

    @Test
    public void testUrl() throws Exception {
        URL u = new URL("https://www.abc.com/?type=future#myhref");
        System.out.println(u.getHost()+ " : " + u.getPath() + " : "+u.getFile() + " : "+u.getQuery());

        System.out.println(FilenameUtils.getExtension("/blah/blah.txt"));

        Path p = Paths.get("/this/is/a/test");

        for (int i = 0; i < p.getNameCount(); i++) {
            System.out.println("p "+p.getName(i));
        }
    }

    @Test
    public void testUrlNormalizer() throws Exception {
        String u = "https://stuff/otherstuff?start=100";
        assertEquals("https://stuff/otherstuff?start=100",
                URLNormalizer.normalize("", u));
        u = "https://abc.com";
        assertEquals("https://abc.com/", URLNormalizer.normalize("", u));
        u = "https://abc.com///////";
        assertEquals("https://abc.com/", URLNormalizer.normalize("", u));

        u = "";
        assertNull(URLNormalizer.normalize("https://abc.com", u));

        u = "/";
        assertEquals("https://abc.com/",
                URLNormalizer.normalize("https://abc.com", u));

        u = "/";
        assertEquals("https://abc.com/",
                URLNormalizer.normalize("https://abc.com/something/or/other.pdf", u));

    }

    @Test
    public void testURI() throws Exception {
        String u = "https://abc.com/news.php?feature=7519";
        URL url = new URL(u);
        System.out.println(url.getPath());
        System.out.println(url.getFile());
        String ext = FilenameUtils.getExtension(url.getPath());
        System.out.println("EXT: " + ext);
    }
}
