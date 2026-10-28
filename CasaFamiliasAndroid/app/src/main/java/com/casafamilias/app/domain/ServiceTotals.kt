package com.casafamilias.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/** Uses whole invoice amounts. Historical averages never reduce a family's share. */
data class ServiceTotals(
    val invoices: BigDecimal,
    val discounts: BigDecimal,
    val toShare: BigDecimal,
    val shares: List<BigDecimal>,
    val excessiveDiscount: Boolean,
) {
    val regularShare: BigDecimal
        get() = shares.firstOrNull() ?: BigDecimal.ZERO

    val finalShare: BigDecimal
        get() = shares.lastOrNull() ?: BigDecimal.ZERO
}

object ServiceCalculation {
    val services = listOf("water", "electricity", "gas", "wifi")

    fun calculate(
        amounts: Map<String, BigDecimal>,
        discounts: Map<String, BigDecimal> = emptyMap(),
        families: Int = 6,
    ): ServiceTotals {
        require(families > 0) { "No hay familias para repartir el gasto." }
        val bills =
            services.map { (amounts[it] ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP) }
        val reductions =
            services.map { (discounts[it] ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP) }
        require(bills.all { it >= BigDecimal.ZERO } && reductions.all { it >= BigDecimal.ZERO })
        val gross = bills.fold(BigDecimal.ZERO, BigDecimal::add)
        val discount = reductions.fold(BigDecimal.ZERO, BigDecimal::add)
        val invalid = bills.zip(reductions).any { (bill, reduction) -> reduction > bill }
        val net =
            bills.zip(reductions).fold(BigDecimal.ZERO) { sum, (bill, reduction) ->
                sum + (bill - reduction).max(BigDecimal.ZERO)
            }
        // Match the existing accounting convention; assign the rounding difference once.
        val regular = net.divide(BigDecimal(families), 2, RoundingMode.HALF_UP)
        // For very small bills use truncation, so the final share can never be negative.
        val base =
            if (regular * BigDecimal(families - 1) > net)
                net.divide(BigDecimal(families), 2, RoundingMode.DOWN)
            else regular
        val shares =
            List(families) { index ->
                if (index == families - 1) net - base * BigDecimal(families - 1) else base
            }
        return ServiceTotals(gross, discount, net, shares, invalid)
    }
}

fun Snapshot.servicePeriods(): List<String> =
    rows("monthly_basic_services")
        .mapNotNull {
            runCatching { YearMonth.parse(it.text("month_start").take(7)).toString() }.getOrNull()
        }
        .distinct()
        .sorted()

fun Snapshot.servicePreview(values: Map<String, String>): ServiceTotals {
    val reference = values["reference"] == "true"
    val amounts =
        ServiceCalculation.services.associateWith {
            Amounts.parse(values[it].orEmpty()) ?: BigDecimal.ZERO
        }
    val discounts =
        ServiceCalculation.services.associateWith { service ->
            if (reference) BigDecimal.ZERO
            else {
                val pending =
                    rows("utility_credits")
                        .filter {
                            it.text("service") == service &&
                                it.text("applied_month_start").isBlank()
                        }
                        .fold(BigDecimal.ZERO) { sum, credit -> sum + credit.money("amount") }
                pending + (Amounts.parse(values["other_$service"].orEmpty()) ?: BigDecimal.ZERO)
            }
        }
    return ServiceCalculation.calculate(amounts, discounts, rows("families").size.coerceAtLeast(1))
}
