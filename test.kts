import org.jsoup.Jsoup

val html = """<meta property="og:title" content="Brooklyn 9-9 / Brooklyn Nine-Nine">"""
val doc = Jsoup.parse(html)
val titleMeta = doc.selectFirst("meta[property=\"og:title\"]")
println("Title: " + titleMeta?.attr("content"))
