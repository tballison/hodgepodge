import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;

/**
 * Created by TALLISON on 12/13/2016.
 */
public class RelationshipsExtractor {
    private Map<String, Integer> counts = new HashMap<>();

    public void process(File f) throws IOException {
        try (OPCPackage opcPackage = OPCPackage.open(f, PackageAccess.READ)) {

            PackageRelationshipCollection prc = opcPackage.getRelationships();
            for (int i = 0; i < prc.size(); i++) {
                PackageRelationship rel = prc.getRelationship(i);
                PackagePart part = opcPackage.getPart(rel);

                PackageRelationshipCollection prcChild = part.getRelationships();
                for (int j = 0; j < prcChild.size(); j++) {
                    PackageRelationship relChild = prcChild.getRelationship(j);
                    PackagePart partChild = part.getRelatedPart(relChild);
                    String s = rel.getRelationshipType() + "\t"+relChild.getRelationshipType()+"\t"+relChild.getTargetURI();
                    //+"\t"+partChild.getSize());
                    Integer c = counts.get(s);
                    if (c == null) {
                        c = 0;
                    }
                    c++;
                    counts.put(s,c);
                }
            }
        } catch (InvalidFormatException e) {

        }
    }

    public void dump() {
        Map<String, Integer> sorted = MapUtil.sortByValue(counts);
        for (Map.Entry<String, Integer> e : sorted.entrySet()) {
            System.out.println(e.getKey() + "\t" +e.getValue());
        }
    }

    public static void main(String[] args) throws Exception {
        RelationshipsExtractor ex = new RelationshipsExtractor();
        Path root = Paths.get(args[0]);
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(args[1]))) {
            String line = reader.readLine();
            int i = 0;
            while (line != null) {
                Path file = root.resolve(line.trim());
                try {
                    if (Files.isRegularFile(file)) {
                        System.err.println(i++ + " : " + file.toAbsolutePath().toString());
                        ex.process(file.toFile());

                        if (i % 1000 == 0) {
                            ex.dump();
                        }
                    }
                } catch (Exception e) {

                }
                line = reader.readLine();
            }
        }
        ex.dump();
    }
}

