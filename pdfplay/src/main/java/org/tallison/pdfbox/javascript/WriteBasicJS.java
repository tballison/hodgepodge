package org.tallison.pdfbox.javascript;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.action.PDPageAdditionalActions;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationPopup;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class WriteBasicJS {

    public static void main(String[] args) throws Exception {
        Path cwd = Paths.get(args[0]);
        PDDocument document = new PDDocument();
        //add a page
        PDPage page = new PDPage();
        document.addPage( page );


// Create a new font object selecting one of the PDF base fonts
        PDFont font = PDType1Font.HELVETICA_BOLD;

// Start a new content stream which will "hold" the to be created content
        PDPageContentStream contentStream = new PDPageContentStream(document, page);

// Define a text content stream using the selected font, moving the cursor and drawing the text "Hello World"
        contentStream.beginText();
        contentStream.setFont( font, 12 );
        contentStream.moveTextPositionByAmount( 100, 700 );
        contentStream.drawString( "Hello World" );
        contentStream.endText();

// Make sure that the content stream is closed:
        contentStream.close();



        String javaScript = "app.alert( {cMsg: 'this is an example', nIcon: 3,"
                + " nType: 0, cTitle: 'PDFBox Javascript example’} );";

        //Creating PDActionJavaScript object
        PDActionJavaScript PDAjavascript = new PDActionJavaScript(javaScript);

        //Embedding java script
        document.getDocumentCatalog().setOpenAction(PDAjavascript);

        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(new PDRectangle(1.0f, 1.0f, 2.0f, 0.3f));
        link.setAction(PDAjavascript);
        List<PDAnnotation> annotations = new ArrayList<>();
        annotations.add(link);
        page.setAnnotations(annotations);
        //Saving the document
        document.save( cwd.resolve("example_js2.pdf").toFile() );
        System.out.println("Data added to the given PDF");

        //Closing the document
        document.close();

    }
}
