package com.casafamilias.app

import com.casafamilias.app.domain.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class PaymentPlansTest {
    private fun money(value: String, actual: BigDecimal) =
        assertEquals(value, 0, BigDecimal(value).compareTo(actual))

    private fun debts() =
        listOf(3, 1, 2).map {
            PaymentDebt(
                "s$it",
                "Servicios",
                "2030-0$it-01",
                "2030-0$it-01",
                "2030-0$it-20",
                BigDecimal("80.00"),
            )
        }

    private fun row(vararg pairs: Pair<String, Any?>) = Record(json(*pairs))

    @Test
    fun twoHundredCoversTwoMonthsAndPartOfThird() {
        val p = PaymentPlans.preview(debts(), BigDecimal("200"))
        assertEquals(listOf("s1", "s2", "s3"), p.allocations.map { it.debt.shareId })
        assertEquals(
            listOf("80.00", "80.00", "40.00"),
            p.allocations.map { it.amount.toPlainString() },
        )
        money("40", p.remainingDebt)
        money("0", p.credit)
        money("200", p.applied)
    }

    @Test
    fun exactThreeMonthPaymentClearsAllDebt() {
        val p = PaymentPlans.preview(debts(), BigDecimal("240"))
        money("0", p.credit)
        money("0", p.remainingDebt)
        money("240", p.applied)
    }

    @Test
    fun excessOnlyBecomesCreditAfterEverySelectedDebt() {
        val p = PaymentPlans.preview(debts(), BigDecimal("300"))
        money("240", p.applied)
        money("60", p.credit)
        money("0", p.remainingDebt)
    }

    @Test
    fun smallerDepositStillMakesPartialPayment() {
        val p = PaymentPlans.preview(debts(), BigDecimal("100"))
        assertEquals(2, p.allocations.size)
        money("20", p.allocations.last().amount)
        money("140", p.remainingDebt)
    }

    @Test
    fun priorPartialPaymentsAreRespected() {
        val ds = debts().map { if (it.shareId == "s1") it.copy(balance = BigDecimal("50")) else it }
        val p = PaymentPlans.preview(ds, BigDecimal("200"))
        money("50", p.allocations.first().amount)
        money("70", p.allocations.last().amount)
        money("10", p.remainingDebt)
    }

    @Test
    fun singleDebtModePreservesOtherMonths() {
        val p = PaymentPlans.preview(debts(), BigDecimal("100"), "s2")
        assertEquals(listOf("s2"), p.allocations.map { it.debt.shareId })
        money("80", p.applied)
        money("20", p.credit)
        money("160", p.remainingDebt)
    }

    @Test
    fun centsNeverUseFloatingPoint() {
        val ds = debts().take(2).map { it.copy(balance = BigDecimal("0.10")) }
        val p = PaymentPlans.preview(ds, BigDecimal("0.20"))
        money("0.20", p.applied)
        money("0", p.credit)
    }

    @Test
    fun paymentBatchShowsOnceWithoutChangingLedgerSum() {
        val child =
            (1..3).map {
                row(
                    "id" to "p$it",
                    "batch_id" to "b",
                    "family_id" to 1,
                    "amount" to if (it == 3) 40 else 80,
                    "applied_amount" to if (it == 3) 40 else 80,
                    "status" to "approved",
                )
            }
        val s =
            Snapshot(
                Profile("u", "Family", "x@example.test", Role.FAMILY, 1, null),
                mapOf(
                    "payments" to child,
                    "payment_batches" to
                        listOf(
                            row(
                                "id" to "b",
                                "family_id" to 1,
                                "amount" to 200,
                                "status" to "approved",
                            )
                        ),
                ),
            )
        assertEquals(3, s.ownPayments().size)
        assertEquals(1, s.paymentEntries().size)
        money("200", s.paymentEntries().single().money("applied_amount"))
        money("200", s.ownPayments().fold(BigDecimal.ZERO) { a, p -> a + p.money("amount") })
    }

    @Test
    fun cleaningAliasNeverMergesFinancialPaymentFamilies() {
        val s =
            Snapshot(
                Profile("u", "Payer", "x@example.test", Role.PAYER, 1, 2),
                mapOf(
                    "expense_shares" to
                        listOf(
                            row("id" to "a", "family_id" to 1, "amount_due" to 80),
                            row("id" to "b", "family_id" to 2, "amount_due" to 90),
                        ),
                    "cleaning_family_aliases" to listOf(row("family_id" to 2, "household_id" to 1)),
                ),
            )
        assertEquals(listOf("a"), s.paymentDebts(1).map { it.shareId })
        assertEquals(listOf("b"), s.paymentDebts(2).map { it.shareId })
    }

    @Test
    fun noDebtAndZeroAmountDoNotInventAllocations() {
        assertTrue(PaymentPlans.preview(emptyList(), BigDecimal("200")).allocations.isEmpty())
        assertTrue(PaymentPlans.preview(debts(), BigDecimal.ZERO).allocations.isEmpty())
    }
}
