import com.example.filman.data.scraper.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val extractor = VoeExtractor()
    val result = extractor.extractVideo("https://voe.sx/e/s40n2idwoxhg")
    println("Result: $result")
}
