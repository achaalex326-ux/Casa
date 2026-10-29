package com.casafamilias.app

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.casafamilias.app.data.*
import com.casafamilias.app.domain.*
import com.casafamilias.app.ui.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** Fake server and identities only. No production accounts or biometric bypass intents. */
class BiometricGateTest {
    @get:Rule val compose = createComposeRule()
    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as Application

    private val accessPrefs
        get() = app.getSharedPreferences("casa_local_access", 0)

    private var now = 1000L
    private lateinit var vm: CasaViewModel
    private lateinit var server: FakeServer

    private class FakeServer : CasaDataSource by PreviewRepository(Role.FAMILY) {
        override var session: Session? =
            Session("fake", "fake", Long.MAX_VALUE, "preview", "vista@example.test")
        var loads = 0
        var writes = 0
        var expired = false

        override suspend fun restore(legacy: String?) {
            if (expired) {
                session = null
                throw SessionExpired()
            }
        }

        override suspend fun load(): Snapshot {
            loads++
            return PreviewRepository(Role.FAMILY).load().copy(preview = false)
        }

        override suspend fun login(email: String, password: String) {
            if (password != "correct-password")
                throw ApiError(400, "El correo o la contraseña no coinciden.")
            session = Session("fake", "fake", Long.MAX_VALUE, "preview", email)
        }

        override suspend fun rpc(name: String, body: JSONObject): Any? {
            writes++
            return null
        }

        override suspend fun logout() {
            session = null
        }
    }

    @Before
    fun setup() {
        accessPrefs.edit().clear().commit()
        app.getSharedPreferences("casa_preferences", 0).edit().clear().commit()
    }

    @After
    fun clean() {
        accessPrefs.edit().clear().commit()
        app.getSharedPreferences("casa_preferences", 0).edit().clear().commit()
    }

    private fun launch(enabled: Boolean = true) {
        if (enabled) accessPrefs.edit().putString("owner", "preview").commit()
        compose.runOnUiThread {
            server = FakeServer()
            vm = CasaViewModel(app, server) { now }
            vm.start()
        }
        compose.setContent { CasaApp(vm) }
        compose.waitUntil(10000) { !vm.state.value.starting }
    }

    private fun idle() {
        compose.waitUntil(10000) { !vm.state.value.busy && !vm.state.value.starting }
        compose.waitForIdle()
    }

    @Test
    fun coldStartAndRetryCannotReadOrMutateBeforeUnlock() {
        launch()
        compose.onNodeWithText("Tu hogar, protegido").assertIsDisplayed()
        compose.onNodeWithText("Deudas").assertDoesNotExist()
        compose.runOnUiThread {
            vm.start()
            vm.refresh()
            vm.mutate("not-a-real-rpc", JSONObject())
        }
        idle()
        assertEquals(0, server.loads)
        assertEquals(0, server.writes)
        assertTrue(vm.state.value.locked)
        assertNull(vm.state.value.data)
    }

    @Test
    fun cancellationAndWrongPasswordNeverUnlockTheStoredSession() {
        launch()
        compose.runOnUiThread {
            assertTrue(vm.beginBiometric(false))
            vm.biometricCancelled(null)
        }
        assertTrue(vm.state.value.locked)
        assertEquals(0, server.loads)
        compose.onNodeWithText("Usar correo y contraseña").performClick()
        compose.onNodeWithText("Correo electrónico").performTextInput("vista@example.test")
        compose.onNodeWithText("Contraseña").performTextInput("wrong-password")
        compose.onNodeWithText("Entrar a mi cuenta").performClick()
        idle()
        assertTrue(vm.state.value.locked)
        assertEquals(0, server.loads)
        compose.onNodeWithText("Deudas").assertDoesNotExist()
        compose.onNodeWithText("Volver al acceso con huella").performScrollTo().performClick()
        compose.onNodeWithText("Tu hogar, protegido").assertIsDisplayed()
    }

    @Test
    fun verifiedBiometricUnlocksAndLongBackgroundLocksAgain() {
        launch()
        compose.runOnUiThread {
            assertTrue(vm.beginBiometric(false))
            vm.biometricVerified()
        }
        idle()
        assertFalse(vm.state.value.locked)
        assertEquals(1, server.loads)
        compose.onAllNodesWithText("Deudas").onFirst().assertExists()
        compose.runOnUiThread {
            vm.background()
            now += 29999
            vm.foreground()
        }
        assertFalse(vm.state.value.locked)
        compose.runOnUiThread {
            vm.background()
            now += 30000
            vm.foreground()
        }
        compose.onNodeWithText("Tu hogar, protegido").assertIsDisplayed()
        compose.onNodeWithText("Deudas").assertDoesNotExist()
        val count = server.loads
        compose.runOnUiThread { vm.refresh() }
        assertEquals(count, server.loads)
    }

    @Test
    fun correctPasswordWorksWhenBiometricsAreUnavailableAndLogoutDisablesOptIn() {
        launch()
        compose.onNodeWithText("Usar correo y contraseña").performClick()
        compose.runOnUiThread { vm.login("vista@example.test", "correct-password") }
        idle()
        assertFalse(vm.state.value.locked)
        assertEquals(1, server.loads)
        assertTrue(vm.state.value.biometricEnabled)
        compose.runOnUiThread { vm.logout() }
        idle()
        assertFalse(vm.state.value.biometricEnabled)
        assertNull(accessPrefs.getString("owner", null))
        compose.onNodeWithText("Entrar a mi cuenta").assertExists()
    }

    @Test
    fun enablingRequiresSuccessfulVerificationAndCanBeDisabled() {
        launch(false)
        assertFalse(vm.state.value.biometricEnabled)
        compose.runOnUiThread {
            assertTrue(vm.beginBiometric(true))
            vm.biometricCancelled(null)
        }
        assertFalse(vm.state.value.biometricEnabled)
        assertNull(accessPrefs.getString("owner", null))
        compose.runOnUiThread {
            assertTrue(vm.beginBiometric(true))
            vm.biometricVerified()
        }
        assertTrue(vm.state.value.biometricEnabled)
        assertEquals("preview", accessPrefs.getString("owner", null))
        compose.runOnUiThread { vm.disableBiometric() }
        assertFalse(vm.state.value.biometricEnabled)
        assertNull(accessPrefs.getString("owner", null))
    }

    @Test
    fun expiredServerSessionRequiresPasswordEvenAfterVerifiedBiometric() {
        launch()
        server.expired = true
        compose.runOnUiThread {
            assertTrue(vm.beginBiometric(false))
            vm.biometricVerified()
        }
        idle()
        assertNull(vm.state.value.data)
        assertFalse(vm.state.value.biometricEnabled)
        compose.onNodeWithText("Entrar a mi cuenta").assertExists()
        assertEquals(0, server.loads)
    }
}
