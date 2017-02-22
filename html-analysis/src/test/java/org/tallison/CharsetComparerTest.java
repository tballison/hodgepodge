package org.tallison;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

/**
 * Created by TALLISON on 2/22/2017.
 */
public class CharsetComparerTest {

    @Test
    public void testBasic() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200000; i++) {
            sb.append(" ");
        }
        CharsetComparer charsetComparer = new CharsetComparer();
        List<EncodingDetector> detectors = charsetComparer.getDetectors();
        for (EncodingDetector detector : detectors ) {
            Charset detected = detector
                    .detect(TikaInputStream
                            .get(sb.toString().getBytes(StandardCharsets.UTF_8)), new Metadata());
            System.out.println(detector.getClass().getSimpleName() + " : " + detected);
        }
    }
}
