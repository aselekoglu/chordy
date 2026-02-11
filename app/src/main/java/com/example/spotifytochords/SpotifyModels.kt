package com.example.spotifytochords

data class TrackInfo(
    val id: String,
    val name: String,
    val artists: List<String>,
    val album: String?
)

data class AudioAnalysis(
    val bars: List<TimedElement>,
    val segments: List<AudioSegment>
)

data class TimedElement(
    val startSec: Double,
    val durationSec: Double,
    val confidence: Double
)

data class AudioSegment(
    val startSec: Double,
    val durationSec: Double,
    val confidence: Double,
    val pitches: DoubleArray
)

data class AudioFeatures(
    val key: Int,
    val mode: Int,
    val tempo: Double,
    val timeSignature: Int
)

data class ChordTimelineEntry(
    val startSec: Double,
    val endSec: Double,
    val chord: String
)

data class ChordProgression(
    val timeline: List<ChordTimelineEntry>,
    val compactProgression: String,
    val keyLabel: String?,
    val tempoBpm: Double?,
    val timeSignature: Int?
)
