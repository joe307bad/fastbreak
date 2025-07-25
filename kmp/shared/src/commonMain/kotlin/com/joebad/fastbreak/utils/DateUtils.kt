package com.joebad.fastbreak.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
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
        return date.year - 2024 + 1
    }
    
    private fun LocalDate.weekOfYear(): Int {
        val firstDayOfYear = LocalDate(this.year, 1, 1)
        val firstMondayOfYear = firstDayOfYear.let { firstDay ->
            val daysUntilMonday = (DayOfWeek.MONDAY.isoDayNumber - firstDay.dayOfWeek.isoDayNumber + 7) % 7
            firstDay.plus(daysUntilMonday, DateTimeUnit.DAY)
        }
        
        return if (this < firstMondayOfYear) {
            // Date is in week 1 or belongs to previous year's last week
            1
        } else {
            val daysSinceFirstMonday = firstMondayOfYear.until(this, DateTimeUnit.DAY)
            (daysSinceFirstMonday / 7) + 1
        }
    }
}