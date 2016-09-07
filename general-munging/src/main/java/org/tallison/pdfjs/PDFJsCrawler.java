package org.tallison.pdfjs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by TALLISON on 9/7/2016.
 */
public class PDFJsCrawler {

    static {
        System.setProperty("http.proxyHost", "gatekeeper-w.mitre.org");
        System.setProperty("http.proxyPort", "80");
        System.setProperty("https.proxyHost", "gatekeeper-w.mitre.org");
        System.setProperty("https.proxyPort", "80");
    }
    public static void main(String[] args) throws IOException {
        PDFJsCrawler crawler = new PDFJsCrawler();
        crawler.execute(Paths.get(args[0]), Paths.get(args[1]));
    }

    private void execute(Path input, Path output) throws IOException {
        for (File f : input.toFile().listFiles()) {
            if (f.getName().endsWith(".link")) {
                fetch(f, output);
            } else if (f.getName().endsWith(".pdf")) {
                Path target = output.resolve(f.getName().toString());
                if (! Files.isRegularFile(target)) {
                    System.out.println("TARGET: "+target);
                    Files.copy(f.toPath(), target);
                }
            }
        }
    }

    private void fetch(File f, Path output) throws IOException {
        Path target = output.resolve(f.getName().replaceAll("\\.link$", ""));
        System.out.println("fetch target: "+target);
        if (Files.isRegularFile(target)) {
            return;
        }
        String url = getURL(f);
        if (url == null) {
            System.err.println("couldn't find url in "+f);
            return;
        }
        URL u = new URL(url);
        System.out.println("fetching: "+url);
        try (InputStream is = u.openStream()) {
            Files.copy(is, target);
        }
    }

    private String getURL(File f) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(f.toPath())) {
            String line = reader.readLine();
            while (line != null) {
                if (line.trim().length() > 0) {
                    return line.trim();
                }
                line = reader.readLine();
            }
        }
        return null;
    }
}
