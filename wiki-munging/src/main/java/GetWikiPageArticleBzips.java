import org.apache.http.client.HttpClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GetWikiPageArticleBzips {

    //TODO: somewhere we have to change "my" to "ms"
    private final static int MAX_BZIPS_PER_LANG = 5;
    //enwiki-latest-pages-articles27.xml-p47163462p48663461.bz2
    private final static Pattern LATEST_HREF_PATTERN =
            Pattern.compile("href=\"([-\\w]+wiki-latest-pages-articles\\d+[^\"]+\\.bz2)\"");

    private final static int MAX_THREADS = 2;
    private final String lang;
    private final Path outputDir;

    public GetWikiPageArticleBzips(String lang, Path dir) throws IOException {
        this.lang = lang;
        this.outputDir = dir;

    }

    public void execute() throws IOException {
        Files.createDirectories(outputDir);
        String baseURL = "https://dumps.wikimedia.org/"+lang+"wiki/latest/";
        ArrayBlockingQueue<String> wikiUrls = getBzipUrls(baseURL);
        System.out.println("found "+wikiUrls.size() + " wiki urls");
        //fill in here. :)
        ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
        ExecutorCompletionService<Integer>
                completionService = new ExecutorCompletionService<Integer>(executorService);

        for (int i = 0; i < MAX_THREADS; i++) {
            completionService.submit(new BzipLangGetter(baseURL, wikiUrls));
        }

        int completed = 0;
        while (completed < MAX_THREADS) {
            Future<Integer> future = null;
            try {
                future = completionService.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                future.get();
            } catch (InterruptedException|ExecutionException e) {
                e.printStackTrace();
            }
            completed++;
        }
        executorService.shutdownNow();

    }

    static ArrayBlockingQueue<String> getBzipUrls(String baseUrl) throws SearchClientException {
        HttpClient client = HttpUtils.getDefaultClient();
        byte[] bytes = HttpUtils.get(client, baseUrl);
        String html = new String(bytes, StandardCharsets.UTF_8);
        return extractUrls(html);
    }

    static ArrayBlockingQueue<String> extractUrls(String html) {
        Matcher m = LATEST_HREF_PATTERN.matcher(html);
        ArrayBlockingQueue<String> list = new ArrayBlockingQueue<>(1000);
        int i = 0;
        while (m.find()) {
            String bzurl = m.group(1);
            list.add(bzurl);
        }
        return list;
    }

    public static void main(String[] args) throws IOException {
        GetWikiPageArticleBzips getter =
                new GetWikiPageArticleBzips(args[0], Paths.get(args[1]));
        getter.execute();
    }

    private class BzipLangGetter implements Callable<Integer> {
        private final String urlBase;
        private final ArrayBlockingQueue<String> files;
        BzipLangGetter(String urlBase, ArrayBlockingQueue<String> files) {
            this.urlBase = urlBase;
            this.files = files;
        }
        @Override
        public Integer call() throws Exception {
            while (files.size() > 0) {
                String file = files.poll();
                if (file == null) {
                    return 1;
                }
                HttpClient client = HttpUtils.getDefaultClient();
                Path target = outputDir.resolve(file);
                if (Files.exists(target)) {
                    System.err.println("already exists: "+target);
                    continue;
                }
                String url = urlBase+"/"+file;
                System.err.println("getting "+url);
                HttpUtils.get(client, url, target);
                System.err.println("finished "+url);
            }
            return 1;
        }
    }
}
