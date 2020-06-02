import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpUtils {
    public static byte[] get(HttpClient httpClient, String url) throws SearchClientException {
        //overly simplistic...need to add proxy, etc., but good enough for now
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        HttpHost target = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        HttpGet httpGet = null;
        try {
            String get = uri.getPath();
            if (!StringUtils.isBlank(uri.getQuery())) {
                get += "?" + uri.getRawQuery();
            }
            httpGet = new HttpGet(get);
        } catch (Exception e) {
            throw new IllegalArgumentException(url, e);
        }

        HttpResponse httpResponse = null;
        try {
            httpResponse = httpClient.execute(target, httpGet);
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                String msg = new String(EntityUtils.toByteArray(
                        httpResponse.getEntity()), StandardCharsets.UTF_8);
                throw new SearchClientException("Bad status code: " +
                        httpResponse.getStatusLine().getStatusCode()
                        + "for url: " + url + "; msg: " + msg);
            }
            return EntityUtils.toByteArray(httpResponse.getEntity());

        } catch (IOException e) {
            throw new SearchClientException(url, e);
        } finally {
            if (httpResponse != null && httpResponse instanceof CloseableHttpResponse) {
                try {
                    ((CloseableHttpResponse) httpResponse).close();
                } catch (IOException e) {
                    throw new SearchClientException(url, e);
                }
            }
        }
    }

    public static void get(HttpClient httpClient, String url, Path path) throws SearchClientException {
        //overly simplistic...need to add proxy, etc., but good enough for now
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }

        HttpHost target = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        HttpGet httpGet = null;
        try {
            String get = uri.getPath();
            if (!StringUtils.isBlank(uri.getQuery())) {
                get += "?" + uri.getRawQuery();
            }
            httpGet = new HttpGet(get);
        } catch (Exception e) {
            throw new IllegalArgumentException(url, e);
        }

        HttpResponse httpResponse = null;
        try {
            httpResponse = httpClient.execute(target, httpGet);
            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                String msg = new String(EntityUtils.toByteArray(
                        httpResponse.getEntity()), StandardCharsets.UTF_8);
                throw new SearchClientException("Bad status code: " +
                        httpResponse.getStatusLine().getStatusCode()
                        + "for url: " + url + "; msg: " + msg);
            }
            Files.copy(httpResponse.getEntity().getContent(), path);

        } catch (IOException e) {
            throw new SearchClientException(url, e);
        } finally {
            if (httpResponse != null && httpResponse instanceof CloseableHttpResponse) {
                try {
                    ((CloseableHttpResponse) httpResponse).close();
                } catch (IOException e) {
                    throw new SearchClientException(url, e);
                }
            }
        }
    }

    public static HttpClient getDefaultClient() {
        return HttpClients.createDefault();
    }
}
