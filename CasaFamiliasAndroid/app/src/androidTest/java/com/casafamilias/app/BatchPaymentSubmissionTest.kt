package com.casafamilias.app

import android.app.Application
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.casafamilias.app.data.*
import com.casafamilias.app.domain.*
import com.casafamilias.app.ui.CasaViewModel
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** A fictitious uploader/server exercises the real submission/retry path, not real accounts. */
class BatchPaymentSubmissionTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as Application
    private class FakeServer(val loseFirstReply: Boolean=false,val gate: CompletableDeferred<Unit>?=null): CasaDataSource by PreviewRepository(Role.FAMILY) {
        var uploads=0;var calls=0
        val deposits=mutableSetOf<String>();val bodies=mutableListOf<JSONObject>()
        override suspend fun load()=PreviewRepository(Role.FAMILY).load().copy(preview=false)
        override suspend fun upload(uri: Uri,qr: Boolean): String {uploads++;return "preview/one-receipt.png"}
        override suspend fun rpc(name: String,body: JSONObject): Any? {
            assertEquals("casa_record_payment_batch",name);calls++;bodies+=JSONObject(body.toString())
            deposits+=body.getString("p_request_id");gate?.await()
            if(loseFirstReply && calls==1)throw IOException("Simulated lost response after committing deposit")
            return body.getString("p_request_id")
        }
    }
    private fun main(block: ()->Unit)=instrumentation.runOnMainSync(block)
    private fun waitFor(test: ()->Boolean) {
        val deadline=System.currentTimeMillis()+15000
        while(!test() && System.currentTimeMillis()<deadline){Thread.sleep(20)}
        assertTrue("Timed out waiting for ViewModel",test())
    }
    private fun clean(){app.getSharedPreferences("casa_local_access",0).edit().clear().commit();app.getSharedPreferences("casa_preferences",0).edit().clear().commit()}
    @Before fun before(){clean()}
    @After fun after(){clean()}
    private fun start(server: FakeServer): CasaViewModel {
        lateinit var vm: CasaViewModel
        main {vm=CasaViewModel(app,server);vm.start()}
        waitFor {!vm.state.value.starting};assertNotNull(vm.state.value.data);return vm
    }
    private fun values(vm: CasaViewModel): Pair<Form,Map<String,String>> {
        val form=Forms.build("payment","",vm.state.value.data!!)
        return form to (form.fields.associate {it.key to it.initial}+mapOf("request_id" to "70000000-0000-0000-0000-000000000001","amount" to "200","receipt" to "content://fictional/receipt","method" to "qr"))
    }
    @Test fun lostReplyRetriesSameDepositAndUploadsReceiptOnlyOnce() {
        val server=FakeServer(loseFirstReply=true);val vm=start(server);val (form,values)=values(vm);var done=0
        main {vm.submit(form,values){done++}}
        waitFor {!vm.state.value.busy};assertNotNull(vm.state.value.error);assertEquals(1,server.calls);assertEquals(0,done)
        main {vm.submit(form,values){done++}}
        waitFor {!vm.state.value.busy};assertNull(vm.state.value.error)
        assertEquals(2,server.calls);assertEquals(1,server.uploads);assertEquals(1,server.deposits.size);assertEquals(1,done)
        assertEquals(server.bodies[0].getString("p_request_id"),server.bodies[1].getString("p_request_id"))
        assertEquals("preview/one-receipt.png",server.bodies[1].getString("p_receipt_path"));assertTrue(server.bodies[1].isNull("p_share_id"))
    }
    @Test fun doubleTapWhileSubmittingDoesNotCreateTwoRequests() {
        val gate=CompletableDeferred<Unit>();val server=FakeServer(gate=gate);val vm=start(server);val (form,values)=values(vm);var done=0
        main {vm.submit(form,values){done++};vm.submit(form,values){done++}}
        waitFor {server.calls==1};assertTrue(vm.state.value.busy);assertEquals(1,server.uploads)
        main {gate.complete(Unit)};waitFor {!vm.state.value.busy}
        assertEquals(1,server.calls);assertEquals(1,done);assertEquals(1,server.deposits.size)
    }
}
