package org.tallison.crawler;


public class SimpleHeader {

    final String key;
    final String value;

    public SimpleHeader(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
