package com.casafamilias.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.casafamilias.app.data.*
import com.casafamilias.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.Instant

/** Real Android notifications, fake account/server, no financial or backend writes. */
class DeviceNotificationsTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get()=context.getSystemService(NotificationManager::class.java)
    private val store get()=SecureSessionStore(context)
    private val user="notify-test"
    private var original: Session?=null
    private var loads=0
    private fun waitForSystem(condition: ()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+5000
        while(!condition() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50)
        assertTrue("Android did not apply the notification change within 5 seconds",condition())
    }
    private fun snapshot(balance: Int=42)=Snapshot(Profile(user,"Prueba","test@example.test",Role.FAMILY,1,null),mapOf("alert_debts" to listOf(Record(json("share_id" to "share-test","balance" to balance,"concept" to "Agua de ejemplo","due_date" to Dates.today())))))
    @Before fun setup() {
        original=store.load();DeviceAlertState.disable(context)
        store.save(Session("fake-test-only","fake-test-only",Long.MAX_VALUE,user,"test@example.test"))
        DeviceAlertState.history(context,user).edit().clear().commit()
        DeviceAlertState.prefs(context).edit().putString("owner",user).putString("enabled_at",Instant.now().minusSeconds(60).toString()).commit()
    }
    @After fun clean() {
        DeviceAlertState.disable(context);DeviceAlertState.history(context,user).edit().clear().commit()
        if(original!=null)store.save(original!!) else store.clear()
    }
    private fun worker(s: Snapshot=snapshot(),expected: String=user): DeviceAlertWorker =
        TestListenableWorkerBuilder<DeviceAlertWorker>(context).setInputData(workDataOf("user_id" to expected))
            .setWorkerFactory(object: WorkerFactory() {
                override fun createWorker(appContext: Context,workerClassName: String,workerParameters: WorkerParameters): ListenableWorker =
                    DeviceAlertWorker(appContext,workerParameters) {_,_,_->loads++;s}
            }).build()
    @Test fun workerPostsToAndroidWithoutAnActivityAndDoesNotRepeat()=runBlocking {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).isEmpty())}
        assertEquals(ListenableWorker.Result.success(),worker().doWork())
        waitForSystem {manager.activeNotifications.any {it.tag=="casa:debt:share-test"}}
        val shown=manager.activeNotifications.single {it.tag=="casa:debt:share-test"}
        assertEquals(Notification.VISIBILITY_PRIVATE,shown.notification.visibility)
        assertTrue(shown.notification.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("42,00"))
        assertFalse(shown.notification.publicVersion.extras.toString().contains("42,00"))
        val seen=DeviceAlertState.history(context,user).getString("seen",null)
        worker().doWork();assertEquals(seen,DeviceAlertState.history(context,user).getString("seen",null))
        assertEquals(1,manager.activeNotifications.count {it.tag=="casa:debt:share-test"})
    }
    @Test fun paidDebtWithdrawsAnAlreadyDisplayedReminder()=runBlocking {
        worker().doWork();waitForSystem {manager.activeNotifications.any {it.tag=="casa:debt:share-test"}}
        worker(snapshot(0)).doWork();waitForSystem {manager.activeNotifications.none {it.tag=="casa:debt:share-test"}}
    }
    @Test fun accountMismatchAndLogoutDoNotFetchOrNotify()=runBlocking {
        worker(expected="another-account").doWork();assertEquals(0,loads)
        store.clear();worker().doWork();assertEquals(0,loads)
        assertTrue(manager.activeNotifications.isEmpty())
    }
    @Test fun disabledAlertsCancelDeliveryAndSchedulingUsesOnePeriodicJob()=runBlocking {
        DeviceAlertState.schedule(context,user,false)
        DeviceAlertState.schedule(context,user,false)
        val work=WorkManager.getInstance(context).getWorkInfosForUniqueWork("casa-alert-periodic").get()
        assertEquals(1,work.count {!it.state.isFinished})
        DeviceAlertState.disable(context);worker().doWork();assertEquals(0,loads)
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
