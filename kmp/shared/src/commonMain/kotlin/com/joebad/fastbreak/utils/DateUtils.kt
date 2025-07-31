package com.joebad.fastbreak.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.until

object DateUtils {
    
    fun getSeasonWeekDay(date: LocalDate): Triple<Int, Int, Int> {
        val season = calculateSeason(date)
        val week = date.weekOfYear()
        val day = date.dayOfWeek.isoDayNumber // Monday=1, Sunday=7
        return Triple(season, week, day)
    }
    
    private fun calculateSeason(date: LocalDate): Int {
        return date.year - 2024
    }

    fun LocalDate.weekOfYear(): Int {
        val firstDayOfYear = LocalDate(this.year, 1, 1)
        
        // Find the first Monday of the year (or January 1st if it's a Monday)
        val firstMonday = if (firstDayOfYear.dayOfWeek == DayOfWeek.MONDAY) {
            firstDayOfYear
        } else {
            val daysUntilMonday = (DayOfWeek.MONDAY.isoDayNumber - firstDayOfYear.dayOfWeek.isoDayNumber + 7) % 7
            firstDayOfYear.plus(daysUntilMonday, DateTimeUnit.DAY)
        }
        
        return if (this < firstMonday) {
            // Date is before the first Monday, so it's in week 1
            1
        } else {
            // Calculate weeks since the first Monday
            val daysSinceFirstMonday = firstMonday.until(this, DateTimeUnit.DAY)
            (daysSinceFirstMonday / 7) + 2 // +2 because week 1 is before firstMonday, and we want 1-based indexing
        }
    }
    
    /**
     * Converts a date string in yyyymmdd format to the previous day in the same format.
     * This is used for leaderboard display, as the leaderboard shows results from the day before.
     * 
     * @param dateString The date string in yyyymmdd format (e.g., "20241215")
     * @return The previous day in yyyymmdd format (e.g., "20241214")
     */
    fun getPreviousDay(dateString: String): String {
        require(dateString.length == 8) { "Date string must be in yyyymmdd format (8 digits)" }
        
        val year = dateString.substring(0, 4).toInt()
        val month = dateString.substring(4, 6).toInt()
        val day = dateString.substring(6, 8).toInt()
        
        val date = LocalDate(year, month, day)
        val previousDay = date.minus(1, DateTimeUnit.DAY)
        
        return "${previousDay.year.toString().padStart(4, '0')}${previousDay.monthNumber.toString().padStart(2, '0')}${previousDay.dayOfMonth.toString().padStart(2, '0')}"
    }
    
    /**
     * Parses a date string in yyyymmdd format and returns the week number of the year.
     * 
     * @param dateString The date string in yyyymmdd format (e.g., "20241215")
     * @return The week number of the year
     */
    fun getWeekOfYear(dateString: String): Int {
        require(dateString.length == 8) { "Date string must be in yyyymmdd format (8 digits)" }
        
        val year = dateString.substring(0, 4).toInt()
        val month = dateString.substring(4, 6).toInt()
        val day = dateString.substring(6, 8).toInt()
        
        val date = LocalDate(year, month, day)
        return date.weekOfYear()
    }
}