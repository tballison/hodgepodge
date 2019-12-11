package org.tallison.selenium;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

public class URLNormalizer {

    /**
     *
     * @param baseUrlString
     * @param link
     * @return a normalized url or null if link is malformed
     */
    public static String normalize(String baseUrlString, String link) {
        link = link.trim();
        baseUrlString = baseUrlString.trim();
        if (link.length() == 0) {
            return null;
        }

        URL baseUrl = null;
        try {
            if (! StringUtils.isBlank(baseUrlString)) {
                baseUrl = new URL(baseUrlString);
            }
        } catch (MalformedURLException e) {
            //swallow
        }


        URL url = null;
        try {
            if (baseUrl != null) {
                url = new URL(baseUrl, link);
            } else {
                url = new URL(link);
            }
        } catch (MalformedURLException e) {
            //log
            return null;
        }
        String host = url.getHost();
        //file includes query; use path instead
        String path = url.getPath();
        path = path.replaceAll("\\/+$", "/");

        String ext = FilenameUtils.getExtension(path);
        if (ext.trim().length() == 0 && ! path.endsWith("/") && StringUtils.isBlank(url.getQuery())) {
            path += "/";
        }
        if (host.endsWith("/")) {
            host = host.substring(0, host.length()-2);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        //this strips out anchors
        String normalized = "https://"+host+"/"+path;
        if (! StringUtils.isBlank(url.getQuery())) {
            normalized += "?"+url.getQuery();
        }
        return normalized;
    }
}
