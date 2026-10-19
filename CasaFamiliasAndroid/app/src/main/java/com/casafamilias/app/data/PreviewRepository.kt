package com.casafamilias.app.data

import android.net.Uri
import com.casafamilias.app.domain.*
import org.json.JSONObject

/** Read-only design fixtures, enabled only by an explicit intent on a debuggable APK. */
class PreviewRepository(private val role: Role): CasaDataSource {
    override val session=Session("design-only","design-only",Long.MAX_VALUE,"preview","vista@example.test")
    override suspend fun restore(legacy: String?){}
    override suspend fun login(email: String,password: String){}
    override suspend fun load(): Snapshot {
        fun row(vararg p: Pair<String,Any?>)=Record(json(*p))
        val month=Dates.month();val monday=Dates.monday(Dates.today())
        val profile=Profile("preview",if(role==Role.FAMILY)"Familia Salinas" else if(role==Role.PAYER)"Encargado de pagos" else if(role==Role.RESTAURANT)"Restaurante" else "Alex", "vista@example.test",role,if(role in listOf(Role.FAMILY,Role.PAYER))1L else null,if(role==Role.PAYER)2L else null)
        val families=listOf("Salinas","Salinas2","Acha","Choque","Galarza","David").mapIndexed {i,n->row("id" to i+1,"name" to "Familia $n")}
        val expenses=listOf("Agua" to 450,"Luz" to 820,"Gas" to 380,"Internet" to 400,"Arreglo de cocina" to 400).mapIndexed {i,(n,a)->row("id" to "e$i","concept" to n,"total_amount" to a,"expense_date" to "$month-05","due_date" to "$month-20","category" to if(i==4)"external" else "basic","justification" to "Ejemplo de diseño" )}
        val payments=listOf(4,1,3).mapIndexed {i,f->row("id" to "p$i","family_id" to f,"share_id" to "s$i","amount" to listOf(85,120,160)[i],"applied_amount" to 0,"credit_amount" to 0,"status" to "pending","paid_at" to "$month-10","created_at" to "$month-10T10:00:00Z","note" to "Pago de servicios","method" to "qr")}
        val shares=listOf(4,1,3).mapIndexed {i,f->row("id" to "s$i","family_id" to f,"expense_id" to "e$i","amount_due" to listOf(85,120,160)[i])}
        val cleaning=listOf(4,5,1,6,3).mapIndexed {i,f->row("id" to "t$i","family_id" to f,"turn_date" to java.time.LocalDate.parse(monday).plusWeeks(i.toLong()).toString(),"status" to "pending","checklist" to org.json.JSONArray(listOf("Baño","Cocina","Áreas comunes")))}
        val swaps=listOf(
            row("id" to "swap-incoming","requester_family_id" to 4,"target_family_id" to 1,"requester_turn_id" to "t0","target_turn_id" to "t2","status" to "pending","created_by" to "another-user","created_at" to Dates.today()),
            row("id" to "swap-outgoing","requester_family_id" to 1,"target_family_id" to 5,"requester_turn_id" to "t2","target_turn_id" to "t1","status" to "pending","created_by" to "preview","created_at" to Dates.today()))
        val tables=mapOf("cleaning_swap_requests" to swaps,"families" to families,"profiles" to listOf(row("id" to "preview","display_name" to profile.name,"role" to role.code,"contact_email" to profile.email,"family_id" to profile.familyId),row("id" to "u1","display_name" to "Cuenta de prueba","role" to "family","family_id" to 4,"contact_email" to "familia@example.test")),"expenses" to expenses,"payments" to if(role==Role.FAMILY)payments.filter {it.number("family_id")==1L} else payments,"expense_shares" to shares,"family_debts" to listOf(row("id" to "s1","share_id" to "s1","concept" to "Agua","balance" to 120,"amount_due" to 120,"due_date" to "$month-20")),"cleaning_turns" to if(role==Role.FAMILY)cleaning.filter {it.number("family_id")==1L} else cleaning,"cleaning_family_aliases" to listOf(row("family_id" to 2,"household_id" to 1)),"cleaning_rotation_settings" to listOf(row("enabled" to true,"family_order" to org.json.JSONArray(listOf(4,5,1,6,3)),"anchor_monday" to monday,"default_checklist" to org.json.JSONArray(listOf("Baño","Cocina","Áreas comunes")))),"notices" to listOf(row("id" to "n1","title" to "Organización de la limpieza","body" to "Revisa el turno de tu familia en el calendario.","created_at" to Dates.today())),"external_utility_debts" to listOf(row("id" to "d1","service" to "electricity","amount_due" to 100,"amount_paid" to 40,"month_start" to "$month-01")))
        return Snapshot(profile,tables,preview=true)
    }
    private fun blocked(): Nothing=error("Vista de diseño: no se guardan datos.")
    override suspend fun rpc(name: String,body: JSONObject): Any?=blocked()
    override suspend fun change(table: String,method: String,body: JSONObject?,id: String?)=blocked()
    override suspend fun notifyPayment(paymentId: String)=blocked()
    override suspend fun createUser(body: JSONObject)=blocked()
    override suspend fun signedImage(path: String,qr: Boolean): String=blocked()
    override suspend fun upload(uri: Uri,qr: Boolean): String=blocked()
    override suspend fun logout()=blocked()
    override suspend fun recover(email: String)=blocked()
    override suspend fun password(value: String)=blocked()
    override suspend fun importRecovery(fragment: String)=blocked()
}
