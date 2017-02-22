package org.tallison;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.txt.Icu4jEncodingDetector;


public class ScrapingICUEncodingDetector implements EncodingDetector {

    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        input.mark(10000);
        try {
            byte[] scraped = new MarkupScraper().scrape(input, 10000);
            if (scraped.length < 200) {
                input.reset();
                return new Icu4jEncodingDetector().detect(input, metadata);
            }
            return new Icu4jEncodingDetector().detect(new ByteArrayInputStream(scraped), metadata);
        } finally {
            input.reset();
        }
    }
}
