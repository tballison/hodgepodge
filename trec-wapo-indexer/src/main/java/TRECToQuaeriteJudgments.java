import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TRECToQuaeriteJudgments {

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);
        Path topics = dir.resolve("newsir19-background-linking-topics.xml");
        Path judgments = dir.resolve("newsir19-qrels-background.txt");
        BufferedWriter writer = Files.newBufferedWriter(
                dir.resolve("wapo_truth.csv"), StandardCharsets.UTF_8);
        Map<Integer, String> topicIdMap = loadTopicIdMap(topics);
        System.out.println(topicIdMap);
        writer.write("rating,docid,index1,id1\n");

        BufferedReader reader = Files.newBufferedReader(judgments, StandardCharsets.UTF_8);
        String line = reader.readLine();
        while (line != null) {
            String[] data = line.split(" +");
            int topic = Integer.parseInt(data[0]);
            String docId = data[2];
            int rating = Integer.parseInt(data[3]);
            writer.write(rating+","+
                    docId+",wapo,"+topicIdMap.get(topic)+"\n");
            line = reader.readLine();
        }

        writer.flush();
        writer.close();
    }

    private static Map<Integer, String> loadTopicIdMap(Path topics) throws IOException {
        BufferedReader reader = Files.newBufferedReader(topics, StandardCharsets.UTF_8);
        Matcher num = Pattern.compile("<num> Number: (\\d+)").matcher("");
        Matcher docid = Pattern.compile("<docid>([-a-z0-9]+)</docid").matcher("");
        String line = reader.readLine();
        Map<Integer, String> topicIdMap = new HashMap<>();
        int numInt = -1;
        String docId = "";
        while (line != null) {
            if (num.reset(line).find()) {
                numInt = Integer.parseInt(num.group(1));
            } else if (docid.reset(line).find()) {
                docId = docid.group(1);
                if (numInt == -1) {
                    throw new IllegalArgumentException("bad number "+docId);
                }
                topicIdMap.put(numInt, docId);
                numInt = -1;
                docId = null;
            }

            line = reader.readLine();
        }
        return topicIdMap;
    }
}
