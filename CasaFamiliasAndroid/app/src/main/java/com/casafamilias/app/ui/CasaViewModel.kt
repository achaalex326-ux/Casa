package com.casafamilias.app.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.casafamilias.app.data.*
import com.casafamilias.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

data class CasaState(val starting: Boolean=true,val data: Snapshot?=null,val busy: Boolean=false,val error: String?=null,val info: String?=null,val recovery: Boolean=false,val dark: Boolean=false,val textScale: Float=1f,val extras: Map<String,List<Record>> = emptyMap(),val locked: Boolean=false,val passwordEntry: Boolean=false,val biometricEnabled: Boolean=false,val biometricPrompting: Boolean=false,val securityRevision: Int=0)
class CasaViewModel(app: Application,private val source: CasaDataSource,private val clock: ()->Long = SystemClock::elapsedRealtime): AndroidViewModel(app) {
    private val prefs=app.getSharedPreferences("casa_preferences",0)
    private val accessPrefs=app.getSharedPreferences("casa_local_access",0)
    private val access=LocalAccessPolicy(accessPrefs.getString("owner",null).takeIf {it==source.session?.userId})
    private var biometricRequest: Boolean?=null
    private val mutable=MutableStateFlow(CasaState(dark=prefs.getBoolean("dark",false),textScale=prefs.getFloat("text_scale",1f),locked=access.requiresUnlock(source.session?.userId),biometricEnabled=access.enabledFor(source.session?.userId)))
    val state=mutable.asStateFlow()
    private val events=MutableSharedFlow<String>(extraBufferCapacity=8);val messages=events.asSharedFlow()
    var hasStarted=false; private set
    fun deviceSettingsChanged(){mutable.update {it.copy(securityRevision=it.securityRevision+1)}}
    private fun resumeDeviceAlerts(data: Snapshot?) {
        if(source !is CasaRepository)return
        val app=getApplication<Application>()
        DeviceAlertState.accountChanged(app,source.session?.userId)
        if(data!=null && !data.preview)DeviceAlertState.schedule(app,data.profile.id)
    }
    fun beginBiometric(enable: Boolean): Boolean {
        val s=state.value
        if(s.busy || s.starting || biometricRequest!=null)return false
        if(enable && (s.locked || s.data==null || s.data.preview || s.data.profile.id!=source.session?.userId))return false
        if(!enable && !access.requiresUnlock(source.session?.userId))return false
        biometricRequest=enable;mutable.update {it.copy(biometricPrompting=true,info=null)};return true
    }
    fun biometricVerified() {
        val enable=biometricRequest ?: return
        biometricRequest=null
        if(enable) {
            val user=source.session?.userId ?: return biometricCancelled("Vuelve a ingresar antes de activar la huella.")
            if(state.value.data?.profile?.id!=user || state.value.locked)return biometricCancelled(null)
            access.enable(user);accessPrefs.edit().putString("owner",user).apply()
        } else access.authenticate()
        mutable.update {it.copy(locked=false,passwordEntry=false,biometricEnabled=true,biometricPrompting=false,info=null,error=null)}
        if(enable)inform("Acceso con huella activado en este teléfono.") else start()
    }
    fun biometricCancelled(message: String?,password: Boolean=false) {
        val enabling=biometricRequest==true;biometricRequest=null
        mutable.update {it.copy(biometricPrompting=false,info=message)}
        if(password && !enabling)usePassword()
        if(message!=null && !state.value.locked)inform(message)
    }
    fun disableBiometric() {
        if(state.value.locked || state.value.data==null)return
        access.disable();accessPrefs.edit().remove("owner").apply()
        mutable.update {it.copy(biometricEnabled=false,biometricPrompting=false)}
        inform("Acceso con huella desactivado en este teléfono.")
    }
    fun usePassword() {if(state.value.locked)mutable.update {it.copy(passwordEntry=true,error=null,info=null)}}
    fun returnToBiometric() {if(state.value.locked)mutable.update {it.copy(passwordEntry=false,error=null,info=null)}}
    fun background() {access.background(clock())}
    fun foreground() {
        access.foreground(clock())
        val locked=access.requiresUnlock(source.session?.userId)
        // Recheck Android enrollment when returning from its security settings.
        mutable.update {it.copy(locked=it.locked || locked,passwordEntry=if(locked)false else it.passwordEntry,info=if(locked)null else it.info,securityRevision=it.securityRevision+1)}
    }
    private fun passwordAccepted() {
        val user=source.session?.userId ?: return
        access.passwordAuthenticated(user)
        if(access.owner==null)accessPrefs.edit().remove("owner").apply()
        mutable.update {it.copy(locked=false,passwordEntry=false,biometricEnabled=access.enabledFor(user))}
    }
    private fun clearLocalAccess() {
        if(source is CasaRepository)DeviceAlertState.disable(getApplication())
        biometricRequest=null;access.disable();accessPrefs.edit().remove("owner").apply()
        mutable.update {it.copy(locked=false,passwordEntry=false,biometricEnabled=false,biometricPrompting=false)}
    }
    fun theme(dark: Boolean) {prefs.edit().putBoolean("dark",dark).apply();mutable.update { it.copy(dark=dark) }}
    fun scale(scale: Float) {prefs.edit().putFloat("text_scale",scale).apply();mutable.update { it.copy(textScale=scale) }}
    fun start(legacy: String?=null,fragment: String?=null) {
        hasStarted=true
        if(fragment==null && access.requiresUnlock(source.session?.userId)) {
            if(source is CasaRepository)source.session?.userId?.let {DeviceAlertState.schedule(getApplication(),it,false)}
            mutable.update {it.copy(starting=false,locked=true)};return
        }
        viewModelScope.launch {
            mutable.update { it.copy(starting=true,error=null) }
            try {
                withTimeout(45000) { if(fragment!=null){source.importRecovery(fragment);clearLocalAccess();mutable.update { it.copy(starting=false,recovery=true,data=null) };return@withTimeout} else source.restore(legacy)
                    val data=if(source.session!=null)source.load() else null
                    resumeDeviceAlerts(data)
                    mutable.update { it.copy(starting=false,data=data,recovery=false) }
                }
            } catch(e: Exception) { if(e is CancellationException && e !is TimeoutCancellationException)throw e;if(source.session==null){clearLocalAccess();mutable.update {it.copy(data=null,extras=emptyMap())}};mutable.update { it.copy(starting=false,error=human(e)) } }
        }
    }
    fun login(email: String,password: String) {
        if(state.value.busy)return
        if(!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) || password.isBlank()){mutable.update { it.copy(error="Escribe tu correo y contraseña.") };return}
        task(authentication=true) {source.login(email,password);passwordAccepted();if(source is CasaRepository)DeviceAlertState.accountChanged(getApplication(),source.session?.userId);mutable.update {it.copy(data=null,extras=emptyMap())};val data=source.load();resumeDeviceAlerts(data);mutable.update { it.copy(data=data,recovery=false,error=null) }}
    }
    fun recover(email: String) {if(email.isBlank()){mutable.update { it.copy(error="Escribe tu correo para enviarte el enlace.") };return};task(authentication=true) {source.recover(email);mutable.update {it.copy(info="Si el correo tiene una cuenta, recibirás el enlace de recuperación.")} }}
    fun refresh() {if(state.value.data?.preview==true){events.tryEmit("Vista de diseño: datos de ejemplo.");return};task { val data=source.load();resumeDeviceAlerts(data);mutable.update { it.copy(data=data,error=null) } }}
    fun logout() {task { source.logout();clearLocalAccess();getApplication<Application>().getSharedPreferences("casa_native_migration",0).edit().putBoolean("done",true).apply();mutable.update { it.copy(data=null,recovery=false,error=null,extras=emptyMap()) } }}
    fun resetPassword(password: String,confirmation: String) {
        if(password.length<8 || password!=confirmation){mutable.update {it.copy(error=if(password.length<8)"La contraseña debe tener al menos 8 caracteres." else "Las contraseñas no coinciden.")};return}
        task {source.password(password);val data=runCatching {source.load()}.getOrNull();mutable.update {it.copy(recovery=false,data=data,error=if(data==null)"Contraseña actualizada. Ingresa con tu nueva contraseña." else null)};events.emit("Contraseña actualizada.")}
    }
    fun clearError(){mutable.update { it.copy(error=null) }}
    fun inform(message: String){events.tryEmit(message)}
    private fun human(e: Exception)=when(e){is java.io.IOException->if(e is ApiError)e.message else "No se pudo conectar. Revisa internet y vuelve a intentar.";is TimeoutCancellationException->"La conexión está tardando demasiado. Revisa internet y vuelve a intentar.";is java.net.UnknownHostException,is java.net.ConnectException->"No se pudo conectar. Revisa internet y vuelve a intentar.";else->e.message?.takeIf { it.isNotBlank() } ?: "No se pudo completar. Inténtalo de nuevo."}
    private fun task(authentication: Boolean=false,block: suspend ()->Unit) {
        if(state.value.busy || (state.value.locked && !authentication))return
        mutable.update { it.copy(busy=true,error=null) }
        viewModelScope.launch {
            try {withTimeout(60000){block()}}
            catch(e: SessionExpired){clearLocalAccess();mutable.update { it.copy(data=null,recovery=false,error=e.message,extras=emptyMap()) }}
            catch(e: Exception){if(e is CancellationException && e !is TimeoutCancellationException)throw e;mutable.update { it.copy(error=human(e)) };events.emit(human(e))}
            finally{mutable.update { it.copy(busy=false) }}
        }
    }
    fun mutate(name: String,body: JSONObject,success: String="Cambio guardado.",done: ()->Unit={}) {
        if(state.value.data?.preview==true){inform("Vista de diseño: no se guardan datos.");return}
        task {source.rpc(name,body);val data=source.load();mutable.update { it.copy(data=data) };events.emit(success);done()}
    }
    fun image(path: String,qr: Boolean=false,done: (String)->Unit) {
        if(state.value.locked)return
        viewModelScope.launch {try {val url=source.signedImage(path,qr);if(!state.value.locked)done(url)}catch(e: Exception){if(e is CancellationException)throw e;events.emit("No se pudo abrir la imagen. Revisa tu conexión.")}}
    }
    fun targets(turnId: String) {
        if(state.value.data?.preview==true)return
        task {val raw=source.rpc("casa_cleaning_swap_targets",json("p_turn_id" to turnId));val values=JSONArray(raw.toString()).records();mutable.update { it.copy(extras=it.extras+("swap:$turnId" to values)) }}
    }
    fun readNotifications(){val s=state.value.data ?: return;if(s.preview)return;val unread=s.rows("notifications").filter { it.text("read_at").isBlank() };if(unread.isEmpty())return
        task {unread.forEach {source.change("notifications","PATCH",json("read_at" to java.time.Instant.now().toString()),it.id)};val updated=source.load();mutable.update { it.copy(data=updated) }}
    }
    fun removeTemplate(id: String){if(state.value.data?.preview==true)return;task {source.change("recurring_expense_templates","DELETE",null,id);mutable.update { it.copy(data=source.load()) };events.emit("Plantilla eliminada.")}}
    fun submit(form: Form,values: Map<String,String>,done: ()->Unit) {
        val error=form.error(values);if(error!=null){mutable.update { it.copy(error=error) };inform(error);return}
        val s=state.value.data ?: return
        if(s.preview){inform("Vista de diseño: no se guardan datos.");return}
        task {
            fun v(key: String)=values[key].orEmpty().trim()
            fun amount(key: String)=Amounts.jsonValue(v(key).ifBlank { "0" })
            fun id(key: String)=v(key).toLongOrNull()
            suspend fun image(key: String,qr: Boolean=false): String?=v(key).takeIf { it.isNotBlank() }?.let { source.upload(Uri.parse(it),qr) }
            val tasks=JSONArray(v("tasks").lines().map {it.trim()}.filter {it.isNotBlank()})
            when(form.kind) {
                "payment" -> {val receipt=image("receipt");val payment=source.rpc("record_payment_v7",json("p_share_id" to v("share"),"p_amount" to amount("amount"),"p_paid_at" to v("date"),"p_method" to v("method"),"p_receipt_path" to receipt,"p_note" to v("note").ifBlank { null },"p_ocr_text" to values["ocr_text"]))
                    // Notification delivery remains a server-side concern; saving a payment is authoritative.
                    payment?.toString()?.takeIf { it.isNotBlank() }?.let { paymentId -> viewModelScope.launch {runCatching {source.notifyPayment(paymentId)}} }
                }
                "expense" -> source.rpc("create_expense",json("p_concept" to v("concept"),"p_category" to "external","p_total_amount" to amount("amount"),"p_expense_date" to v("date"),"p_due_date" to v("due"),"p_justification" to v("justification"),"p_receipt_path" to image("receipt"),"p_ocr_text" to values["ocr_text"],"p_paid_by_family_id" to id("paid_by")))
                "credit" -> source.rpc("create_utility_credit",json("p_service" to v("service"),"p_amount" to amount("amount"),"p_note" to v("note"),"p_receipt_path" to image("receipt")))
                "services" -> { val reference=v("reference")=="true";source.rpc("create_monthly_basic_services",json("p_month" to v("month")+"-01","p_water" to amount("water"),"p_electricity" to amount("electricity"),"p_gas" to amount("gas"),"p_wifi" to amount("wifi"),"p_due_date" to v("due"),"p_water_receipt" to image("water_receipt"),"p_electricity_receipt" to image("electricity_receipt"),"p_gas_receipt" to image("gas_receipt"),"p_wifi_receipt" to image("wifi_receipt"),"p_other_water" to if(reference)0 else amount("other_water"),"p_other_electricity" to if(reference)0 else amount("other_electricity"),"p_reference_only" to reference)) }
                "family-name" -> source.rpc("casa_admin_rename_family",json("p_family_id" to form.recordId.toLong(),"p_name" to v("name")))
                "assignment" -> source.rpc("casa_admin_assign_user_families",json("p_user_id" to form.recordId,"p_family_id" to id("family"),"p_second_family_id" to if(v("role")=="payer")id("second") else null))
                "user" -> {if(v("role")=="payer" && v("payer_family")==v("second"))error("El encargado necesita dos familias diferentes.");source.createUser(json("action" to "create","display_name" to v("name"),"username" to v("username"),"email" to v("email"),"password" to values["password"],"role" to v("role"),"family_id" to when(v("role")){"family"->id("family");"payer"->id("payer_family");else->null},"second_family_id" to if(v("role")=="payer")id("second") else null))}
                "qr" -> {val path=image("receipt",true) ?: s.rows("payment_qr_settings").firstOrNull()?.text("qr_image_path");require(!path.isNullOrBlank()){ "Selecciona una imagen del QR." };source.rpc("save_payment_qr_settings",json("p_qr_image_path" to path,"p_qr_title" to v("title").ifBlank { "Pago por QR" },"p_qr_note" to v("note").ifBlank { null }))}
                "payer-families" -> source.rpc("set_payer_families",json("p_first_family" to id("first"),"p_second_family" to id("second")))
                "turn" -> source.rpc("create_cleaning_turn",json("p_family_id" to id("family"),"p_turn_date" to v("date"),"p_checklist" to tasks,"p_note" to v("note").ifBlank { null }))
                "complete" -> source.rpc("complete_cleaning_turn",json("p_turn_id" to form.recordId,"p_receipt_path" to image("receipt"),"p_note" to v("note").ifBlank { null }))
                "swap" -> source.rpc("request_cleaning_swap",json("p_requester_turn" to form.recordId,"p_target_turn" to v("target")))
                "rotation" -> {val order=JSONArray(form.fields.filter { it.key.startsWith("order_") }.map { id(it.key) });source.rpc("casa_configure_cleaning_rotation",json("p_enabled" to (v("enabled")=="true"),"p_family_order" to order,"p_anchor_monday" to v("anchor"),"p_default_checklist" to tasks))}
                "notice" -> source.rpc("create_notice",json("p_title" to v("title"),"p_body" to v("body"),"p_until" to v("until").ifBlank { null }))
                "proposal" -> source.rpc("create_proposal",json("p_concept" to v("concept"),"p_amount" to amount("amount"),"p_justification" to v("justification"),"p_receipt_path" to image("receipt")))
                "comment" -> source.rpc("add_expense_comment",json("p_expense" to form.recordId,"p_body" to v("body")))
                "template" -> {val day=v("day").toIntOrNull() ?: 0;require(day in 1..28){"Elige un día entre 1 y 28."};source.change("recurring_expense_templates","POST",json("concept" to v("concept"),"category" to v("category"),"default_due_day" to day,"justification" to v("justification"),"created_by" to s.profile.id))}
                "external-paid" -> source.rpc("mark_external_utility_paid",json("p_debt_id" to form.recordId,"p_received_by" to v("received_by"),"p_paid_amount" to amount("amount")))
                "password" -> {source.password(values["password"].orEmpty());mutable.update { it.copy(recovery=false) }}
                else -> error("Operación no disponible.")
            }
            events.emit(if(form.kind=="payment")"Pago enviado para revisión." else "Cambio guardado.");done();val refreshed=runCatching {source.load()};mutable.update { it.copy(data=refreshed.getOrNull() ?: it.data,error=if(refreshed.isFailure) "Guardado. No se pudo actualizar la vista; usa Actualizar datos." else null) }
        }
    }
}
