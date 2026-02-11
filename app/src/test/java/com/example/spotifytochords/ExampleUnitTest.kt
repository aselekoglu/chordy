package com.example.spotifytochords

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun parser_accepts_url_uri_and_id() {
        val id = "11dFghVXANMlKmJXsNCbNl"
        assertEquals(id, SpotifyTrackParser.extractTrackId(id))
        assertEquals(id, SpotifyTrackParser.extractTrackId("spotify:track:$id"))
        assertEquals(id, SpotifyTrackParser.extractTrackId("https://open.spotify.com/track/$id?si=abc123"))
        assertEquals(id, SpotifyTrackParser.extractTrackId("https://open.spotify.com/intl-tr/track/$id"))
    }

    @Test
    fun parser_rejects_invalid_input() {
        assertNull(SpotifyTrackParser.extractTrackId(""))
        assertNull(SpotifyTrackParser.extractTrackId("https://example.com/not-spotify"))
        assertNull(SpotifyTrackParser.extractTrackId("short-id"))
    }

    @Test
    fun chord_estimator_detects_basic_progression() {
        val cMajor = doubleArrayOf(1.0, 0.05, 0.1, 0.05, 0.95, 0.05, 0.05, 0.9, 0.05, 0.1, 0.05, 0.05)
        val gMajor = doubleArrayOf(0.9, 0.05, 0.05, 0.1, 0.05, 0.05, 0.05, 1.0, 0.05, 0.05, 0.1, 0.95)
        val analysis = AudioAnalysis(
            bars = listOf(
                TimedElement(startSec = 0.0, durationSec = 2.0, confidence = 1.0),
                TimedElement(startSec = 2.0, durationSec = 2.0, confidence = 1.0)
            ),
            segments = listOf(
                AudioSegment(startSec = 0.0, durationSec = 0.9, confidence = 0.9, pitches = cMajor),
                AudioSegment(startSec = 0.9, durationSec = 1.1, confidence = 0.8, pitches = cMajor),
                AudioSegment(startSec = 2.0, durationSec = 1.0, confidence = 0.9, pitches = gMajor),
                AudioSegment(startSec = 3.0, durationSec = 1.0, confidence = 0.9, pitches = gMajor)
            )
        )
        val features = AudioFeatures(key = 0, mode = 1, tempo = 120.0, timeSignature = 4)

        val progression = ChordEstimator.estimate(analysis, features)
        assertTrue(progression.compactProgression.contains("C"))
        assertTrue(progression.compactProgression.contains("G"))
        assertEquals("C major", progression.keyLabel)
        assertEquals(2, progression.timeline.size)
    }
}
