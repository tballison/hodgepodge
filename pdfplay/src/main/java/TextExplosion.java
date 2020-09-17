import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.util.Random;

public class TextExplosion {

    //from https://pdfbox.apache.org/1.8/cookbook/documentcreation.html
    public static void main(String[] args) throws Exception {
        int numPages = Integer.parseInt(args[0]);
        int textPieces = Integer.parseInt(args[1]);
        int textPieceLength = Integer.parseInt(args[2]);
// Create a document and add a page to it
        PDDocument document = new PDDocument();
        for (int p = 0; p < numPages; p++) {
            PDPage page = new PDPage();
            document.addPage(page);

            Random r = new Random();
            PDFont font = PDType1Font.HELVETICA_BOLD;
// Start a new content stream which will hold the to be created content
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(font, 1);
            for (int i = 0; i < textPieces; i++) {
                StringBuilder kaboom = new StringBuilder();
                int cp = 65 + r.nextInt(26);
                String s = new String(new int[]{cp}, 0, 1);
                while (kaboom.length() < textPieceLength) {
                    kaboom.append(s);
                }
// Create a new font object selecting one of the PDF base fonts

// Define a text content stream using the selected font, moving the cursor and drawing the text "Hello World"

                contentStream.beginText();
                contentStream.newLineAtOffset(r.nextInt(100), r.nextInt(900));
                contentStream.showText(kaboom.toString());
                contentStream.endText();
                System.out.println("adding stream " + p + "# " + i);
            }
            contentStream.close();
        }
// Make sure that the content stream is closed:

// Save the results and ensure that the document is properly closed:

        document.save( numPages+"_"+textPieces+"_"+textPieceLength+".pdf");
        document.close();
    }
}
