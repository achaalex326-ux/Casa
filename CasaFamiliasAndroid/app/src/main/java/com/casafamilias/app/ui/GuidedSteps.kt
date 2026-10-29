package com.casafamilias.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun GuidedSteps(current: Int, labels: List<String>) {
    Column(
        Modifier.fillMaxWidth().testTag("guided-step-$current"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Paso ${current+1} de ${labels.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color =
                            if (index <= current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "${index+1}",
                                color =
                                    if (index <= current) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                        color =
                            if (index == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
