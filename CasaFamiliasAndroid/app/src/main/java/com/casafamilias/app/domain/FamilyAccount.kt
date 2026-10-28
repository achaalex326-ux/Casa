package com.casafamilias.app.domain

import java.math.BigDecimal
import java.time.YearMonth

data class AccountDebt(
    val id: String,
    val concept: String,
    val period: String,
    val assigned: BigDecimal,
    val covered: BigDecimal,
    val balance: BigDecimal,
    val dueDate: String,
    val reviewing: BigDecimal,
)

data class AccountMonth(val period: String, val debts: List<AccountDebt>) {
    val assigned
        get() = debts.fold(BigDecimal.ZERO) { a, d -> a + d.assigned }

    val covered
        get() = debts.fold(BigDecimal.ZERO) { a, d -> a + d.covered }

    val balance
        get() = debts.fold(BigDecimal.ZERO) { a, d -> a + d.balance }

    val reviewing
        get() = debts.fold(BigDecimal.ZERO) { a, d -> a + d.reviewing }

    val label
        get() = if (period.isBlank()) "Sin mes identificado" else Dates.monthLabel(period)

    val status
        get() =
            when {
                balance <= BigDecimal.ZERO -> "Al día"
                covered > BigDecimal.ZERO -> "Pago parcial"
                else -> "Pendiente"
            }
}

data class FamilyAccount(val months: List<AccountMonth>, val reviewingCount: Int) {
    val pending
        get() = months.filter { it.balance > BigDecimal.ZERO }

    val latestPublished
        get() = months.map { it.period }.filter { it.isNotBlank() }.maxOrNull()

    val earliestPending
        get() = pending.map { it.period }.filter { it.isNotBlank() }.minOrNull()

    val balance
        get() = months.fold(BigDecimal.ZERO) { a, m -> a + m.balance }

    val missingMonths: List<String>
        get() {
            val known = months.map { it.period }.filter { it.isNotBlank() }.toSet()
            if (known.size < 2) return emptyList()
            var period = YearMonth.parse(known.min())
            val last = YearMonth.parse(known.max())
            val missing = mutableListOf<String>()
            while (period < last) {
                if (period.toString() !in known) missing += period.toString()
                period = period.plusMonths(1)
            }
            return missing
        }
}

private fun validMonth(value: String) =
    runCatching { YearMonth.parse(value.take(7)).toString() }.getOrDefault("")

fun Snapshot.debtPeriod(shareId: String): String {
    val portal = rows("family_debts").firstOrNull { it.text("share_id") == shareId }
    val share = find("expense_shares", shareId)
    val expenseId =
        portal?.text("expense_id").orEmpty().ifBlank { share?.text("expense_id").orEmpty() }
    val monthly =
        rows("monthly_basic_services")
            .filter {
                expenseId.isNotBlank() &&
                    (it.text("expense_id") == expenseId ||
                        it.text("monthly_expense_id") == expenseId)
            }
            .minByOrNull { it.text("month_start") }
    return validMonth(portal?.text("month_start").orEmpty())
        .ifBlank { validMonth(monthly?.text("month_start").orEmpty()) }
        .ifBlank { validMonth(portal?.text("expense_date").orEmpty()) }
        .ifBlank { validMonth(find("expenses", expenseId)?.text("expense_date").orEmpty()) }
}

fun Snapshot.familyAccount(family: Long?): FamilyAccount {
    if (
        family == null ||
            profile.role == Role.RESTAURANT ||
            (profile.role == Role.FAMILY && family != profile.familyId)
    )
        return FamilyAccount(emptyList(), 0)
    val payments = rows("payments").filter { it.number("family_id") == family }
    val batches =
        rows("payment_batches").filter {
            it.number("family_id") == family && it.text("status") == "pending"
        }
    val inReview = mutableMapOf<String, BigDecimal>()
    payments
        .filter { it.text("status") == "pending" && it.text("batch_id").isBlank() }
        .forEach { p ->
            val key = p.text("share_id")
            inReview[key] = (inReview[key] ?: BigDecimal.ZERO) + p.money("amount")
        }
    batches.forEach { b ->
        b.json.optJSONArray("planned_allocation")?.records().orEmpty().forEach { p ->
            val key = p.text("share_id")
            inReview[key] = (inReview[key] ?: BigDecimal.ZERO) + p.money("amount")
        }
    }
    val records =
        if (profile.role == Role.FAMILY) rows("family_debts")
        else rows("expense_shares").filter { it.number("family_id") == family }
    val debts =
        records.map { r ->
            val id = r.text("share_id").ifBlank { r.id }
            val expense = find("expenses", r.text("expense_id"))
            AccountDebt(
                id,
                r.text("concept").ifBlank {
                    expense?.text("concept").orEmpty().ifBlank { "Gasto compartido" }
                },
                debtPeriod(id),
                r.money("amount_due"),
                if (profile.role == Role.FAMILY) r.money("amount_paid")
                else
                    payments
                        .filter { it.text("share_id") == id && it.text("status") == "approved" }
                        .fold(BigDecimal.ZERO) { a, p -> a + p.money("applied_amount") },
                if (profile.role == Role.FAMILY) r.money("balance") else shareBalance(r),
                r.text("due_date").ifBlank { expense?.text("due_date").orEmpty() },
                inReview[id] ?: BigDecimal.ZERO,
            )
        }
    val count =
        payments.count { it.text("status") == "pending" && it.text("batch_id").isBlank() } +
            batches.size
    return FamilyAccount(
        debts
            .groupBy { it.period }
            .map { (period, items) -> AccountMonth(period, items.sortedBy { it.dueDate }) }
            .sortedBy { it.period },
        count,
    )
}

fun Snapshot.paymentPeriods(entry: Record): List<String> {
    val periods =
        when {
            entry.flag("batch") && entry.text("status") != "approved" ->
                entry.json.optJSONArray("planned_allocation")?.records().orEmpty().map {
                    validMonth(it.text("month_start"))
                        .ifBlank { validMonth(it.text("expense_date")) }
                        .ifBlank { debtPeriod(it.text("share_id")) }
                }
            entry.flag("batch") ->
                rows("payments")
                    .filter {
                        it.text("batch_id") == entry.id &&
                            it.money("applied_amount") > BigDecimal.ZERO
                    }
                    .map { debtPeriod(it.text("share_id")) }
            else ->
                if (
                    entry.text("status") == "approved" &&
                        entry.money("applied_amount") <= BigDecimal.ZERO
                )
                    emptyList()
                else listOf(debtPeriod(entry.text("share_id")))
        }
    return periods.filter { it.isNotBlank() }.distinct().sorted()
}
