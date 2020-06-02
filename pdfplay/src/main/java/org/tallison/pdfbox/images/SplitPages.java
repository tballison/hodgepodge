package org.tallison.pdfbox.images;

import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SplitPages {



    public static void main(String[] args) throws Exception {
        SplitPages ex = new SplitPages();
        ex.execute(Paths.get(args[0]), Paths.get(args[1]));
    }
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
            Splitter splitter = new Splitter();
            List<PDDocument> docs = splitter.split(pd);
            for (int i = 0; i < docs.size(); i++) {
                Path output = imageDir.resolve(imageBaseName+"/"+imageBaseName+"_"+(i+1)+".pdf");
                Files.createDirectories(output.getParent());
                try {
                    docs.get(i).save(output.toFile());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    docs.get(i).close();
                }
            }
        }
    }

}
