package org.tallison.pdfbox.images;

import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fuzzing.general.ByteDeleter;
import org.apache.tika.fuzzing.general.ByteFlipper;
import org.apache.tika.fuzzing.general.ByteInjector;
import org.apache.tika.fuzzing.general.GeneralTransformer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;

import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

//Copied nearly verbatim from PDFBox's example code
public class EvilJpegFinder {

    public static void main(String[] args) throws Exception {
        File[] jpegs = Paths.get(args[0]).toFile().listFiles();
        Path outDir = Paths.get(args[1]);
        if (!Files.isDirectory(outDir)) {
            Files.createDirectories(outDir);
        }
        ByteFlipper byteFlipper = new ByteFlipper();
        ByteInjector byteInjector = new ByteInjector();
        byteFlipper.setPercentCorrupt(0.0001f);
        ByteDeleter byteDeleter = new ByteDeleter();

        Path beasties = Paths.get("/home/tallison/Desktop/jpegs/beasties");
        Files.createDirectories(beasties);
        Path exceptions = Paths.get("/home/tallison/Desktop/jpegs/exceptions");
        Files.createDirectories(exceptions);

        WebClient client = WebClient.create("http://localhost:9998/rmeta");
        HTTPConduit http = (HTTPConduit) client.getConfiguration().getConduit();
        HTTPClientPolicy httpClientPolicy = new HTTPClientPolicy();
        httpClientPolicy.setConnectionTimeout(10000);
        httpClientPolicy.setReceiveTimeout(10000);
        http.setClient(httpClientPolicy);

        GeneralTransformer transformer = new GeneralTransformer(1, byteFlipper,
                byteInjector, byteDeleter);

        for (int i = 0; i < 100000; i++) {
            for (File f : jpegs) {
                try {
                    processDoc(i, f, client, transformer, outDir, beasties, exceptions);
                } catch (IOException e) {

                }
            }
            if (i % 1000 == 0) {
                System.out.println(i + " completed");
            }
        }
        FileUtils.deleteDirectory(outDir.toFile());
    }

    private static void processDoc(int i, File jpeg,
                                   WebClient client, GeneralTransformer transformer, Path outDir,
                                   Path beasties, Path exceptions) throws IOException {
        Path out = outDir.resolve("fuzzed-" + i + "-"+jpeg.getName());
        try (InputStream is = Files.newInputStream(jpeg.toPath());
            OutputStream os = Files.newOutputStream(out)) {
            transformer.transform(is, os);
        } catch (TikaException e) {
            e.printStackTrace();
        }

        Files.createDirectories(Paths.get("/home/tallison/Desktop/jpegs/tmp"));
        Path tmp = Files.createTempDirectory(Paths.get("/home/tallison/Desktop/jpegs/tmp"), "fuzz-" + i + "-");
        long elapsed = System.currentTimeMillis();
        //String[] pArgs = {"pdfimages",
        //out.toAbsolutePath().toString(), Files.createTempFile(tmp, "fuzz-", ".out").toAbsolutePath().toString()};

        try {
            Response response = client
                    .accept("application/json")
                    .put(Files.newInputStream(out));
            if (response.getStatus() != 200) {
                mv(out, beasties);
            } else {
                checkForExceptions((InputStream)response.getEntity(), out, exceptions);
            }
        } catch (Exception e) {
            mv(out, beasties);
        }

    }

    private static void checkForExceptions(InputStream entity, Path file, Path outDir) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Reader reader = new BufferedReader(
                new InputStreamReader(entity, StandardCharsets.UTF_8))) {
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            for (Metadata m : metadataList) {
                if (m.get(TikaCoreProperties.CONTAINER_EXCEPTION) != null) {
                    String exc = m.get(TikaCoreProperties.CONTAINER_EXCEPTION);
                    String[] lines = exc.split("[\r\n]");
                    String msg = lines[0];
                    if (! msg.contains("End of data reached") &&
                            ! msg.contains("XMP packet not properly") &&
                    ! msg.contains("JPEG segment size would be less than zero") &&
                    ! msg.contains("Invalid XMP packet header")) {
                        System.out.println("container: " +
                                file.getFileName() + " : " + msg);//m.get(TikaCoreProperties.CONTAINER_EXCEPTION));
                        mv(file, outDir);
                    }
                } else if (m.get(TikaCoreProperties.EMBEDDED_EXCEPTION) != null) {
                    //System.out.println("embedded: " + m.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
                    //mv(file, outDir);
                }
            }
        } catch (Exception e) {
            mv(file, outDir);
        }
    }


    private static void mv(Path out, Path beasties) throws IOException {
        Path target = Files.createTempFile(beasties, "fuzz-" ,".jpg");
        Files.copy(out, target, StandardCopyOption.REPLACE_EXISTING);

    }
}
