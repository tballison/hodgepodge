import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class MetadataInjection {

    //from https://pdfbox.apache.org/1.8/cookbook/documentcreation.html
    public static void main(String[] args) throws Exception {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000000; i++) {
            sb.append("0");
        }
 // Create a document and add a page to it
        PDDocument document = new PDDocument();
        document.getDocumentInformation().setAuthor(sb.toString());
        PDPage page = new PDPage();
        document.addPage(page);

// Create a new font object selecting one of the PDF base fonts
        PDFont font = PDType1Font.HELVETICA_BOLD;
// Start a new content stream which will "hold" the to be created content
        PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(font, 32);

            contentStream.beginText();

            contentStream.newLineAtOffset(100, 500);
            contentStream.showText("Hello World!");
            contentStream.endText();

        contentStream.close();
// Make sure that the content stream is closed:

// Save the results and ensure that the document is properly closed:
        document.save("hello_world_metadata_1000000_length_author.pdf");
        document.close();
    }
}
