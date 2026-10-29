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

@Composable
fun AccountSummary(s: Snapshot, family: Long?) {
    val account = s.familyAccount(family)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeBalance(s, family)
        if (account.reviewingCount > 0)
            Text(
                "${account.reviewingCount} pagos por revisar. Se descuentan cuando se aprueban.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        if (s.credit(family) > BigDecimal.ZERO)
            Text(
                "Tienes ${Amounts.format(s.credit(family))} a favor.",
                style = MaterialTheme.typography.bodySmall,
            )
        Text(
            if (account.pending.isEmpty()) "Al día en los meses registrados"
            else
                "${account.pending.size} ${if(account.pending.size==1)"mes con saldo pendiente" else "meses con saldo pendiente"}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (account.missingMonths.isNotEmpty())
            Text(
                "Hay meses sin registro. No se cuentan como pagados.",
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

@Composable
private fun MonthStatus(month: AccountMonth) {
    Badge(
        when (month.status) {
            "Al día" -> "paid"
            "Pago parcial" -> "partial"
            else -> "pending"
        }
    )
}

@Composable
fun FamilyAccountScreen(s: Snapshot, go: Go, family: Long? = s.profile.familyId) {
    val account = s.familyAccount(family)
    val familyMode = s.profile.role == Role.FAMILY
    var filter by
        rememberSaveable(family) {
            mutableStateOf(if (account.pending.isEmpty()) "all" else "pending")
        }
    val months =
        account.months
            .filter {
                filter == "all" ||
                    if (filter == "paid") it.balance <= BigDecimal.ZERO
                    else it.balance > BigDecimal.ZERO
            }
            .let {
                if (filter == "pending") it else it.sortedByDescending { month -> month.period }
            }

    ContentList {
        item { Title("Mis deudas", s.family(family)) }
        item { AccountSummary(s, family) }
        if (familyMode && account.balance > BigDecimal.ZERO)
            item { Action("Pagar") { go("form/payment") } }
        item { Text("Meses registrados", style = MaterialTheme.typography.titleLarge) }
        item {
            Filters(
                listOf(
                    Choice("pending", "Por pagar"),
                    Choice("paid", "Al día"),
                    Choice("all", "Todos"),
                ),
                filter,
                { filter = it },
            )
        }
        items(months, key = { it.period }) { month ->
            var detail by rememberSaveable(family, month.period) { mutableStateOf(false) }
            Panel(Modifier.testTag("account-month-${month.period}")) {
                Text(month.label, style = MaterialTheme.typography.titleLarge)
                MonthStatus(month)
                Text("Falta pagar", style = MaterialTheme.typography.labelLarge)
                Text(Amounts.format(month.balance), style = MaterialTheme.typography.headlineMedium)
                if (month.reviewing > BigDecimal.ZERO)
                    Text(
                        "${Amounts.format(month.reviewing)} en revisión. Todavía no descontado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                Secondary(if (detail) "Cerrar detalle" else "Ver detalle") { detail = !detail }
                if (detail) {
                    DataLine("Importe asignado", Amounts.format(month.assigned))
                    DataLine("Pagos y coberturas aplicados", Amounts.format(month.covered))
                    month.debts.forEach { debt ->
                        HorizontalDivider()
                        Text(debt.concept, style = MaterialTheme.typography.titleSmall)
                        DataLine("Todavía falta", Amounts.format(debt.balance))
                        if (debt.dueDate.isNotBlank())
                            Text(
                                "Pagar hasta ${Dates.label(debt.dueDate)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        if (familyMode && debt.balance > BigDecimal.ZERO) {
                            Secondary("Pagar solo esta deuda") { go("form/payment?id=${debt.id}") }
                        }
                    }
                }
            }
        }
        if (months.isEmpty())
            item {
                Empty(
                    when (filter) {
                        "paid" -> "Todavía no hay meses cubiertos"
                        "pending" -> "No tienes meses pendientes"
                        else -> "No hay gastos registrados"
                    }
                )
            }
        item {
            Secondary("Ver comprobantes") {
                go(if (familyMode) "payments" else "payments-family/$family")
            }
        }
        if (!familyMode && s.profile.role == Role.ADMIN)
            item { Secondary("Editar nombre de familia") { go("form/family-name?id=$family") } }
    }
}
