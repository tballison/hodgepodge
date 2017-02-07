package org.apache.tika.parsers;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;

public class EmbeddedVisioTest {

    private static final Set<MediaType> EXCLUDES = new HashSet<>();

    static {
        EXCLUDES.add(MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document"));
        EXCLUDES.add(MediaType.application("pdf"));
    }

    private static final Parser PARSERS[] = new Parser[] {
            // documents
            new org.apache.tika.parser.html.HtmlParser(),
            new org.apache.tika.parser.rtf.RTFParser(),
            ParserDecorator.withoutTypes(new org.apache.tika.parser.pdf.PDFParser(), EXCLUDES),
            new org.apache.tika.parser.txt.TXTParser(),
            //new org.apache.tika.parser.microsoft.OfficeParser(),
            new org.apache.tika.parser.microsoft.OldExcelParser(),
            ParserDecorator.withoutTypes(new org.apache.tika.parser.microsoft.ooxml.OOXMLParser(),
                    EXCLUDES),
            new org.apache.tika.parser.odf.OpenDocumentParser(),
            new org.apache.tika.parser.iwork.IWorkPackageParser(),
            new org.apache.tika.parser.xml.DcXMLParser(),
            new org.apache.tika.parser.epub.EpubParser(),
    };

    private static final AutoDetectParser PARSER_INSTANCE = new AutoDetectParser(PARSERS);

    private static final Tika TIKA_INSTANCE = new Tika(PARSER_INSTANCE.getDetector(), PARSER_INSTANCE);
    @Test
    public void testBasic() throws Exception {
//        String docName = "testWORD_embeddedVisio.docx";
        String docName = "testPDF.pdf";
        try (InputStream is =
                     TikaInputStream.get(this.getClass().getResourceAsStream("/test-documents/"+docName))) {
            BodyContentHandler contentHandler = new BodyContentHandler();
            ParseContext pc = new ParseContext();
            Metadata metadata = new Metadata();
            String txt = TIKA_INSTANCE.parseToString(is, metadata);
            for (String k : metadata.names()) {
                for (String v : metadata.getValues(k)) {
                    System.out.println(k + " : " + v);
                }
            }
            System.out.println(txt);
        }
    }
}
