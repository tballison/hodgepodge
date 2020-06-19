import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDStream;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public class FilterExplosion {

    public static void main(String[] args) throws Exception {
        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);

        int numFilters = 10000;
        FilterExplosion.explode(input, output, numFilters);
    }

    private static void explode(Path input, Path output, int numFilters) throws IOException {
        PDDocument doc = null;
        doc = PDDocument.load(input.toFile());
        doc.setAllSecurityToBeRemoved(true);
        Random random = new Random();
        for (COSObject cosObject : doc.getDocument().getObjects()) {
            COSBase base = cosObject.getObject();
            if (base instanceof COSStream) {
                COSStream stream = (COSStream) base;
                if (
                        COSName.XOBJECT.equals(stream.getItem(COSName.TYPE)) &&
                                COSName.IMAGE.equals(stream.getItem(COSName.SUBTYPE))) {
                    continue;
                }
                byte[] bytes;
                try {
                    bytes = new PDStream(stream).toByteArray();
                } catch (IOException ex) {
                    System.err.println("skip " +
                            cosObject.getObjectNumber() + " " +
                            cosObject.getGenerationNumber() + " obj: " +
                            ex.getMessage());
                    continue;
                }
                COSBase filterBase = stream.getFilters();
                COSArray filters = new COSArray();
                for (int i = 0; i < numFilters; i++) {
                    if (random.nextDouble() > 0.99) {
                        filters.add(COSName.LZW_DECODE);
                    } else {
                        filters.add(COSName.FLATE_DECODE);
                    }
                }
                stream.setItem(COSName.FILTER, filters);
                OutputStream streamOut = stream.createOutputStream();
                streamOut.write(bytes);
                streamOut.close();
            }
        }
        doc.getDocumentCatalog();
        doc.getDocument().setIsXRefStream(false);
        doc.save(output.toFile());

    }
}
