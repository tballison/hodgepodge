import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.tallison.quaerite.connectors.SearchClient;
import org.tallison.quaerite.connectors.SearchClientException;
import org.tallison.quaerite.connectors.SearchClientFactory;
import org.tallison.quaerite.core.StoredDocument;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WapoIndexer {

    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    public static void main(String[] args) throws Exception {
        Path json = Paths.get(args[0]);
        SearchClient client = SearchClientFactory.getClient(args[1]);
        WapoIndexer indexer = new WapoIndexer();
        indexer.execute(json, client);
    }

    private void execute(Path jsonPath, SearchClient searchClient) throws IOException, SearchClientException {
        int cnt = 0;
        long start = System.currentTimeMillis();
        List<Article> articles = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(jsonPath, StandardCharsets.UTF_8)) {
            String json = reader.readLine();
            while (json != null) {
                Article article = nextArticle(JsonParser.parseString(json).getAsJsonObject());
                //System.out.println(article);
                articles.add(article);
                    cnt++;
                    if (articles.size() >= 100) {
                        searchClient.addDocuments(buildDocuments(articles));
                        System.out.println("indexed " + cnt + " in " +
                                (System.currentTimeMillis() - start) + " ms "+
                                articles.get(articles.size()-1).date);
                        articles.clear();
                    }

                json = reader.readLine();
            }
        }
        searchClient.addDocuments(buildDocuments(articles));
        System.out.println("indexed " + cnt + " in " +
                (System.currentTimeMillis() - start) + " ms "+
                articles.get(articles.size()-1).date);
        System.out.println("added "+cnt);
    }

    private List<StoredDocument> buildDocuments(List<Article> articles) {
        List<StoredDocument> docs = new ArrayList<>();
        for (Article a : articles) {
            StoredDocument sd = new StoredDocument(a.id);
            sd.addNonBlankField("author", a.author);
            sd.addNonBlankField("title", a.title);
            sd.addNonBlankField("kicker", a.kicker);
            System.out.println("story type: " + a.storyType);
            sd.addNonBlankField("story_type", a.storyType);
            sd.addNonBlankField("paragraphs", a.paragraphs);
            List<String> first = new ArrayList<>();
            if (a.paragraphs.size() > 0) {
                first.add(a.paragraphs.get(0));
                sd.addNonBlankField("paragraphs1", first);
            }
            if (a.paragraphs.size() > 1) {
                first.add(a.paragraphs.get(1));
                sd.addNonBlankField("paragraphs2", first);
            }
            if (a.paragraphs.size() > 2) {
                first.add(a.paragraphs.get(2));
                sd.addNonBlankField("paragraphs3", first);
            }
            if (a.date != null) {
                sd.addNonBlankField("pub_date", df.format(a.date));
            }
            docs.add(sd);
        }
        return docs;
    }

    private Article nextArticle(JsonObject root) {
        String id = root.get("id").getAsString();
        Article article = new Article(id);
        article.author = root.get("author").getAsString();
        if (root.has("title")) {
            JsonElement titleEl = root.get("title");
            if (! titleEl.isJsonNull()) {
                article.title = root.get("title").getAsString();
            }
        }
        for (JsonElement contentElement : root.get("contents").getAsJsonArray()) {
            if (contentElement == null || ! contentElement.isJsonObject()) {
                continue;
            }
            JsonObject contentObj = contentElement.getAsJsonObject();
            if (! contentObj.has("content")) {
//                System.out.println(contentObj.toString());
                //image?
                continue;
            }
            if (contentObj.get("content").isJsonNull() ||
                contentObj.get("content").isJsonObject() ||
                contentObj.get("content").isJsonArray()) {
                continue;
            }
            if (contentObj.get("content").isJsonObject()) {
                System.out.println("WHOA "+contentObj.toString());
            }
            String contentString = contentObj.get("content").getAsString();
            String cleanContent = cleanContent(contentString);
            if (contentObj.has("subtype") && "paragraph".equals(
                    contentObj.get("subtype").getAsString()
            )) {
                article.paragraphs.add(cleanContent);
            } else if (contentObj.has("type") &&
                    "kicker".equals(contentObj.get("type").getAsString())) {
                article.kicker = cleanContent;
                if (contentObj.has("storyType") &&
                        contentObj.get("storyType").isJsonPrimitive()) {
                    article.storyType = contentObj.get("storyType").getAsString();
                 }
            } else if (contentObj.has("type") &&
                "date".equals(contentObj.get("type").getAsString())) {
                long timestamp = contentObj.get("content").getAsLong();
                article.date = new Date(timestamp);
            }
        }
        return article;
    }

    private String cleanContent(String contentString) {
        //TODO - extract urls
        Matcher m = Pattern.compile("<[^>]+>").matcher(contentString);
        return m.replaceAll("");
    }

    private static class Article {
        private final String id;
        private String author;
        private String title;
        private String kicker;
        private String storyType;
        Date date;
        private List<String> paragraphs = new ArrayList<>();
        private List<String> urls = new ArrayList<>();

        Article(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Article{" +
                    "id='" + id + '\'' +
                    ", author='" + author + '\'' +
                    ", title='" + title + '\'' +
                    ", kicker='" + kicker + '\'' +
                    ", storyType='" + storyType + '\'' +
                    ", date=" + date +
                    ", paragraphs=" + paragraphs +
                    ", urls=" + urls +
                    '}';
        }
    }
}
