package org.tallison.selenium.filters;

import org.tallison.selenium.URLFilter;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class CompositeURLFilter implements URLFilter {

    private final List<URLFilter> filters = new ArrayList<>();

    public CompositeURLFilter(URLFilter ... filters) {
        for (URLFilter f : filters) {
            this.filters.add(f);
        }
    }

    @Override
    public boolean accept(URL url) {
        for (URLFilter f : filters) {
            if (!f.accept(url)) {
                return false;
            }
        }
        return true;
    }
}
