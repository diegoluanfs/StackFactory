package com.diegoluan.stackfactory.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressOwnershipTest {
    @Test fun firstLoginCanClaimUnownedLocalData() {
        assertTrue(ProgressOwnership.mayUpload(null, "anon-123"))
    }
    @Test fun existingOwnerCanContinueSyncing() {
        assertTrue(ProgressOwnership.mayUpload("same-uid", "same-uid"))
    }
    @Test fun existingGoogleAccountCannotSilentlyImportGuestData() {
        assertFalse(ProgressOwnership.mayUpload("anon-123", "google-999"))
    }
    @Test fun noSignInCannotUpload() {
        assertFalse(ProgressOwnership.mayUpload(null, ""))
    }
}
