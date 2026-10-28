package com.casafamilias.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.casafamilias.app.domain.*
import java.math.BigDecimal

@Composable fun AccountSummary(s: Snapshot,family: Long?) {
    val account=s.familyAccount(family)
    Hero("Te falta pagar",account.balance,s.family(family))
    Panel {
        if(account.months.isEmpty())Text("Todavía no hay gastos registrados para esta familia.")
        else {
            Text(if(account.pending.isEmpty())"Al día en los meses registrados" else "${account.pending.size} ${if(account.pending.size==1)"mes con saldo pendiente" else "meses con saldo pendiente"}",style=MaterialTheme.typography.titleMedium)
            account.latestPublished?.let {DataLine("Gastos registrados hasta",Dates.monthLabel(it))}
            account.earliestPending?.let {DataLine("Primer mes que falta cubrir",Dates.monthLabel(it))}
            if(account.pending.isEmpty())Text("Cuando se publique otro gasto, aparecerá aquí.",style=MaterialTheme.typography.bodySmall)
        }
        if(account.reviewingCount>0)Text("${account.reviewingCount} ${if(account.reviewingCount==1)"comprobante por revisar" else "comprobantes por revisar"}. El saldo baja al aprobar el pago.",color=MaterialTheme.colorScheme.primary)
        if(s.credit(family)>BigDecimal.ZERO)DataLine("Saldo a favor disponible",Amounts.format(s.credit(family)))
        if(account.missingMonths.isNotEmpty()){
            Text("Hay meses sin registro: "+account.missingMonths.take(3).joinToString {Dates.monthLabel(it)}+(if(account.missingMonths.size>3)" y ${account.missingMonths.size-3} más." else "."),style=MaterialTheme.typography.bodySmall)
            Text("Un mes sin registro no se considera pagado.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun MonthStatus(status: String) {
    val background=if(status=="Al día")MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    Surface(color=background,shape=MaterialTheme.shapes.small){Text(status,Modifier.padding(horizontal=12.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge)}
}
@Composable fun FamilyAccountScreen(s: Snapshot,go: Go,family: Long?=s.profile.familyId) {
    val account=s.familyAccount(family);val familyMode=s.profile.role==Role.FAMILY
    var filter by rememberSaveable(family){mutableStateOf(if(account.pending.isEmpty())"all" else "pending")}
    val months=account.months.filter {filter=="all" || if(filter=="paid")it.balance<=BigDecimal.ZERO else it.balance>BigDecimal.ZERO}
        .let {if(filter=="pending")it else it.sortedByDescending {m->m.period}}
    ContentList {
        item{Title("Estado de cuenta",s.family(family))}
        item{AccountSummary(s,family)}
        if(familyMode && account.balance>BigDecimal.ZERO)item{Action("Pagar varios meses con un comprobante"){go("form/payment")}}
        if(!familyMode && s.profile.role==Role.ADMIN)item{Secondary("Editar nombre de familia"){go("form/family-name?id=$family")}}
        item{NavRow("payments","Ver comprobantes",if(familyMode)"Consulta qué pagos están por revisar o aprobados" else "Historial de depósitos de esta familia"){go(if(familyMode)"payments" else "payments-family/$family")}}
        item{Text("Tus deudas, mes por mes",style=MaterialTheme.typography.titleLarge)}
        item{Filters(listOf(Choice("pending","Por pagar"),Choice("paid","Al día"),Choice("all","Todos")),filter,{filter=it})}
        items(months,key={it.period}){month->
            var detail by rememberSaveable(family,month.period){mutableStateOf(false)}
            Panel(Modifier.testTag("account-month-${month.period}")) {
                Text(month.label,style=MaterialTheme.typography.titleLarge)
                MonthStatus(month.status)
                DataLine("Falta pagar",Amounts.format(month.balance))
                Text("Asignado: ${Amounts.format(month.assigned)} · Cubierto: ${Amounts.format(month.covered)}",style=MaterialTheme.typography.bodyMedium)
                if(month.reviewing>BigDecimal.ZERO)Text("Hay comprobantes por ${Amounts.format(month.reviewing)} en revisión para este mes. Aún no se descuentan.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                Secondary(if(detail)"Ocultar detalle del mes" else "Ver detalle del mes"){detail=!detail}
                if(detail)month.debts.forEach {debt->
                    HorizontalDivider();Text(debt.concept,style=MaterialTheme.typography.titleSmall)
                    DataLine("Importe asignado",Amounts.format(debt.assigned));DataLine("Ya cubierto",Amounts.format(debt.covered));DataLine("Todavía falta",Amounts.format(debt.balance))
                    if(debt.dueDate.isNotBlank())DataLine("Fecha límite",Dates.label(debt.dueDate))
                    if(familyMode && debt.balance>BigDecimal.ZERO)Secondary("Pagar solo esta deuda"){go("form/payment?id=${debt.id}")}
                }
            }
        }
        if(months.isEmpty())item{Empty(when(filter){"paid"->"Todavía no hay meses cubiertos";"pending"->"No tienes meses pendientes";else->"No hay gastos registrados"})}
        item{Text("Cubierto incluye pagos aprobados, créditos aplicados y coberturas del encargado. Los gastos aún no publicados no forman parte del saldo.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
