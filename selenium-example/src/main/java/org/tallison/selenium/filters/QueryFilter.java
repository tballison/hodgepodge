package org.tallison.selenium.filters;

import org.apache.commons.lang3.StringUtils;
import org.tallison.selenium.URLFilter;

import java.net.URL;

/**
 * Do not include urls that have a query component
 */
public class QueryFilter implements URLFilter {

    @Override
    public boolean accept(URL url) {
        return StringUtils.isBlank(url.getQuery());
    }
}
