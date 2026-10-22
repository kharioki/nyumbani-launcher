package dev.kharioki.nyumbani

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    private fun newer(a: String, b: String) = Updates.compareVersions(a, b) > 0

    @Test
    fun comparesNumbersNotStrings() {
        assertTrue(newer("1.10.0", "1.9.9"))
        assertTrue(newer("2.0.0", "1.99.99"))
        assertTrue(newer("1.0.1", "1.0.0"))
    }

    @Test
    fun ignoresLeadingV() {
        assertEquals(0, Updates.compareVersions("v1.2.0", "1.2.0"))
        assertTrue(Updates.isNewer("v1.2.1", "1.2.0"))
        assertFalse(Updates.isNewer("v1.2.0", "1.2.0"))
    }

    @Test
    fun preReleasesSortBeforeTheRelease() {
        assertTrue(newer("1.3.0", "1.3.0-beta.2"))
        assertTrue(newer("1.3.0-beta.2", "1.3.0-beta.1"))
        assertTrue(newer("1.3.0-beta.10", "1.3.0-beta.9"))
        assertTrue(newer("1.3.0-rc.1", "1.3.0-beta.5"))
        assertTrue(newer("1.3.0-beta.1", "1.2.9"))
    }

    @Test
    fun devBuildIsOlderThanAnyRelease() {
        assertTrue(Updates.isNewer("v0.0.1", "0.0.0-dev"))
        assertTrue(Updates.isNewer("v1.0.0", "0.0.0-dev"))
        assertFalse(Updates.isNewer(null, "1.0.0"))
    }
}
