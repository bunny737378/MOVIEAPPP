package com.lagradost.cloudstream3.ui.player

enum class CSPlayerEvent(val value: Int) {
    Pause(0),
    Play(1),
    SeekForward(2),
    SeekBack(3),
    NextEpisode(4),
    PrevEpisode(5),
    PlayPauseToggle(6),
    ToggleMute(7),
    SkipCurrentChapter(8),
    SkipOp(9),
}

enum class PlayerEventSource {
    UI,
    Player,
    Sync,
}

enum class SubtitleOrigin {
    URL,
    DOWNLOADED_FILE,
    EMBEDDED_IN_VIDEO,
}

data class SubtitleData(
    val originalName: String,
    val name: String,
    val url: String,
    val origin: SubtitleOrigin,
    val mimeType: String = "text/vtt",
    val headers: Map<String, String> = emptyMap(),
    val languageCode: String? = null,
)

interface IPlayer {
    fun getPosition(): Long? = null
    fun getDuration(): Long? = null
    fun getIsPlaying(): Boolean = false
    fun seekTime(time: Long, source: PlayerEventSource = PlayerEventSource.UI) {}
    fun seekTo(time: Long, source: PlayerEventSource = PlayerEventSource.UI) {}
    fun handleEvent(event: CSPlayerEvent, source: PlayerEventSource = PlayerEventSource.UI) {}
    fun release() {}
    fun isActive(): Boolean = false
    fun getPlaybackSpeed(): Float = 1f
    fun setPlaybackSpeed(speed: Float) {}
}
