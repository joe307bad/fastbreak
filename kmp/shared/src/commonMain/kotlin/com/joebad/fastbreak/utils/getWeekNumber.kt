import kotlinx.datetime.LocalDate

fun getWeekNumber(dateString: String?): Int {
    if(dateString == null) {
        return 0;
    }

    // Parse the date string (yyyymmdd format)
    val year = dateString.substring(0, 4).toInt()
    val month = dateString.substring(4, 6).toInt()
    val day = dateString.substring(6, 8).toInt()

    val date = LocalDate(year, month, day)

    // Get January 1st of the same year
    val jan1 = LocalDate(year, 1, 1)

    // Calculate days since January 1st
    val daysSinceJan1 = date.toEpochDays() - jan1.toEpochDays()

    // Get the day of week for January 1st (Monday = 1, Sunday = 7)
    val jan1DayOfWeek = jan1.dayOfWeek.ordinal + 1 // DayOfWeek.MONDAY.ordinal = 0, so add 1

    // Calculate week number
    // Add the days since Jan 1st to the day of week offset
    val weekNumber = (daysSinceJan1 + jan1DayOfWeek - 1) / 7 + 1

    return weekNumber.toInt()
}