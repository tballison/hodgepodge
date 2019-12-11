package org.tallison.selenium.filters;

import org.tallison.selenium.URLFilter;

import java.net.URL;
import java.util.List;
import java.util.regex.Matcher;

public class RegexFilter implements URLFilter {

    public static RegexFilter blacklist(List<Matcher> blacklist) {
        return new RegexFilter(blacklist);
    }


    private final List<Matcher> blacklist;
    private RegexFilter(List<Matcher> blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public boolean accept(URL url) {
        String urlString = url.toString();
        for (Matcher m : blacklist) {
            if (m.reset(urlString).find()) {
                return false;
            }
        }
        return true;
    }
}
