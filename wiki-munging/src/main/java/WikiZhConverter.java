import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * This runs wikipedia mapping sets against the zh dump.  I pulled the
 * included mapping file from:
 * https://phab.wmfusercontent.org/file/data/ycg62tzo5qyv5txmiamh/PHID-FILE-66gf4k72tgxhksd5j36x/ZhConversion.php
 * on 20190128.
 */
public class WikiZhConverter {

    private static WikiZhConverter WIKI_ZH_CONVERTER;

    private final Map<String, Map<Matcher, String>> map;
    private WikiZhConverter(Map<String, Map<Matcher, String>> map) {
        this.map = map;
    }

    public static WikiZhConverter getInstance() throws Exception {
        //add synchronization to make this thread safe
        //no need for now
        if (WIKI_ZH_CONVERTER == null) {
            WIKI_ZH_CONVERTER = load();
        }
        return WIKI_ZH_CONVERTER;
    }

    private static WikiZhConverter load() throws Exception {
        Map<String, Map<Matcher, String>> map = new HashMap<>();
        String currConversion = null;
        Matcher conversionTypeMatcher = Pattern.compile("public static \\$(zh2\\w+)").matcher("");
        Matcher mapMatcher = Pattern.compile("'([^']+)' => '([^']+)'").matcher("");
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                WikiZhConverter.class.getResourceAsStream("ZHConversion.php"),
                                StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while (line != null) {
                if (line.startsWith("'")) {
                    mapMatcher.reset(line);
                    if (mapMatcher.find()) {
                        if (currConversion != null) {
                            Map<Matcher, String> matcherMap = map.get(currConversion);
                            if (matcherMap == null) {
                                matcherMap = getNewMap();
                            }
                            matcherMap.put(Pattern.compile(mapMatcher.group(1)).matcher(""),
                                    mapMatcher.group(2));
                            map.put(currConversion, matcherMap);
                        }
                    }
                }else if (conversionTypeMatcher.reset(line).find()) {
                        currConversion = conversionTypeMatcher.group(1);
                } else {
                    System.err.println("skipping");
                }

                line = reader.readLine();
            }
        }
        return new WikiZhConverter(map);
    }

    public static void main(String[] args) throws Exception {
        String whichConversion = args[0];
        Path inputFile = Paths.get(args[1]);
        Path outputFile = Paths.get(args[2]);
        WikiZhConverter wikiZhConverter = WikiZhConverter.getInstance();
        try (BufferedReader reader = getReader(inputFile)) {
            try (BufferedWriter writer = getWriter(outputFile)) {
                String line = reader.readLine();
                while (line != null) {
                    line = wikiZhConverter.convert(whichConversion, line);
                    writer.write(line);
                    writer.newLine();
                    line = reader.readLine();
                }
                writer.flush();
            }
        }
    }

    private static BufferedWriter getWriter(Path outputFile) throws IOException {
        if (outputFile.getFileName().toString().endsWith(".gz")) {
            return new BufferedWriter(
                    new OutputStreamWriter(
                            new GzipCompressorOutputStream(Files.newOutputStream(outputFile)),
                            StandardCharsets.UTF_8));
        }
        return Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8);
    }

    private static BufferedReader getReader(Path inputFile) throws IOException {
        if (inputFile.getFileName().toString().endsWith(".gz")) {
            return new BufferedReader(
                    new InputStreamReader(
                            new GzipCompressorInputStream(Files.newInputStream(inputFile)),
                            StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(inputFile, StandardCharsets.UTF_8);
    }

    private static Map<Matcher, String> getNewMap() {
        return new TreeMap<Matcher, String>(
                new Comparator<Matcher>() {
                    @Override
                    public int compare(Matcher m1, Matcher m2) {
                        if (m1.pattern().toString().length() > m2.pattern().toString().length()) {
                            return -1;
                        } else if (m1.pattern().toString().length() < m2.pattern().toString().length()) {
                            return 1;
                        } else {
                            return m1.pattern().toString().compareTo(m2.pattern().toString());
                        }
                    }
                });
    }

    public String convert(String whichConversion, String line) {
        Map<Matcher, String> conversion = map.get(whichConversion);
        if (conversion == null) {
            throw new IllegalArgumentException("Can't find: "+whichConversion);
        }
        String ret = line;
        for (Map.Entry<Matcher, String> e : conversion.entrySet()) {
            //System.out.println(e.getKey());
            e.getKey().reset(ret);
            ret = e.getKey().replaceAll(e.getValue());

        }
        /*
        if (! line.equals(ret)) {
            System.out.println("DIFF: "+line+"\n"+ret+"\n\n");
        } else {
            System.out.println("NO DIFF");
        }*/
        return ret;
    }
}
