package org.tallison.pdfbox.images;

import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.filter.MissingImageReaderException;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceCMYK;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fuzzing.Transformer;
import org.apache.tika.fuzzing.general.GeneralTransformer;
import org.w3c.dom.Element;

public final class EvilJPEGFactory {
    private static final Log LOG = LogFactory.getLog(org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory.class);

    private final GeneralTransformer generalTransformer;
    public EvilJPEGFactory(GeneralTransformer generalTransformer) {
        this.generalTransformer = generalTransformer;
    }

    public static PDImageXObject createFromStream(
            PDDocument document, InputStream stream, Transformer transformer) throws IOException {
        return createFromByteArray(document, IOUtils.toByteArray(stream), transformer);
    }

    public static PDImageXObject createFromByteArray(
            PDDocument document, byte[] byteArray, Transformer transformer) throws IOException {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(byteArray);
        Dimensions meta = retrieveDimensions(byteStream);
        Object colorSpace;
        Random r = new Random();
        int numComponents = r.nextInt(3);
        switch(numComponents) {
            case 0:
                colorSpace = PDDeviceGray.INSTANCE;
                break;
            case 1:
                colorSpace = PDDeviceRGB.INSTANCE;
                break;
            case 2:
            default:
                colorSpace = PDDeviceCMYK.INSTANCE;

        }
/*        switch(meta.numComponents) {
            case 1:
                colorSpace = PDDeviceGray.INSTANCE;
                break;
            case 2:
            default:
                throw new UnsupportedOperationException("number of data elements not supported: " + meta.numComponents);
            case 3:
                colorSpace = PDDeviceRGB.INSTANCE;
                break;
            case 4:
                colorSpace = PDDeviceCMYK.INSTANCE;
        }*/

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        //try {
            IOUtils.copy(byteStream, bos);
//            transformer.transform(byteStream, bos);
        //} catch (TikaException e) {
          //  throw new IOException(e);
       // }
        PDImageXObject pdImage = new PDImageXObject(document,
                new ByteArrayInputStream(bos.toByteArray()),
                COSName.DCT_DECODE, meta.width, meta.height,
                8, (PDColorSpace)colorSpace);
        if (colorSpace instanceof PDDeviceCMYK) {
            COSArray decode = new COSArray();
            decode.add(COSInteger.ONE);
            decode.add(COSInteger.ZERO);
            decode.add(COSInteger.ONE);
            decode.add(COSInteger.ZERO);
            decode.add(COSInteger.ONE);
            decode.add(COSInteger.ZERO);

            if (r.nextFloat() > 0.9) {
                decode.add(COSInteger.ONE);
                decode.add(COSInteger.ZERO);
            } else {
                decode.add(COSInteger.THREE);
                decode.add(COSInteger.ZERO);
            }
            pdImage.setDecode(decode);
        }

        return pdImage;
    }

    private static Dimensions retrieveDimensions(ByteArrayInputStream stream) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("JPEG");
        ImageReader reader = null;

        while(readers.hasNext()) {
            reader = (ImageReader)readers.next();
            if (reader.canReadRaster()) {
                break;
            }
        }

        if (reader == null) {
            throw new MissingImageReaderException("Cannot read JPEG image: a suitable JAI I/O image filter is not installed");
        } else {
            ImageInputStream iis = null;

            Dimensions var6;
            try {
                //iis = ImageIO.createImageInputStream(stream);
                //reader.setInput(iis);
                Dimensions meta = new Dimensions();
                meta.width = 100;//reader.getWidth(0);
                meta.height = 100;//reader.getHeight(0);

                //try {
                    meta.numComponents = 4;//getNumComponentsFromImageMetadata(reader);
                    if (meta.numComponents != 0) {
                        Dimensions var12 = meta;
                        return var12;
                    }

                    LOG.warn("No image metadata, will decode image and use raster size");
                //} catch (IOException var10) {
                  //  LOG.warn("Error reading image metadata, will decode image and use raster size", var10);
                //}

                ImageIO.setUseCache(false);
                Raster raster = reader.readRaster(0, (ImageReadParam)null);
                meta.numComponents = raster.getNumDataElements();
                var6 = meta;
            } finally {
                if (iis != null) {
                    iis.close();
                }
                stream.reset();
                reader.dispose();
            }

            return var6;
        }
    }

    private static int getNumComponentsFromImageMetadata(ImageReader reader) throws IOException {
        IIOMetadata imageMetadata = reader.getImageMetadata(0);
        if (imageMetadata == null) {
            return 0;
        } else {
            Element root = (Element)imageMetadata.getAsTree("javax_imageio_jpeg_image_1.0");
            if (root == null) {
                return 0;
            } else {
                try {
                    XPath xpath = XPathFactory.newInstance().newXPath();
                    String numScanComponents = xpath.evaluate("markerSequence/sos/@numScanComponents", root);
                    return numScanComponents.isEmpty() ? 0 : Integer.parseInt(numScanComponents);
                } catch (NumberFormatException var5) {
                    LOG.warn(var5.getMessage(), var5);
                    return 0;
                } catch (XPathExpressionException var6) {
                    LOG.warn(var6.getMessage(), var6);
                    return 0;
                }
            }
        }
    }

    public static PDImageXObject createFromImage(PDDocument document, BufferedImage image) throws IOException {
        return createFromImage(document, image, 0.75F);
    }

    public static PDImageXObject createFromImage(PDDocument document, BufferedImage image, float quality) throws IOException {
        return createFromImage(document, image, quality, 72);
    }

    public static PDImageXObject createFromImage(PDDocument document, BufferedImage image, float quality, int dpi) throws IOException {
        return createJPEG(document, image, quality, dpi);
    }

    private static BufferedImage getAlphaImage(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return null;
        } else if (image.getTransparency() == 2) {
            throw new UnsupportedOperationException("BITMASK Transparency JPEG compression is not useful, use LosslessImageFactory instead");
        } else {
            WritableRaster alphaRaster = image.getAlphaRaster();
            if (alphaRaster == null) {
                return null;
            } else {
                BufferedImage alphaImage = new BufferedImage(image.getWidth(), image.getHeight(), 10);
                alphaImage.setData(alphaRaster);
                return alphaImage;
            }
        }
    }

    private static PDImageXObject createJPEG(PDDocument document, BufferedImage image, float quality, int dpi) throws IOException {
        BufferedImage awtColorImage = getColorImage(image);
        BufferedImage awtAlphaImage = getAlphaImage(image);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        encodeImageToJPEGStream(awtColorImage, quality, dpi, baos);
        ByteArrayInputStream byteStream = new ByteArrayInputStream(baos.toByteArray());
        PDImageXObject pdImage = new PDImageXObject(document, byteStream, COSName.DCT_DECODE, awtColorImage.getWidth(), awtColorImage.getHeight(), 8, getColorSpaceFromAWT(awtColorImage));
        if (awtAlphaImage != null) {
            PDImage xAlpha = createFromImage(document, awtAlphaImage, quality);
            pdImage.getCOSObject().setItem(COSName.SMASK, xAlpha);
        }

        return pdImage;
    }

    private static ImageWriter getJPEGImageWriter() throws IOException {
        ImageWriter writer = null;
        Iterator writers = ImageIO.getImageWritersBySuffix("jpeg");

        do {
            if (!writers.hasNext()) {
                throw new IOException("No ImageWriter found for JPEG format");
            }

            if (writer != null) {
                writer.dispose();
            }

            writer = (ImageWriter)writers.next();
        } while(writer == null || !(writer.getDefaultWriteParam() instanceof JPEGImageWriteParam));

        return writer;
    }

    private static void encodeImageToJPEGStream(BufferedImage image, float quality, int dpi, OutputStream out) throws IOException {
        ImageOutputStream ios = null;
        ImageWriter imageWriter = null;

        try {
            imageWriter = getJPEGImageWriter();
            ios = ImageIO.createImageOutputStream(out);
            imageWriter.setOutput(ios);
            ImageWriteParam jpegParam = imageWriter.getDefaultWriteParam();
            jpegParam.setCompressionMode(2);
            jpegParam.setCompressionQuality(quality);
            ImageTypeSpecifier imageTypeSpecifier = new ImageTypeSpecifier(image);
            IIOMetadata data = imageWriter.getDefaultImageMetadata(imageTypeSpecifier, jpegParam);
            Element tree = (Element)data.getAsTree("javax_imageio_jpeg_image_1.0");
            Element jfif = (Element)tree.getElementsByTagName("app0JFIF").item(0);
            jfif.setAttribute("Xdensity", Integer.toString(dpi));
            jfif.setAttribute("Ydensity", Integer.toString(dpi));
            jfif.setAttribute("resUnits", "1");
            imageWriter.write(data, new IIOImage(image, (List)null, (IIOMetadata)null), jpegParam);
        } finally {
            IOUtils.closeQuietly(out);
            if (ios != null) {
                ios.close();
            }

            if (imageWriter != null) {
                imageWriter.dispose();
            }

        }

    }

    private static PDColorSpace getColorSpaceFromAWT(BufferedImage awtImage) {
        if (awtImage.getColorModel().getNumComponents() == 1) {
            return PDDeviceGray.INSTANCE;
        } else {
            ColorSpace awtColorSpace = awtImage.getColorModel().getColorSpace();
            if (awtColorSpace instanceof ICC_ColorSpace && !awtColorSpace.isCS_sRGB()) {
                throw new UnsupportedOperationException("ICC color spaces not implemented");
            } else {
                switch(awtColorSpace.getType()) {
                    case 5:
                        return PDDeviceRGB.INSTANCE;
                    case 6:
                        return PDDeviceGray.INSTANCE;
                    case 7:
                    case 8:
                    default:
                        throw new UnsupportedOperationException("color space not implemented: " + awtColorSpace.getType());
                    case 9:
                        return PDDeviceCMYK.INSTANCE;
                }
            }
        }
    }

    private static BufferedImage getColorImage(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return image;
        } else if (image.getColorModel().getColorSpace().getType() != 5) {
            throw new UnsupportedOperationException("only RGB color spaces are implemented");
        } else {
            BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), 5);
            return (new ColorConvertOp((RenderingHints)null)).filter(image, rgbImage);
        }
    }

    private static class Dimensions {
        private int width;
        private int height;
        private int numComponents;

        private Dimensions() {
        }
    }
}
