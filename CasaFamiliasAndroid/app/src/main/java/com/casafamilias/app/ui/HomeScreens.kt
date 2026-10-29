package com.casafamilias.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role as SemanticRole
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casafamilias.app.domain.*
import java.math.BigDecimal

/** One whole row is a button. No small trailing arrow or hidden tap target. */
@Composable
fun TaskButton(
    title: String,
    description: String,
    icon: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier =
            Modifier.fillMaxWidth().heightIn(min = 80.dp).semantics { role = SemanticRole.Button },
        shape = RoundedCornerShape(20.dp),
        color =
            if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor =
            if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (primary) Glyph(icon, modifier = Modifier.size(28.dp)) else Tile(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun HomeScreen(s: Snapshot, go: Go) {
    val profile = s.profile
    val pending = s.paymentEntries().count { it.text("status") == "pending" }
    val ownTurn =
        s.rows("cleaning_turns")
            .filter {
                it.text("status") == "pending" && s.representsCleaning(it.number("family_id"))
            }
            .minByOrNull { it.text("turn_date") }
    ContentList {
        item {
            Title(
                if (profile.role == Role.FAMILY) s.family(profile.familyId)
                else "Hola, ${profile.name.split(' ').firstOrNull().orEmpty()}",
                "¿Qué necesitas hacer hoy?",
            )
        }
        when (profile.role) {
            Role.FAMILY -> {
                item { HomeBalance(s, profile.familyId) }
                item {
                    TaskButton(
                        if (s.familyAccount(profile.familyId).balance > BigDecimal.ZERO) "Pagar"
                        else "Ver mis pagos",
                        if (s.familyAccount(profile.familyId).balance > BigDecimal.ZERO)
                            "Subir una foto del comprobante"
                        else "Consultar comprobantes y estados",
                        "payments",
                        primary = true,
                    ) {
                        go(
                            if (s.familyAccount(profile.familyId).balance > BigDecimal.ZERO)
                                "form/payment"
                            else "payments"
                        )
                    }
                }
                item {
                    TaskButton("Ver mis deudas", "Lo que falta pagar en cada mes", "expenses") {
                        go("dues")
                    }
                }
                item {
                    TaskButton("Mis pagos", "Ver si mi comprobante fue aprobado", "check") {
                        go("payments")
                    }
                }
                item {
                    if (ownTurn != null)
                        Panel {
                            Text(
                                "Tu próxima limpieza",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                Dates.weekLabel(ownTurn.text("turn_date")),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Secondary("Ver turno y tareas") { go("turn/${ownTurn.id}") }
                        }
                    else
                        TaskButton("Limpieza", "Consultar mi semana y mis tareas", "cleaning") {
                            go("cleaning")
                        }
                }
            }
            Role.PAYER,
            Role.ADMIN -> {
                item { PublishedThrough(s.servicePeriods().lastOrNull()) }
                item {
                    TaskButton(
                        "Registrar facturas",
                        "Agua, luz, gas e internet",
                        "expenses",
                        primary = true,
                    ) {
                        go("form/services")
                    }
                }
                item {
                    TaskButton(
                        "Revisar pagos",
                        if (pending == 0) "No hay comprobantes por revisar"
                        else "$pending comprobantes por revisar",
                        "payments",
                    ) {
                        go("payments")
                    }
                }
                item {
                    TaskButton("Ver deudas", "Consultar cuánto debe cada familia", "families") {
                        go("families")
                    }
                }
                item {
                    val turn = s.cleaningWeek().firstOrNull()
                    TaskButton(
                        "Limpieza esta semana",
                        turn?.let { s.family(s.household(it.number("family_id"))) }
                            ?: "Consulta las semanas de todas las familias",
                        "cleaning",
                    ) {
                        go(turn?.let { "turn/${it.id}" } ?: "cleaning")
                    }
                }
                item {
                    TaskButton(
                        "Calendario de todas las familias",
                        "Consultar semanas anteriores y próximas",
                        "cleaning",
                    ) {
                        go("cleaning")
                    }
                }
            }
            Role.RESTAURANT -> {
                val debts = s.rows("external_utility_debts")
                item {
                    Hero(
                        "Te falta pagar",
                        debts.fold(BigDecimal.ZERO) { sum, r ->
                            sum +
                                (r.money("amount_due") - r.money("amount_paid")).max(
                                    BigDecimal.ZERO
                                )
                        },
                        "Agua y luz",
                    )
                }
                items(debts) { row ->
                    Panel {
                        Text(
                            serviceLabel(row.text("service")),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        DataLine("Mes", Dates.monthLabel(row.text("month_start").take(7)))
                        DataLine(
                            "Falta pagar",
                            Amounts.format(
                                (row.money("amount_due") - row.money("amount_paid")).max(
                                    BigDecimal.ZERO
                                )
                            ),
                        )
                    }
                }
                if (debts.isEmpty()) item { Empty("No tienes deudas registradas") }
            }
        }
    }
}

@Composable
fun PublishedThrough(period: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("published-through"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph("date", tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Registrado hasta", style = MaterialTheme.typography.labelLarge)
                Text(
                    period?.let(Dates::monthLabel) ?: "Todavía sin registros",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
fun HomeBalance(s: Snapshot, family: Long?) {
    val account = s.familyAccount(family)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (account.balance > BigDecimal.ZERO) Color(0xFF192D50) else Color(0xFF087F5B),
        contentColor = Color.White,
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (account.balance > BigDecimal.ZERO) "Te falta pagar" else "Estás al día",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(Amounts.format(account.balance), style = MaterialTheme.typography.headlineLarge)
            Text(
                account.latestPublished?.let { "Registrado hasta ${Dates.monthLabel(it)}" }
                    ?: "Todavía no se publicaron gastos",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
