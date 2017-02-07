import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.util.SAXHelper;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class BodyElementsCounter {

    //a docx file should have one of these "main story" parts
    private final static String[] MAIN_STORY_PART_RELATIONS = new String[]{
            XWPFRelation.DOCUMENT.getContentType(),
            XWPFRelation.MACRO_DOCUMENT.getContentType(),
            XWPFRelation.TEMPLATE.getContentType(),
            XWPFRelation.MACRO_TEMPLATE_DOCUMENT.getContentType()

    };
    private Map<String, Integer> counts = new HashMap<>();

    public void process(File f) throws IOException {
        try (OPCPackage opcPackage = OPCPackage.open(f, PackageAccess.READ)) {
            for (PackagePart part : getStoryDocumentParts(opcPackage)) {
                try (InputStream is = part.getInputStream()) {
                    extract(is);
                } catch (Exception e) {
                    //swallow
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

    private void extract(InputStream is) {
        try {
            XMLReader reader = SAXHelper.newXMLReader();
            reader.setContentHandler(new ElementCounter());
            reader.parse(new InputSource(is));
        } catch (SAXException e) {
        } catch (ParserConfigurationException e) {
        } catch (IOException e) {
        }
    }

    private List<PackagePart> getStoryDocumentParts(OPCPackage opcPackage) {

        for (String contentType : MAIN_STORY_PART_RELATIONS) {
            List<PackagePart> pps = opcPackage.getPartsByContentType(contentType);
            if (pps.size() > 0) {
                return pps;
            }
        }
        return new ArrayList<>();
    }

    private class ElementCounter extends DefaultHandler {
        private Stack<String> stack = new Stack();
        boolean inBody = false;
        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {

            if (inBody) {
                stack.push(localName);
            }

            if (localName.equals("body")) {
                inBody = true;
            }



        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (localName.equals("body")) {
                inBody = false;
                return;
            }
            if (stack.size() == 1) {
                String combined = uri+":"+localName;
                Integer c = counts.get(combined);
                if (c == null) {
                    c = 0;
                }
                c++;
                counts.put(combined, c);
            }
            if (inBody) {
                stack.pop();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        BodyElementsCounter ex = new BodyElementsCounter();
        Path root = Paths.get(args[0]);
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(args[1]))) {
            String line = reader.readLine();
            int i = 0;
            while (line != null) {
                Path file = root.resolve(line.trim());
                try {
                    if (Files.isRegularFile(file)) {
                        System.err.println(i++ +" : " + file.toAbsolutePath().toString());
                        ex.process(file.toFile());
                    }
                    if (i % 1000 == 0) {
                        ex.dump();
                    }
                } catch (Exception e) {

                }
                line = reader.readLine();
            }
        }
        ex.dump();
    }

}

