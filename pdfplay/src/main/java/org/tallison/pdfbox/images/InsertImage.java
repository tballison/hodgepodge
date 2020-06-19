package org.tallison.pdfbox.images;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.nio.file.Path;
import java.nio.file.Paths;

//Copied nearly verbatim from PDFBox's example code
public class InsertImage {

    public static void main(String[] args) throws Exception {
        Path img = Paths.get(args[0]);
        PDDocument doc = new PDDocument();
        //we will add the image to the first page.
        PDPage page = new PDPage();
        doc.addPage(page);
        // createFromFile is the easiest way with an image file
        // if you already have the image in a BufferedImage,
        // call LosslessFactory.createFromImage() instead

        PDImageXObject pdImage = PDImageXObject.createFromFileByContent(img.toFile(), doc);
        PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true);

        // contentStream.drawImage(ximage, 20, 20 );
        // better method inspired by http://stackoverflow.com/a/22318681/535646
        // reduce this value if the image is too large
        float scale = 4f;
        contentStream.drawImage(pdImage, 100, 300, pdImage.getWidth()*scale, pdImage.getHeight()*scale);

        contentStream.close();
        doc.save(args[1]);
    }
}
