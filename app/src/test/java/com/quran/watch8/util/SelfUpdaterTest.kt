package com.quran.watch8.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelfUpdaterTest {
    @Test
    fun readsTheFacePackageFromItsAssetName() {
        assertEquals("com.quran.watch8.watchface.row", SelfUpdater.facePackage("face-com.quran.watch8.watchface.row-v140.apk"))
    }

    @Test
    fun theAppItselfIsNoFace() {
        assertNull(SelfUpdater.facePackage("build-quran-watch8-v140.apk"))
    }

    @Test
    fun versionComesFromTheTag() {
        assertEquals(140L, SelfUpdater.versionFromTag("v140"))
    }
}
