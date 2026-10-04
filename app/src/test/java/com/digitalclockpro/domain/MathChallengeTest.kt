package com.digitalclockpro.domain

import com.digitalclockpro.alarm.challenge.MathChallengeGenerator
import com.digitalclockpro.domain.model.DismissChallenge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathChallengeTest {

    @Test
    fun `generates the requested number of problems`() {
        DismissChallenge.Difficulty.entries.forEach { difficulty ->
            (1..5).forEach { count ->
                assertEquals(count, MathChallengeGenerator.generate(difficulty, count).size)
            }
        }
    }

    @Test
    fun `problem count is clamped to 1 - 5`() {
        assertEquals(1, MathChallengeGenerator.generate(DismissChallenge.Difficulty.EASY, 0).size)
        assertEquals(5, MathChallengeGenerator.generate(DismissChallenge.Difficulty.HARD, 99).size)
    }

    @Test
    fun `hard problems are harder than easy ones`() {
        val easy = MathChallengeGenerator.generate(DismissChallenge.Difficulty.EASY, 5)
        val hard = MathChallengeGenerator.generate(DismissChallenge.Difficulty.HARD, 5)
        assertTrue(easy.all { it.question.length <= hard.maxOf { h -> h.question.length } })
    }
}
