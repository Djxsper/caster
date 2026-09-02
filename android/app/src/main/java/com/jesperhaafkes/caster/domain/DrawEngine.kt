package com.jesperhaafkes.caster.domain

import kotlin.random.Random

/**
 * Pure selection logic. Deliberately free of UI and of stored state so it can
 * be reasoned about — and unit-tested — in isolation.
 */
object DrawEngine {

    /**
     * Picks one element with probability proportional to its weight.
     * Returns null only when there is nothing selectable.
     */
    fun <T> drawWithWeights(array: List<T>, weights: List<Int>): T? {
        val index = drawIndexWithWeights(weights, array.size) ?: return null
        return array[index]
    }

    /**
     * The index form of [drawWithWeights], for callers that need to know
     * *which* slot won rather than just its value.
     */
    fun drawIndexWithWeights(weights: List<Int>, limit: Int): Int? {
        val count = minOf(limit, weights.size)
        if (count <= 0) return null

        val normalized = weights.take(count).map { maxOf(0, it) }
        val total = normalized.sum()

        // Every weight was zero: fall back to a uniform pick so a caller that
        // hands us an all-zero set still gets a result.
        if (total <= 0) return Random.nextInt(count)

        var accumulated = 0
        val roll = Random.nextInt(total)
        for ((index, weight) in normalized.withIndex()) {
            accumulated += weight
            if (roll < accumulated) return index
        }
        return count - 1
    }

    /**
     * [count] distinct members of [pool], chosen uniformly. Returns the whole
     * pool (shuffled) when [count] meets or exceeds its size.
     */
    fun <T> pick(count: Int, from: List<T>): List<T> {
        if (count <= 0) return emptyList()
        return from.shuffled().take(count)
    }

    /**
     * Assigns each member of [pool] a team index, balanced to within one member
     * per team, with the membership shuffled.
     *
     * Returned in the same order as [pool], so element *i* of the result is the
     * team for element *i* of the input.
     */
    fun <T> splitIntoTeams(pool: List<T>, teams: Int): List<Int> {
        val teamCount = maxOf(1, teams)
        if (pool.isEmpty()) return emptyList()

        // Build a balanced bag of team numbers, then shuffle *that* — dealing
        // round-robin from a shuffled pool would bias the last team small.
        return pool.indices.map { it % teamCount }.shuffled()
    }

    /** A shuffled 1-based ranking, one entry per member of the pool. */
    fun randomOrder(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        return (1..count).shuffled()
    }
}
