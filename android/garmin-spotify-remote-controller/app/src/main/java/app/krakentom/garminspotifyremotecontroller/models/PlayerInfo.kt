package app.krakentom.garminspotifyremotecontroller.models

class PlayerInfo(
    var song: String = "",
    var artist: String = "",
    var duration: Long = 0,
    var isInLibrary: Boolean = false,
) {
    fun toMap(): Map<String, Any> {
        val seconds = duration / 1000

        return mapOf(
            "song" to song,
            "artist" to artist,
            "length" to "%02d:%02d".format(seconds / 60, seconds % 60),
            "isInLibrary" to isInLibrary,
        )
    }
}