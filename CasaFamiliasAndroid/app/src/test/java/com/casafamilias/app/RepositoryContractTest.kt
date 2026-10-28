package com.casafamilias.app

import com.casafamilias.app.data.*
import com.casafamilias.app.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class RepositoryContractTest {
    private lateinit var server: MockWebServer

    private class Store(var value: Session?) : SessionStore {
        override fun load() = value

        override fun save(session: Session) {
            value = session
        }

        override fun clear() {
            value = null
        }
    }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun close() {
        server.shutdown()
    }

    private fun token(
        access: String = "token",
        expiry: Long = System.currentTimeMillis() / 1000 + 3600,
    ) =
        """{"access_token":"$access","refresh_token":"refresh","expires_at":$expiry,"user":{"id":"u","email":"test@example.test"}}"""

    private fun repo(store: Store) =
        CasaRepository(
            null,
            ApiConfig(server.url("/").toString().trimEnd('/'), "public-test-key"),
            store,
        )

    @Test
    fun loginUsesExistingAuthContract() = runBlocking {
        server.enqueue(MockResponse().setBody(token()))
        val store = Store(null)
        repo(store).login(" test@example.test ", "password")
        val r = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=password", r.path)
        assertEquals("public-test-key", r.getHeader("apikey"))
        assertEquals("test@example.test", JSONObject(r.body.readUtf8()).getString("email"))
        assertNotNull(store.value)
    }

    @Test
    fun rpcPreservesArgumentsAndUserAuthorization() = runBlocking {
        server.enqueue(MockResponse().setBody("null"))
        val store = Store(Session.from(JSONObject(token())))
        repo(store).rpc("review_payment", json("p_payment_id" to "payment", "p_approve" to true))
        val r = server.takeRequest()
        assertEquals("/rest/v1/rpc/review_payment", r.path)
        assertEquals("Bearer token", r.getHeader("Authorization"))
        assertTrue(JSONObject(r.body.readUtf8()).getBoolean("p_approve"))
    }

    @Test
    fun expiredSessionsRefreshBeforeBusinessCall() = runBlocking {
        server.enqueue(MockResponse().setBody(token("new")))
        server.enqueue(MockResponse().setBody("[]"))
        repo(Store(Session.from(JSONObject(token(expiry = 0)))))
            .rpc("family_portal_debts", JSONObject())
        assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)
        assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun rejectedRefreshClearsSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        val store = Store(Session.from(JSONObject(token(expiry = 0))))
        try {
            repo(store).restore()
            fail("Expired session accepted")
        } catch (e: SessionExpired) {
            assertNull(store.value)
        }
    }

    @Test
    fun failedSignOutKeepsSessionForRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val store = Store(Session.from(JSONObject(token())))
        try {
            repo(store).logout()
            fail("Expected failure")
        } catch (e: ApiError) {
            assertNotNull(store.value)
        }
    }

    @Test
    fun recoveryUsesRegisteredAndroidDeepLink() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        repo(Store(null)).recover("test@example.test")
        val r = server.takeRequest()
        assertEquals("casafamilias://auth", r.requestUrl!!.queryParameter("redirect_to"))
        assertEquals("/auth/v1/recover", r.requestUrl!!.encodedPath)
    }

    @Test
    fun familyLoadNeverRequestsOtherFamilyFinancialTables() = runBlocking {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(r: RecordedRequest): MockResponse {
                    val path = r.requestUrl!!.encodedPath
                    val body =
                        when (path) {
                            "/auth/v1/user" -> """{"id":"u","email":"test@example.test"}"""
                            "/rest/v1/profiles" ->
                                """[{"id":"u","role":"family","family_id":1,"display_name":"Familia"}]"""
                            "/rest/v1/rpc/family_portal_debts" ->
                                """[{"share_id":"s","balance":12.34}]"""
                            else -> "[]"
                        }
                    return MockResponse().setBody(body)
                }
            }
        val snapshot = repo(Store(Session.from(JSONObject(token())))).load()
        assertEquals("12.34", snapshot.balance(1).toPlainString())
        val requests = (0 until server.requestCount).map { server.takeRequest() }
        assertTrue(
            requests.none {
                it.path!!.contains("expense_shares") ||
                    it.path!!.contains("/expenses") ||
                    it.path!!.contains("audit_log")
            }
        )
        assertEquals(
            "eq.1",
            requests
                .first { it.requestUrl!!.encodedPath == "/rest/v1/payments" }
                .requestUrl!!
                .queryParameter("family_id"),
        )
        assertEquals(
            "eq.1",
            requests
                .first { it.requestUrl!!.encodedPath == "/rest/v1/payment_batches" }
                .requestUrl!!
                .queryParameter("family_id"),
        )
        assertFalse(
            requests
                .first { it.requestUrl!!.encodedPath == "/rest/v1/proposals" }
                .requestUrl!!
                .queryParameter("select")!!
                .contains("created_by")
        )
    }

    @Test
    fun selectFetchesEveryPageWithoutDroppingRows() = runBlocking {
        server.enqueue(
            MockResponse().setBody(org.json.JSONArray((1..500).map { json("id" to it) }).toString())
        )
        server.enqueue(MockResponse().setBody("[{\"id\":501}]"))
        val rows = repo(Store(Session.from(JSONObject(token())))).select("families")
        assertEquals(501, rows.size)
        assertEquals("501", rows.last().id)
        val first = server.takeRequest().requestUrl!!
        val second = server.takeRequest().requestUrl!!
        assertEquals("500", first.queryParameter("limit"))
        assertEquals("0", first.queryParameter("offset"))
        assertEquals("500", second.queryParameter("offset"))
        assertEquals("id", second.queryParameter("order"))
    }

    @Test
    fun legacyMissingUserIdKeepsRefreshedTokensDuringLoad() = runBlocking {
        val old = Session.from(JSONObject(token(expiry = 0))).copy(userId = "")
        val store = Store(old)
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(r: RecordedRequest): MockResponse {
                    return MockResponse()
                        .setBody(
                            when (r.requestUrl!!.encodedPath) {
                                "/auth/v1/token" -> token("new-token")
                                "/auth/v1/user" -> "{\"id\":\"u\",\"email\":\"test@example.test\"}"
                                "/rest/v1/profiles" ->
                                    "[{\"id\":\"u\",\"role\":\"restaurant\",\"display_name\":\"Restaurante\"}]"
                                else -> "[]"
                            }
                        )
                }
            }
        repo(store).load()
        assertEquals("new-token", store.value!!.accessToken)
        assertEquals("u", store.value!!.userId)
    }

    @Test
    fun anotherRepositoryReusesTheLatestRotatedSession() = runBlocking {
        val store = Store(Session.from(JSONObject(token(expiry = 0))))
        val background = repo(store)
        val foreground = repo(store)
        server.enqueue(MockResponse().setBody(token("rotated")))
        foreground.restore()
        server.enqueue(MockResponse().setBody("[]"))
        background.rpc("family_portal_debts", JSONObject())
        assertEquals(2, server.requestCount)
        assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)
        assertEquals("Bearer rotated", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun oldRepositoryCannotEraseTheNewAccountsSession() = runBlocking {
        val store = Store(Session.from(JSONObject(token())))
        val old = repo(store)
        store.value = store.value!!.copy(userId = "other-user", accessToken = "new-user-token")
        try {
            old.rpc("family_portal_debts", JSONObject())
            fail("Old account was accepted")
        } catch (e: SessionExpired) {
            assertEquals("other-user", store.value?.userId)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun rejectedAccessTokenForcesARefreshEvenBeforeItsExpiry() = runBlocking {
        val store = Store(Session.from(JSONObject(token())))
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setBody(token("replacement")))
        server.enqueue(MockResponse().setBody("[]"))
        repo(store).rpc("family_portal_debts", JSONObject())
        assertEquals(3, server.requestCount)
        server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)
        assertEquals("Bearer replacement", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun backgroundSyncReadsOnlyItsOwnNotificationsAndDoesNotGenerateOrChangeData() = runBlocking {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(r: RecordedRequest): MockResponse =
                    MockResponse()
                        .setBody(
                            when (r.requestUrl!!.encodedPath) {
                                "/auth/v1/user" -> "{\"id\":\"u\",\"email\":\"test@example.test\"}"
                                "/rest/v1/profiles" ->
                                    "[{\"id\":\"u\",\"role\":\"family\",\"family_id\":1,\"display_name\":\"Familia\"}]"
                                else -> "[]"
                            }
                        )
            }
        repo(Store(Session.from(JSONObject(token())))).notificationSnapshot("2026-10-19T00:00:00Z")
        val requests = (0 until server.requestCount).map { server.takeRequest() }
        val notices =
            requests.single { it.requestUrl!!.encodedPath == "/rest/v1/notifications" }.requestUrl!!
        assertEquals("eq.u", notices.queryParameter("user_id"))
        assertEquals("is.null", notices.queryParameter("read_at"))
        assertTrue(
            requests.all {
                it.method == "GET" ||
                    it.requestUrl!!.encodedPath == "/rest/v1/rpc/family_portal_debts"
            }
        )
        assertFalse(
            requests.any {
                it.path!!.contains("generate_due_notifications") ||
                    it.path!!.contains("expense_shares") ||
                    it.path!!.contains("/payments")
            }
        )
    }

    @Test
    fun batchRpcKeepsIdempotencyKeyTotalFamilyAndNullableShare() = runBlocking {
        server.enqueue(MockResponse().setBody("\"batch-id\""))
        repo(Store(Session.from(JSONObject(token()))))
            .rpc(
                "casa_record_payment_batch",
                json(
                    "p_request_id" to "stable-request",
                    "p_family_id" to 1,
                    "p_amount" to java.math.BigDecimal("200.00"),
                    "p_paid_at" to "2030-03-20",
                    "p_method" to "qr",
                    "p_receipt_path" to "u/receipt.png",
                    "p_note" to null,
                    "p_ocr_text" to null,
                    "p_share_id" to null,
                ),
            )
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/casa_record_payment_batch", request.requestUrl!!.encodedPath)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("stable-request", body.getString("p_request_id"))
        assertEquals(1, body.getInt("p_family_id"))
        assertEquals(200.0, body.getDouble("p_amount"), 0.0)
        assertTrue(body.isNull("p_share_id"))
    }

    @Test
    fun unmigratedServerCannotSilentlySaveUsingOldAverageCalculation() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(
                    """{"code":"PGRST202","message":"Could not find public.casa_create_monthly_services"}"""
                )
        )
        try {
            repo(Store(Session.from(JSONObject(token()))))
                .rpc("casa_create_monthly_services", json())
            fail("Saved without migration")
        } catch (e: ApiError) {
            assertTrue(e.message.contains("Este mes no se guardó"))
            assertEquals(1, server.requestCount)
        }
    }
}
