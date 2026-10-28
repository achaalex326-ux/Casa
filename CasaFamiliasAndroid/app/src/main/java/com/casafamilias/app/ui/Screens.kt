package com.casafamilias.app.ui

import com.casafamilias.app.BuildConfig
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.casafamilias.app.domain.*
import java.math.BigDecimal
import org.json.JSONArray

typealias Go = (String)->Unit
@Composable fun ContentList(content: LazyListScope.()->Unit) {LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)}
@Composable fun HomeScreen(s: Snapshot,go: Go) {
    val p=s.profile;val month=Dates.month();val pending=s.paymentEntries().filter {it.text("status")=="pending"}
    val cleaning=s.rows("cleaning_turns").filter {it.text("status")=="pending"}.sortedBy {it.text("turn_date")}
    ContentList {
        item {Title("Buen día, ${p.name.split(' ').firstOrNull().orEmpty()}","Así está nuestra casa hoy")}
        if(p.role==Role.RESTAURANT){
            val debts=s.rows("external_utility_debts");item {Hero("Saldo pendiente de servicios",debts.fold(BigDecimal.ZERO){a,r->a+(r.money("amount_due")-r.money("amount_paid")).max(BigDecimal.ZERO)},"Tus importes de luz y agua")}
            items(debts){r->Panel{Text(if(r.text("service")=="electricity")"Luz" else "Agua",style=MaterialTheme.typography.titleMedium);DataLine("Mes",Dates.monthLabel(r.text("month_start").take(7)));DataLine("Saldo pendiente",Amounts.format((r.money("amount_due")-r.money("amount_paid")).max(BigDecimal.ZERO)));Badge(if(r.money("amount_due")<=r.money("amount_paid"))"paid" else "pending")}}
            if(debts.isEmpty())item{Empty("No tienes deudas registradas")}
        } else if(p.role==Role.FAMILY) {
            item {AccountSummary(s,p.familyId)}
            item {NavRow("expenses","Ver mi estado de cuenta","Revisa qué meses faltan y cuánto debes"){go("dues")}}
            val own=cleaning.firstOrNull {s.ownsCleaning(it.number("family_id"))}
            item {if(own!=null)Panel{Text("Tu próxima limpieza",style=MaterialTheme.typography.titleMedium);DataLine(s.family(s.household(own.number("family_id"))),Dates.label(own.text("turn_date")));Secondary("Ver turno y tareas"){go("turn/${own.id}")}} else Empty("No tienes un turno pendiente","Cuando se asigne aparecerá aquí.")}
            val next=s.rows("family_debts").filter {it.money("balance")>BigDecimal.ZERO}.sortedBy {it.text("due_date")}.firstOrNull()
            if(next!=null)item{Panel{Text("Próximo vencimiento",style=MaterialTheme.typography.titleMedium);DataLine(next.text("concept"),Dates.label(next.text("due_date")));Text(Amounts.format(next.money("balance")))}}
            item {Panel{DataLine("Saldo a favor",Amounts.format(s.credit(p.familyId)));Text("Se aplica según las reglas de tus próximas cuotas.",style=MaterialTheme.typography.bodySmall)}}
        } else {
            item {Hero("Gastos del mes",s.total(month),Dates.monthLabel(month))}
            item {Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                MetricButton("payments","Pagos por revisar","${pending.size}","Ver pagos",Modifier.weight(1f).fillMaxHeight()){go("payments")}
                MetricButton("families","Familias con deuda","${s.rows("families").count {s.balance(it.number("id"))>BigDecimal.ZERO}} de ${s.rows("families").size}","Ver familias",Modifier.weight(1f).fillMaxHeight()){go("families")}
            }}
            item {CleaningThisWeek(s,go)}
            item {NavRow("cleaning","Calendario de todas las familias","Consultar semanas anteriores y próximas"){go("cleaning")}}
            item {Text("Actividad reciente",style=MaterialTheme.typography.titleLarge)}
            val recent=s.paymentEntries().sortedByDescending {it.text("created_at")}.take(4)
            items(recent){r->NavRow("payments",s.family(r.number("family_id")),Amounts.format(r.money("amount"))+" · "+statusLabel(r.text("status"))){go("payment/${r.id}")}}
            if(recent.isEmpty())item{Empty("Todavía no hay movimientos")}
        }
    }
}
fun statusLabel(s: String)=mapOf("pending" to "En revisión","approved" to "Aprobado","rejected" to "Rechazado")[s] ?: s
@Composable fun ExpensesScreen(s: Snapshot,go: Go) {
    var month by rememberSaveable {mutableStateOf(Dates.month())};var tab by rememberSaveable {mutableStateOf("services")};var add by remember {mutableStateOf(false)}
    ContentList {
        item {Title("Gastos","Servicios y gastos del hogar")};item {MonthPicker(month,{month=it})}
        item {Hero("Gastos del mes",s.total(month))}
        item {Filters(listOf(Choice("services","Servicios"),Choice("external","Extraordinarios"),Choice("credits","Descuentos"),Choice("restaurant","Restaurante")),tab,{tab=it})}
        item {Action("Registrar gasto"){add=true}}
        when(tab) {
            "services" -> {val rows=s.rows("monthly_basic_services").filter {it.text("month_start").startsWith(month)}
                items(rows){r->Panel {Text(Dates.monthLabel(r.text("month_start").take(7)),style=MaterialTheme.typography.titleLarge)
                    listOf("water_amount" to "Agua","electricity_amount" to "Luz","gas_amount" to "Gas","wifi_amount" to "Internet").forEach {(key,label)->DataLine(label,Amounts.format(r.money(key)));val path=r.text(key.removeSuffix("_amount")+"_receipt_path");if(path.isNotBlank())ImageReceiptButton(path,label)}
                    DataLine("Vencimiento",Dates.label(r.text("due_date")));if(r.flag("reference_only"))Text("Referencia histórica: no genera deuda.")
                }}
                if(rows.isEmpty())item{Empty("No hay servicios en este mes")}
            }
            "external" -> {val rows=s.expenses(month).filter {it.text("category")=="external"};items(rows){r->NavRow("expenses",r.text("concept"),Amounts.format(r.money("total_amount"))+" · "+Dates.label(r.text("expense_date"))){go("expense-detail/${r.id}")}};if(rows.isEmpty())item{Empty("No hay gastos extraordinarios")}}
            "credits" -> {val rows=s.rows("utility_credits");items(rows){r->Panel{Text(serviceLabel(r.text("service")),style=MaterialTheme.typography.titleMedium);DataLine("Monto",Amounts.format(r.money("amount")));Text(r.text("note"));Badge(r.text("status"));if(r.text("receipt_path").isNotBlank())ImageReceiptButton(r.text("receipt_path"))}};if(rows.isEmpty())item{Empty("No hay descuentos registrados")}}
            else -> {val rows=s.rows("external_utility_debts").filter {it.text("month_start").startsWith(month)};items(rows){r->Panel{Text(serviceLabel(r.text("service"))+" · "+Dates.monthLabel(month));DataLine("Saldo del restaurante",Amounts.format((r.money("amount_due")-r.money("amount_paid")).max(BigDecimal.ZERO)));if(r.money("amount_due")>r.money("amount_paid"))Secondary("Registrar aporte recibido"){go("form/external-paid?id=${r.id}")}}};if(rows.isEmpty())item{Empty("No hay aportes del restaurante en este mes")}}
        }
    }
    if(add)AlertDialog(onDismissRequest={add=false},title={Text("Registrar gasto")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Secondary("Servicios del mes"){add=false;go("form/services")};Secondary("Extraordinario o arreglo"){add=false;go("form/expense")};Secondary("Descontar de un servicio"){add=false;go("form/credit")}}},confirmButton={TextButton(onClick={add=false}){Text("Cancelar")}})
}
// The only full image viewer is opened by the shared navigation host.
val ReceiptViewer=staticCompositionLocalOf<(String,Boolean)->Unit> { {_,_->} }
@Composable fun ImageReceiptButton(path: String,label: String="comprobante",qr: Boolean=false) {val viewer=ReceiptViewer.current;TextButton(onClick={viewer(path,qr)}){Glyph("image");Spacer(Modifier.width(8.dp));Text("Ver $label")}}
fun serviceLabel(s: String)=mapOf("water" to "Agua","electricity" to "Luz","gas" to "Gas","wifi" to "Internet")[s] ?: s
@Composable fun PaymentsScreen(s: Snapshot,go: Go,family: String="") {
    var filter by rememberSaveable {mutableStateOf(if(s.profile.role.staff)"pending" else "all")}
    val all=s.paymentEntries().filter {family.isBlank() || it.text("family_id")==family}
    val rows=all.filter {filter=="all" || it.text("status")==filter}.sortedByDescending {it.text("created_at")}
    ContentList {
        item{Title(if(family.isBlank())"Pagos y comprobantes" else "Pagos de ${s.family(family.toLongOrNull())}",if(s.profile.role.staff)"Revisa un depósito completo de una sola vez" else "Aquí ves lo que enviaste y si ya fue aprobado")}
        if(s.profile.role==Role.FAMILY)item{NavRow("expenses","¿Qué meses me faltan pagar?","Abre tu estado de cuenta por mes"){go("dues")}}
        else if(family.isNotBlank())item{NavRow("expenses","Ver deudas por mes","Saldo y meses pendientes de esta familia"){go("family/$family")}}
        item{Panel{
            val waiting=all.filter {it.text("status")=="pending"}
            Text("${waiting.size} ${if(waiting.size==1)"comprobante por revisar" else "comprobantes por revisar"}",style=MaterialTheme.typography.titleMedium)
            if(waiting.isNotEmpty())Text("Total enviado por revisar: "+Amounts.format(waiting.fold(BigDecimal.ZERO){a,r->a+r.money("amount")}))
            Text("Enviar un comprobante no descuenta la deuda. El saldo se actualiza cuando se aprueba.",style=MaterialTheme.typography.bodySmall)
        }}
        if(s.profile.role!=Role.RESTAURANT)item{Action("Registrar pago"){go("form/payment")}}
        item{Filters(listOf(Choice("pending","Por revisar"),Choice("approved","Aprobados"),Choice("rejected","Rechazados"),Choice("all","Todos")),filter,{filter=it})}
        items(rows){r->Panel{
            Text(if(s.profile.role.staff)s.family(r.number("family_id")) else if(r.flag("batch"))"Depósito con un comprobante" else "Pago registrado",style=MaterialTheme.typography.titleMedium)
            Text(Amounts.format(r.money("amount")),style=MaterialTheme.typography.titleLarge);Badge(r.text("status"))
            DataLine("Fecha del pago",Dates.label(r.text("paid_at")))
            val periods=s.paymentPeriods(r)
            if(periods.isNotEmpty())Text((when(r.text("status")){"approved"->"Aplicado a: ";"rejected"->"Se solicitó para: ";else->"Previsto para: "})+periods.take(3).joinToString {Dates.monthLabel(it)}+(if(periods.size>3)" y ${periods.size-3} meses más" else ""),style=MaterialTheme.typography.bodyMedium)
            if(r.text("status")=="approved"){DataLine("Descontado de la deuda",Amounts.format(r.money("applied_amount")));if(r.money("credit_amount")>BigDecimal.ZERO)DataLine("Quedó a favor",Amounts.format(r.money("credit_amount")))}
            if(r.text("status")=="pending")Text("Todavía no descontado del saldo.",style=MaterialTheme.typography.bodySmall)
            if(r.text("status")=="rejected")Text("Este comprobante fue rechazado y no descontó tu deuda.",style=MaterialTheme.typography.bodySmall)
            Secondary(if(r.flag("batch"))if(s.profile.role.staff && r.text("status")=="pending")"Revisar depósito completo" else "Ver reparto del depósito" else if(s.profile.role.staff && r.text("status")=="pending")"Revisar comprobante" else "Ver detalle"){go("payment/${r.id}")}
        }}
        if(rows.isEmpty())item{Empty("No hay pagos en esta sección")}
    }
}
@Composable fun PaymentDetail(s: Snapshot,id: String,vm: CasaViewModel) {
    val r=s.paymentEntries().firstOrNull {it.id==id};var action by remember {mutableStateOf<Boolean?>(null)};val state by vm.state.collectAsState()
    if(r==null){Empty("Pago no disponible");return}
    val batch=r.flag("batch");val approved=r.text("status")=="approved"
    val parts=if(batch && !approved)r.json.optJSONArray("planned_allocation")?.records().orEmpty() else if(batch)s.rows("payments").filter {it.text("batch_id")==id} else emptyList()
    ContentList {
        item{Title(if(batch)"Detalle del depósito" else "Detalle del pago",s.family(r.number("family_id")))}
        item{Panel{
            DataLine(if(batch)"Total depositado" else "Monto",Amounts.format(r.money("amount")));Badge(r.text("status"))
            DataLine("Aplicado a tus deudas",Amounts.format(r.money("applied_amount")));DataLine("Saldo a favor generado",Amounts.format(r.money("credit_amount")))
            DataLine("Fecha",Dates.label(r.text("paid_at")));DataLine("Forma de pago",r.paymentMethodLabel())
            if(r.text("note").isNotBlank())Text(r.text("note"))
        }}
        if(batch){
            item{Text(if(approved)"Reparto aprobado por mes" else "Reparto previsto al enviar",style=MaterialTheme.typography.titleLarge)}
            items(parts){part->
                val share=s.find("expense_shares",part.text("share_id"))
                val debt=s.rows("family_debts").firstOrNull {it.text("share_id")==part.text("share_id")}
                val expense=s.find("expenses",share?.text("expense_id").orEmpty())
                val monthly=s.rows("monthly_basic_services").firstOrNull {m->val expenseId=share?.text("expense_id").orEmpty();expenseId.isNotBlank() && (m.text("expense_id")==expenseId || m.text("monthly_expense_id")==expenseId)}
                val month=part.text("month_start").ifBlank {debt?.text("month_start").orEmpty().ifBlank {monthly?.text("month_start").orEmpty()}}
                val concept=part.text("concept").ifBlank {debt?.text("concept").orEmpty().ifBlank {expense?.text("concept").orEmpty().ifBlank {"Deuda"}}}
                Panel{Text(if(month.isNotBlank())Dates.monthLabel(month.take(7)) else concept,style=MaterialTheme.typography.titleMedium);if(month.isNotBlank())Text(concept);DataLine(if(approved)"Importe aplicado" else "Importe previsto",Amounts.format(part.money(if(approved)"applied_amount" else "amount")))}
            }
            if(!approved)item{Panel{
                if(r.money("planned_credit")>BigDecimal.ZERO)DataLine("Saldo a favor previsto",Amounts.format(r.money("planned_credit")))
                Text(if(r.text("status")=="rejected")"Depósito rechazado: no se descontó ninguna deuda." else "Los saldos no cambian hasta aprobar. El reparto se recalcula al revisar, por si ya se aprobó otro pago.")
            }}
        }
        if(r.text("receipt_path").isNotBlank())item{Panel{Text("Un comprobante para todo el depósito",style=MaterialTheme.typography.titleMedium);ImageReceipt(vm,r.text("receipt_path"))}} else item{Text("Este pago no tiene una imagen adjunta.")}
        if(s.profile.role.staff && r.text("status")=="pending"){item{Action(if(batch)"Aprobar depósito completo" else "Aprobar pago",!state.busy){action=true}};item{Secondary(if(batch)"Rechazar depósito completo" else "Rechazar pago",!state.busy){action=false}}}
    }
    if(action!=null)Confirm(if(action==true)"Aprobar depósito" else "Rechazar depósito","${if(action==true)"¿Confirmas la aprobación" else "¿Confirmas el rechazo"} de ${Amounts.format(r.money("amount"))}? ${if(batch)"Se revisará el depósito completo, con un solo comprobante." else ""}",onDismiss={action=null}){
        val approve=action==true;action=null
        vm.mutate(if(batch)"casa_review_payment_batch" else "review_payment",if(batch)json("p_batch_id" to id,"p_approve" to approve) else json("p_payment_id" to id,"p_approve" to approve),if(approve)"Pago aprobado." else "Pago rechazado.")
    }
}
@Composable fun FamiliesScreen(s: Snapshot,go: Go) {
    var search by rememberSaveable {mutableStateOf("")};val rows=s.rows("families").filter {it.text("name").contains(search,true)}
    ContentList {item{Title("Familias","Las cuentas de nuestra casa")};item{OutlinedTextField(search,{search=it},label={Text("Buscar familia")},leadingIcon={Glyph("search")},modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium)}
        item{Text("${s.rows("families").size} familias · ${s.rows("families").count {s.balance(it.number("id"))>BigDecimal.ZERO}} con saldo pendiente",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(rows){f->Panel{Text(f.text("name"),style=MaterialTheme.typography.titleMedium);Text(Amounts.format(s.balance(f.number("id"))),style=MaterialTheme.typography.titleLarge);Text(if(s.balance(f.number("id"))==BigDecimal.ZERO)"Al día" else "Saldo pendiente");Secondary("Ver cuenta"){go("family/${f.id}")}}}
        if(rows.isEmpty())item{Empty("No se encontraron familias")}
    }
}
@Composable fun DuesScreen(s: Snapshot,go: Go,family: Long?=s.profile.familyId) = FamilyAccountScreen(s,go,family)
@Composable fun CleaningScreen(s: Snapshot,go: Go) {
    var month by rememberSaveable {mutableStateOf(Dates.month())};var scope by rememberSaveable {mutableStateOf("all")}
    val rows=s.cleaningMonth(month).filter {scope=="all" || s.representsCleaning(it.number("family_id"))}
    ContentList {item{Title("Limpieza",if(s.profile.role.staff)"Calendario de todas las familias" else "Los turnos de tu familia")}
        item{CleaningThisWeek(s,go)}
        item{MonthPicker(month,{month=it})}
        if(month!=Dates.month())item{Secondary("Volver al mes actual"){month=Dates.month()}}
        if(s.profile.role.staff)item{Filters(listOf(Choice("all","Todas las familias"),Choice("mine","Mis familias")),scope,{scope=it})}
        if(s.profile.role==Role.ADMIN){item{NavRow("refresh","Repetición semanal",if(s.rows("cleaning_rotation_settings").firstOrNull()?.flag("enabled")==true)"Activa: se conservan las semanas asignadas" else "Revisar el orden y la repetición"){go("form/rotation")}};item{Secondary("Asignar una semana"){go("form/turn")}}}
        item{NavRow("cleaning","Solicitudes de intercambio","Acuerdos entre las familias involucradas"){go("swaps")}}
        items(rows){r->Panel{Text(s.family(s.household(r.number("family_id"))),style=MaterialTheme.typography.titleMedium);Text(Dates.weekLabel(r.text("turn_date")));if(Dates.monday(r.text("turn_date"))==Dates.monday(Dates.today()))Text("Esta semana",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge);Text(if(r.text("status")=="done")"Limpieza completada" else "Limpieza pendiente");Secondary("Ver turno y tareas"){go("turn/${r.id}")}}}
        if(rows.isEmpty())item{Empty("No hay turnos en este mes")}
        if(s.rows("cleaning_family_aliases").isNotEmpty())item{Text("Las cuentas de una misma familia comparten el turno de limpieza.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
@Composable private fun CleaningThisWeek(s: Snapshot,go: Go) {
    val turns=s.cleaningWeek()
    Panel {
        Text("¿Quién limpia esta semana?",style=MaterialTheme.typography.titleMedium)
        Text(Dates.weekLabel(Dates.today()),style=MaterialTheme.typography.bodyMedium)
        if(turns.isEmpty())Text(if(s.profile.role.staff)"Todavía no hay una familia asignada." else "Tu familia no tiene turno esta semana.")
        turns.forEach {r->
            Text(s.family(s.household(r.number("family_id"))),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
            Text(if(r.text("status")=="done")"Limpieza completada" else "Limpieza pendiente")
            Secondary("Ver tareas de esta semana"){go("turn/${r.id}")}
        }
    }
}
@Composable fun TurnDetail(s: Snapshot,id: String,go: Go,vm: CasaViewModel) {
    val r=s.find("cleaning_turns",id);if(r==null){Empty("Turno no disponible");return};val can=s.ownsCleaning(r.number("family_id"));var checks by rememberSaveable(id){mutableStateOf(List(r.list("checklist").size){false})}
    ContentList {item{Title(s.family(s.household(r.number("family_id"))),"Semana del "+Dates.label(r.text("turn_date")))};item{Badge(r.text("status"))}
        itemsIndexed(r.list("checklist")){index,task->Row(verticalAlignment=Alignment.CenterVertically){Checkbox(r.text("status")=="done" || checks[index],onCheckedChange={v->checks=checks.mapIndexed {i,x->if(i==index)v else x}},enabled=can && r.text("status")=="pending");Text(task,Modifier.weight(1f))}}
        if(r.text("note").isNotBlank())item{Panel{Text(r.text("note"))}}
        if(r.text("completion_receipt_path").isNotBlank())item{ImageReceipt(vm,r.text("completion_receipt_path"))}
        if(can && r.text("status")=="pending"){
            item{Action("Completar turno",checks.all {it}){go("form/complete?id=$id")}}
            if(s.representsCleaning(r.number("family_id")))item{Secondary("Solicitar intercambio"){go("form/swap?id=$id")}}
        }
    }
}
@Composable fun SwapsScreen(s: Snapshot,vm: CasaViewModel) {
    var decision by remember {mutableStateOf<Pair<String,Boolean>?>(null)}
    var scope by rememberSaveable {mutableStateOf("all")}
    val rows=s.rows("cleaning_swap_requests").sortedByDescending {it.text("created_at")}.filter {r->when(scope){"received"->s.representsCleaning(r.number("target_family_id"));"sent"->s.representsCleaning(r.number("requester_family_id"));else->true}}
    ContentList {
        item{Title("Intercambios","Cada cambio se acuerda entre dos familias")}
        item{Text("La familia receptora acepta o rechaza. Hasta que acepte, las semanas no cambian. Solo puedes solicitar cambios para tus familias.")}
        if(s.profile.familyIds.isNotEmpty())item{Filters(listOf(Choice("all","Todos"),Choice("received","Recibidos"),Choice("sent","Enviados")),scope,{scope=it})}
        items(rows){r->Panel{
            DataLine("Solicita",s.family(s.household(r.number("requester_family_id"))))
            DataLine("Recibe",s.family(s.household(r.number("target_family_id"))))
            Text(when(r.text("status")){"pending"->"Esperando respuesta de la familia receptora";"accepted"->"Intercambio aceptado";"rejected"->"Intercambio rechazado";else->"Solicitud cerrada"},style=MaterialTheme.typography.labelLarge)
            if(s.canAnswerSwap(r)){Secondary("Aceptar intercambio"){decision=r.id to true};Secondary("Rechazar intercambio"){decision=r.id to false}}
            else if(r.text("status")=="pending" && s.profile.role.staff && !s.representsCleaning(r.number("target_family_id")))Text("Consulta del acuerdo: responde la familia receptora.",style=MaterialTheme.typography.bodySmall)
        }}
        if(rows.isEmpty())item{Empty("No hay solicitudes")}
    }
    decision?.let {d->Confirm(if(d.second)"Aceptar intercambio" else "Rechazar intercambio","¿Confirmas esta respuesta?",onDismiss={decision=null}){decision=null;vm.mutate("review_cleaning_swap",json("p_request" to d.first,"p_accept" to d.second))}}
}
@Composable fun UsersScreen(s: Snapshot,go: Go) {
    var search by rememberSaveable{mutableStateOf("")};var filter by rememberSaveable{mutableStateOf("all")}
    val rows=s.rows("profiles").filter {(filter=="all" || it.text("role")==filter) && (it.text("display_name")+it.text("contact_email")).contains(search,true)}
    ContentList {item{Title("Usuarios","Cuentas, roles y familias")};item{OutlinedTextField(search,{search=it},label={Text("Buscar nombre o correo")},modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium)};item{Filters(listOf(Choice("all","Todos"))+Role.entries.map {Choice(it.code,it.label)},filter,{filter=it})};item{Action("Crear usuario"){go("form/user")}}
        items(rows){r->NavRow("profile",r.text("display_name"),Role.from(r.text("role")).label+(if(r.number("family_id")!=null)" · "+s.family(r.number("family_id")) else "")){go("user/${r.id}")}}
        if(rows.isEmpty())item{Empty("No se encontraron usuarios")}
    }
}
@Composable fun UserDetail(s: Snapshot,id: String,go: Go) {val u=s.find("profiles",id);if(u==null){Empty("Cuenta no disponible");return};ContentList{item{Title(u.text("display_name"))};item{Panel{DataLine("Rol",Role.from(u.text("role")).label);DataLine("Correo",u.text("contact_email"));if(u.number("family_id")!=null)DataLine("Familia",s.family(u.number("family_id")));if(u.number("second_family_id")!=null)DataLine("Segunda familia",s.family(u.number("second_family_id")))}};if(u.text("role") in listOf("family","payer"))item{Action("Cambiar asignación familiar"){go("form/assignment?id=$id")}}}}
@Composable fun ExpenseDetail(s: Snapshot,id: String,go: Go,vm: CasaViewModel) {val e=s.find("expenses",id);if(e==null){Empty("Gasto no disponible");return};ContentList{item{Title(e.text("concept"))};item{Panel{DataLine("Monto",Amounts.format(e.money("total_amount")));DataLine("Fecha",Dates.label(e.text("expense_date")));DataLine("Vencimiento",Dates.label(e.text("due_date")));DataLine("Justificación",e.text("justification"))}};if(e.text("receipt_path").isNotBlank())item{ImageReceipt(vm,e.text("receipt_path"))};item{Text("Comentarios",style=MaterialTheme.typography.titleLarge)};items(s.rows("expense_comments").filter {it.text("expense_id")==id}){r->Panel{Text(r.text("body"));Text(Dates.label(r.text("created_at")),style=MaterialTheme.typography.bodySmall)}};item{Secondary("Añadir comentario"){go("form/comment?id=$id")}}}}
@Composable fun NoticesScreen(s: Snapshot,go: Go,notifications: Boolean=false,vm: CasaViewModel) {val rows=s.rows(if(notifications)"notifications" else "notices").sortedByDescending {it.text("created_at")}
    if(notifications)LaunchedEffect(Unit){vm.readNotifications()}
    ContentList{item{Title(if(notifications)"Notificaciones" else "Avisos","Comunicados de nuestra casa")};if(!notifications && s.profile.role.staff)item{Action("Publicar aviso"){go("form/notice")}};items(rows){r->Panel{Text(r.text("title"),style=MaterialTheme.typography.titleMedium);Text(r.text("body"));Text(Dates.label(r.text("created_at")),style=MaterialTheme.typography.bodySmall)}};if(rows.isEmpty())item{Empty("No hay ${if(notifications)"notificaciones" else "avisos"}")}}
}
@Composable fun ProposalsScreen(s: Snapshot,go: Go,vm: CasaViewModel) {var vote by remember {mutableStateOf<Pair<String,Boolean>?>(null)}
    ContentList{item{Title("Propuestas","Ideas y decisiones del hogar")};item{Action("Publicar propuesta"){go("form/proposal")}}
        items(s.rows("proposals").sortedByDescending {it.text("created_at")}){p->val votes=s.rows("proposal_votes").filter {it.text("proposal_id")==p.id};val own=votes.firstOrNull {it.number("family_id")==s.profile.familyId}
            Panel{Text(p.text("concept"),style=MaterialTheme.typography.titleMedium);Badge(p.text("status"));DataLine("Presupuesto estimado",Amounts.format(p.money("estimated_amount")));Text(p.text("justification"));val totals=s.rows("proposal_totals").firstOrNull {it.text("proposal_id")==p.id};Text("Sí: ${totals?.number("yes_count") ?: votes.count {it.flag("vote")}} · No: ${totals?.number("no_count") ?: votes.count {!it.flag("vote")}}")
                if(p.text("receipt_path").isNotBlank())ImageReceiptButton(p.text("receipt_path"))
                if(p.text("status")=="open" && s.profile.familyId!=null){Secondary(if(own?.flag("vote")==true)"Tu voto: Sí" else "Votar Sí"){vote=p.id to true};Secondary(if(own!=null && !own.flag("vote"))"Tu voto: No" else "Votar No"){vote=p.id to false}}
                if(p.text("status")=="approved" && s.profile.role.staff)Secondary("Preparar gasto desde esta propuesta"){go("form/expense?id=${p.id}")}
            }
        }
        if(s.rows("proposals").isEmpty())item{Empty("Todavía no hay propuestas")}
    }
    vote?.let {v->Confirm("Confirmar voto","¿Quieres votar ${if(v.second)"Sí" else "No"} en esta propuesta?",onDismiss={vote=null}){vote=null;vm.mutate("cast_proposal_vote",json("p_proposal" to v.first,"p_vote" to v.second),"Voto guardado.")}}
}
@Composable fun ReportsScreen(s: Snapshot,onExport: (String,List<List<String>>,String)->Unit) {
    var month by rememberSaveable{mutableStateOf(Dates.month())};var mode by rememberSaveable{mutableStateOf("monthly")};val period=if(mode=="year")month.take(4) else month
    val expenses=s.rows("expenses").filter {it.text("expense_date").startsWith(period)};val total=expenses.fold(BigDecimal.ZERO){a,e->a+e.money("total_amount")};val paid=s.rows("payments").filter {it.text("paid_at").startsWith(period) && it.text("status")=="approved"}.fold(BigDecimal.ZERO){a,p->a+p.money("amount")}
    val grouped=expenses.groupBy {it.text("concept")}.mapValues {(_,items)->items.fold(BigDecimal.ZERO){a,e->a+e.money("total_amount")}}
    val export=listOf(listOf("Fecha","Concepto","Tipo","Total","Asignado","Reembolsado","Pendiente","Justificación"))+expenses.map {e->
        val shares=s.rows("expense_shares").filter {it.text("expense_id")==e.id};val assigned=shares.fold(BigDecimal.ZERO){a,r->a+r.money("amount_due")};val pending=shares.fold(BigDecimal.ZERO){a,r->a+s.shareBalance(r)}
        listOf(e.text("expense_date"),e.text("concept"),e.text("category"),e.money("total_amount").toPlainString(),assigned.toPlainString(),(assigned-pending).toPlainString(),pending.toPlainString(),e.text("justification"))
    }
    val pdfRows=export.map {row->listOf(row[0],row[1],row[3],row[6])}
    ContentList{item{Title("Reportes","Resúmenes de tus registros autorizados")};item{MonthPicker(month,{month=it})};item{Filters(listOf(Choice("monthly","Mensual"),Choice("year","Anual")),mode,{mode=it})};item{Hero("Gastos del período",total)};item{Panel{DataLine("Pagos aprobados del período",Amounts.format(paid))}}
        if(grouped.isNotEmpty())item{Panel{Text("Distribución por concepto",style=MaterialTheme.typography.titleMedium);grouped.entries.sortedByDescending {it.value}.forEach {(label,value)->Text(label,style=MaterialTheme.typography.bodyMedium);LinearProgressIndicator(progress={if(total>BigDecimal.ZERO)(value/total).toFloat() else 0f},modifier=Modifier.fillMaxWidth());Text(Amounts.format(value),style=MaterialTheme.typography.labelMedium)}}}
        item{Secondary("Exportar PDF"){onExport("pdf",pdfRows,period)}};item{Secondary("Exportar Excel"){onExport("xlsx",export,period)}}
        if(expenses.isEmpty())item{Empty("No hay movimientos en este período")}
    }
}
@Composable fun AdministrationScreen(go: Go,onBackup: ()->Unit) {ContentList{item{Title("Administración","Control y configuración de la casa")};item{Panel{Glyph("admin");Text("Los cambios importantes quedan registrados.")}};item{Text("Configuración",style=MaterialTheme.typography.titleLarge)};item{NavRow("qr","QR de pago","Imagen e instrucciones"){go("form/qr")}};item{NavRow("families","Familias del encargado","Dos asignaciones contables"){go("form/payer-families")}};item{NavRow("expenses","Plantillas de gastos","Conceptos recurrentes"){go("templates")}};item{Text("Control",style=MaterialTheme.typography.titleLarge)};item{NavRow("date","Cierre y reapertura de meses","Control de períodos contables"){go("periods")}};item{NavRow("admin","Auditoría","Registro de cambios"){go("audit")}};item{Text("Datos",style=MaterialTheme.typography.titleLarge)};item{NavRow("download","Exportar respaldo","Datos y rutas de comprobantes"){onBackup()}}}}
@Composable fun ProfileScreen(s: Snapshot,vm: CasaViewModel,go: Go) {val state by vm.state.collectAsState();var logout by remember {mutableStateOf(false)}
    val biometrics=LocalBiometricAccess.current
    val alerts=LocalDeviceAlerts.current
    ContentList{item{Title("Mi perfil",s.profile.role.label)};item{Panel{DataLine("Nombre",s.profile.name);DataLine("Correo",s.profile.email);if(s.profile.familyId!=null)DataLine("Familia",s.family(s.profile.familyId))}};item{NavRow("lock","Cambiar contraseña","Gestiona el acceso a tu cuenta"){go("form/password")}}
        item{Panel{
            Glyph("lock");Text("Acceso con huella",style=MaterialTheme.typography.titleMedium)
            Text(if(state.biometricEnabled)"Activado en este teléfono. Se solicitará al volver a abrir la app o después de 30 segundos fuera de ella." else biometrics?.explanation() ?: "Disponible en teléfonos con biometría compatible.",style=MaterialTheme.typography.bodyMedium)
            when {
                s.preview -> Text("La huella se configura al ingresar con tu cuenta real.",style=MaterialTheme.typography.bodySmall)
                state.biometricEnabled -> Secondary("Desactivar huella",!state.busy && !state.biometricPrompting){vm.disableBiometric()}
                biometrics?.available()==true -> Action("Activar huella",!state.busy && !state.biometricPrompting){biometrics.authenticate(enable=true)}
                biometrics?.canEnroll()==true -> Secondary("Configurar huella en el teléfono"){biometrics.enroll()}
            }
        }}
        item{Panel{
            Glyph("notifications");Text("Notificaciones del celular",style=MaterialTheme.typography.titleMedium)
            Text("Recibe tu turno de limpieza, nuevas deudas y avisos de servicios. Se revisan en segundo plano aproximadamente cada 15 minutos; Android puede demorarlos por batería o conexión.")
            when {
                s.preview -> Text("Actívalas al ingresar con tu cuenta real.",style=MaterialTheme.typography.bodySmall)
                alerts?.enabled()==true -> {
                    Text(if(alerts.allowed())"Activadas en este teléfono." else "Android está bloqueando las notificaciones. Permítelas en Ajustes.")
                    Secondary("Probar notificación",alerts.allowed()){alerts.test()}
                    Secondary("Desactivar notificaciones"){alerts.disable()}
                }
                alerts!=null -> Action("Activar notificaciones",!state.busy){alerts.enable()}
            }
            if(!s.preview && alerts!=null)Secondary("Ajustes de notificaciones"){alerts.settings()}
        }}
        item{Panel{Row(verticalAlignment=Alignment.CenterVertically){Text("Modo oscuro",Modifier.weight(1f));Switch(state.dark,{vm.theme(it)})};Text("Tamaño del texto",style=MaterialTheme.typography.titleMedium);Filters(listOf(Choice("1.0","Normal"),Choice("1.15","Grande"),Choice("1.3","Extra grande")),state.textScale.toString(),{vm.scale(it.toFloat())})}};item{Secondary("Actualizar datos",!state.busy){vm.refresh()}};item{Text("Casa Familias ${BuildConfig.VERSION_NAME} · Android",style=MaterialTheme.typography.bodySmall)};item{Secondary("Cerrar sesión",!state.busy){logout=true}}}
    if(logout)Confirm("Cerrar sesión","¿Quieres salir de esta cuenta en el teléfono?",onDismiss={logout=false}){logout=false;vm.logout()}
}
@Composable fun PeriodsScreen(s: Snapshot,vm: CasaViewModel) {var month by rememberSaveable{mutableStateOf(Dates.month())};var action by remember{mutableStateOf<Pair<String,Boolean>?>(null)}
    ContentList{item{Title("Períodos contables")};item{MonthPicker(month,{month=it})};item{Action("Cerrar mes seleccionado"){action=month to true}};items(s.rows("periods").sortedByDescending{it.text("month_start")}){r->Panel{Text(Dates.monthLabel(r.text("month_start").take(7)));Badge(r.text("status"));if(r.text("status")=="closed")Secondary("Reabrir este mes"){action=r.text("month_start").take(7) to false}}}}
    action?.let {a->Confirm(if(a.second)"Cerrar mes" else "Reabrir mes","¿Confirmas ${if(a.second)"cerrar" else "reabrir"} ${Dates.monthLabel(a.first)}?",onDismiss={action=null}){action=null;vm.mutate(if(a.second)"close_period" else "reopen_period",json("p_month" to a.first+"-01"))}}
}
@Composable fun TemplatesScreen(s: Snapshot,vm: CasaViewModel,go: Go) {var delete by remember {mutableStateOf<String?>(null)};ContentList{item{Title("Plantillas de gastos")};item{Action("Crear plantilla"){go("form/template")}};items(s.rows("recurring_expense_templates")){r->Panel{Text(r.text("concept"),style=MaterialTheme.typography.titleMedium);Text("Día de vencimiento: "+r.text("default_due_day"));TextButton(onClick={delete=r.id}){Text("Eliminar plantilla")}}};if(s.rows("recurring_expense_templates").isEmpty())item{Empty("No hay plantillas")}};delete?.let{id->Confirm("Eliminar plantilla","Los gastos ya registrados se conservan. ¿Eliminar esta plantilla?",onDismiss={delete=null}){delete=null;vm.removeTemplate(id)}}}
@Composable fun AuditScreen(s: Snapshot) {ContentList{item{Title("Auditoría","Últimos cambios registrados")};items(s.rows("audit_log").sortedByDescending{it.text("created_at")}){r->Panel{Text(r.text("action")+" · "+r.text("entity"),style=MaterialTheme.typography.titleSmall);Text(Dates.label(r.text("created_at")));Text(r.json.opt("details")?.toString().orEmpty(),style=MaterialTheme.typography.bodySmall)}};if(s.rows("audit_log").isEmpty())item{Empty("No hay cambios registrados")}}}
