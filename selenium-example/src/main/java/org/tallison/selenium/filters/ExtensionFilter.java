package org.tallison.selenium.filters;

import org.apache.commons.io.FilenameUtils;
import org.tallison.selenium.URLFilter;

import java.net.URL;
import java.util.Locale;
import java.util.Set;

public class ExtensionFilter implements URLFilter {

    private final Set blackList;
    public ExtensionFilter(Set<String> blackList) {
        this.blackList = blackList;
    }

    @Override
    public boolean accept(URL url) {
        String ext = FilenameUtils.getExtension(url.getPath());
        if (ext == null) {
            return true;
        }
        String lc = ext.toLowerCase(Locale.US);
        if (blackList.contains(lc)) {
            return false;
        }
        return true;
    }
}
