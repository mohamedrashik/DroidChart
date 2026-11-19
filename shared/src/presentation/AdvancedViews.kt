package com.droid.bootchart.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droid.bootchart.domain.BootchartReport

/**
 * Advanced analysis that a bootchart capture can support, plus views that stay disabled
 * with a stated reason because the data they need is not in this format.
 */
@Composable
internal fun AdvancedViews(report: BootchartReport) {
    val suspects = remember(report) {
        report.processes.filter { it.cpuTicks > 0 }.sortedByDescending { it.cpuTicks }.take(8)
    }
    Panel("BOTTLENECK SUSPECTS") {
        Text(
            "Ranked by cumulative user+system CPU time. These are suspects, not a critical path: " +
                "a bootchart has no wait, lock or binder events that would show what blocked what.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (suspects.isEmpty()) {
            Text("-- No process used measurable CPU time in this capture.", style = TelemetryStyle)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            suspects.forEach { p ->
                Row {
                    Text("${p.pid}", Modifier.width(72.dp), style = TelemetryStyle)
                    Text("${p.cpuTicks / 100}.${(p.cpuTicks % 100).toString().padStart(2, '0')} s", Modifier.width(88.dp), style = TelemetryStyle)
                    Text(p.name, style = TelemetryStyle, maxLines = 1)
                }
            }
        }
    }
}
