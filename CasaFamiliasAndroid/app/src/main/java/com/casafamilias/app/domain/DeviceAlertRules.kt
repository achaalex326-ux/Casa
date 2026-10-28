package com.casafamilias.app.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class DeviceAlert(
    val key: String,
    val tag: String,
    val channel: String,
    val title: String,
    val body: String,
    val remember: Set<String> = setOf(key),
)

data class DeviceAlertPlan(val alerts: List<DeviceAlert>, val activeTags: Set<String>)

/** Pure rules; do not trust old server reminders as evidence of a current debt. */
object DeviceAlertRules {
    fun plan(
        s: Snapshot,
        seen: Set<String>,
        enabledAt: Instant,
        today: LocalDate,
    ): DeviceAlertPlan {
        val result = mutableListOf<DeviceAlert>()
        val active = mutableSetOf<String>()
        val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val householdIds = s.profile.familyIds.map { s.household(it) }.toSet()
        if (s.profile.role != Role.RESTAURANT)
            s.rows("cleaning_turns").forEach { t ->
                val date =
                    runCatching { LocalDate.parse(t.text("turn_date")) }.getOrNull()
                        ?: return@forEach
                if (date < monday || date >= monday.plusWeeks(1) || t.text("status") == "done")
                    return@forEach
                if (
                    s.profile.role != Role.ADMIN &&
                        s.household(t.number("family_id")) !in householdIds
                )
                    return@forEach
                val tag = "cleaning:${t.id}"
                active += tag
                val key = "$tag:$monday"
                if (key !in seen)
                    result +=
                        DeviceAlert(
                            key,
                            tag,
                            "cleaning",
                            if (s.profile.role == Role.ADMIN) "Limpieza de esta semana"
                            else "Esta semana te toca la limpieza",
                            "${s.family(t.number("family_id"))}: del ${Dates.label(monday.toString())} al ${Dates.label(monday.plusDays(6).toString())}. Revisa tus tareas en la aplicación.",
                        )
            }
        s.rows("alert_debts").forEach { d ->
            if (d.money("balance") <= BigDecimal.ZERO) return@forEach
            val id = d.text("share_id").ifBlank { d.id }
            if (id.isBlank()) return@forEach
            val tag = "debt:$id"
            active += tag
            val newKey = "new:$tag"
            val dailyKey = "due:$tag:$today"
            val due = runCatching { LocalDate.parse(d.text("due_date")) }.getOrNull()
            val isNew = newKey !in seen
            if (isNew || (due != null && due <= today.plusDays(3) && dailyKey !in seen)) {
                val title =
                    if (isNew) "Se registró una deuda"
                    else if (due != null && due < today) "Tienes un pago vencido"
                    else "Tu pago está por vencer"
                val body =
                    "${d.text("concept").ifBlank {"Servicios de casa"}}: saldo pendiente ${Amounts.format(d.money("balance"))}." +
                        (due?.let { " Vence ${Dates.label(it.toString())}." } ?: "")
                result +=
                    DeviceAlert(
                        if (isNew) newKey else dailyKey,
                        tag,
                        "payments",
                        title,
                        body,
                        setOf(newKey, dailyKey),
                    )
            }
        }
        s.rows("notifications")
            .filter { it.text("kind") !in setOf("due", "cleaning") && it.text("read_at").isBlank() }
            .forEach { n ->
                val date =
                    runCatching { Instant.parse(n.text("created_at")) }.getOrNull()
                        ?: return@forEach
                val key = "notice:${n.id}"
                if (date >= enabledAt && key !in seen && n.text("title").isNotBlank())
                    result +=
                        DeviceAlert(
                            key,
                            key,
                            if (n.text("kind") == "expense") "payments" else "general",
                            n.text("title"),
                            n.text("body"),
                        )
            }
        return DeviceAlertPlan(result.distinctBy { it.key }.take(8), active)
    }
}
