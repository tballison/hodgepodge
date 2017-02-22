package org.tallison;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.html.HtmlEncodingDetector;
import org.apache.tika.parser.txt.Icu4jEncodingDetector;
import org.apache.tika.parser.txt.UniversalEncodingDetector;

public class CharsetComparer {

    int filesProcessed = 0;
    private List<EncodingDetector> detectors = new ArrayList<>();

    public CharsetComparer() {
        detectors.add(new HtmlEncodingDetector());
        detectors.add(new UniversalEncodingDetector());
        detectors.add(new Icu4jEncodingDetector());
        detectors.add(new ScrapingHTMLEncodingDetector());
        detectors.add(new ScrapingUniversalEncodingDetector());
        detectors.add(new ScrapingICUEncodingDetector());
    }
    public static void main(String[] args) throws Exception {
        CharsetComparer charsetComparer = new CharsetComparer();
        Path directory = Paths.get(args[0]);
        Path reportFile = Paths.get(args[1]);
        charsetComparer.execute(directory, reportFile);
    }

    private void execute(Path startDir, Path outputFile) throws IOException {
        Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
        writer.write("File\t");
        for (EncodingDetector detector : detectors) {
            writer.write(detector.getClass().getSimpleName()+"\t");
        }
        writer.write("\n");
        processDir(startDir, writer);
        writer.flush();
        writer.close();
    }

    public List<EncodingDetector> getDetectors() {
        return detectors;
    }
    private void processDir(Path startDir, Writer writer) {
        for (File f : startDir.toFile().listFiles()) {
            if (f.isFile()) {
                try {
                    processFile(f.toPath(), writer);
                } catch (IOException e) {
                    System.out.println(e.getMessage()+"\t"+f.getName());
                }
            } else {
                processDir(f.toPath(), writer);
            }
        }
    }

    private void processFile(Path path, Writer writer) throws IOException {
        Map<String, String> results = new HashMap<>();
        if (++filesProcessed % 1000 == 0) {
            System.out.println("processed "+filesProcessed);
        }
        for (EncodingDetector detector : detectors) {
            try (InputStream is = TikaInputStream.get(path)) {
                Charset detected = detector.detect(is, new Metadata());
                if (detected == null) {
                    results.put(detector.getClass().getSimpleName(), "");
                } else {
                    results.put(detector.getClass().getSimpleName(), detected.toString());
                }
            } catch (IOException e) {
                System.err.println(e.getMessage()+": "+path);
            }
        }
        writer.write(path.getFileName().toString()+"\t");
        for (EncodingDetector detector : detectors) {
            String r = results.get(detector.getClass().getSimpleName());
            writer.write(r+"\t");
        }
        writer.write("\n");

    }
}
