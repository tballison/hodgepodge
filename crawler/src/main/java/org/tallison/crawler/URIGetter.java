package org.tallison.crawler;


import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.log4j.Logger;

public class URIGetter implements Callable<String> {

    static Logger logger = Logger.getLogger(URIGetter.class);
    private final int MAX_HEADER_LENGTH = 10000;

    private final Path docRoot;
    private final ArrayBlockingQueue<String> urls;
    private final Gson gson = new Gson();

    private final Reporter reporter;
    private Base32 base32 = new Base32();
    private boolean followLinks = true;
    private int proxyPort = -1;
    private String proxyHost = null;
    private int timeoutMilliseconds = 30000;

    private String linksMustMatchDomain = null;

    public URIGetter(ArrayBlockingQueue<String> urls, Path docRoot, Reporter reporter) {
        this.urls = urls;
        this.docRoot = docRoot;
        this.reporter = reporter;
    }

    @Override
    public String call() throws Exception {
        while (true) {
            String urlString = urls.poll(1, TimeUnit.SECONDS);
            if (urlString == null) {
                continue;
            }
            if (URLCrawler.POISON.equals(urlString)) {
                return "DONE";
            }
            fetch(urlString);
        }
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }


    private void fetch(String urlString) {
        URI uri = null;
        try {
            uri = new URI(urlString);
        } catch (URISyntaxException e) {
            logger.warn("Bad url: "+urlString);
            reporter.setStatus(null, urlString, Reporter.FETCH_STATUS.BAD_URL);
            return;
        }
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpHost target = new HttpHost(uri.getHost());
        String urlPath = uri.getRawPath();
        System.out.println(uri);
        if (uri.getRawQuery() != null) {
            urlPath+="?"+uri.getRawQuery();
        }
        System.out.println("going to get: "+ urlString + " : "+urlPath);
        boolean success = reporter.setStatus(null, urlString, Reporter.FETCH_STATUS.ABOUT_TO_FETCH);
        if (! success) {
            return;
        }
        boolean shouldFetch = checkHeader(httpClient, target, urlString, urlPath);
        if (shouldFetch) {
            actuallyFetch(httpClient, target, urlString, urlPath);
        }

    }

    private void actuallyFetch(CloseableHttpClient httpClient, HttpHost target, String urlString, String urlPath) {
        HttpGet httpGet = null;

        try {
            httpGet = new HttpGet(urlPath);
        } catch (Exception e) {
            logger.warn("bad path "+urlPath, e);
            reporter.setStatus(null, urlString, Reporter.FETCH_STATUS.BAD_URL);
            return;
        }
        httpGet.setConfig(getRequestConfig(proxyHost, proxyPort));


        HttpCoreContext coreContext = new HttpCoreContext();
        CloseableHttpResponse httpResponse = null;
        URI lastURI = null;
        try {
            httpResponse = httpClient.execute(target, httpGet, coreContext);
            RedirectLocations redirectLocations = (RedirectLocations)coreContext.getAttribute(
                    DefaultRedirectStrategy.REDIRECT_LOCATIONS);
            if (redirectLocations != null) {
                for (URI redirectURI : redirectLocations.getAll()) {
                    lastURI = redirectURI;
                }
            } else {
                lastURI = httpGet.getURI();
            }
        } catch (IOException e) {
            System.out.println("IOException: "+urlString);
            logger.warn("IOException for "+urlString, e);
            reporter.setStatus(null, urlString, Reporter.FETCH_STATUS.FETCHED_IO_EXCEPTION);
            return;
        }
//        lastURI = uri.resolve(lastURI);
  //      int lastURIId = db.addURL(lastURI.toString());

        if (httpResponse.getStatusLine().getStatusCode() != 200) {
            System.out.println("STATUS: "+httpResponse.getStatusLine().getStatusCode());
            reporter.setResponse(null, urlString, Reporter.FETCH_STATUS.FETCHED_NOT_200,
                    httpResponse.getStatusLine().getStatusCode());
            return;
        }

        String headerString = gson.toJson(new SimpleHeaders(httpResponse.getAllHeaders()));
        if (headerString.length() > MAX_HEADER_LENGTH) {
            logger.warn("truncating header of length: "+headerString.length());
            headerString = headerString.substring(0, MAX_HEADER_LENGTH);
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("tallison-crawler", "");
            Files.copy(httpResponse.getEntity().getContent(),
                    tmp,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            reporter.setResponse(null, urlString, Reporter.FETCH_STATUS.FETCHED_IO_EXCEPTION_READING_ENTITY,
                    200, headerString, null);
            try {
                Files.delete(tmp);
            } catch (IOException e2) {
                logger.warn("couldn't delete tmp file: "+tmp.toString());
            }
            return;
        }

        String digest = null;
        try (InputStream is = Files.newInputStream(tmp)) {
            digest = base32.encodeAsString(DigestUtils.sha1(is));
        } catch (IOException e) {
            reporter.setResponse(null, urlString, Reporter.FETCH_STATUS.FETCHED_IO_EXCEPTION_SHA1, 200, headerString, null);
            logger.warn("IOException during digesting: "+tmp.toAbsolutePath());
            return;
        }

        Path targFile = docRoot.resolve(digest.substring(0,2)+"/"+digest);
        Reporter.FETCH_STATUS fetchStatus = Reporter.FETCH_STATUS.FETCHED_SUCCESSFULLY;
        if (! Files.exists(targFile)) {
            try {
                Files.createDirectories(targFile.getParent());
                Files.copy(tmp, targFile);
                fetchStatus = Reporter.FETCH_STATUS.ADDED_TO_REPOSITORY;
            } catch (IOException e) {
                //swallow
            }
        }
        try {
            Files.delete(tmp);
        } catch (IOException e) {
            logger.warn("couldn't delete tmp file: "+tmp.toString());
        }
        reporter.setResponse(null, urlString, fetchStatus, 200, headerString, targFile.getFileName().toString());
    }

    private RequestConfig getRequestConfig(String proxyHost, int proxyPort) {
        if (proxyHost != null && proxyPort > -1) {
            HttpHost proxy = new HttpHost(proxyHost, proxyPort, "http");
            return RequestConfig.custom()
                    .setConnectionRequestTimeout(30000)
                    .setCircularRedirectsAllowed(false)
                    .setMaxRedirects(10)
                    .setSocketTimeout(30000)
                    .setConnectTimeout(30000)
                    .setProxy(proxy).build();
        } else {
            return RequestConfig.custom()
                    .setConnectionRequestTimeout(30000)
                    .setCircularRedirectsAllowed(false)
                    .setMaxRedirects(10)
                    .setSocketTimeout(30000)
                    .setConnectTimeout(30000).build();
        }

    }

    private boolean checkHeader(CloseableHttpClient httpClient, HttpHost target, String urlString, String urlPath) {
        HttpHead httpHead = null;
        try {
            httpHead = new HttpHead(urlPath);
        } catch (Exception e) {
            logger.warn("bad path " + urlString, e);
            reporter.setStatus(null, urlString, Reporter.FETCH_STATUS.BAD_URL);
            return false;
        }
        httpHead.setConfig(getRequestConfig(proxyHost, proxyPort));

        HttpCoreContext coreContext = new HttpCoreContext();
        CloseableHttpResponse httpResponse = null;
        URI lastURI = null;
        try {
            httpResponse = httpClient.execute(target, httpHead, coreContext);
        } catch (IOException e) {
            e.printStackTrace();
            reporter.setStatus(null, urlString, Reporter.FETCH_STATUS.FETCHED_IO_EXCEPTION);
            return false;
        }
        if (httpResponse.getStatusLine().getStatusCode() != 200) {
            reporter.setResponse(null, urlString, Reporter.FETCH_STATUS.FETCHED_NOT_200, httpResponse.getStatusLine().getStatusCode());
            return false;
        }

        String headerString = gson.toJson(new SimpleHeaders(httpResponse.getAllHeaders()));
        reporter.setResponse(null, urlString, Reporter.FETCH_STATUS.RETRIEVED_HEAD, 200, headerString, null);
        String contentType = null;
        for (Header header : httpResponse.getAllHeaders()) {
            if ("content-type".equalsIgnoreCase(header.getName())) {
                contentType = header.getValue();
                break;
            }
        }
        if (contentType == null) {
            reporter.setResponse(null, urlString, Reporter.FETCH_STATUS.HEADER_NOT_SELECTED, 200, headerString, null);
            return false;
        }
        reporter.setResponse(null, urlString, Reporter.FETCH_STATUS.HEADER_SELECTED, 200, headerString, null);
        return true;

    }


    private void delete(int fetchId, Path tmp) {
        if (tmp != null) {
            try {
                Files.delete(tmp);
            } catch (IOException e){
                logger.warn("couldn't delete tmp file:"+tmp.toAbsolutePath().toString() +
                        " for fetchId: "+fetchId, e);
            }
        }

    }
}
