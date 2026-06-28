import java.util.Base64

fun main() {
    val original = "https://filman.cc/s/659/brooklyn-9-9-brooklyn-nine-nine"
    val encodedWithPad = Base64.getUrlEncoder().encodeToString(original.toByteArray())
    val encodedNoPad = Base64.getUrlEncoder().withoutPadding().encodeToString(original.toByteArray())
    println("Encoded (pad): " + encodedWithPad)
    println("Encoded (no pad): " + encodedNoPad)
    
    // Simulating Android's NO_PADDING flag which uses Base64.Decoder
    // Java's Base64 Decoder allows missing padding by default for getUrlDecoder
    val decoded = String(Base64.getUrlDecoder().decode(encodedNoPad))
    println("Decoded (no pad string): " + decoded)
}
