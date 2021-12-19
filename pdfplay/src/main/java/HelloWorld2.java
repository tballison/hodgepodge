import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public class HelloWorld {

    //from https://pdfbox.apache.org/1.8/cookbook/documentcreation.html
    public static void main(String[] args) throws Exception {
// Create a document and add a page to it
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage( page );

// Create a new font object selecting one of the PDF base fonts
        PDFont font = PDType1Font.HELVETICA_BOLD;
// Start a new content stream which will "hold" the to be created content
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

// Define a text content stream using the selected font, moving the cursor and drawing the text "Hello World"
            contentStream.setFont(font, 60);
            contentStream.beginText();
            contentStream.newLineAtOffset(100, 600);
            contentStream.showText("Hello World!");
            contentStream.endText();
            contentStream.close();
// Make sure that the content stream is closed:

// Save the results and ensure that the document is properly closed:
        document.save( "/home/tallison/Desktop/hello_world.pdf");
        document.close();
    }
}
