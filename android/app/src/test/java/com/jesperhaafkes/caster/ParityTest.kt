package com.jesperhaafkes.caster

import com.jesperhaafkes.caster.domain.GameTuning
import com.jesperhaafkes.caster.domain.PlayerLimits
import com.jesperhaafkes.caster.ui.theme.PlayerPalette
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Holds this app to the contract in `shared/parity/golden.json`.
 *
 * Caster is written twice on purpose — nothing shares a UIKit multi-touch layer,
 * Core Haptics and AVAudioEngine with Android — but two independent codebases
 * drift, and the drift is silent. A window widened on iOS and not here makes
 * Chicken a different game on the two phones, and no build anywhere goes red.
 *
 * So the numbers live in a file neither app owns, and both suites read it. The
 * iOS half does not exist yet: the Swift project has no test target at all.
 * Until it does, this is one side of a handshake.
 */
class ParityTest {

    private val golden: JSONObject by lazy {
        // Unit tests run with the module directory as the working directory.
        val file = File("../../shared/parity/golden.json")
        assertTrue(
            "golden.json not found at ${file.absolutePath} — the parity fixture is " +
                "shared with the iOS app and must not be moved without updating both.",
            file.exists(),
        )
        JSONObject(file.readText())
    }

    @Test
    fun `seat colours match the shared palette`() {
        val expected = golden.getJSONObject("seatColors").getJSONArray("values")
        assertEquals(
            "the number of seat colours changed",
            expected.length(),
            PlayerPalette.colors.size,
        )
        for (i in 0 until expected.length()) {
            val row = expected.getJSONObject(i)
            val actual = PlayerPalette.colors[i]
            val name = row.getString("name")
            assertEquals("$name red", row.getDouble("r").toFloat(), actual.red, 0.001f)
            assertEquals("$name green", row.getDouble("g").toFloat(), actual.green, 0.001f)
            assertEquals("$name blue", row.getDouble("b").toFloat(), actual.blue, 0.001f)
        }
    }

    @Test
    fun `game timing matches the shared contract`() {
        val timing = golden.getJSONObject("timing")

        val chicken = timing.getJSONObject("chicken")
        assertEquals(chicken.getDouble("startWindowMs"), GameTuning.Chicken.START_WINDOW_MS, 0.0)
        assertEquals(chicken.getDouble("windowStepMs"), GameTuning.Chicken.WINDOW_STEP_MS, 0.0)
        assertEquals(chicken.getDouble("maxWindowMs"), GameTuning.Chicken.MAX_WINDOW_MS, 0.0)
        assertEquals(chicken.getLong("latencyGraceMs"), GameTuning.Chicken.LATENCY_GRACE_MS)
        assertEquals(chicken.getLong("settleDurationMs"), GameTuning.Chicken.SETTLE_DURATION_MS)

        assertEquals(
            timing.getJSONObject("fingerPicker").getInt("holdDurationMs"),
            GameTuning.FingerPicker.HOLD_DURATION_MS,
        )
        assertEquals(
            timing.getJSONObject("uppercut").getLong("settleDurationMs"),
            GameTuning.Uppercut.SETTLE_DURATION_MS,
        )
        assertEquals(
            timing.getJSONObject("tapFrenzy").getLong("settleDurationMs"),
            GameTuning.TapFrenzy.SETTLE_DURATION_MS,
        )
    }

    @Test
    fun `roster limits match the shared contract`() {
        val roster = golden.getJSONObject("roster")
        assertEquals(roster.getInt("minimum"), PlayerLimits.MINIMUM)
        assertEquals(roster.getInt("maximum"), PlayerLimits.MAXIMUM)
    }

    @Test
    fun `the tap frenzy advantage cap matches the shared contract`() {
        assertEquals(
            golden.getJSONObject("draw").getDouble("tapFrenzyMaxAdvantageRatio"),
            GameTuning.TapFrenzy.MAX_ADVANTAGE_RATIO,
            0.0,
        )
    }

    @Test
    fun `the spread rules match the shared contract`() {
        // Not the resulting colours: those go through Color.hsv, whose rounding
        // is a platform detail. The rules that produce them are the contract.
        val rules = golden.getJSONObject("spreadRules")
        assertEquals(0.62, rules.getDouble("saturationEven"), 0.0)
        assertEquals(0.74, rules.getDouble("saturationOdd"), 0.0)
        assertEquals(0.92, rules.getDouble("brightnessEveryThird"), 0.0)
        assertEquals(0.80, rules.getDouble("brightnessOther"), 0.0)
        assertEquals(0.11, rules.getDouble("hueStridePerIndexMod3"), 0.0)

        // And that spread still falls back to the fixed eight below the cap.
        for (i in 0 until PlayerPalette.colors.size) {
            assertEquals(
                "spread should return seat colour $i unchanged at small counts",
                PlayerPalette.colors[i],
                PlayerPalette.spread(i, PlayerPalette.colors.size),
            )
        }
    }
}
