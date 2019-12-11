package org.tallison.selenium;

import org.apache.commons.io.FileUtils;
import org.tallison.selenium.filters.CompositeURLFilter;
import org.tallison.selenium.filters.ExtensionFilter;
import org.tallison.selenium.filters.HostFilter;
import org.tallison.selenium.filters.LengthFilter;
import org.tallison.selenium.filters.RegexFilter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * lightweight class to extract urls from a directory to load into a db
 * to seed it
 */
public class InsertUrls {

    private final URLManager urlManager;
    private final Path docRoot;
    public InsertUrls(URLManager urlManager, Path docRoot) {
        this.urlManager = urlManager;
        this.docRoot = docRoot;
    }

    public static void main(String[] args) throws Exception {
        Path db = Paths.get(args[0]);
        Path docRoot = Paths.get(args[1]);
        Set<String> extensionBlacklist;
        extensionBlacklist = new HashSet<>();
        extensionBlacklist.addAll(Arrays.asList("mov,mp4,mpeg".split(",")));

        List<Matcher> matchers = new ArrayList<>();
        matchers.add(Pattern.compile("(?i)(javascript|mailto):").matcher(""));
        URLFilter[] filters = new URLFilter[3];
        filters[0] = new LengthFilter(512);
        filters[1] = new ExtensionFilter(extensionBlacklist);
        filters[2] = RegexFilter.blacklist(matchers);

        try (URLManager urlManager = URLManager.open(db, new CompositeURLFilter(filters), true)){
            InsertUrls insertUrls = new InsertUrls(urlManager, docRoot);
            insertUrls.execute();
        }

    }

    private void execute() throws SQLException {
        processDirectory(docRoot.toFile());
    }

    private void processDirectory(File dir) throws SQLException {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                processDirectory(f);
            } else {
                processFile(f);
            }
        }
    }

    private void processFile(File f) throws SQLException {
        String s = null;
        try {
            s = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }
        Matcher m = Pattern.compile("\\shref=\"([^\"]+)").matcher(s);
        while (m.find()) {
            String url = m.group(1);
            if (url.startsWith("http")) {
                System.out.println("inserting: "+url);
                urlManager.insert(url);
            } else {
                System.out.println("skipping: "+url);
            }
        }
    }
}
