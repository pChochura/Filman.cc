import org.jsoup.Jsoup;
import org.jsoup.Connection;
import org.jsoup.nodes.Document;

public class TestJsoup15 {
    public static void main(String[] args) throws Exception {
        String urlString = "https://filman.cc/m/robin-hood-koniec-legendy-the-death-of-robin-hood-2026";
        Connection conn = Jsoup.connect(urlString)
            .userAgent("Mozilla/5.0")
            .header("Cookie", "PHPSESSID=9am2t5lekt01algj8mp3rb0i72; BKD_REMEMBER=BmNJLd1EWOTxh9oMqSauHI6Ag7UFt3r8nz4skjifDbPRvV5Cy2KGwXcplY; user_id=1811721")
            .ignoreHttpErrors(true)
            .followRedirects(true);
            
        Document doc = conn.get();
        System.out.println("Final URL: " + conn.response().url().toString());
        System.out.println("Status code: " + conn.response().statusCode());
    }
}
