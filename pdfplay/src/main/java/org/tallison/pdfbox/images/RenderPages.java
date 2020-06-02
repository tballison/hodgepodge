package org.tallison.pdfbox.images;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class RenderPages {


    private static final List<String> JPEG = Arrays.asList(
            COSName.DCT_DECODE.getName(),
            COSName.DCT_DECODE_ABBREVIATION.getName());

    private static final List<String> JP2 =
            Arrays.asList(COSName.JPX_DECODE.getName());

    private static final List<String> JB2 = Arrays.asList(
            COSName.JBIG2_DECODE.getName());


    public static void main(String[] args) throws Exception {
        RenderPages ex = new RenderPages();
        ex.execute(Paths.get(args[0]), Paths.get(args[1]));
    }
    private int pageNumber = 1;
    private void execute(Path src, Path imageDir) {
        System.out.println(src);
        for (File f : src.toFile().listFiles()) {
            if (f.getName().toLowerCase().endsWith(".pdf")) {
                System.out.println("processing: "+f);
                try {
                    processFile(f, imageDir);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void processFile(File f, Path imageDir) throws IOException {
        String imageBaseName = f.getName().replaceAll("(?i).pdf$", "");
        try (PDDocument pd = PDDocument.load(f)) {
            for (int i = 1; i < pd.getNumberOfPages(); i++) {
                System.out.println("processing "+f + " page: "+i);
                try {
                    Path imageSubDir = imageDir.resolve(imageBaseName);
                    Files.createDirectories(imageSubDir);
                    processPage(pd, imageSubDir, imageBaseName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        pageNumber = 1;
    }

    private void processPage(PDDocument pd, Path imageDir, String imageBaseName) throws Exception {
        Path output = imageDir.resolve(imageBaseName+"_"+pageNumber+".jpg");
        PDFRenderer renderer = new PDFRenderer(pd);

            BufferedImage image = renderer.renderImage(pageNumber, 5, ImageType.RGB);
            try (OutputStream os = Files.newOutputStream(output)) {
                //TODO: get output format from TesseractConfig
                ImageIOUtil.writeImage(image, "jpeg",
                        os, 500, 1.0f);
            }

        pageNumber++;
    }
}
