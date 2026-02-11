package com.example.spotifytochords

object SpotifyTrackParser {
    private val rawTrackId = Regex("^[A-Za-z0-9]{22}$")
    private val urlTrackId = Regex("""open\.spotify\.com/(?:intl-[^/]+/)?track/([A-Za-z0-9]{22})""")
    private val uriTrackId = Regex("""spotify:track:([A-Za-z0-9]{22})""")

    fun extractTrackId(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (rawTrackId.matches(value)) return value

        val urlMatch = urlTrackId.find(value)
        if (urlMatch != null) {
            return urlMatch.groupValues[1]
        }

        val uriMatch = uriTrackId.find(value)
        if (uriMatch != null) {
            return uriMatch.groupValues[1]
        }

        return null
    }
}
