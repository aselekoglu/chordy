package com.example.spotifytochords

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ChordEstimator {
    private const val minSegmentDurationSec = 0.12
    private const val minScoreThreshold = 0.35
    private const val minScoreMargin = 0.03
    private const val mergeGapThresholdSec = 0.15
    private const val shortNoChordThresholdSec = 0.8

    private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val chordCandidates = buildCandidates()

    fun estimate(analysis: AudioAnalysis, features: AudioFeatures?): ChordProgression {
        if (analysis.segments.isEmpty()) {
            return ChordProgression(
                timeline = emptyList(),
                compactProgression = "No chord data available.",
                keyLabel = formatKeyLabel(features),
                tempoBpm = features?.tempo?.takeIf { it.isFinite() },
                timeSignature = features?.timeSignature?.takeIf { it > 0 }
            )
        }

        val predicted = predictSegmentChords(
            segments = analysis.segments,
            key = features?.key ?: -1,
            mode = features?.mode ?: -1
        )
        val merged = mergePredictions(predicted)
        val timeline = if (analysis.bars.size >= 4) {
            alignToBars(analysis.bars, merged)
        } else {
            merged.map { ChordTimelineEntry(it.startSec, it.endSec, it.chord) }
        }
        val collapsed = collapseAdjacent(timeline)
        val compact = collapsed
            .map { it.chord }
            .filter { it != "N" }
            .joinToString(" | ")
            .ifBlank { "No stable chord progression detected." }

        return ChordProgression(
            timeline = collapsed,
            compactProgression = compact,
            keyLabel = formatKeyLabel(features),
            tempoBpm = features?.tempo?.takeIf { it.isFinite() },
            timeSignature = features?.timeSignature?.takeIf { it > 0 }
        )
    }

    private fun predictSegmentChords(
        segments: List<AudioSegment>,
        key: Int,
        mode: Int
    ): List<PredictedChord> {
        val predictions = mutableListOf<PredictedChord>()
        for (segment in segments) {
            if (segment.durationSec < minSegmentDurationSec) continue
            val normalized = normalize(segment.pitches)
            if (normalized.sum() == 0.0) continue

            var bestCandidate: ChordCandidate? = null
            var bestScore = Double.NEGATIVE_INFINITY
            var secondBest = Double.NEGATIVE_INFINITY

            for (candidate in chordCandidates) {
                var score = cosineSimilarity(normalized, candidate.profile)
                score += diatonicBias(candidate, key, mode)
                score *= 0.7 + (segment.confidence.coerceIn(0.0, 1.0) * 0.3)
                if (score > bestScore) {
                    secondBest = bestScore
                    bestScore = score
                    bestCandidate = candidate
                } else if (score > secondBest) {
                    secondBest = score
                }
            }

            val margin = bestScore - secondBest
            val chord = if (bestScore >= minScoreThreshold && margin >= minScoreMargin) {
                bestCandidate?.name ?: "N"
            } else {
                "N"
            }

            predictions += PredictedChord(
                startSec = segment.startSec,
                endSec = segment.startSec + segment.durationSec,
                chord = chord,
                score = bestScore.coerceAtLeast(0.0)
            )
        }
        return predictions
    }

    private fun mergePredictions(predictions: List<PredictedChord>): List<PredictedChord> {
        if (predictions.isEmpty()) return emptyList()
        val sorted = predictions.sortedBy { it.startSec }
        val merged = mutableListOf<PredictedChord>()

        for (prediction in sorted) {
            val previous = merged.lastOrNull()
            if (
                previous != null &&
                previous.chord == prediction.chord &&
                prediction.startSec - previous.endSec <= mergeGapThresholdSec
            ) {
                merged[merged.lastIndex] = previous.copy(
                    endSec = max(previous.endSec, prediction.endSec),
                    score = max(previous.score, prediction.score)
                )
            } else {
                merged += prediction
            }
        }

        val cleaned = mutableListOf<PredictedChord>()
        var index = 0
        while (index < merged.size) {
            val current = merged[index]
            val duration = current.endSec - current.startSec
            val next = merged.getOrNull(index + 1)
            val previous = cleaned.lastOrNull()
            if (
                current.chord == "N" &&
                duration <= shortNoChordThresholdSec &&
                previous != null &&
                next != null &&
                previous.chord == next.chord &&
                previous.chord != "N"
            ) {
                cleaned[cleaned.lastIndex] = previous.copy(endSec = next.endSec)
                index += 2
                continue
            }
            cleaned += current
            index += 1
        }
        return cleaned
    }

    private fun alignToBars(
        bars: List<TimedElement>,
        predicted: List<PredictedChord>
    ): List<ChordTimelineEntry> {
        if (predicted.isEmpty()) return emptyList()
        val aligned = mutableListOf<ChordTimelineEntry>()
        var lastChord = predicted.first().chord

        for (bar in bars) {
            val barStart = bar.startSec
            val barEnd = bar.startSec + bar.durationSec
            var bestChord = "N"
            var bestScore = 0.0

            val overlapByChord = mutableMapOf<String, Double>()
            for (event in predicted) {
                val overlap = overlapSeconds(barStart, barEnd, event.startSec, event.endSec)
                if (overlap <= 0.0) continue
                val weightedScore = overlap * event.score
                overlapByChord[event.chord] = (overlapByChord[event.chord] ?: 0.0) + weightedScore
            }

            for ((chord, score) in overlapByChord) {
                if (score > bestScore) {
                    bestScore = score
                    bestChord = chord
                }
            }

            if (bestChord == "N" && lastChord != "N") {
                bestChord = lastChord
            }
            lastChord = bestChord

            aligned += ChordTimelineEntry(
                startSec = barStart,
                endSec = barEnd,
                chord = bestChord
            )
        }
        return aligned
    }

    private fun collapseAdjacent(timeline: List<ChordTimelineEntry>): List<ChordTimelineEntry> {
        if (timeline.isEmpty()) return emptyList()
        val collapsed = mutableListOf(timeline.first())
        for (index in 1 until timeline.size) {
            val previous = collapsed.last()
            val current = timeline[index]
            if (previous.chord == current.chord) {
                collapsed[collapsed.lastIndex] = previous.copy(endSec = current.endSec)
            } else {
                collapsed += current
            }
        }
        return collapsed
    }

    private fun overlapSeconds(aStart: Double, aEnd: Double, bStart: Double, bEnd: Double): Double {
        return max(0.0, min(aEnd, bEnd) - max(aStart, bStart))
    }

    private fun normalize(values: DoubleArray): DoubleArray {
        val norm = sqrt(values.sumOf { it * it })
        if (norm == 0.0) return DoubleArray(values.size)
        return DoubleArray(values.size) { index -> values[index] / norm }
    }

    private fun cosineSimilarity(vectorA: DoubleArray, vectorB: DoubleArray): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (index in vectorA.indices) {
            dot += vectorA[index] * vectorB[index]
            normA += vectorA[index] * vectorA[index]
            normB += vectorB[index] * vectorB[index]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }

    private fun diatonicBias(candidate: ChordCandidate, key: Int, mode: Int): Double {
        if (key !in 0..11 || mode !in 0..1) return 0.0
        val scaleDegree = (candidate.root - key + 12) % 12
        val inScale = when (mode) {
            1 -> if (candidate.isMinor) scaleDegree in setOf(2, 4, 9) else scaleDegree in setOf(0, 5, 7)
            0 -> if (candidate.isMinor) scaleDegree in setOf(0, 5, 7) else scaleDegree in setOf(3, 8, 10)
            else -> false
        }
        return if (inScale) 0.04 else -0.01
    }

    private fun buildCandidates(): List<ChordCandidate> {
        val list = mutableListOf<ChordCandidate>()
        for (root in 0..11) {
            list += ChordCandidate(
                name = noteNames[root],
                root = root,
                isMinor = false,
                profile = majorProfile(root)
            )
            list += ChordCandidate(
                name = "${noteNames[root]}m",
                root = root,
                isMinor = true,
                profile = minorProfile(root)
            )
        }
        return list
    }

    private fun majorProfile(root: Int): DoubleArray {
        val profile = DoubleArray(12)
        profile[root] = 1.0
        profile[(root + 4) % 12] = 0.85
        profile[(root + 7) % 12] = 0.75
        profile[(root + 2) % 12] = 0.18
        profile[(root + 9) % 12] = 0.16
        return profile
    }

    private fun minorProfile(root: Int): DoubleArray {
        val profile = DoubleArray(12)
        profile[root] = 1.0
        profile[(root + 3) % 12] = 0.85
        profile[(root + 7) % 12] = 0.75
        profile[(root + 2) % 12] = 0.16
        profile[(root + 10) % 12] = 0.18
        return profile
    }

    private fun formatKeyLabel(features: AudioFeatures?): String? {
        features ?: return null
        if (features.key !in 0..11) return null
        val modeLabel = when (features.mode) {
            1 -> "major"
            0 -> "minor"
            else -> "unknown mode"
        }
        return "${noteNames[features.key]} $modeLabel"
    }

    private data class ChordCandidate(
        val name: String,
        val root: Int,
        val isMinor: Boolean,
        val profile: DoubleArray
    )

    private data class PredictedChord(
        val startSec: Double,
        val endSec: Double,
        val chord: String,
        val score: Double
    )
}
