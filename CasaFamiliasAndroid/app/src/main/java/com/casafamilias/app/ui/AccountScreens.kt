package com.casafamilias.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.casafamilias.app.domain.*
import java.math.BigDecimal

@Composable
fun AccountSummary(s: Snapshot, family: Long?) {
    val account = s.familyAccount(family)
    Panel {
        Text("Gastos registrados hasta", style = MaterialTheme.typography.labelLarge)
        Text(
            account.latestPublished?.let(Dates::monthLabel) ?: "Todavía sin registros",
            style = MaterialTheme.typography.titleLarge,
        )
        HorizontalDivider()
        Text("Te falta pagar", style = MaterialTheme.typography.labelLarge)
        Text(
            Amounts.format(account.balance),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            when {
                account.months.isEmpty() -> "Todavía no se publicaron gastos para esta familia."
                account.pending.isEmpty() -> "Al día en los meses registrados"
                account.pending.size == 1 -> "1 mes con saldo pendiente"
                else -> "${account.pending.size} meses con saldo pendiente"
            }
        )
        account.earliestPending?.let {
            Text(
                "Primero falta ${Dates.monthLabel(it)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (account.reviewingCount > 0)
            Text(
                "${account.reviewingCount} ${if (account.reviewingCount == 1) "comprobante por revisar" else "comprobantes por revisar"}. El saldo baja al aprobar el pago.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        if (s.credit(family) > BigDecimal.ZERO)
            DataLine("Saldo a favor disponible", Amounts.format(s.credit(family)))
        if (account.missingMonths.isNotEmpty()) {
            Text(
                "Sin registro: " +
                    account.missingMonths.take(3).joinToString { Dates.monthLabel(it) } +
                    if (account.missingMonths.size > 3) " y ${account.missingMonths.size - 3} más."
                    else ".",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Solo se cuentan los gastos publicados. Un mes sin registro no se considera pagado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        item { Title("Estado de cuenta", s.family(family)) }
        item { AccountSummary(s, family) }
        if (familyMode && account.balance > BigDecimal.ZERO)
            item { Action("Pagar mis deudas") { go("form/payment") } }
        item { Text("Tus deudas, mes por mes", style = MaterialTheme.typography.titleLarge) }
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
                Secondary(if (detail) "Cerrar detalle del mes" else "Ver cálculo de este mes") {
                    detail = !detail
                }
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
