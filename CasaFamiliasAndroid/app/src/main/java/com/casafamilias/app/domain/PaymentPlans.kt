package com.casafamilias.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

data class PaymentDebt(
    val shareId: String,
    val concept: String,
    val month: String,
    val expenseDate: String,
    val dueDate: String,
    val balance: BigDecimal,
) {
    val label
        get() =
            if (month.isNotBlank()) "${Dates.monthLabel(month.take(7))} · $concept"
            else "$concept · ${Dates.label(expenseDate)}"
}

data class PaymentAllocation(val debt: PaymentDebt, val amount: BigDecimal)

data class PaymentPreview(
    val amount: BigDecimal,
    val allocations: List<PaymentAllocation>,
    val credit: BigDecimal,
    val remainingDebt: BigDecimal,
) {
    val applied
        get() = allocations.fold(BigDecimal.ZERO) { sum, it -> sum + it.amount }
}

object PaymentPlans {
    fun preview(
        debts: List<PaymentDebt>,
        amount: BigDecimal,
        share: String? = null,
    ): PaymentPreview {
        val total = amount.setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO)
        var remaining = total
        val parts =
            debts
                .filter {
                    it.balance > BigDecimal.ZERO && (share.isNullOrBlank() || it.shareId == share)
                }
                .sortedWith(
                    compareBy<PaymentDebt> { it.month.ifBlank { it.expenseDate } }
                        .thenBy { it.dueDate }
                        .thenBy { it.shareId }
                )
                .mapNotNull { d ->
                    val applied = remaining.min(d.balance)
                    remaining -= applied
                    if (applied > BigDecimal.ZERO) PaymentAllocation(d, applied) else null
                }
        val applied = total - remaining
        return PaymentPreview(
            total,
            parts,
            remaining,
            (debts.fold(BigDecimal.ZERO) { a, d -> a + d.balance } - applied).max(BigDecimal.ZERO),
        )
    }
}

fun Snapshot.paymentDebts(family: Long?): List<PaymentDebt> {
    if (family == null || (profile.role == Role.FAMILY && family != profile.familyId))
        return emptyList()
    val records =
        if (profile.role == Role.FAMILY) rows("family_debts")
        else rows("expense_shares").filter { it.number("family_id") == family }
    return records
        .map { r ->
            val expense = find("expenses", r.text("expense_id"))
            val monthly =
                rows("monthly_basic_services").firstOrNull {
                    it.text("expense_id") == r.text("expense_id") ||
                        it.text("monthly_expense_id") == r.text("expense_id")
                }
            PaymentDebt(
                r.text("share_id").ifBlank { r.id },
                r.text("concept").ifBlank {
                    expense?.text("concept").orEmpty().ifBlank { "Deuda" }
                },
                r.text("month_start").ifBlank { monthly?.text("month_start").orEmpty() },
                r.text("expense_date").ifBlank { expense?.text("expense_date").orEmpty() },
                r.text("due_date").ifBlank { expense?.text("due_date").orEmpty() },
                if (profile.role == Role.FAMILY) r.money("balance") else shareBalance(r),
            )
        }
        .filter { it.balance > BigDecimal.ZERO }
}

/** A deposit is shown once; raw payments remain split for existing balance/report calculations. */
fun Snapshot.paymentEntries(): List<Record> =
    ownPayments().filter { it.text("batch_id").isBlank() } +
        rows("payment_batches").map { b ->
            val parts = rows("payments").filter { it.text("batch_id") == b.id }
            Record(
                org.json.JSONObject(b.json.toString()).apply {
                    put("batch", true)
                    if (!has("applied_amount"))
                        put(
                            "applied_amount",
                            parts
                                .filter { it.text("status") == "approved" }
                                .fold(BigDecimal.ZERO) { a, p -> a + p.money("applied_amount") },
                        )
                    if (!has("credit_amount"))
                        put(
                            "credit_amount",
                            parts
                                .filter { it.text("status") == "approved" }
                                .fold(BigDecimal.ZERO) { a, p -> a + p.money("credit_amount") },
                        )
                }
            )
        }
