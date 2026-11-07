package app.parkingsms.util

object MessageTemplate {
    const val DEFAULT_START = "{zone} {plate}"
    const val DEFAULT_STOP = "S"

    fun render(template: String, zone: String, plate: String): String =
        template.replace("{zone}", zone).replace("{plate}", plate).trim()
}
