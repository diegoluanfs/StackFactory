package com.diegoluan.stackfactory.referral

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferralInstallParserTest {
    private val code = "A0123456789B01234567"
    @Test fun parsesGooglePlayCode() {
        assertEquals(code, ReferralInstallParser.parsePlayReferrer("sf_a0123456789b01234567"))
        assertEquals(code, ReferralInstallParser.parsePlayReferrer("sf_$code"))
    }
    @Test fun rejectsUnrelatedCampaignsOrExtraData() {
        assertNull(ReferralInstallParser.parsePlayReferrer("utm_source=ads"))
        assertNull(ReferralInstallParser.parsePlayReferrer("sf_$code&other=true"))
        assertNull(ReferralInstallParser.parsePlayReferrer("SF-$code"))
        assertNull(ReferralInstallParser.parsePlayReferrer("sf_12"))
    }
    @Test fun debugInputAcceptsOnlyCodes() {
        assertEquals(code, ReferralInstallParser.parseDebugCode("SF-$code"))
        assertEquals(code, ReferralInstallParser.parseDebugCode("$code"))
        assertNull(ReferralInstallParser.parseDebugCode("https://other.site/?code=$code"))
    }
    @Test fun checksFreshInstall() {
        val now = 1_800_000_000_000L
        assertTrue(ReferralInstallParser.isRecentInstall(now - 3600_000L, now))
        assertFalse(ReferralInstallParser.isRecentInstall(now - 32L * 24 * 3600_000L, now))
        assertFalse(ReferralInstallParser.isRecentInstall(now + 1000L, now))
    }
    @Test fun returningPlayerIsNotAutoAttributed() {
        val now = 1_800_000_000_000L
        val install = now - 2_000L
        assertFalse(ReferralInstallParser.canAttributeToNewAccount(install, now - 365L * 24 * 3600_000L, now))
        assertTrue(ReferralInstallParser.canAttributeToNewAccount(install, now - 1_000L, now))
    }
}
