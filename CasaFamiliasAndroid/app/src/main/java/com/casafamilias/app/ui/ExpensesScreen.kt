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
fun ExpensesScreen(s: Snapshot, go: Go) {
    val periods = s.servicePeriods()
    var selected by rememberSaveable { mutableStateOf("all") }
    var section by rememberSaveable { mutableStateOf("services") }
    val choices =
        listOf(Choice("all", "Todos los meses")) +
            (periods + s.rows("expenses").map { it.text("expense_date").take(7) })
                .filter { it.length == 7 }
                .distinct()
                .sortedDescending()
                .map { Choice(it, Dates.monthLabel(it)) }
    val services =
        s.rows("monthly_basic_services")
            .filter { selected == "all" || it.text("month_start").startsWith(selected) }
            .sortedByDescending { it.text("month_start") }

    ContentList {
        item { Title("Gastos", "Facturas de la casa, ordenadas por mes") }
        item {
            Panel(Modifier.testTag("registered-services")) {
                Text("Servicios registrados hasta", style = MaterialTheme.typography.labelLarge)
                Text(
                    periods.lastOrNull()?.let(Dates::monthLabel) ?: "Todavía no hay registros",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${periods.size} meses registrados. Un mes sin registro todavía no tiene un cobro publicado.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Action(
                when (section) {
                    "external" -> "Registrar gasto extraordinario"
                    "credits" -> "Registrar descuento"
                    else -> "Registrar servicios del mes"
                }
            ) {
                go(
                    when (section) {
                        "external" -> "form/expense"
                        "credits" -> "form/credit"
                        else -> "form/services"
                    }
                )
            }
        }
        item { Secondary("Ver deudas de las familias") { go("families") } }
        item {
            Filters(
                listOf(
                    Choice("services", "Facturas"),
                    Choice("external", "Otros gastos"),
                    Choice("credits", "Descuentos"),
                    Choice("restaurant", "Restaurante"),
                ),
                section,
                { section = it },
            )
        }
        item {
            Input(
                Field(
                    "month",
                    "Mes que quieres consultar",
                    InputType.CHOICE,
                    choices = choices,
                    required = true,
                ),
                selected,
            ) {
                selected = it
            }
        }
        when (section) {
            "services" -> {
                items(services, key = { it.id }) { ServiceInvoiceCard(s, it) }
                if (services.isEmpty()) item { Empty("No hay facturas para esta selección") }
            }
            "external" -> {
                val rows =
                    s.rows("expenses")
                        .filter {
                            it.text("category") == "external" &&
                                (selected == "all" || it.text("expense_date").startsWith(selected))
                        }
                        .sortedByDescending { it.text("expense_date") }
                items(rows, key = { it.id }) { row ->
                    NavRow(
                        "expenses",
                        row.text("concept"),
                        Amounts.format(row.money("total_amount")) +
                            " · " +
                            Dates.label(row.text("expense_date")),
                    ) {
                        go("expense-detail/${row.id}")
                    }
                }
                if (rows.isEmpty()) item { Empty("No hay otros gastos para esta selección") }
            }
            "credits" -> {
                val rows =
                    s.rows("utility_credits").filter {
                        selected == "all" || it.text("applied_month_start").startsWith(selected)
                    }
                items(rows, key = { it.id }) { row ->
                    Panel {
                        Text(
                            "Descuento de ${serviceLabel(row.text("service")).lowercase()}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            Amounts.format(row.money("amount")),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(row.text("note"))
                        Text(
                            if (row.text("applied_month_start").isBlank())
                                "Disponible para el próximo registro"
                            else
                                "Usado en ${Dates.monthLabel(row.text("applied_month_start").take(7))}"
                        )
                        if (row.text("receipt_path").isNotBlank())
                            ImageReceiptButton(row.text("receipt_path"))
                    }
                }
                if (rows.isEmpty()) item { Empty("No hay descuentos para esta selección") }
            }
            else -> {
                item {
                    Text(
                        "El reparto nuevo usa las facturas completas. Aquí se conservan los registros anteriores del restaurante.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val rows =
                    s.rows("external_utility_debts")
                        .filter { selected == "all" || it.text("month_start").startsWith(selected) }
                        .sortedByDescending { it.text("month_start") }
                items(rows, key = { it.id }) { row ->
                    Panel {
                        Text(
                            serviceLabel(row.text("service")) +
                                " · " +
                                Dates.monthLabel(row.text("month_start").take(7)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        DataLine(
                            "Falta pagar",
                            Amounts.format(
                                (row.money("amount_due") - row.money("amount_paid")).max(
                                    BigDecimal.ZERO
                                )
                            ),
                        )
                        if (row.money("amount_due") > row.money("amount_paid")) {
                            Secondary("Registrar aporte recibido") {
                                go("form/external-paid?id=${row.id}")
                            }
                        }
                    }
                }
                if (rows.isEmpty()) item { Empty("No hay registros del restaurante") }
            }
        }
    }
}

@Composable
private fun ServiceInvoiceCard(s: Snapshot, row: Record) {
    var detail by rememberSaveable(row.id) { mutableStateOf(false) }
    val bills = ServiceCalculation.services.associateWith { row.money(it + "_amount") }
    val gross = bills.values.fold(BigDecimal.ZERO, BigDecimal::add)
    val reference = row.flag("is_reference_only") || row.flag("reference_only")
    val count = row.number("family_count")?.toInt() ?: s.rows("families").size
    val expenseId = row.text("expense_id").ifBlank { row.text("monthly_expense_id") }
    val shares = s.rows("expense_shares").filter { it.text("expense_id") == expenseId }
    Panel(Modifier.testTag("services-month-${row.text("month_start").take(7)}")) {
        Text(
            Dates.monthLabel(row.text("month_start").take(7)),
            style = MaterialTheme.typography.titleLarge,
        )
        Text("Total de las facturas", style = MaterialTheme.typography.labelLarge)
        Text(Amounts.format(gross), style = MaterialTheme.typography.headlineMedium)
        if (reference) Text("Solo referencia: no genera deuda.")
        else {
            Text(
                "Total repartido: ${Amounts.format(row.money("family_total_amount"))} entre $count cuentas."
            )
            if (shares.isNotEmpty())
                Text(
                    "Importe por cuenta: ${Amounts.format(shares.first().money("amount_due"))} aprox.",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (
                row.text("calculation_method") != "bill_total" &&
                    gross > row.money("family_total_amount")
            ) {
                Text(
                    "Este registro anterior tiene descuentos o un reparto por promedio. Abre el detalle para ver la diferencia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            "Pagar hasta ${Dates.label(row.text("due_date"))}",
            style = MaterialTheme.typography.bodySmall,
        )
        Secondary(if (detail) "Cerrar detalle de facturas" else "Ver facturas y cálculo") {
            detail = !detail
        }
        if (detail) {
            ServiceCalculation.services.forEach { service ->
                DataLine(serviceLabel(service), Amounts.format(bills.getValue(service)))
                val path = row.text(service + "_receipt_path")
                if (path.isNotBlank()) ImageReceiptButton(path, serviceLabel(service))
            }
            if (!reference) {
                HorizontalDivider()
                DataLine("Total de facturas", Amounts.format(gross))
                DataLine(
                    "Descuentos de agua y luz",
                    Amounts.format(
                        row.money("other_water_contribution") +
                            row.money("other_electricity_contribution")
                    ),
                )
                val otherCredits =
                    s.rows("utility_credits")
                        .filter {
                            it.text("applied_month_start") == row.text("month_start") &&
                                it.text("service") in listOf("gas", "wifi")
                        }
                        .fold(BigDecimal.ZERO) { sum, credit -> sum + credit.money("amount") }
                if (otherCredits > BigDecimal.ZERO)
                    DataLine("Descuentos de gas e internet", Amounts.format(otherCredits))
                val restaurant =
                    row.money("restaurant_water_amount") +
                        row.money("restaurant_electricity_amount")
                if (restaurant > BigDecimal.ZERO)
                    DataLine("Separado antes para restaurante", Amounts.format(restaurant))
                DataLine(
                    "Total repartido entre las familias",
                    Amounts.format(row.money("family_total_amount")),
                )
                shares
                    .sortedBy { it.number("family_id") }
                    .forEach { share ->
                        DataLine(
                            s.family(share.number("family_id")),
                            Amounts.format(share.money("amount_due")),
                        )
                    }
                Text(
                    "Las cuentas del encargado quedan cubiertas al pagar las facturas. Ese importe no se vuelve a cobrar a las otras familias.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun ServiceFormSummary(s: Snapshot, values: Map<String, String>) {
    val preview = runCatching { s.servicePreview(values) }.getOrNull() ?: return
    Panel(Modifier.testTag("service-calculation-preview")) {
        Text("Revisa la suma antes de guardar", style = MaterialTheme.typography.titleMedium)
        DataLine("Total de las cuatro facturas", Amounts.format(preview.invoices))
        if (values["reference"] == "true") Text("Referencia histórica: no se crea deuda.")
        else {
            DataLine("Descuentos registrados", Amounts.format(preview.discounts))
            DataLine("Total a repartir", Amounts.format(preview.toShare))
            Text(
                "Dividido entre ${preview.shares.size} cuentas: ${Amounts.format(preview.regularShare)} por cuenta, aproximadamente."
            )
            if (preview.finalShare != preview.regularShare)
                Text(
                    "La última cuenta recibe ${Amounts.format(preview.finalShare)} para conservar los centavos del total.",
                    style = MaterialTheme.typography.bodySmall,
                )
            if (preview.excessiveDiscount)
                Text(
                    "Hay un descuento mayor a su factura. Corrígelo antes de guardar.",
                    color = MaterialTheme.colorScheme.error,
                )
            Text(
                "No se descuenta un promedio histórico. Gas en 0 no suma ningún cobro.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
