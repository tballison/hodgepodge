import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


public class XMFExtractor {

    public static void main(String[] args) throws Exception {
        Path srcDir = Paths.get(args[0]);
        Path fileList = Paths.get(args[1]);
        Path targDir = Paths.get(args[2]);

        BufferedReader r = Files.newBufferedReader(fileList);
        String line = r.readLine();
        Parser p = new AutoDetectParser();
        while (line != null) {
            try {
                Path inputFile = srcDir.resolve(line);
                ParseContext pc = new ParseContext();
                EmbeddedDocumentExtractor ex = new XMFHandler(targDir, inputFile);
                pc.set(EmbeddedDocumentExtractor.class, ex);
                System.out.println("parsing: " + inputFile);
                try (InputStream is = TikaInputStream.get(inputFile)) {
                    p.parse(is, new DefaultHandler(), new Metadata(), pc);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            line = r.readLine();
        }
    }

    private static class XMFHandler implements EmbeddedDocumentExtractor {

        private final Path targDir;
        private final Path srcFilePath;
        private int fileCounter = 0;
        XMFHandler(Path targDir, Path srcFilePath) {
            this.targDir = targDir;
            this.srcFilePath = srcFilePath;
        }

        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            String ct = metadata.get(Metadata.CONTENT_TYPE);
            System.out.println("CD: "+ct);
            if (ct != null && (
                    ct.contains("x-emf") || ct.contains("x-wmf")
                    || ct.contains("msmetafile") || ct.contains("x-tika-msoffice")
            )) {
                return true;
            }
            return false;
        }

        @Override
        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean b) throws SAXException, IOException {
            String ct = metadata.get(Metadata.CONTENT_TYPE);
            if (ct == null) {
                return;
            }
            String xmf = "";
            if (ct.contains("emf")) {
                xmf = "emf";
            } else if (ct.contains("wmf")) {
                xmf = "wmf";
            } else if (ct.contains("meta")) {
                xmf = "meta";
            } else if (ct.contains("x-tika")) {
                xmf = "tika-msoffice";
            } else {
                xmf = "unk";
            }

            Path target = targDir.resolve(xmf+"/"+srcFilePath.getFileName().toString()+"-"+fileCounter++ + "."+xmf);
            Files.createDirectories(target.getParent());
            if (ct.contains("x-tika")) {
                TikaInputStream tis = TikaInputStream.get(inputStream);
                Object dNodeObject = tis.getOpenContainer();
                if (dNodeObject instanceof DirectoryNode) {
                    OutputStream os = Files.newOutputStream(target);
                    ((DirectoryNode)dNodeObject).getNFileSystem().writeFilesystem(os);
                    os.flush();
                    os.close();
                }
            } else {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
