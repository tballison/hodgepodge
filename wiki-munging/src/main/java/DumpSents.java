import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

public class DumpSents {

    public static void main(String[] args) throws Exception {
        Connection connection = DriverManager.getConnection(args[0]);
        Path file = Paths.get(args[1]);

        String sql = "select sent from wiki \n" +
                "order by fileid, sentid";
        try (BufferedWriter writer =
                     new BufferedWriter(
                             new OutputStreamWriter(
                                     new GzipCompressorOutputStream(
                                             Files.newOutputStream(file)
                                     ), StandardCharsets.UTF_8))) {
            try (ResultSet rs = connection.createStatement().executeQuery(sql)) {
                while (rs.next()) {
                    String sent = rs.getString(1);
                    writer.write(sent+"\n");
                }
            }
        }
    }
}
