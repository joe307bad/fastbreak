import kotlin.random.Random

fun getRandomId(length: Int = 12): String {
    val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return (1..length)
        .map { allowedChars.random(Random) }
        .joinToString("")
}