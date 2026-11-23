package com.diegoluan.stackfactory.referral

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferralLinksTest {
    @Test fun sameAccountHasStableCode() {
        assertEquals(ReferralLinks.codeForUid("firebase-123"), ReferralLinks.codeForUid("firebase-123"))
    }

    @Test fun differentAccountsHaveDifferentCodes() {
        assertNotEquals(ReferralLinks.codeForUid("firebase-123"), ReferralLinks.codeForUid("firebase-456"))
    }

    @Test fun codeIsFixedLengthAndSafeForUrls() {
        assertTrue(ReferralLinks.codeForUid("firebase-123").matches(Regex("^[A-F0-9]{20}$")))
    }

    @Test fun linkPointsToCorrectStorePageWithoutExposingUid() {
        val url = ReferralLinks.urlForUid("my-private-firebase-uid")
        assertTrue(url.startsWith("https://play.google.com/store/apps/details?id=com.diegoluan.stackfactory&referrer=sf_"))
        assertFalse(url.contains("my-private-firebase-uid"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyUserCannotGenerateInvitation() {
        ReferralLinks.codeForUid(" ")
    }
}
