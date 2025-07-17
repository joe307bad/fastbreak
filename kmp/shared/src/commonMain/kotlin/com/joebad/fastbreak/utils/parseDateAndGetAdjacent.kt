
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.minus
import kotlinx.datetime.plus

fun parseDateAndGetAdjacent(dateString: String?): Pair<String?, String?> {

    if(dateString == null) {
        return Pair(null, null)
    }

    return try {
        val format = LocalDate.Format {
            year()
            monthNumber()
            dayOfMonth()
        }

        val date = LocalDate.parse(dateString, format)

        val dayBefore = date.minus(1, kotlinx.datetime.DateTimeUnit.DAY)
        val dayAfter = date.plus(1, kotlinx.datetime.DateTimeUnit.DAY)

        Pair(dayBefore.format(format), dayAfter.format(format))

    } catch (e: Exception) {
        Pair(null, null)
    }
}