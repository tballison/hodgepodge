package org.tallison.selenium.filters;

import org.tallison.selenium.URLFilter;

import java.net.MalformedURLException;
import java.net.URL;

public class HostFilter implements URLFilter {

    private final String host;

    public HostFilter(String host) {
        this.host = host;
    }

    @Override
    public boolean accept(URL url) {
        String candHost = url.getHost();
        if (candHost.endsWith(host)) {
            return true;
        }
        return false;
    }
}
