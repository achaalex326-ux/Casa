package com.casafamilias.app

import com.casafamilias.app.domain.LocalAccessPolicy
import org.junit.Assert.*
import org.junit.Test

class LocalAccessPolicyTest {
    @Test
    fun savedOptInRequiresAuthenticationAfterProcessRestart() {
        val policy = LocalAccessPolicy("family-a")
        assertTrue(policy.requiresUnlock("family-a"))
        policy.authenticate()
        assertFalse(policy.requiresUnlock("family-a"))
        assertTrue(LocalAccessPolicy(policy.owner).requiresUnlock("family-a"))
    }

    @Test
    fun aDifferentAccountCannotInheritBiometricOptIn() {
        val policy = LocalAccessPolicy("family-a")
        assertFalse(policy.enabledFor("family-b"))
        policy.passwordAuthenticated("family-b")
        assertNull(policy.owner)
        assertFalse(policy.enabledFor("family-b"))
    }

    @Test
    fun savedOptInDoesNotCreateASession() {
        val policy = LocalAccessPolicy("family-a")
        assertFalse(policy.enabledFor(null))
        assertFalse(policy.requiresUnlock(null))
    }

    @Test
    fun briefFilePickerOrCameraVisitDoesNotRelockTheForm() {
        val policy = LocalAccessPolicy()
        policy.enable("family-a")
        policy.background(1000)
        policy.foreground(30999)
        assertFalse(policy.requiresUnlock("family-a"))
    }

    @Test
    fun thirtySecondsInBackgroundRequiresNewAuthentication() {
        val policy = LocalAccessPolicy()
        policy.enable("family-a")
        policy.background(1000)
        policy.foreground(31000)
        assertTrue(policy.requiresUnlock("family-a"))
    }

    @Test
    fun passwordAuthenticatesOnlyAfterTheCallerHasVerifiedCredentials() {
        val policy = LocalAccessPolicy("family-a")
        policy.foreground(100)
        assertTrue(policy.requiresUnlock("family-a"))
        policy.passwordAuthenticated("family-a")
        assertFalse(policy.requiresUnlock("family-a"))
        assertTrue(policy.enabledFor("family-a"))
    }

    @Test
    fun disablingRemovesTheOptInAndBackgroundLock() {
        val policy = LocalAccessPolicy("family-a")
        policy.disable()
        policy.background(0)
        policy.foreground(50000)
        assertFalse(policy.requiresUnlock("family-a"))
        assertNull(policy.owner)
    }
}
