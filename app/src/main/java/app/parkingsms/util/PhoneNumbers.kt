package app.parkingsms.util

object PhoneNumbers {
    /** Loose comparison of a configured number and an incoming sender address. */
    fun matches(a: String, b: String): Boolean {
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.length < 3 || db.length < 3) return false
        return da == db || da.endsWith(db) || db.endsWith(da)
    }
}
