package org.tallison.crawler;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;

/**
 * Created by TALLISON on 8/5/2016.
 */
public class SimpleHeaders {
    List<SimpleHeader> headers = new ArrayList<>();
    public SimpleHeaders(Header[] headerArr) {
        if (headerArr != null) {
            for (Header header : headerArr) {
                headers.add(new SimpleHeader(header.getName(), header.getValue()));
            }
        }
    }

    public List<SimpleHeader> getHeaders() {
        return headers;
    }
}
