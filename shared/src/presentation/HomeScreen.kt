package com.droid.bootchart.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.bootchart.domain.BootchartReport
import kotlin.math.roundToInt

/** Compact forensic workspace; timeline consumes the remaining window rather than a fixed-height card. */
@Composable
internal fun HomeScreen(
    state: HomeState,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onImport: () -> Unit,
) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val compact = maxWidth < 1000.dp || maxHeight < 700.dp
    var showMetadata by remember(compact) { mutableStateOf(!compact) }
    var showAnalysis by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxSize()) {
            CommandStrip(state, darkTheme, onToggleTheme, onImport)
            state.error?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(8.dp))
                }
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { showMetadata = !showMetadata },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${if (showMetadata) "-" else "+"}  SYSTEM CONFIGURATION & BOOT METADATA",
                        style = CaptionStyle, modifier = Modifier.weight(1f).semantics { heading() },
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        Text("Source: /data/bootchart/header", style = TelemetryStyle.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (showMetadata) MetadataGrid(state.report)
            }
            Kpis(state.report)
            val report = state.report
            if (report == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.widthIn(max = 520.dp).padding(24.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Import Android Bootchart", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Choose a ZIP containing header and proc_ps.log. CPU and disk tracks appear when proc_stat.log and proc_diskstats.log are included.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilterPill(if (state.importing) "Importing..." else "Browse File System", true, onImport, enabled = !state.importing)
                        Text("Local processing only. ZIP up to 32 MiB; expanded up to 64 MiB.",
                            style = TelemetryStyle, color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                Waterfall(report, Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    FilterPill(if (showAnalysis) "- Analysis & capabilities" else "+ Analysis & capabilities",
                        showAnalysis, { showAnalysis = !showAnalysis })
                }
                if (showAnalysis) {
                    Column(Modifier.fillMaxWidth().height(160.dp).verticalScroll(rememberScrollState()).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdvancedViews(report)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandStrip(state: HomeState, dark: Boolean, onToggleTheme: () -> Unit, onImport: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(colors.surfaceContainer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(">_", style = TelemetryStyle, color = colors.primary)
        Text("DroidChart", style = MaterialTheme.typography.titleMedium, color = colors.primary,
            modifier = Modifier.semantics { heading() })
        Text("PRO", Modifier.background(colors.surfaceContainerHighest, RoundedCornerShape(2.dp)).padding(horizontal = 4.dp, vertical = 2.dp),
            style = CaptionStyle, color = colors.secondary)
        Text(
            state.report?.let { "Trace: ${it.fileName} | ${it.sampleCount} samples" } ?: "No capture loaded",
            Modifier.weight(1f), style = TelemetryStyle, color = colors.outline, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        FilterPill(if (dark) "Light" else "Dark", false, onToggleTheme)
        Surface(
            Modifier.clickable(enabled = !state.importing, role = Role.Button, onClick = onImport),
            color = colors.primary, contentColor = colors.onPrimary, shape = RoundedCornerShape(4.dp),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.importing) CircularProgressIndicator(Modifier.size(12.dp), color = colors.onPrimary, strokeWidth = 1.dp)
                Text(if (state.importing) "Importing..." else "Capture / Import", style = TelemetryStyle)
            }
        }
    }
}

@Composable
private fun MetadataGrid(report: BootchartReport?) {
    val metadata = report?.metadata.orEmpty()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 1000.dp) 4 else 2
        val width = (maxWidth - 8.dp * (columns - 1)) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Panel("KERNEL & BUILD INFO", Modifier.width(width).height(126.dp)) {
                Field("Kernel version", metadata["system.uname"])
                Field("System release", metadata["system.release"])
            }
            Panel("HARDWARE SPECS", Modifier.width(width).height(126.dp)) {
                Field("CPU / architecture", metadata["system.cpu"])
                Field("Core topology / RAM", null, "Not supplied by this header")
            }
            Panel("BOOT ARGUMENTS (KERNEL CMDLINE)", Modifier.width(width).height(126.dp)) {
                SelectionContainer {
                    Box(Modifier.fillMaxWidth().heightIn(max = 80.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(2.dp))
                        .verticalScroll(rememberScrollState()).padding(6.dp)) {
                        Text(metadata["system.kernel.options"]?.takeIf { it.isNotBlank() } ?: "-- Not supplied by this header",
                            style = TelemetryStyle.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Panel("SAMPLING & TELEMETRY RATE", Modifier.width(width).height(126.dp)) {
                Field("Capture", report?.title)
                Text(report?.let { "${it.processes.size} processes | ${it.sampleCount} samples | capture end ${timelineSeconds(it.captureEndSeconds)}s" } ?: "-- No capture",
                    style = TelemetryStyle.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.secondary)
                Text("Frequency: -- Not supplied by header", style = TelemetryStyle.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun Kpis(report: BootchartReport?) {
    val completion = report?.estimatedBootCompletionSeconds
    val peakCpu = report?.cpu?.maxByOrNull { it.busyFraction }
    val peakDisk = report?.disk?.maxByOrNull { it.readKibPerSecond + it.writeKibPerSecond }
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Kpi(
            if (completion != null) "BOOT COMPLETED (EST.)" else "BOOT COMPLETION",
            completion?.let { "${timelineSeconds(it)}s" },
            when {
                report == null -> "No capture"
                completion == null -> "SystemUI not observed"
                else -> "SystemUI first observed"
            },
            if (completion != null) colors.secondary else colors.onSurface,
            Modifier.weight(1f),
        )
        Kpi("PEAK CPU LOAD", peakCpu?.let { "${(it.busyFraction * 100).roundToInt()}%" },
            peakCpu?.let { "@ ${timelineSeconds(it.timeSeconds)}s" } ?: "CPU data unavailable", colors.primary, Modifier.weight(1f))
        Kpi("PEAK DISK I/O", peakDisk?.let { "${((it.readKibPerSecond + it.writeKibPerSecond) / 1024).roundToInt()} MiB/s" },
            peakDisk?.let { "@ ${timelineSeconds(it.timeSeconds)}s" } ?: "Disk data unavailable", colors.secondary, Modifier.weight(1f))
        Kpi("TRACKED PROCESSES", report?.processes?.size?.toString(),
            report?.let { "${it.sampleCount} snapshots" } ?: "No capture", colors.tertiary, Modifier.weight(1f))
    }
}

@Composable
private fun Kpi(label: String, value: String?, detail: String, tint: Color, modifier: Modifier) {
    Panel(label, modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value ?: "--", style = MaterialTheme.typography.titleLarge, color = tint)
            Text(detail, style = TelemetryStyle.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.outline,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Field(label: String, value: String?, reason: String = "Not supplied") {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.outline)
        Text(value?.takeIf { it.isNotBlank() } ?: "-- $reason", style = TelemetryStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun Panel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = CaptionStyle, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun HomePreview() {
    DroidChartTheme(dark = true) { HomeScreen(HomeState(), true, {}, {}) }
}
