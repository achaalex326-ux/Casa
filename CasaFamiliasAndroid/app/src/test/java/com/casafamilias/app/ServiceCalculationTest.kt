package com.casafamilias.app

import com.casafamilias.app.domain.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ServiceCalculationTest {
    private fun money(value: String) = BigDecimal(value)

    private fun amounts(
        water: String,
        electricity: String,
        gas: String = "0",
        wifi: String = "149",
    ) =
        mapOf(
            "water" to money(water),
            "electricity" to money(electricity),
            "gas" to money(gas),
            "wifi" to money(wifi),
        )

    private fun equal(expected: String, actual: BigDecimal) =
        assertEquals(0, money(expected).compareTo(actual))

    @Test
    fun sixWorksheetMonthsUseWholeInvoicesAndConserveEveryCent() {
        val months =
            listOf(
                listOf("36", "211.5", "396.50", "66.08"),
                listOf("24", "165.6", "338.60", "56.43"),
                listOf("32", "145.2", "326.20", "54.37"),
                listOf("36", "243.6", "428.60", "71.43"),
                listOf("36.5", "198", "383.50", "63.92"),
                listOf("36.5", "191.6", "377.10", "62.85"),
            )
        months.forEach { row ->
            val result = ServiceCalculation.calculate(amounts(row[0], row[1]))
            equal(row[2], result.invoices)
            equal(row[2], result.toShare)
            equal(row[3], result.regularShare)
            equal(row[2], result.shares.fold(BigDecimal.ZERO, BigDecimal::add))
            assertFalse(result.excessiveDiscount)
        }
    }

    @Test
    fun onlyExplicitDiscountsReduceTheTotal() {
        val result =
            ServiceCalculation.calculate(
                amounts("36", "211.5"),
                mapOf("water" to money("12"), "wifi" to money("5")),
            )
        equal("17", result.discounts)
        equal("379.5", result.toShare)
    }

    @Test
    fun discountGreaterThanItsServiceIsRejectedEvenWhenOtherBillsAreLarge() {
        val result =
            ServiceCalculation.calculate(amounts("0", "211.5"), mapOf("water" to money("1")))
        assertTrue(result.excessiveDiscount)
    }

    @Test
    fun smallBillsNeverAssignNegativeRoundingRemainders() {
        (0..100).forEach { cents ->
            val amount = BigDecimal(cents).movePointLeft(2)
            val result = ServiceCalculation.calculate(mapOf("water" to amount))
            assertTrue(result.shares.all { it >= BigDecimal.ZERO })
            assertEquals(0, amount.compareTo(result.shares.fold(BigDecimal.ZERO, BigDecimal::add)))
        }
    }

    @Test
    fun zeroGasAddsNoChargeAndOtherServicesStillSum() {
        val result = ServiceCalculation.calculate(amounts("36", "211.5"))
        equal("396.50", result.toShare)
        equal("0", ServiceCalculation.calculate(emptyMap()).toShare)
    }

    @Test
    fun previewCountsOnlyUnconsumedDiscountsAndIgnoresBaselines() {
        val snapshot =
            Snapshot(
                Profile("u", "Admin", "x@example.test", Role.ADMIN, null, null),
                mapOf(
                    "families" to (1..6).map { Record(json("id" to it)) },
                    "utility_baselines" to
                        listOf(Record(json("service" to "water", "base_amount" to 1))),
                    "utility_credits" to
                        listOf(
                            Record(json("service" to "water", "amount" to 10)),
                            Record(
                                json(
                                    "service" to "wifi",
                                    "amount" to 90,
                                    "applied_month_start" to "2026-01-01",
                                )
                            ),
                        ),
                ),
            )
        val values =
            mapOf(
                "water" to "36,00",
                "electricity" to "211,50",
                "gas" to "0",
                "wifi" to "149",
                "other_water" to "2",
            )
        equal("384.50", snapshot.servicePreview(values).toShare)
        equal("396.50", snapshot.servicePreview(values + ("reference" to "true")).toShare)
    }

    @Test
    fun latestRegisteredMonthIsBasedOnRecordsRatherThanTodaysDate() {
        val snapshot =
            Snapshot(
                Profile("u", "Admin", "x@example.test", Role.ADMIN, null, null),
                mapOf(
                    "monthly_basic_services" to
                        listOf("2026-06-01", "2026-01-01", "bad").map {
                            Record(json("month_start" to it))
                        }
                ),
            )
        assertEquals(listOf("2026-01", "2026-06"), snapshot.servicePeriods())
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotDivideWithoutFamilies() {
        ServiceCalculation.calculate(emptyMap(), families = 0)
    }
}
