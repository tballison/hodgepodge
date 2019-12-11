package org.tallison.selenium.filters;

import org.tallison.selenium.URLFilter;

import java.net.URL;

public class AcceptAllFilter implements URLFilter {
    @Override
    public boolean accept(URL url) {
        return true;
    }
}
