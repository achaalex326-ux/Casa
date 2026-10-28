package com.casafamilias.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.casafamilias.app.data.*
import com.casafamilias.app.domain.*
import com.casafamilias.app.ui.*
import java.io.File
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** Real provider/JPEG/form processing; a registry supplies camera results instead of a physical sensor. */
class PhotoAttachmentsTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val app get()=instrumentation.targetContext.applicationContext as Application
    private val files=mutableListOf<Uri>()
    private lateinit var vm: CasaViewModel
    private lateinit var server: Server
    private var now=1000L
    private class Server(val role: Role): CasaDataSource by PreviewRepository(role) {
        var uploads=0;var writes=0;var body=JSONObject()
        override suspend fun load()=PreviewRepository(role).load().copy(preview=false)
        override suspend fun upload(uri: Uri,qr: Boolean): String {
            val app=InstrumentationRegistry.getInstrumentation().targetContext
            val bytes=app.contentResolver.openInputStream(uri)!!.use {it.readBytes()}
            assertTrue(bytes.size>2 && bytes[0]==0xff.toByte() && bytes[1]==0xd8.toByte())
            uploads++;return "preview/test-receipt.jpg"
        }
        override suspend fun rpc(name: String,body: JSONObject): Any? {writes++;this.body=body;return "fake-id"}
    }
    private fun writePhoto(uri: Uri,width: Int=128,height: Int=64) {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        app.contentResolver.openOutputStream(uri,"w")!!.use {assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,92,it))};bitmap.recycle()
    }
    private inner class CameraRegistry(val cancel: Boolean=false,val defer: Boolean=false): ActivityResultRegistry() {
        var uri: Uri?=null;var code=0
        override fun <I,O> onLaunch(requestCode: Int,contract: ActivityResultContract<I,O>,input: I,options: ActivityOptionsCompat?) {
            assertTrue(contract is ReceiptCaptureContract);uri=input as Uri;files+=uri!!;code=requestCode
            if(!cancel)writePhoto(uri!!)
            if(!defer)dispatchResult(requestCode,!cancel)
        }
        fun finish(){println("PHOTO_DELIVERED="+dispatchResult(code,true))}
    }
    @Before fun before(){app.getSharedPreferences("casa_local_access",0).edit().clear().commit();app.getSharedPreferences("casa_preferences",0).edit().clear().commit()}
    @After fun after(){files.forEach {ReceiptPhotos.deleteIfOwned(app,it)};before()}
    private fun start(role: Role) {
        server=Server(role)
        compose.runOnUiThread {vm=CasaViewModel(app,server){now};vm.start()}
        compose.waitUntil(15000){!vm.state.value.starting};assertNotNull(vm.state.value.data)
    }
    private fun owner(registry: CameraRegistry)=object: ActivityResultRegistryOwner {override val activityResultRegistry=registry}
    private fun form(kind: String,registry: CameraRegistry,initial: Uri?=null): Form {
        val snapshot=vm.state.value.data!!
        val built=Forms.build(kind,"",snapshot).let {if(initial==null)it else it.copy(fields=it.fields.map {f->if(f.key=="receipt")f.copy(initial=initial.toString()) else f})}
        compose.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner(registry)){CasaTheme(false){FormScreen(vm,snapshot,built){}}}}
        return built
    }
    private fun captured(){
        try {compose.waitUntil(15000){compose.onAllNodesWithText("Quitar imagen").fetchSemanticsNodes().isNotEmpty() && vm.state.value.preparingPhotos.isEmpty()}}
        catch(e: Throwable){println("PHOTO_DEBUG locked=${vm.state.value.locked} starting=${vm.state.value.starting} work=${vm.state.value.preparingPhotos} results=${vm.state.value.preparedPhotos}");println(compose.onRoot().printToString());throw e}
    }
    @Test fun providerOnlySharesReceiptFilesAndIntentGrantsCameraOutput() {
        val uri=ReceiptPhotos.create(app);files+=uri
        assertEquals("content",uri.scheme);assertEquals(app.packageName+".receipts",uri.authority)
        val intent=ReceiptCaptureContract().createIntent(app,uri)
        assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE,intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION!=0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        assertEquals(uri,intent.clipData!!.getItemAt(0).uri)
        val private=File(app.filesDir,"should-remain-private.txt").apply {writeText("fictional")}
        try {assertThrows(IllegalArgumentException::class.java){FileProvider.getUriForFile(app,app.packageName+".receipts",private)}}finally{private.delete()}
    }
    @Test fun photoIsResizedRotatedAndStrippedOfCameraMetadata() {
        val uri=ReceiptPhotos.create(app);files+=uri;writePhoto(uri,4000,1000)
        app.contentResolver.openFileDescriptor(uri,"rw")!!.use {pfd->val exif=ExifInterface(pfd.fileDescriptor);exif.setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE,"1/1,2/1,3/1");exif.saveAttributes()}
        ReceiptPhotos.prepare(app,uri)
        val bytes=app.contentResolver.openInputStream(uri)!!.use {it.readBytes()};assertTrue(bytes.size<=5*1024*1024)
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size);assertEquals(640,bitmap.width);assertEquals(2560,bitmap.height);bitmap.recycle()
        app.contentResolver.openInputStream(uri)!!.use {val exif=ExifInterface(it);assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))}
    }
    @Test fun serviceBillPhotoAttachesToCorrectServiceAndOnlyUploadsOnSave() {
        start(Role.ADMIN);val registry=CameraRegistry();val f=form("services",registry)
        compose.onNodeWithText("Continuar").performScrollTo().performClick()
        compose.onAllNodesWithText("Tomar foto").onFirst().performScrollTo().performClick();captured()
        assertEquals(1,compose.onAllNodesWithText("Quitar imagen").fetchSemanticsNodes().size)
        assertEquals(0,server.uploads);assertEquals(0,server.writes)
        val values=f.fields.associate {it.key to it.initial}+mapOf("water" to "50","water_receipt" to registry.uri.toString())
        compose.runOnUiThread {vm.submit(f,values){}}
        compose.waitUntil(15000){!vm.state.value.busy}
        assertNull(vm.state.value.error);assertEquals(1,server.uploads);assertEquals(1,server.writes)
        assertEquals("preview/test-receipt.jpg",server.body.getString("p_water_receipt"));assertTrue(server.body.isNull("p_electricity_receipt"))
        assertThrows(java.io.FileNotFoundException::class.java){app.contentResolver.openInputStream(registry.uri!!)!!.close()}
    }
    @Test fun cancellingCameraKeepsPreviousAttachment() {
        start(Role.ADMIN);val existing=ReceiptPhotos.create(app);files+=existing;writePhoto(existing)
        val registry=CameraRegistry(cancel=true);form("expense",registry,existing)
        compose.onNodeWithText("Tomar foto").performScrollTo().performClick();compose.waitForIdle()
        compose.onNodeWithText("Quitar imagen").assertExists()
        assertTrue(app.contentResolver.openInputStream(existing)!!.use {it.readBytes()}.isNotEmpty())
        assertThrows(java.io.FileNotFoundException::class.java){app.contentResolver.openInputStream(registry.uri!!)!!.close()}
        assertEquals(0,server.uploads)
    }
    @Test fun delayedCameraResultSurvivesBiometricLockAndKeepsPaymentDraft() {
        start(Role.FAMILY);val registry=CameraRegistry(defer=true)
        compose.runOnUiThread {assertTrue(vm.beginBiometric(true));vm.biometricVerified()}
        compose.setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner(registry)){CasaApp(vm)}}
        compose.onNodeWithText("Pagos").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Registrar pago"))
        compose.onNodeWithText("Registrar pago").performClick()
        compose.onNodeWithText("Total depositado (Bs)").performScrollTo().performTextInput("200")
        compose.runOnUiThread {
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).single()
            activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(activity.window.decorView.windowToken,0)
            activity.currentFocus?.clearFocus()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Tomar foto").performScrollTo().performClick()
        assertNotNull("The camera must launch before simulating a long background",registry.uri)
        compose.runOnUiThread {vm.background();now+=31000;vm.foreground()}
        compose.onNodeWithText("Tu hogar, protegido").assertExists()
        compose.onNodeWithText("Total depositado (Bs)").assertDoesNotExist()
        compose.runOnUiThread {registry.finish();assertTrue(vm.beginBiometric(false));vm.biometricVerified()}
        compose.waitUntil(15000){!vm.state.value.starting && !vm.state.value.locked}
        captured()
        compose.onNode(hasSetTextAction() and hasText("200")).assertExists()
        assertEquals(0,server.uploads);assertEquals(0,server.writes)
    }
}
