package org.tallison;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.txt.UniversalEncodingDetector;


public class ScrapingUniversalEncodingDetector implements EncodingDetector {

    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        input.mark(10000);
        try {
            byte[] scraped = new MarkupScraper().scrape(input, 10000);
            if (scraped.length < 200) {
                input.reset();
                return new UniversalEncodingDetector().detect(input, metadata);
            } else {
                return new UniversalEncodingDetector().detect(new ByteArrayInputStream(scraped), metadata);
            }
        } finally {
            input.reset();
        }
    }
}
