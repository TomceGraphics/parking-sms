package app.parkingsms.util

/** Single place for live input cleaning (plates, zone codes, SMS numbers). */
object TextCleaner {
    /** "bt-1110 ab" -> "BT1110AB". */
    fun cleanPlate(input: String): String = alnumUpper(input)

    /** "b 9" -> "B9". */
    fun cleanZone(input: String): String = alnumUpper(input)

    /** Keeps digits and a single leading '+'. */
    fun cleanSmsNumber(input: String): String {
        val sb = StringBuilder()
        for ((index, ch) in input.trim().withIndex()) {
            if (ch.isDigit() || (ch == '+' && index == 0)) sb.append(ch)
        }
        return sb.toString()
    }

    private fun alnumUpper(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            if (ch.isLetterOrDigit()) sb.append(ch.uppercaseChar())
        }
        return sb.toString()
    }
}
