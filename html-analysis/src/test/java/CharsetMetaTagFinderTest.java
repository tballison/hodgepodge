import org.junit.Test;
import org.tallison.CharsetMetaTagFinder;

/**
 * Created by TALLISON on 2/22/2017.
 */
public class CharsetMetaTagFinderTest {
    @Test
    public void testCommentStripper() {
        String s = "abc asdf  or other";
        System.out.println(CharsetMetaTagFinder.stripComments(s));
    }
}
