import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestGetUrls {

    @Test
    public void testUrls() throws Exception {
        File f = Paths.get(getClass().getResource("enwiki_latest.html").toURI()).toFile();
        String html = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
        ArrayBlockingQueue<String> urls = GetWikiPageArticleBzips.extractUrls(html);
        assertEquals(60, urls.size());
    }

    @Test
    public void testUrls2() throws Exception {
        String url = "https://dumps.wikimedia.org/enwiki/latest/";
        ArrayBlockingQueue<String> urls = GetWikiPageArticleBzips.getBzipUrls(url);
        assertEquals(60, urls.size());
    }

    @Test
    public void flipBytes() throws Exception {
        String s = "  blah<text bytes=\"82301\" xml:space=\"preserve\">{{pp-protected blah";
        String flipped = WikiToTableSimple.flipBytes(s);
        assertTrue(flipped.contains("<text xml:space=\"preserve\" bytes=\"82301\">"));
    }
}
