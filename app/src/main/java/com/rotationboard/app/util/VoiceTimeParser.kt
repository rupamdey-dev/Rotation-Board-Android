package com.rotationboard.app.util

// Parses spoken/transcribed phrases like "nine forty five pm", "9:45 PM",
// "quarter past five in the evening", "half past nine am", "noon", "midnight"
// into a 24-hour (hour, minute) pair. Returns null if it can't make sense of it.
//
// Android's built-in speech recognizer often already normalizes spoken numbers
// into digits (e.g. "nine forty five pm" -> "9:45 pm"), so the digit-pattern
// check runs first since it's the most reliable path. The word-based parser
// is the fallback for phrasing the recognizer left as words.
object VoiceTimeParser {

    private val wordNumbers = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50
    )

    fun parse(rawText: String): Pair<Int, Int>? {
        val text = rawText.trim().lowercase()
            .replace(".", "")
            .replace(Regex("\\s+"), " ")

        // Special cases
        if (text.contains("noon")) return 12 to 0
        if (text.contains("midnight")) return 0 to 0

        parseDigits(text)?.let { return it }
        parseWords(text)?.let { return it }
        return null
    }

    private fun detectAmPm(text: String): Boolean? {
        return when {
            Regex("\\bpm\\b").containsMatchIn(text) -> true
            Regex("\\bam\\b").containsMatchIn(text) -> false
            text.contains("evening") || text.contains("night") || text.contains("afternoon") -> true
            text.contains("morning") -> false
            else -> null
        }
    }

    private fun to24Hour(hour12: Int, isPm: Boolean?): Int {
        var h = hour12 % 12
        if (isPm == true) h += 12
        // If no am/pm was said at all, assume the *next* upcoming occurrence
        // is more likely to be what they meant during typical waking hours;
        // caller (AddEditAccountActivity/DashboardActivity) already rolls the
        // date to "next occurrence" if the time has passed today, so a plain
        // 24h interpretation here is safe — default to as-spoken (AM) and let
        // the rollover logic handle it.
        return h
    }

    private fun parseDigits(text: String): Pair<Int, Int>? {
        // Matches "9:45 pm", "9 45 pm", "9:45", "21:45", "945pm"
        val match = Regex("(\\d{1,2})[:\\s]?(\\d{2})?\\s*(am|pm)?").find(text) ?: return null
        val hourStr = match.groupValues[1]
        val minuteStr = match.groupValues[2]
        val ampmStr = match.groupValues[3]

        var hour = hourStr.toIntOrNull() ?: return null
        var minute = minuteStr.toIntOrNull() ?: 0

        // Handle "945" style (no separator) where group2 is empty but hourStr
        // itself is 3-4 digits, e.g. "945" or "2145".
        if (minuteStr.isEmpty() && hourStr.length >= 3) {
            minute = hourStr.takeLast(2).toIntOrNull() ?: 0
            hour = hourStr.dropLast(2).toIntOrNull() ?: return null
        }

        if (hour !in 0..23 || minute !in 0..59) return null

        val isPm = when (ampmStr) {
            "pm" -> true
            "am" -> false
            else -> detectAmPm(text)
        }

        val finalHour = if (hour in 1..12) to24Hour(hour, isPm) else hour
        return finalHour to minute
    }

    private fun parseWords(text: String): Pair<Int, Int>? {
        val tokens = text.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        // "quarter past five" / "quarter to five" / "half past nine"
        val quarterPastIdx = tokens.indexOf("past").takeIf { it > 0 }
        val quarterToIdx = tokens.indexOf("to").takeIf { it > 0 }
        if (tokens.contains("quarter") || tokens.contains("half")) {
            val minuteWord = if (tokens.contains("half")) 30 else 15
            val hourIdx = quarterPastIdx?.plus(1) ?: quarterToIdx?.plus(1)
            if (hourIdx != null && hourIdx < tokens.size) {
                val hourWord = wordNumbers[tokens[hourIdx]]
                if (hourWord != null) {
                    val isPm = detectAmPm(text)
                    return if (quarterToIdx != null && !tokens.contains("half")) {
                        // "quarter to five" = 4:45
                        val h = if (hourWord == 1) 12 else hourWord - 1
                        to24Hour(h, isPm) to 45
                    } else {
                        to24Hour(hourWord, isPm) to minuteWord
                    }
                }
            }
        }

        // Plain word hour, optional word minutes, optional am/pm
        // e.g. "nine forty five pm", "nine o'clock", "five thirty"
        var hour: Int? = null
        var minute = 0
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i].replace("o'clock", "").replace("oclock", "")
            if (t.isBlank()) { i++; continue }
            val num = wordNumbers[t]
            if (num != null) {
                if (hour == null) {
                    hour = num
                } else {
                    // second number group = minutes; combine tens+ones like "forty five"
                    minute = if (num < 10 && minute >= 20 && minute % 10 == 0) minute + num else num
                }
            }
            i++
        }

        if (hour == null) return null
        val isPm = detectAmPm(text)
        return to24Hour(hour, isPm) to minute
    }
}
