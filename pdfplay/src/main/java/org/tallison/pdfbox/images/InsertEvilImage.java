package org.tallison.pdfbox.images;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.http.util.EntityUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.tika.fuzzing.general.ByteDeleter;
import org.apache.tika.fuzzing.general.ByteFlipper;
import org.apache.tika.fuzzing.general.ByteInjector;
import org.apache.tika.fuzzing.general.GeneralTransformer;
import org.apache.tika.fuzzing.general.SpanSwapper;
import org.apache.tika.fuzzing.general.Truncator;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

//Copied nearly verbatim from PDFBox's example code
public class InsertEvilImage {

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
        httpClientPolicy.setConnectionTimeout(30000);
        httpClientPolicy.setReceiveTimeout(30000);
        http.setClient(httpClientPolicy);

        GeneralTransformer transformer = new GeneralTransformer(1, byteFlipper,
                byteInjector, byteDeleter);

        for (int i = 0; i < 1; i++) {
            for (File f : jpegs) {
                try {
                    processDoc(i, f, client, transformer, outDir, beasties, exceptions);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (i > 0 && i % 1000 == 0) {
                System.err.println(i + " completed");
            }
        }
        FileUtils.deleteDirectory(outDir.toFile());

    }

    private static void processDoc(int i, File jpeg,
                                   WebClient client, GeneralTransformer transformer, Path outDir,
                                   Path beasties, Path exceptions) throws IOException {
        if (jpeg.isDirectory()) {
            for (File f : jpeg.listFiles()) {
                try {
                    processDoc(i, f, client, transformer, outDir, beasties, exceptions);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return;
        }
        if (! jpeg.getName().endsWith("jpeg") && ! jpeg.getName().endsWith("jpg")) {
            return;
        }

        System.out.println(jpeg.getName());
        Path out = null;
        try (PDDocument doc = new PDDocument()) {
            //we will add the image to the first page.
            PDPage page = new PDPage();
            doc.addPage(page);
            // createFromFile is the easiest way with an image file
            // if you already have the image in a BufferedImage,
            // call LosslessFactory.createFromImage() instead


            PDImageXObject pdImage = EvilJPEGFactory.createFromStream(doc, Files.newInputStream(jpeg.toPath()), transformer);
            PDPageContentStream contentStream = new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true, true);

            // contentStream.drawImage(ximage, 20, 20 );
            // better method inspired by http://stackoverflow.com/a/22318681/535646
            // reduce this value if the image is too large
            float scale = 2f;
            contentStream.drawImage(pdImage, 100, 100, pdImage.getWidth() * scale, pdImage.getHeight() * scale);

            contentStream.close();
            out = outDir.resolve("fuzzed-" + i + "-" + jpeg.getName()+".pdf");
            doc.save(out.toFile());
        }

        try {
            client.removeAllHeaders();
            Response response = client
                    .accept("application/json")
                    .header("Content-Disposition",
                            "attachment; filename="+out.getFileName().toString())
                    .put(Files.newInputStream(out));

            if (response.getStatus() != 200) {
                mv(out, beasties, "response code "+Integer.toString(response.getStatus()));
                try {
                    Thread.sleep(20000);
                } catch (InterruptedException e2) {

                }
            } else {
                checkForExceptions((InputStream)response.getEntity(), out, exceptions);
            }
        } catch (Exception e) {
            mv(out, beasties, e.getCause().getMessage());
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e2) {

            }
        }


    }



            /*
            String[] pArgs = {
                    "java",
                    "-Xmx512m",
                    "-jar",
                    "tika-app-2.0.0-SNAPSHOT.jar",
                    "-J", "-t",
                    "--config=tika-inline-config.xml",
                    out.toAbsolutePath().toString()
            };

            ProcessBuilder pb = new ProcessBuilder(pArgs);
            pb.directory(Paths.get("/home/tallison/Desktop/jpegs").toFile());
            pb.inheritIO();
            Process p = pb.start();
            boolean finished = p.waitFor(20000, TimeUnit.MILLISECONDS);
            if (! finished) {
                p.destroyForcibly();
            }
            if (p.exitValue() != 0) {
            }
            System.out.println("\n\n\nextracted "+i + " : " + (System.currentTimeMillis()-elapsed) + " : "+p.exitValue());
            */


    private static void checkForExceptions(InputStream entity, Path file, Path outDir) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (Reader reader = new BufferedReader(
                new InputStreamReader(entity, StandardCharsets.UTF_8))) {
            List<Metadata> metadataList = JsonMetadataList.fromJson(reader);
            for (Metadata m : metadataList) {
                if (m.get(TikaCoreProperties.CONTAINER_EXCEPTION) != null) {
                    String[] lines = m.get(TikaCoreProperties.CONTAINER_EXCEPTION).split("[\r\n]");
                    System.out.println(m.get(TikaCoreProperties.CONTAINER_EXCEPTION));
                    mv(file, outDir, lines[0]);
                } else if (m.get(TikaCoreProperties.EMBEDDED_EXCEPTION) != null) {
                    //System.out.println("embedded: " + m.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
                    String msg = m.get(TikaCoreProperties.EMBEDDED_EXCEPTION);
                    System.out.println(msg);
                    if (msg.contains("End of data reached") || msg.contains("segment size would be less than zero")) {

                    } else {
                        String[] lines = m.get(TikaCoreProperties.EMBEDDED_EXCEPTION).split("[\r\n]");
                        String cause = lines[0];
                        for (String line : lines) {
                            if (line.startsWith("Caused by:")) {
                                cause = line;
                            }
                        }
                        //System.err.println(m.get(TikaCoreProperties.EMBEDDED_EXCEPTION));
                        mv(file, outDir, cause);
                    }
                }
            }
        } catch (Exception e) {
            mv(file, outDir, "broken json");
        }
    }


    private static void mv(Path out, Path beasties, String msg) throws IOException {
        String digest = "";
        try (InputStream is = Files.newInputStream(out)) {
            digest = DigestUtils.sha256Hex(is);
        }
        Path target = beasties.resolve(digest);
        System.out.println(digest+"\t"+out.getFileName() +"\t"+msg);
        //Files.copy(out, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
