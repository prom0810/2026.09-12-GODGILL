package com.safewalk.map.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteSafetyScorerTest {

    @Test
    fun normalizeMapsObservedRangeToZeroAndOne() {
        assertEquals(0.0, RouteSafetyScorer.normalize(raw = 10.0, min = 10.0, max = 30.0), 0.0001)
        assertEquals(0.5, RouteSafetyScorer.normalize(raw = 20.0, min = 10.0, max = 30.0), 0.0001)
        assertEquals(1.0, RouteSafetyScorer.normalize(raw = 30.0, min = 10.0, max = 30.0), 0.0001)
    }

    @Test
    fun normalizeClampsValuesOutsideObservedRange() {
        assertEquals(0.0, RouteSafetyScorer.normalize(raw = 0.0, min = 10.0, max = 30.0), 0.0001)
        assertEquals(1.0, RouteSafetyScorer.normalize(raw = 40.0, min = 10.0, max = 30.0), 0.0001)
    }

    @Test
    fun normalizeUsesNeutralScoreWhenRangeHasNoVariation() {
        assertEquals(0.5, RouteSafetyScorer.normalize(raw = 10.0, min = 10.0, max = 10.0), 0.0001)
    }
}
