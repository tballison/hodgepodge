package org.tallison.pdfbox.javascript;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;

import java.io.File;

public class ReadJS {
    public static void main(String[] args) throws Exception {
        PDDocument doc = PDDocument.load(new File(args[0]));

        System.out.println(doc.getDocumentCatalog().getOpenAction());
        PDActionJavaScript script = (PDActionJavaScript)doc.getDocumentCatalog().getOpenAction();
        System.out.println(script.getAction());
    }
}
