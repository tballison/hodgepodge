package org.tallison.pdfbox.images;

import com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReader;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class TwelveMonkeysMetadata {

    public static void main(String[] args) throws Exception {
        Path p = Paths.get("/home/tallison/Desktop/jpegs/beasties/extracted.jpeg");
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
        ImageReader reader = null;
        while (readers.hasNext()) {
            reader = readers.next();
            break;
        }
        try (InputStream is = Files.newInputStream(p)) {
            ImageInputStream iis = ImageIO.createImageInputStream(is);

            reader.setInput(iis);
            ImageReadParam irp = reader.getDefaultReadParam();

            Raster raster;
            try {
                BufferedImage image = reader.read(0, irp);
                raster = image.getRaster();
            } catch (IIOException var20) {
                raster = reader.readRaster(0, irp);
            }
        }
    }
}
