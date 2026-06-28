import org.jsoup.Jsoup
import java.net.URLDecoder

fun main() {
    val originalUrl = "https://filman.cc/m/sukienka-evil-dress-el-vestido-2026"
    
    // Simulate what might happen to the URL
    val encodedUrl = java.net.URLEncoder.encode(originalUrl, "UTF-8")
    // Compose Nav decoding
    val composeDecoded = URLDecoder.decode(encodedUrl, "UTF-8")
    // MainActivity explicit decoding
    val finalUrl = URLDecoder.decode(composeDecoded, "UTF-8")
    
    println("Fetching: \$finalUrl")
    
    val cookie = "PHPSESSID=9am2t5lekt01algj8mp3rb0i72; BKD_REMEMBER=BmNJLd1EWOTxh9oMqSauHI6Ag7UFt3r8nz4skjifDbPRvV5Cy2KGwXcplY; user_id=1811721"
    
    try {
        val conn = Jsoup.connect(finalUrl)
            .userAgent("Mozilla/5.0")
            .header("Cookie", cookie)
            .ignoreHttpErrors(true)
            .followRedirects(true)
            
        val doc = conn.get()
        val currentUrl = conn.response().url().toString()
        println("Final URL after fetch: \$currentUrl")
    } catch(e: Exception) {
        e.printStackTrace()
    }
}
