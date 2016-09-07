package org.tallison.crawler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Created by TALLISON on 8/5/2016.
 */
public class URIGetterTest {
    static Path docRoot;
    @BeforeClass
    public static void setUp() {
        try {
            docRoot = Files.createTempDirectory("urlgettertest");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterClass
    public static void cleanUp() {
        System.out.println(docRoot.toAbsolutePath());
        /*
        try {
            for (File f : docRoot.toFile().listFiles()) {
                Files.delete(f.toPath());
            }
            Files.delete(docRoot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
    }
    @Test
    public void testBasic() throws Exception {
        ArrayBlockingQueue<String> urls = new ArrayBlockingQueue<>(10);
        urls.add("http://www.cnn.com");
        urls.add(URLCrawler.POISON);
        URIGetter uriGetter = new URIGetter(urls, docRoot, new StdOutReporter());
        uriGetter.setProxyHost("gatekeeper-w.mitre.org");
        uriGetter.setProxyPort(80);
        ExecutorService ex = Executors.newFixedThreadPool(10);
        ExecutorCompletionService<String> completionService = new ExecutorCompletionService<String>(ex);
        completionService.submit(uriGetter);

        int finished = 0;
        while (finished < 1) {
            Future<String> future = completionService.poll();

            if (future != null && future.isDone()) {
                System.out.println(future.get());
                finished++;
            }
        }
        ex.shutdown();
        ex.shutdownNow();
        System.out.println("completed");
    }

    private static class StdOutReporter implements Reporter {

        @Override
        public boolean setStatus(String key, String url, FETCH_STATUS status) {
            System.out.println(key + " : "+url + ": "+status);
            return true;
        }

        @Override
        public void setResponse(String key, String url, FETCH_STATUS status, int httpCode) {
            System.out.println(key + " : "+url + ": "+status + " : "+httpCode);

        }

        @Override
        public void setResponse(String key, String url, FETCH_STATUS status, int httpCode, String header, String digest) {
            System.out.println(key + " : "+url + ": "+status + " : "+httpCode + " : " + header + " : "+ digest);

        }
    }
}
