package org.tallison.selenium;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tallison.selenium.filters.CompositeURLFilter;
import org.tallison.selenium.filters.ExtensionFilter;
import org.tallison.selenium.filters.LengthFilter;
import org.tallison.selenium.filters.QueryFilter;
import org.tallison.selenium.filters.RegexFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LightweightSeleniumCrawler {
    private static final Logger LOG = LoggerFactory.getLogger(LightweightSeleniumCrawler.class);
    private static final int HARD_TIMEOUT_SECONDS = 360;
    private static final long MAX_HEADER_LENGTH = 100_000_000;


    static Options OPTIONS;

    static {
        //By the time this commandline is parsed, there should be both an extracts and an inputDir
        Option extracts = new Option("extracts", true, "directory for extract files");
        extracts.setRequired(true);

        Option inputDir = new Option("inputDir", true,
                "optional: directory for original binary input documents." +
                        " If not specified, -extracts is crawled as is.");

        OPTIONS = new Options()
                .addOption("d", "fileDirectory", true, "root directory for storing files")
                .addOption("u", "startUrl", true, "start url")
                .addOption("m", "maxUrlLength", true, "max url length")
                .addOption("s", "skipExtensions", true, "skip extensions")
                .addOption("r", "skipRegex", true, "skip regex")
                .addOption("db", "database", true, "db file")
                .addOption("f", "freshStart", false, "whether to wipe the db")
                .addOption("l", "lightRestart", false, "sets all statuses to unfetched")
        ;

    }

    private final URLManager urlManager;
    private final Path outputDirectory;
    private final WebDriver driver;
    private final Base32 base32 = new Base32();

    public static void main(String[] args) throws Exception {
        DefaultParser defaultCLIParser = new DefaultParser();
        CommandLine commandLine = null;
        try {
            commandLine = defaultCLIParser.parse(OPTIONS, args);
        } catch (ParseException e) {
            e.printStackTrace();
            return;
        }
        try (URLManager manager = initURLManager(commandLine)) {
            Path rootDir = Paths.get(commandLine.getOptionValue("d"));
            LightweightSeleniumCrawler c = new LightweightSeleniumCrawler(manager, rootDir);
            c.execute();
        }
    }

    private static URLManager initURLManager(CommandLine commandLine) throws Exception {
        boolean freshStart = commandLine.hasOption("f");
        Path db = Paths.get(commandLine.getOptionValue("db"));
        Set<String> extensionBlacklist;
        extensionBlacklist = new HashSet<>();
        extensionBlacklist.addAll(Arrays.asList("mov,mp4,m4v,mpeg,mpg,gif,tif,jpeg,jpg,png".split(",")));

        List<Matcher> matchers = new ArrayList<>();
        matchers.add(Pattern.compile("(?i)(javascript|mailto):").matcher(""));
        URLFilter[] filters = new URLFilter[4];
        filters[0] = new LengthFilter(512);
        filters[1] = new ExtensionFilter(extensionBlacklist);
        filters[2] = RegexFilter.blacklist(matchers);
        filters[3] = new QueryFilter();
        URLManager urlManager = URLManager.open(db, new CompositeURLFilter(filters), freshStart);
        if (commandLine.hasOption("u")) {
            String normalized = URLNormalizer.normalize("", commandLine.getOptionValue("u"));

            urlManager.insert(normalized);
        }
        if (commandLine.hasOption("l")) {
            urlManager.resetAllStatuses();
        }
        return urlManager;
    }


    public LightweightSeleniumCrawler(URLManager urlManager, Path outputDir) {
        this.urlManager = urlManager;
        this.outputDirectory = outputDir;
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--window-size=1920,1200", "--ignore-certificate-errors", "--silent");
        driver = new ChromeDriver(options);

    }

    public void execute() throws SQLException {
        String next = urlManager.next();
        while (next != null) {
            if (islikelyHTML(next)) {
                seleniumFetch(next);
            } else {
                binaryFetch(next);
            }
            next = urlManager.next();
        }

    }

    private boolean islikelyHTML(String urlString) {
        //TODO: pull first 1024 bytes(?) and run file id via tika-core?
        try {
            URL url = new URL(urlString);
            String ext = FilenameUtils.getExtension(url.getPath());
            if (ext.trim().length() == 0 || ext.startsWith("htm")
                    || ext.startsWith("xht") || ext.equals("php") || ext.equals("cfm")) {
                return true;
            }
        } catch (MalformedURLException e) {
            return false;
        }
        return false;
    }

    private void seleniumFetch(String urlString) throws SQLException {

        LOG.info("fetching selenium " + urlString);
        try {
            driver.get(urlString);
        } catch (Exception e) {
            LOG.warn("couldn't get " + urlString, e);
            urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_ERR);
            return;
        }
        Path tmpOut = null;
        String hash = null;
        try {
            tmpOut = Files.createTempFile("", "-sel-crawler");

            try (OutputStream os = Files.newOutputStream(tmpOut)) {
                String html = null;
                try {
                    html = driver.getPageSource();
                } catch (Exception e) {
                    LOG.warn("problem getting source");
                }
                if (html != null) {
                    IOUtils.copy(
                            new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), os);

                }
            }
            hash = mvTmp(tmpOut);
        } catch (IOException e) {
            urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_ERR);
        } finally {
            if (tmpOut != null) {
                try {
                    if (Files.isRegularFile(tmpOut)) {
                        Files.delete(tmpOut);
                    }
                } catch (IOException e) {
                    LOG.error("couldn't delete: " + tmpOut.toAbsolutePath(), e);
                }
            }
        }
        urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_SUCCESS, hash);

        List<WebElement> elements =
                driver.findElements(By.tagName("a"));
        for (WebElement e : elements) {
            try {
                tryAddLink(urlString, e.getAttribute("href"));
            } catch (Exception ex) {
                //swallow
            }
        }
    }

    private String mvTmp(Path tmp) throws IOException {

        String hash = null;
        try (InputStream is = Files.newInputStream(tmp)) {
            hash =
                DigestUtils.sha256Hex(is);
        }

        Path targ = outputDirectory.resolve(
                hash.substring(0, 2)
                        + "/" + hash.substring(2,4)
                        + "/" + hash.substring(4,6)
                        + "/" + hash.substring(6,8)
                        + "/" + hash.substring(8,10)
                        + "/" +hash);
        if (Files.isRegularFile(targ)) {
            //log
            return hash;
        }
        Files.createDirectories(targ.getParent());
        Files.copy(tmp, targ);
        return hash;
    }

    private void tryAddLink(String baseUrl, String link) {
        String normalized = URLNormalizer.normalize(baseUrl, link);
        try {
            urlManager.insert(normalized);
        } catch (SQLException e) {
            LOG.warn("problem inserting", e);
        }
    }

    private void binaryFetch(String urlString) throws SQLException {
        LOG.info("fetching binary: " + urlString);
        get(urlString);
    }


    private void get(String urlString) throws SQLException {
        //overly simplistic...need to add proxy, etc., but good enough for now
        URL uri = null;
        try {
            uri = new URL(urlString);
        } catch (MalformedURLException e) {
            LOG.warn("bad url " + urlString);
            urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_ERR);
            return;
        }
        HttpHost target = new HttpHost(uri.getHost(), uri.getPort());
        final HttpGet httpGet;
        try {
            String get = uri.getFile();
            if (!StringUtils.isBlank(uri.getQuery())) {
                get += "?" + uri.getQuery();
            }
            httpGet = new HttpGet(get);
        } catch (Exception e) {
            LOG.warn("problem getting " + urlString, e);
            urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_ERR);
            return;
        }

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (httpGet != null) {
                    httpGet.abort();
                }
            }
        };

        new Timer(true).schedule(task, HARD_TIMEOUT_SECONDS * 1000);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            long start = System.currentTimeMillis();
            try (CloseableHttpResponse httpResponse = httpClient.execute(target, httpGet)) {
                for (Header header : httpResponse.getAllHeaders()) {
                    if (header.getName().toLowerCase().equals("content-length")) {
                        try {
                            long length = Long.parseLong(header.getValue());
                            if (MAX_HEADER_LENGTH > 0 && length > MAX_HEADER_LENGTH) {
                                LOG.warn("skipping " + urlString + "; too long " + length);
                                urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_GT_MAX_HEADER);
                                return;
                            }
                        } catch (NumberFormatException e) {
                            //swallow
                        }
                        break;
                    }
                }
                long elapsed = System.currentTimeMillis() - start;
                LOG.info("got response " + httpResponse.getStatusLine().toString() + " in " +
                        elapsed + " millis.");
                if (httpResponse.getStatusLine().getStatusCode() < 200 ||
                    httpResponse.getStatusLine().getStatusCode() >= 300) {
                    String msg = new String(EntityUtils.toByteArray(
                            httpResponse.getEntity()), StandardCharsets.UTF_8);
                    LOG.warn("bad response:" + msg);
                    urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_ERR);
                    return;
                }
                String hash = writeBinary(httpResponse.getEntity().getContent());
                elapsed = System.currentTimeMillis() - start;
                urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_SUCCESS, hash);
                LOG.info("Finished downloading in " + elapsed + "ms");
            }
        } catch (IOException e) {
            LOG.error("problem", e);
            urlManager.lazyUpdate(urlString, URLManager.STATUS.FETCH_ERR);
        }
    }

    private String writeBinary(InputStream content) throws IOException {
        Path tmp = Files.createTempFile("", "-crawler");
        try {
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            return mvTmp(tmp);
        } finally {
            if (tmp != null) {
                Files.delete(tmp);
            }
        }
    }
}
