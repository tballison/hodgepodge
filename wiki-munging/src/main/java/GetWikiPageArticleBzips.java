import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//this is a stub that hasn't actually been run!!!
public class GetWikiPageArticleBzips {

    //TODO: somewhere we have to change "my" to "ms"
    private final static int MAX_BZIPS_PER_LANG = 5;
    private final static Pattern LATEST_HREF_PATTERN =
            Pattern.compile("href=\"[-\\w]+wiki-latest-pages-articles[^\"]+.xml.bz2\"");

    private final static int MAX_THREADS = 3;
    private final Path outputDir;
    private final ArrayBlockingQueue<String> countries;

    public GetWikiPageArticleBzips(Path langList, Path dir) throws IOException {
        BufferedReader reader = Files.newBufferedReader(langList, StandardCharsets.UTF_8);
        countries = new ArrayBlockingQueue<String>(3);
        String line = reader.readLine();
        while (line != null) {
            countries.add(line.trim());
            line = reader.readLine();
        }
        reader.close();
        outputDir = dir;

    }

    public void execute() throws IOException {
        //fill in here. :)
        ExecutorService executorService = Executors.newFixedThreadPool(MAX_THREADS);
        ExecutorCompletionService<Integer>
                completionService = new ExecutorCompletionService<Integer>(executorService);

        for (int i = 0; i < MAX_THREADS; i++) {
            completionService.submit(new BzipLangGetter());
        }

    }


    public static void main(String[] args) throws IOException {
        GetWikiPageArticleBzips getter =
                new GetWikiPageArticleBzips(Paths.get(args[0]), Paths.get(args[1]));
    }

    private class BzipLangGetter implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            String country = countries.poll();
            if (country == null) {
                return 1;
            }
            String latestPageURL = "https://dumps.wikimedia.org/"+country+"wiki/latest/";
            String html = "";//actually get main page
            Matcher m = LATEST_HREF_PATTERN.matcher(html);
            while (m.find()) {
                //actually get the bzip2
            }

            return 1;
        }
    }
}
