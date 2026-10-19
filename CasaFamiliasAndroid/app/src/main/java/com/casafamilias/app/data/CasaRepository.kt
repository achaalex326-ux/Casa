package com.casafamilias.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.casafamilias.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class ApiError(val status: Int, override val message: String): IOException(message)
class SessionExpired: IOException("Tu sesión terminó. Vuelve a ingresar.")
data class ApiConfig(val url: String,val key: String,val bucket: String="receipts") {
    companion object {
        fun load(context: Context): ApiConfig {
            val text=context.assets.open("web/config.js").bufferedReader().use { it.readText() }
            fun value(name: String)=Regex("[\"']?"+name+"[\"']?\\s*:\\s*[\"']([^\"']+)").find(text)?.groupValues?.get(1) ?: error("La conexión de Casa Familias no está configurada.")
            return ApiConfig(value("SUPABASE_URL").trimEnd('/'),value("SUPABASE_PUBLISHABLE_KEY"))
        }
    }
}
interface CasaDataSource {
    val session: Session?
    suspend fun login(email: String,password: String)
    suspend fun restore(legacy: String?=null)
    suspend fun load(): Snapshot
    suspend fun rpc(name: String,body: JSONObject): Any?
    suspend fun change(table: String,method: String,body: JSONObject?,id: String?=null)
    suspend fun notifyPayment(paymentId: String)
    suspend fun createUser(body: JSONObject)
    suspend fun signedImage(path: String,qr: Boolean=false): String
    suspend fun upload(uri: Uri,qr: Boolean=false): String
    suspend fun logout()
    suspend fun recover(email: String)
    suspend fun password(value: String)
    suspend fun importRecovery(fragment: String)
}
class CasaRepository(private val context: Context?,private val config: ApiConfig,private val store: SessionStore,private val client: OkHttpClient=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(35,TimeUnit.SECONDS).build()): CasaDataSource {
    override var session: Session?=store.load();private set
    companion object { private val authLock=Mutex() }
    private var notifiedForUser: String?=null
    private fun message(raw: String,status: Int): String {
        val j=runCatching { JSONObject(raw) }.getOrNull()
        val m=j?.optString("message")?.takeIf { it.isNotBlank() } ?: j?.optString("msg")?.takeIf { it.isNotBlank() } ?: j?.optString("error_description")?.takeIf { it.isNotBlank() } ?: j?.optString("error")?.takeIf { it.isNotBlank() }
        return when { status==401 -> "La sesión no es válida. Vuelve a ingresar."
            m?.contains("Invalid login credentials",true)==true -> "El correo o la contraseña no coinciden."
            m?.contains("Bucket not found",true)==true -> "No está disponible el almacenamiento del QR. Vuelve a intentar o avisa al administrador."
            status==403 -> "Tu cuenta no tiene permiso para realizar este cambio."
            m?.contains("duplicate",true)==true -> "Ya existe un registro con esos datos."
            else -> m ?: "No se pudo completar la operación. Inténtalo de nuevo." }
    }
    private suspend fun send(path: String,method: String="GET",body: String?=null,query: Map<String,String> = emptyMap(),token: String?=session?.accessToken,media: String="application/json",bytes: ByteArray?=null): String = withContext(Dispatchers.IO) {
        val url=(config.url+path).toHttpUrl().newBuilder().apply { query.forEach { (k,v)->addQueryParameter(k,v) } }.build()
        val req=Request.Builder().url(url).header("apikey",config.key).header("Authorization","Bearer ${token ?: config.key}").header("Accept","application/json")
        req.method(method,if(method in listOf("GET","HEAD")) null else (bytes ?: (body ?: "{}").toByteArray()).toRequestBody(media.toMediaType()))
        if(method=="PATCH" || method=="POST")req.header("Prefer","return=representation")
        client.newCall(req.build()).execute().use { response ->
            val result=response.body?.string().orEmpty()
            if(!response.isSuccessful)throw ApiError(response.code,message(result,response.code))
            result
        }
    }
    private fun save(j: JSONObject) { val parsed=Session.from(j);require(parsed.accessToken.isNotBlank() && parsed.refreshToken.isNotBlank());store.save(parsed);session=parsed }
    private suspend fun refreshLocked(rejectedToken: String?=null) {
        // Foreground and background repositories share one rotating refresh token.
        val latest=store.load()
        if(latest==null || (session?.userId?.isNotBlank()==true && latest.userId!=session?.userId)) {
            session=null;throw SessionExpired()
        }
        session=latest
        val s=session ?: throw SessionExpired()
        if(s.expiresAt>System.currentTimeMillis()/1000+60 && s.accessToken!=rejectedToken)return
        try { save(JSONObject(send("/auth/v1/token","POST",json("refresh_token" to s.refreshToken).toString(),mapOf("grant_type" to "refresh_token"),token=null))) }
        catch(e: ApiError) { if(e.status in 400..499 && e.status!=429) {store.clear();session=null;throw SessionExpired()} else throw e }
    }
    private suspend fun refresh(rejectedToken: String?=null) = authLock.withLock {refreshLocked(rejectedToken)}
    private suspend fun authorized(path: String,method: String="GET",body: JSONObject?=null,query: Map<String,String> = emptyMap()): String {
        refresh()
        val attemptedToken=session?.accessToken
        return try {send(path,method,body?.toString(),query)} catch(e: ApiError) {
            if(e.status!=401)throw e
            refresh(attemptedToken);send(path,method,body?.toString(),query)
        }
    }
    override suspend fun login(email: String,password: String) {authLock.withLock {save(JSONObject(send("/auth/v1/token","POST",json("email" to email.trim(),"password" to password).toString(),mapOf("grant_type" to "password"),token=null)))}}
    override suspend fun restore(legacy: String?) {
        if(session==null && !legacy.isNullOrBlank())runCatching { save(JSONObject(legacy)) }
        if(session!=null)refresh()
    }
    suspend fun select(table: String,query: Map<String,String> = emptyMap()): List<Record> {
        if(query.containsKey("limit"))return JSONArray(authorized("/rest/v1/$table",query=mapOf("select" to "*")+query)).records()
        val order=when(table){"cleaning_family_aliases"->"family_id";"family_credits"->"family_id";"utility_baselines"->"service";"periods"->"month_start";else->"id"}
        val all=mutableListOf<Record>();var offset=0
        while(true){val page=JSONArray(authorized("/rest/v1/$table",query=mapOf("select" to "*","order" to order)+query+mapOf("limit" to "500","offset" to offset.toString()))).records();all.addAll(page);if(page.size<500)break;offset+=page.size}
        return all
    }
    override suspend fun load(): Snapshot = coroutineScope {
        val s=session ?: throw SessionExpired()
        val user=JSONObject(authorized("/auth/v1/user"));val id=user.getString("id")
        val current=session ?: s
        if(id!=current.userId)authLock.withLock {val latest=store.load() ?: throw SessionExpired();if(latest.userId.isNotBlank() && latest.userId!=id)throw SessionExpired();val updated=latest.copy(userId=id,email=user.optString("email"));store.save(updated);session=updated}
        val p=select("profiles",mapOf("id" to "eq.$id")).firstOrNull()?.let(Profile::from) ?: error("La cuenta no tiene un perfil disponible. Consulta al administrador.")
        if(p.role.staff && notifiedForUser!=p.id){runCatching {rpc("generate_due_notifications",JSONObject())};notifiedForUser=p.id}
        val names=when(p.role) {
            Role.RESTAURANT -> listOf("external_utility_debts")
            Role.FAMILY -> listOf("families","payments","payment_batches","cleaning_turns","notices","notifications","payment_qr_settings","family_credits","proposals","proposal_votes","cleaning_swap_requests","cleaning_family_aliases")
            else -> listOf("families","profiles","periods","recurring_expense_templates","expenses","monthly_basic_services","external_utility_debts","utility_baselines","utility_credits","expense_shares","family_credits","payments","payment_batches","cleaning_turns","cleaning_swap_requests","proposals","proposal_votes","notices","expense_comments","notifications","payment_qr_settings","cleaning_family_aliases")+if(p.role==Role.ADMIN) listOf("audit_log","cleaning_rotation_settings") else emptyList()
        }
        val tables=names.map { name -> async { val filters=when {
            p.role==Role.RESTAURANT -> mapOf("profile_id" to "eq.${p.id}","order" to "month_start.desc")
            p.role==Role.FAMILY && name=="proposals" -> mapOf("select" to "id,concept,estimated_amount,justification,receipt_path,status,created_at")
            p.role==Role.FAMILY && name=="proposal_votes" -> mapOf("select" to "id,proposal_id,family_id,vote")
            name=="notifications" -> mapOf("order" to "created_at.desc","limit" to "100")
            name=="audit_log" -> mapOf("order" to "created_at.desc","limit" to "300")
            p.role==Role.FAMILY && name in listOf("payments","payment_batches","family_credits") -> mapOf("family_id" to "eq.${p.familyId}")
            else -> emptyMap()
        };name to select(name,filters) } }.awaitAll().toMap().toMutableMap()
        if(p.role==Role.FAMILY){tables["family_debts"]=JSONArray(rpc("family_portal_debts",JSONObject()).toString()).records();tables["proposal_totals"]=JSONArray(rpc("casa_proposal_totals",JSONObject()).toString()).records()}
        Snapshot(p,tables)
    }
    /** Minimal, authenticated reads for opted-in background alerts; no mutations. */
    suspend fun notificationSnapshot(enabledAt: String): Snapshot {
        val user=JSONObject(authorized("/auth/v1/user"))
        val id=user.getString("id")
        if(session?.userId!=id)throw SessionExpired()
        val p=select("profiles",mapOf("id" to "eq.$id")).firstOrNull()?.let(Profile::from) ?: throw SessionExpired()
        val tables=mutableMapOf<String,List<Record>>()
        tables["notifications"]=select("notifications",mapOf("user_id" to "eq.$id","created_at" to "gte.$enabledAt","read_at" to "is.null","order" to "created_at.desc","limit" to "100"))
        if(p.role!=Role.RESTAURANT) {
            tables["families"]=select("families")
            tables["cleaning_family_aliases"]=select("cleaning_family_aliases")
            val monday=Dates.monday(Dates.today())
            tables["cleaning_turns"]=select("cleaning_turns",mapOf("turn_date" to "gte.$monday","and" to "(turn_date.lt.${java.time.LocalDate.parse(monday).plusWeeks(1)})"))
        }
        tables["alert_debts"]=when {
            p.role==Role.FAMILY -> JSONArray(rpc("family_portal_debts",JSONObject()).toString()).records()
            p.role==Role.RESTAURANT -> select("external_utility_debts",mapOf("profile_id" to "eq.$id")).map {d->Record(json("id" to d.id,"balance" to (d.money("amount_due")-d.money("amount_paid")).max(java.math.BigDecimal.ZERO),"concept" to if(d.text("service")=="water")"Agua" else "Luz","due_date" to d.text("due_date")))}
            p.familyIds.isNotEmpty() -> {
                val shares=select("expense_shares",mapOf("family_id" to "in.(${p.familyIds.joinToString(",")})","select" to "id,family_id,amount_due,expenses!inner(id,concept,due_date)"))
                val paid=select("payments",mapOf("family_id" to "in.(${p.familyIds.joinToString(",")})","status" to "eq.approved","select" to "id,share_id,applied_amount"))
                shares.map {d->val e=d.json.optJSONObject("expenses") ?: JSONObject();val applied=paid.filter {it.text("share_id")==d.id}.fold(java.math.BigDecimal.ZERO){a,r->a+r.money("applied_amount")};Record(json("share_id" to d.id,"concept" to e.optString("concept"),"due_date" to e.optString("due_date"),"balance" to (d.money("amount_due")-applied).max(java.math.BigDecimal.ZERO)))}
            }
            else -> emptyList()
        }
        return Snapshot(p,tables)
    }
    override suspend fun rpc(name: String,body: JSONObject): Any? = authorized("/rest/v1/rpc/$name","POST",body).takeIf { it.isNotBlank() && it!="null" }?.let { org.json.JSONTokener(it).nextValue() }
    override suspend fun change(table: String,method: String,body: JSONObject?,id: String?) { authorized("/rest/v1/$table",method,body,if(id!=null) mapOf("id" to "eq.$id") else emptyMap()) }
    override suspend fun notifyPayment(paymentId: String) { authorized("/functions/v1/notify-payment-upload","POST",json("payment_id" to paymentId)) }
    override suspend fun createUser(body: JSONObject) { val result=JSONObject(authorized("/functions/v1/admin-users","POST",body));if(result.has("error"))error(result.getString("error")) }
    override suspend fun signedImage(path: String,qr: Boolean): String {
        val bucket=if(qr) "payment-qr" else config.bucket
        val encoded=path.split('/').joinToString("/") { Uri.encode(it) }
        val j=JSONObject(authorized("/storage/v1/object/sign/$bucket/$encoded","POST",json("expiresIn" to 300)))
        val signed=j.optString("signedURL").ifBlank { j.optString("signedUrl") }
        require(signed.isNotBlank()) { "No se pudo abrir la imagen." }
        return if(signed.startsWith("https://")) {require(signed.toHttpUrl().host==config.url.toHttpUrl().host);signed} else config.url+"/storage/v1"+signed
    }
    override suspend fun upload(uri: Uri,qr: Boolean): String = withContext(Dispatchers.IO) {
        val resolver=requireNotNull(context).contentResolver
        val mime=resolver.getType(uri) ?: error("No se pudo reconocer el formato de la imagen.")
        require(mime in listOf("image/png","image/jpeg","image/webp")) { "Selecciona una imagen PNG, JPG o WebP." }
        val max=if(qr) 5242880 else 10485760
        val data=resolver.openInputStream(uri)?.use { stream -> val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(out.size()<=max){val n=stream.read(buffer,0,minOf(buffer.size,max+1-out.size()));if(n<0)break;out.write(buffer,0,n)};out.toByteArray() } ?: error("No se pudo leer la imagen.")
        require(data.isNotEmpty() && data.size<=max) { if(qr) "La imagen debe pesar entre 1 byte y 5 MB." else "La imagen debe pesar entre 1 byte y 10 MB." }
        refresh();val user=requireNotNull(session).userId
        val extension=when(mime){"image/png"->"png";"image/webp"->"webp";else->"jpg"}
        val path=(if(qr) "qr" else user)+"/${UUID.randomUUID()}.$extension"
        send("/storage/v1/object/${if(qr) "payment-qr" else config.bucket}/$path","POST",media=mime,bytes=data)
        path
    }
    override suspend fun logout() {authLock.withLock {if(session!=null){refreshLocked();send("/auth/v1/logout","POST")};store.clear();session=null;notifiedForUser=null}}
    override suspend fun recover(email: String) {send("/auth/v1/recover","POST",json("email" to email.trim()).toString(),mapOf("redirect_to" to "casafamilias://auth"),token=null)}
    override suspend fun password(value: String) {require(value.length>=8);authorized("/auth/v1/user","PUT",json("password" to value))}
    override suspend fun importRecovery(fragment: String) {authLock.withLock {
        val values=Uri.parse("https://local/?"+fragment)
        val access=values.getQueryParameter("access_token") ?: error("El enlace de recuperación no es válido.")
        val refresh=values.getQueryParameter("refresh_token") ?: error("El enlace de recuperación no es válido.")
        val u=JSONObject(send("/auth/v1/user",token=access))
        save(json("access_token" to access,"refresh_token" to refresh,"expires_at" to (System.currentTimeMillis()/1000+3600),"user" to u))
    }}
}
