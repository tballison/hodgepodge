import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * Created by TALLISON on 12/13/2016.
 */
public class RelationshipsExtractorTest {

    @Test
    public void testBasic() throws Exception {
        Path p = Paths.get(this.getClass().getResource("/test-documents/test_recursive_embedded.docx").toURI());
        //RelationshipsExtractor ex = new RelationshipsExtractor();
//        DocElementsCounter ex = new DocElementsCounter();
//        BodyElementsCounter ex = new BodyElementsCounter();
        DepthCounter ex = new DepthCounter();
        ex.process(p.toFile());
        ex.dump();
    }
}
