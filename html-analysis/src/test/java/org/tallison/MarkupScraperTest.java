/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tallison;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

public class MarkupScraperTest {

    private static String RANDOM_STRING;
    private static Long SEED;

    @BeforeClass
    public static void setUp() {
        SEED = new Random().nextLong();
    }

    private Random random = new Random(SEED);

    @Test
    public void testBasic() throws Exception {

        assertScrapedEquals(" a <sp", " a ");
        assertScrapedEquals(" a <sp ", " a ");
        assertScrapedEquals(" a <span> b c d </span> ", " a  b c d  ");
        assertScrapedEquals(" a <span> b c d <  /   span> ", " a  b c d  ");
        assertScrapedEquals(" a < span> b c d </span> ", " a  b c d  ");
        assertScrapedEquals(" a <span attr=\"attr1\"> b c d </span> ", " a  b c d  ");
        assertScrapedEquals(" a <span attr=\"attr1\" > b c d </span> ", " a  b c d  ");
        assertScrapedEquals(" a < span attr=\"attr1\"> b c d </span> ", " a  b c d  ");
        assertScrapedEquals(" a < span attr=\"attr1\" > b c d </span> ", " a  b c d  ");
        assertScrapedEquals(" a < span attr=\"attr1\" > b c d </spa", " a  b c d ");
        assertScrapedEquals(" a <span\tattr=\"attr1\"> b c d <  /   span> ", " a  b c d  ");
        assertScrapedEquals(" a <span\t\r\n\r\nattr=\"attr1\" > b c d </span> ", " a  b c d  ");
        assertScrapedEquals(" a <script> b c </SCRIpT> d", " a  d");
        assertScrapedEquals(" a <  sTyLE > b < script />c </STyle> d", " a  d");
        assertScrapedEquals(" a <  script > b c </script /> d", " a  d");

        //comments
        assertScrapedEquals(" a <!--  script > b c --> d", " a  d");
        assertScrapedEquals(" a <!--  script > b c \n\n some other stuff --> d", " a  d");
        //allow whitespace btwn < and !
        assertScrapedEquals(" a < !--  comment --> d", " a  d");

        assertScrapedEquals(" a <!-- blah - -> d", " a ");
        assertScrapedEquals(" a <!-- blah -- > d", " a ");
        assertScrapedEquals(" a <!- - blah --> d", " a  d");//treated not as comment but entity with name !-


        assertScrapedEquals(" a <!--  script > b c - -> d", " a ");

        //javascript comment does not turn off end tag for general comment
        assertScrapedEquals(" a <!-- \n//--> b c", " a  b c");

        //make sure forward slashes don't cause problems
        assertScrapedEquals("/a /b <t/able h/ref=\"some/thing\" or // other/> c ",
                "/a /b  c ");

        assertScrapedEquals("a b <script> if x < 4  then blah blah blah </script> d", "a b  d");

    }

    @Test
    public void testMetaheaderPassThrough() throws Exception {
        assertScrapedEquals(" blah <meta charset=\"xyz\"> blah",
                " blah <meta charset=\"xyz\"> blah" );
        assertScrapedEquals("blah <meta http-equiv=\"content-type\" content=\"text/html; charset=xyz\"/> blah",
                "blah <meta http-equiv=\"content-type\" content=\"text/html; charset=xyz\"/> blah");

        assertScrapedEquals(" blah < MeTa charSet  =  \"xyz\"> blah",
                " blah <MeTa charSet  =  \"xyz\"> blah" );


        //needs to have both meta and charset
        assertScrapedEquals(" blah <meta charst=\"xyz\"> blah",
                " blah  blah" );

    }

    @Test
    public void testOneOff() throws Exception {
        assertScrapedEquals(" a <script> b c </SCRIpT> d", " a  d");

    }

    @Test
    public void testLimit() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            sb.append("ab <!- > <span>aa</span> bb \n or other ");
        }
        String s = sb.toString();
        MarkupScraper markupScraper = new MarkupScraper();
        byte[] bytes = markupScraper.scrape(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)), 10000);
        assertTrue(bytes.length < 14000);
    }

    @Test(timeout = 2000)
    public void testRandomStrings() {
        //make sure that this won't cause permanent hangs
        MarkupScraper markupScraper = new MarkupScraper();
        for (int i = 0; i < 100; i++) {
            RANDOM_STRING = generateRandomHTML();
            try {
                byte[] bytes = markupScraper.scrape(new ByteArrayInputStream(
                        RANDOM_STRING.getBytes(StandardCharsets.UTF_8)));
            } catch (IOException e) {
                //these are ok
            }
        }
        RANDOM_STRING = null;
    }


    @Test
    public void testOneOffLocal() throws Exception {
        String digest = "AAPL3VFVUU6B6GKHBVMVGI3ZFSYKBS5F";
        Path p = Paths.get("C:\\Users\\tallison\\Desktop\\working\\New folder (4)")
                .resolve(digest);
        System.out.println(new String(new MarkupScraper().scrape(TikaInputStream.get(p))));
        System.out.println(new ScrapingHTMLEncodingDetector().detect(TikaInputStream.get(p), new Metadata()));
        System.out.println(new ScrapingICUEncodingDetector().detect(TikaInputStream.get(p), new Metadata()));
    }
    @After
    public void after() {
        assertNull("Random seed:"+SEED, RANDOM_STRING);
    }

    private String generateRandomHTML() {
        StringBuffer sb = new StringBuffer();
        Random r = new Random();
        for (int i = 0; i < 10000; i++) {
            int c = r.nextInt(500);
            sb.append(Character.toChars(c));
            if (r.nextDouble() < .01) {
                sb.append("<");
            }
            if (r.nextDouble() < .01) {
                sb.append(">");
            }
            if (r.nextDouble() < .01) {
                sb.append("<!--");
            }
            if (r.nextDouble() < .01) {
                sb.append("-->");
            }
        }
        return sb.toString();
    }

    private static void assertScrapedEquals(String markup, String expected) throws IOException {
        byte[] arr = new MarkupScraper().scrape(TikaInputStream.get(markup.getBytes(StandardCharsets.UTF_8)));
        String result = new String(arr, StandardCharsets.UTF_8);
        assertEquals(expected, result);
    }
}
