package com.digitalclockpro.alarm.challenge

import com.digitalclockpro.domain.model.DismissChallenge
import kotlin.random.Random

data class MathProblem(val question: String, val answer: Int)

object MathChallengeGenerator {
    fun generate(difficulty: DismissChallenge.Difficulty, count: Int): List<MathProblem> =
        List(count.coerceIn(1, 5)) { single(difficulty) }

    private fun single(difficulty: DismissChallenge.Difficulty): MathProblem = when (difficulty) {
        DismissChallenge.Difficulty.EASY -> {
            val a = Random.nextInt(2, 20); val b = Random.nextInt(2, 20)
            if (Random.nextBoolean()) MathProblem("$a + $b", a + b)
            else MathProblem("${a + b} − $b", a)
        }
        DismissChallenge.Difficulty.MEDIUM -> {
            val a = Random.nextInt(3, 13); val b = Random.nextInt(3, 13); val c = Random.nextInt(1, 20)
            MathProblem("$a × $b + $c", a * b + c)
        }
        DismissChallenge.Difficulty.HARD -> {
            val a = Random.nextInt(11, 30); val b = Random.nextInt(11, 20); val c = Random.nextInt(2, 12)
            MathProblem("$a × $b − $c × ${c + 1}", a * b - c * (c + 1))
        }
    }
}
