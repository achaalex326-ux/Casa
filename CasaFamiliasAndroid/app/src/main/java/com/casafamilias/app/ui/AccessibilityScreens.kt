package com.casafamilias.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.casafamilias.app.domain.Role as AccountRole
import com.casafamilias.app.domain.Snapshot

@Composable
fun ReadingPreferences(vm: CasaViewModel) {
    val state by vm.state.collectAsState()
    Panel {
        Text("Letras y colores", style = MaterialTheme.typography.titleLarge)
        Text("Si las letras se ven pequeñas, elige Grande o Extra grande.")
        listOf(1f to "Normal", 1.15f to "Grande", 1.3f to "Extra grande").forEach { (size, label) ->
            val chosen = state.textScale == size
            OutlinedButton(
                onClick = { vm.scale(size) },
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { selected = chosen },
                contentPadding = PaddingValues(16.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor =
                            if (chosen) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                if (chosen) {
                    Glyph("check", modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(
                    value = state.dark,
                    role = Role.Switch,
                    onValueChange = { vm.theme(it) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Modo oscuro", Modifier.weight(1f))
            Switch(state.dark, onCheckedChange = null)
        }
    }
}

@Composable
fun CleaningReminderPanel(s: Snapshot, vm: CasaViewModel) {
    val state by vm.state.collectAsState()
    val alerts = LocalDeviceAlerts.current
    // Read this revision here so permission changes update this panel immediately.
    key(state.securityRevision) {
        Panel {
            Glyph("notifications")
            Text("Aviso de limpieza en tu celular", style = MaterialTheme.typography.titleMedium)
            Text(
                if (s.profile.role == AccountRole.ADMIN)
                    "Recibe un aviso para saber qué familia limpia esta semana."
                else "Recibe un aviso durante la semana que le toca limpiar a tu familia."
            )
            when {
                s.preview ->
                    Text("Actívalo al ingresar con tu cuenta. El aviso llega a este celular.")
                alerts == null -> Text("Abre la aplicación Android para activar este aviso.")
                alerts.enabled() && alerts.cleaningAllowed() ->
                    Text("Recordatorio activado en este celular.")
                alerts.enabled() && !alerts.cleaningAllowed() -> {
                    Text("El teléfono está bloqueando los avisos de limpieza.")
                    Action("Permitir avisos de limpieza", !state.busy) { alerts.cleaningSettings() }
                }
                else -> Action("Activar recordatorio", !state.busy) { alerts.enable() }
            }
            Text(
                "Necesita internet. Android revisa los avisos aproximadamente cada 15 minutos y puede demorarlos por batería o conexión.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
