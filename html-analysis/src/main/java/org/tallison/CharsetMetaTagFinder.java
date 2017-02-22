package org.tallison;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CharsetMetaTagFinder {

    private static final Pattern HTTP_META_PATTERN = Pattern.compile(
            "(?is)<\\s*meta\\s+([^<>]+)"
    );

    //this should match both the older:
    //<meta http-equiv="content-type" content="text/html; charset=xyz"/>
    //and
    //html5 <meta charset="xyz">
    //See http://webdesign.about.com/od/metatags/qt/meta-charset.htm
    //for the noisiness that one might encounter in charset attrs.
    //Chose to go with strict ([-_:\\.a-z0-9]+) to match encodings
    //following http://docs.oracle.com/javase/7/docs/api/java/nio/charset/Charset.html
    //For a more general "not" matcher, try:
    //("(?is)charset\\s*=\\s*['\\\"]?\\s*([^<>\\s'\\\";]+)")
    private static final Pattern FLEXIBLE_CHARSET_ATTR_PATTERN = Pattern.compile(
            ("(?is)charset\\s*=\\s*(?:['\\\"]\\s*)?([-_:\\.a-z0-9]+)")
    );

    private static final Pattern COMMENT_STRIPPER = Pattern.compile("(?s)(<!--.*?-->)");
    int filesProcessed = 0;

    public static void main(String[] args) throws Exception {
        CharsetMetaTagFinder charsetMetaTagFinder = new CharsetMetaTagFinder();
        Path directory = Paths.get(args[0]);
        Path reportFile = Paths.get(args[1]);
        boolean stripComments = false;
        if (args.length > 2) {
            if (args[2].equals("strip")) {
                stripComments = true;
            } else {
                System.err.println("third parameter must be 'strip' to strip comments, " +
                        "otherwise, leave out the third parameter and comments will not be stripped.");
                return;
            }
        }
        charsetMetaTagFinder.execute(Paths.get(args[0]), Paths.get(args[1]), stripComments);
    }

    private void execute(Path startDir, Path outputFile, boolean stripComments) throws IOException {
        Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
        processDir(startDir, writer, stripComments);
        writer.flush();
        writer.close();
    }

    private void processDir(Path startDir, Writer writer, boolean stripComments) {
        for (File f : startDir.toFile().listFiles()) {
            if (f.isFile()) {
                try {
                    processFile(f.toPath(), writer, stripComments);
                } catch (IOException e) {
                    System.out.println(e.getMessage()+"\t"+f.getName());
                }
            } else {
                processDir(f.toPath(), writer, stripComments);
            }
        }
    }

    private void processFile(Path path, Writer writer, boolean stripComments) throws IOException {
        if (++filesProcessed % 1000 == 0) {
            System.out.println("processed "+filesProcessed);
        }
        String txt = new String(Files.readAllBytes(path), StandardCharsets.US_ASCII);

        String noComments = (stripComments) ? stripComments(txt) : txt;

        Matcher metaMatcher = HTTP_META_PATTERN.matcher(noComments);
        int index = -1;
        int numFound = 0;

        while (metaMatcher.find()) {
            Matcher charsetMatcher = FLEXIBLE_CHARSET_ATTR_PATTERN.matcher(metaMatcher.group(1));
            if (charsetMatcher.find()) {
                index = metaMatcher.start();
                String charsetName = charsetMatcher.group(1).toLowerCase(Locale.US);
                charsetName = charsetName.trim();
                charsetName = charsetName.replaceAll("[\r\n\t]", " ");
                writer.write(path.getFileName().toString()+"\t"+
                        index+"\t"+numFound+"\t"+charsetName);
                writer.write("\n");
                numFound++;
            }
        }
        if (numFound == 0) {
            writer.write(path.getFileName().toString()+"\t"+
                    -1+"\t"+numFound+"\t");
            writer.write("\n");
        }
    }

    public static String stripComments(String txt) {
        StringBuffer sb = new StringBuffer();
        Matcher m = COMMENT_STRIPPER.matcher(txt);
        while (m.find()) {
            m.appendReplacement(sb, spaces(m.end()-m.start()));
        }

        m.appendTail(sb);
        return sb.toString();
    }

    private static String spaces(int num) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < num; i++) {
            sb.append(" ");
        }
        return sb.toString();
    }

    private static String join(Set<String> items, String joiner) {
        if (items == null || items.size() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int cnt = 0;
        for (String item : items) {
            if (cnt++ > 0) {
                sb.append(joiner);
            }
            sb.append(item);
        }
        return sb.toString();
    }
}
