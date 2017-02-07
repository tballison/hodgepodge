import java.nio.file.Paths;

import org.apache.tika.Tika;

/**
 * Created by TALLISON on 9/30/2016.
 */
public class TikaTest {
    public static void main(String[] args) throws Exception{
        System.out.println(new Tika().parseToString(Paths.get(args[0])));
    }
}
