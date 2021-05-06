package org.tallison.pdfbox.images;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.MissingImageReaderException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public class ExtractInlineImages {


    private static final List<String> JPEG = Arrays.asList(
            COSName.DCT_DECODE.getName(),
            COSName.DCT_DECODE_ABBREVIATION.getName());

    private static final List<String> JP2 =
            Arrays.asList(COSName.JPX_DECODE.getName());

    private static final List<String> JB2 = Arrays.asList(
            COSName.JBIG2_DECODE.getName());


    public static void main(String[] args) throws Exception {
        ExtractInlineImages ex = new ExtractInlineImages();
        ex.execute(Paths.get(args[0]), Paths.get(args[1]));
    }
    private int imageCountPerPage = 0;
    private int pageNumber = 1;
    private void execute(Path src, Path imageDir) {
        System.out.println(src);
        if (Files.isDirectory(src)) {
            for (File f : src.toFile().listFiles()) {
                if (f.getName().toLowerCase().endsWith(".pdf")) {
                    System.out.println("processing: " + f);
                    try {
                        processFile(f, imageDir);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            try {
                processFile(src.toFile(), imageDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processFile(File f, Path imageDir) throws IOException {
        String imageBaseName = f.getName().replaceAll("(?i).pdf$", "");
        try (PDDocument pd = PDDocument.load(f)) {
            for (int i = 0; i < pd.getNumberOfPages(); i++) {
                System.out.println("processing "+f + " page: "+i);
                try {
                    Path imageSubDir = imageDir.resolve(imageBaseName);
                    Files.createDirectories(imageSubDir);
                    processPage(i, pd.getPage(i), imageSubDir, imageBaseName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        pageNumber = 0;
        imageCountPerPage = 0;
    }

    private void processPage(int i, PDPage page, Path imageDir, String imageBaseName) throws Exception {
        extractImages(page.getResources(), imageDir, imageBaseName);
        imageCountPerPage = 0;
        pageNumber++;
    }

    private void extractImages(PDResources resources, Path imageDir, String imageBaseName)
            throws IOException {
        if (resources == null ) {
            return;
        }
        for (COSName name : resources.getXObjectNames()) {

            PDXObject object = null;
            try {
                object = resources.getXObject(name);
            } catch (MissingImageReaderException e) {
                e.printStackTrace();
                continue;
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            if (object == null) {
                continue;
            }
            COSStream cosStream = object.getCOSObject();


            if (object instanceof PDFormXObject) {
                extractImages(((PDFormXObject) object).getResources(), imageDir, imageBaseName);
            } else if (object instanceof PDImageXObject) {

                PDImageXObject image = (PDImageXObject) object;

                String extension = image.getSuffix();

                if (extension == null || extension.equals("png")) {
                    extension = "png";
                } else if (extension.equals("jpg")) {
                    extension = "jpg";
                } else if (extension.equals("tiff")) {
                    extension = "tif";
                } else if (extension.equals("jpx")) {
                    //extension = "jp2";
                } else if (extension.equals("jb2")) {
                    //extension = "jb2";
                } else {
                    //TODO: determine if we need to add more image types
//                    throw new RuntimeException("EXTEN:" + extension);
                }


                String fileName = imageBaseName +"_p_"+pageNumber+"_i_"+ imageCountPerPage++ + "."+extension;
                Path path = imageDir.resolve(fileName);
                if (Files.exists(path)) {
                    System.err.println("file exists: "+path);
                }
                try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE)) {
                    writeToBuffer(image, extension, os);
                }
            }
        }
    }

    //nearly directly copied from PDFBox ExtractImages
    private void writeToBuffer(PDImageXObject pdImage, String suffix, OutputStream out)
            throws IOException {

        BufferedImage image = pdImage.getImage();
        if (image != null) {
            if ("jpg".equals(suffix)) {
                String colorSpaceName = pdImage.getColorSpace().getName();
                //TODO: figure out if we want directJPEG as a configuration
                //previously: if (directJPeg || PDDeviceGray....
                if (PDDeviceGray.INSTANCE.getName().equals(colorSpaceName) ||
                        PDDeviceRGB.INSTANCE.getName().equals(colorSpaceName)) {
                    // RGB or Gray colorspace: get and write the unmodifiedJPEG stream
                    InputStream data = pdImage.getStream().createInputStream(JPEG);
                    org.apache.pdfbox.io.IOUtils.copy(data, out);
                    org.apache.pdfbox.io.IOUtils.closeQuietly(data);
                } else {
                    // for CMYK and other "unusual" colorspaces, the JPEG will be converted
                    ImageIOUtil.writeImage(image, suffix, out);
                }
            } else if ("jp2".equals(suffix) || "jpx".equals(suffix)) {
                InputStream data = pdImage.createInputStream(JP2);
                org.apache.pdfbox.io.IOUtils.copy(data, out);
                org.apache.pdfbox.io.IOUtils.closeQuietly(data);
            } else if ("jb2".equals(suffix)) {
                InputStream data = pdImage.createInputStream(JB2);
                org.apache.pdfbox.io.IOUtils.copy(data, out);
                org.apache.pdfbox.io.IOUtils.closeQuietly(data);
            } else{
                ImageIOUtil.writeImage(image, suffix, out);
            }
        }
        out.flush();
    }

}
