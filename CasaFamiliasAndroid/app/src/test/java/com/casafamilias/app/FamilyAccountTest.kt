package com.casafamilias.app

import com.casafamilias.app.domain.*
import java.math.BigDecimal
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class FamilyAccountTest {
    private fun row(vararg p: Pair<String, Any?>) = Record(json(*p))

    private fun debt(id: String, month: String, due: Int = 80, paid: Int = 0) =
        row(
            "share_id" to id,
            "expense_id" to "e$id",
            "concept" to "Servicios de la casa",
            "month_start" to "$month-01",
            "expense_date" to "$month-01",
            "amount_due" to due,
            "amount_paid" to paid,
            "balance" to due - paid,
        )

    private fun family(vararg debts: Record) =
        Snapshot(
            Profile("u", "Family", "x@example.test", Role.FAMILY, 1, null),
            mapOf("family_debts" to debts.toList()),
        )

    private fun money(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test
    fun monthsShowLatestIssuedAndFirstUnpaidWithoutAssumingCurrentMonth() {
        val a =
            family(debt("a", "2026-08", 80, 80), debt("b", "2026-09", 80, 40), debt("c", "2026-10"))
                .familyAccount(1)
        assertEquals("2026-10", a.latestPublished)
        assertEquals("2026-09", a.earliestPending)
        assertEquals(2, a.pending.size)
        money("120", a.balance)
        assertEquals(listOf("Al día", "Pago parcial", "Pendiente"), a.months.map { it.status })
    }

    @Test
    fun missingMonthIsNotAssumedPaid() {
        val a = family(debt("a", "2026-08", 80, 80), debt("b", "2026-10", 80, 80)).familyAccount(1)
        assertEquals(listOf("2026-09"), a.missingMonths)
        assertTrue(a.pending.isEmpty())
        assertEquals(2, a.months.size)
    }

    @Test
    fun differentDebtsInSameMonthAreCombined() {
        val a = family(debt("a", "2026-09", 80, 30), debt("b", "2026-09", 20, 10)).familyAccount(1)
        assertEquals(1, a.months.size)
        money("100", a.months.single().assigned)
        money("40", a.months.single().covered)
        money("60", a.balance)
    }

    @Test
    fun onePendingBatchIsAllocatedToAllThreeMonthsWithoutReducingBalance() {
        val s = family(debt("a", "2026-08"), debt("b", "2026-09"), debt("c", "2026-10"))
        val b =
            row(
                "id" to "batch",
                "family_id" to 1,
                "status" to "pending",
                "amount" to 200,
                "planned_allocation" to
                    JSONArray(
                        listOf(
                            json("share_id" to "a", "amount" to 80),
                            json("share_id" to "b", "amount" to 80),
                            json("share_id" to "c", "amount" to 40),
                        )
                    ),
            )
        val anchor =
            row(
                "id" to "batch",
                "family_id" to 1,
                "share_id" to "a",
                "batch_id" to "batch",
                "status" to "pending",
                "amount" to 200,
            )
        val a =
            s.copy(
                    tables =
                        s.tables +
                            mapOf("payment_batches" to listOf(b), "payments" to listOf(anchor))
                )
                .familyAccount(1)
        assertEquals(1, a.reviewingCount)
        money("240", a.balance)
        assertEquals(listOf("80", "80", "40"), a.months.map { it.reviewing.toPlainString() })
    }

    @Test
    fun approvedAndRejectedBatchesAreNotCountedAsPending() {
        val s = family(debt("a", "2026-09", 80, 40))
        val b =
            row(
                "id" to "b",
                "family_id" to 1,
                "status" to "approved",
                "planned_allocation" to JSONArray(listOf(json("share_id" to "a", "amount" to 40))),
            )
        val a =
            s.copy(
                    tables =
                        s.tables +
                            ("payment_batches" to
                                listOf(
                                    b,
                                    b.copy(
                                        json("id" to "c", "family_id" to 1, "status" to "rejected")
                                    ),
                                ))
                )
                .familyAccount(1)
        assertEquals(0, a.reviewingCount)
        money("0", a.months.single().reviewing)
    }

    @Test
    fun oldPendingPaymentIsCountedOnce() {
        val s = family(debt("a", "2026-09"))
        val a =
            s.copy(
                    tables =
                        s.tables +
                            ("payments" to
                                listOf(
                                    row(
                                        "id" to "p",
                                        "share_id" to "a",
                                        "family_id" to 1,
                                        "status" to "pending",
                                        "amount" to 40,
                                    )
                                ))
                )
                .familyAccount(1)
        assertEquals(1, a.reviewingCount)
        money("40", a.months.single().reviewing)
        money("80", a.balance)
    }

    @Test
    fun familyCannotRequestAnotherAccountingFamilyEvenWithCleaningAlias() {
        val s =
            family(debt("a", "2026-09"))
                .copy(
                    tables =
                        family(debt("a", "2026-09")).tables +
                            ("cleaning_family_aliases" to
                                listOf(row("family_id" to 2, "household_id" to 1)))
                )
        assertTrue(s.familyAccount(2).months.isEmpty())
    }

    @Test
    fun staffUsesApprovedAppliedMoneyAndDoesNotSubtractPendingMoney() {
        val s =
            Snapshot(
                Profile("u", "Payer", "x@example.test", Role.PAYER, 1, 2),
                mapOf(
                    "expense_shares" to
                        listOf(
                            row(
                                "id" to "s",
                                "family_id" to 2,
                                "expense_id" to "e",
                                "amount_due" to 80,
                            )
                        ),
                    "expenses" to
                        listOf(
                            row(
                                "id" to "e",
                                "expense_date" to "2026-09-01",
                                "concept" to "Servicios",
                            )
                        ),
                    "payments" to
                        listOf(
                            row(
                                "id" to "p",
                                "family_id" to 2,
                                "share_id" to "s",
                                "status" to "approved",
                                "applied_amount" to 30,
                            ),
                            row(
                                "id" to "q",
                                "family_id" to 2,
                                "share_id" to "s",
                                "status" to "pending",
                                "amount" to 50,
                            ),
                        ),
                ),
            )
        val a = s.familyAccount(2)
        money("50", a.balance)
        money("30", a.months.single().covered)
        assertEquals("Pago parcial", a.months.single().status)
    }

    @Test
    fun zeroAssignedDebtIsCalledUpToDateWithoutInventingCashPayment() {
        val a = family(debt("a", "2026-09", 0, 0)).familyAccount(1)
        assertEquals("Al día", a.months.single().status)
        money("0", a.months.single().covered)
    }

    @Test
    fun pendingReceiptMonthLabelsAreDerivedFromPlan() {
        val s = family(debt("a", "2026-09"))
        val b =
            row(
                "id" to "b",
                "batch" to true,
                "status" to "pending",
                "planned_allocation" to JSONArray(listOf(json("share_id" to "a", "amount" to 80))),
            )
        assertEquals(listOf("2026-09"), s.paymentPeriods(b))
    }

    @Test
    fun paymentDateDoesNotReplaceTheMonthBeingPaid() {
        val s = family(debt("a", "2026-07"))
        val p =
            row(
                "id" to "p",
                "share_id" to "a",
                "paid_at" to "2026-10-28",
                "status" to "approved",
                "applied_amount" to 40,
            )
        assertEquals(listOf("2026-07"), s.paymentPeriods(p))
    }

    @Test
    fun emptyAccountDoesNotClaimMonthsPaid() {
        val a = family().familyAccount(1)
        assertNull(a.latestPublished)
        assertNull(a.earliestPending)
        assertTrue(a.months.isEmpty())
        assertTrue(a.missingMonths.isEmpty())
    }

    @Test
    fun creditOnlyPaymentDoesNotClaimToCoverAnyMonth() {
        val s = family(debt("a", "2026-07", 80, 80))
        val p =
            row(
                "id" to "p",
                "share_id" to "a",
                "status" to "approved",
                "applied_amount" to 0,
                "credit_amount" to 200,
            )
        assertTrue(s.paymentPeriods(p).isEmpty())
    }
}
