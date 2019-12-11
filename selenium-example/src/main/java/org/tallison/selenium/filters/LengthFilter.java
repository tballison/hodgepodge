package org.tallison.selenium.filters;

import org.tallison.selenium.URLFilter;

import java.net.URL;

public class LengthFilter implements URLFilter {

    private final int maxLength;

    public LengthFilter(int maxLength) {
        this.maxLength = maxLength;
    }
    @Override
    public boolean accept(URL url) {
        return url.toString().length() < maxLength;
    }
}
