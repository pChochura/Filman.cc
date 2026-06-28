import java.net.HttpURLConnection;
import java.net.URL;

public class TestHttpNoHeaders {
    public static void main(String[] args) throws Exception {
        String urlString = "https://filman.cc/m/robin-hood-koniec-legendy-the-death-of-robin-hood-2026";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Cookie", "PHPSESSID=9am2t5lekt01algj8mp3rb0i72; BKD_REMEMBER=BmNJLd1EWOTxh9oMqSauHI6Ag7UFt3r8nz4skjifDbPRvV5Cy2KGwXcplY; user_id=1811721");
        conn.setInstanceFollowRedirects(true);
        
        int status = conn.getResponseCode();
        System.out.println("Status: " + status);
        System.out.println("URL: " + conn.getURL());
    }
}
