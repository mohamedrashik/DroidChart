package com.droid.bootchart.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.bootchart.domain.BootchartProcess
import com.droid.bootchart.domain.BootchartReport
import kotlinx.coroutines.launch

private val LabelWidth = 256.dp
private const val PlaybackSeconds = 10.0

/** Dense reference-style waterfall with pinned labels and one synchronized horizontal scroll state. */
@Composable
internal fun Waterfall(report: BootchartReport, modifier: Modifier = Modifier) {
    val end = report.captureEndSeconds
    val completion = remember(report) { report.estimatedBootCompletionSeconds }
    var query by remember(report) { mutableStateOf("") }
    var category by remember(report) { mutableStateOf<ProcessCategory?>(null) }
    var threshold by remember(report) { mutableStateOf(0.0) }
    var zoom by remember(report) { mutableStateOf(1f) }
    var cursor by remember(report) { mutableStateOf(0.0) }
    var playing by remember(report) { mutableStateOf(false) }
    var selected by remember(report) { mutableStateOf<BootchartProcess?>(null) }
    val horizontal = remember(report) { ScrollState(0) }
    val sorted = remember(report) { report.processes.sortedWith(compareBy({ it.startSeconds }, { it.pid }, { it.startTicks })) }
    val counts = remember(report) { report.processes.groupingBy(::processCategory).eachCount() }
    val visible = remember(sorted, query, category, threshold) { filterProcesses(sorted, query, category, threshold) }

    LaunchedEffect(report, playing) {
        if (!playing || end <= 0.0) return@LaunchedEffect
        if (cursor >= end) cursor = 0.0
        var last = withFrameMillis { it }
        while (playing) {
            val now = withFrameMillis { it }
            cursor = (cursor + (now - last) / 1000.0 * (end / PlaybackSeconds)).coerceAtMost(end)
            last = now
            if (cursor >= end) playing = false
        }
    }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProcessSearch(query, { query = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterPill("All (${report.processes.size})", category == null, { category = null })
                    listOf(ProcessCategory.Kernel, ProcessCategory.Native, ProcessCategory.Framework, ProcessCategory.Apps).forEach { group ->
                        FilterPill("${group.label} (${counts[group] ?: 0})", category == group, { category = group })
                    }
                    FilterPill("Critical Path (--)", false, {}, enabled = false)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Threshold:", style = TelemetryStyle, color = MaterialTheme.colorScheme.outline)
                    listOf("Off" to 0.0, ">50ms" to 0.05, ">200ms" to 0.2, ">1s" to 1.0).forEach { (name, value) ->
                        FilterPill(name, threshold == value, { threshold = value })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("Waterfall", true, {})
                    Text("${visible.size} shown", style = TelemetryStyle, color = MaterialTheme.colorScheme.outline)
                }
            }
            Text(
                "Categories are name/parent heuristics; Other (${counts[ProcessCategory.Other] ?: 0}) is in All. Critical Path needs causal events.",
                style = TelemetryStyle.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.outline,
            )
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(if (playing) "Pause" else "Play", playing, { playing = !playing }, enabled = end > 0.0)
            Slider(
                value = cursor.toFloat().coerceIn(0f, end.toFloat().coerceAtLeast(1f)),
                onValueChange = { cursor = it.toDouble().coerceAtMost(end); playing = false },
                valueRange = 0f..end.toFloat().coerceAtLeast(1f), enabled = end > 0.0,
                modifier = Modifier.weight(1f).height(32.dp).semantics { contentDescription = "Playback position, seconds since boot" },
            )
            Text("${timelineSeconds(cursor)}s", Modifier.width(72.dp), style = TelemetryStyle)
            Text("Zoom", style = TelemetryStyle, color = MaterialTheme.colorScheme.outline)
            FilterPill("-", false, { zoom = (zoom / 1.5f).coerceAtLeast(1f) }, enabled = zoom > 1f)
            FilterPill("${timelineSeconds(zoom.toDouble())}x", zoom > 1f, { zoom = 1f })
            FilterPill("+", false, { zoom = (zoom * 1.5f).coerceAtMost(20f) }, enabled = zoom < 20f)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Every scrollable cell has identical viewport/content widths, so ruler, plots and bars stay aligned.
            val labelWidth = if (maxWidth < 600.dp) 200.dp else LabelWidth
            val chartWidth = maxOf(960.dp, maxWidth - labelWidth) * zoom
            Column(Modifier.fillMaxSize()) {
                TimelineRow(labelWidth, chartWidth, horizontal, 28.dp, label = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Process / Track ID", style = TelemetryStyle, fontWeight = FontWeight.SemiBold)
                        Text("PID / Dur", style = TelemetryStyle, color = MaterialTheme.colorScheme.outline)
                    }
                }) { AxisChart(end, cursor) }
                val colors = LocalChartColors.current
                TimelineRow(labelWidth, chartWidth, horizontal, 64.dp, label = {
                    TrackLabel("CPU Utilization", "Busy / IOWait", colors.cpu)
                }) {
                    if (report.cpu.isEmpty()) MissingTrack("No CPU samples in this capture")
                    else Chart(end, cursor) { scale ->
                        areaSeries(report.cpu.map { scale.x(it.timeSeconds) to it.busyFraction.toFloat() }, colors.cpu)
                        areaSeries(report.cpu.map { scale.x(it.timeSeconds) to it.iowaitFraction.toFloat() }, colors.diskRead)
                    }
                }
                val peakDisk = remember(report) { report.disk.maxOfOrNull { maxOf(it.readKibPerSecond, it.writeKibPerSecond) } ?: 0.0 }
                TimelineRow(labelWidth, chartWidth, horizontal, 56.dp, label = {
                    TrackLabel("Disk I/O Rate", "Read / Write (MiB/s)", colors.diskRead)
                }) {
                    if (report.disk.isEmpty()) MissingTrack("No whole-disk samples in this capture")
                    else Chart(end, cursor) { scale ->
                        val peak = peakDisk.coerceAtLeast(1.0)
                        areaSeries(report.disk.map { scale.x(it.timeSeconds) to (it.readKibPerSecond / peak).toFloat() }, colors.diskRead)
                        areaSeries(report.disk.map { scale.x(it.timeSeconds) to (it.writeKibPerSecond / peak).toFloat() }, colors.diskWrite)
                    }
                }
                TimelineRow(labelWidth, chartWidth, horizontal, 28.dp, label = {
                    Text("Milestones", style = TelemetryStyle)
                }) { BootCompletionMilestone(completion, end, cursor) }
                if (visible.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                        Text("No processes match these filters.", style = TelemetryStyle)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        itemsIndexed(visible, key = { _, p -> "${p.pid}:${p.startTicks}" }) { index, p ->
                            ProcessRow(p, index, p == selected, { selected = p }, end, cursor, labelWidth, chartWidth, horizontal)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(16.dp)) {
                    Text("SCROLL TIMELINE", Modifier.width(labelWidth).padding(start = 12.dp), style = CaptionStyle, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(1.dp))
                    TimelineScrollbar(horizontal, chartWidth, Modifier.weight(1f))
                }
            }
        }
        Inspector(report, selected, cursor)
    }
}

@Composable
private fun ProcessSearch(query: String, onChange: (String) -> Unit) {
    BasicTextField(
        query, onChange, singleLine = true,
        textStyle = TelemetryStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.width(288.dp).height(28.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp).semantics { contentDescription = "Filter processes by name or PID" },
        decorationBox = { field ->
            Box {
                if (query.isEmpty()) Text("Filter processes (zygote, vold, PID)...", style = TelemetryStyle, color = MaterialTheme.colorScheme.outline)
                field()
            }
        },
    )
}

@Composable
internal fun FilterPill(text: String, active: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.semantics { selected = active }.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        color = if (active) colors.surfaceContainerHighest else Color.Transparent,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = TelemetryStyle,
            color = if (!enabled) colors.outline.copy(alpha = 0.5f) else if (active) colors.primary else colors.onSurfaceVariant,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TimelineRow(
    labelWidth: Dp,
    chartWidth: Dp,
    horizontal: ScrollState,
    height: Dp,
    modifier: Modifier = Modifier,
    labelBackground: Color? = null,
    label: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val border = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth().height(height).border(width = 0.5.dp, color = border.copy(alpha = 0.4f))) {
        Box(
            Modifier.width(labelWidth).fillMaxHeight().background(labelBackground ?: MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart,
        ) { label() }
        Box(Modifier.width(1.dp).fillMaxHeight().background(border))
        Box(Modifier.weight(1f).clipToBounds().horizontalScroll(horizontal)) {
            Box(Modifier.width(chartWidth).fillMaxHeight()) { content() }
        }
    }
}

@Composable
private fun TrackLabel(title: String, legend: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(legend, style = TelemetryStyle.copy(fontSize = 10.sp), color = color)
    }
}

@Composable
private fun MissingTrack(reason: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
        Text(reason, style = TelemetryStyle, color = MaterialTheme.colorScheme.outline)
    }
}

/** Palette follows the prototype: slate kernel, blue init/display, violet framework, green apps. */
@Composable
private fun processTint(process: BootchartProcess): Color {
    val colors = MaterialTheme.colorScheme
    val command = process.name.substringBefore(' ').substringAfterLast('/').lowercase()
    return when {
        command == "init" || command == "surfaceflinger" -> colors.primary
        command == "bootanimation" -> colors.outline
        command.contains("servicemanager") -> colors.tertiary
        command == "com.android.systemui" -> colors.secondary
        else -> when (processCategory(process)) {
            ProcessCategory.Kernel -> colors.outline
            ProcessCategory.Framework -> colors.tertiary
            ProcessCategory.Apps -> colors.secondary
            ProcessCategory.Native -> listOf(colors.secondary, colors.primary, colors.tertiary)[(process.name.hashCode() and Int.MAX_VALUE) % 3]
            ProcessCategory.Other -> colors.onSurfaceVariant
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcessRow(
    process: BootchartProcess, index: Int, selected: Boolean, onSelect: () -> Unit,
    end: Double, cursor: Double, labelWidth: Dp, chartWidth: Dp, horizontal: ScrollState,
) {
    val colors = MaterialTheme.colorScheme
    val tint = processTint(process)
    val measurer = rememberTextMeasurer()
    val barTextColor = when (tint) {
        colors.primary -> colors.onPrimary
        colors.secondary -> colors.onSecondary
        colors.tertiary -> colors.onTertiary
        else -> colors.onSurface
    }
    val style = TelemetryStyle.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = barTextColor)
    val times = "${timelineSeconds(process.startSeconds)}s - ${timelineSeconds(process.barEndSeconds)}s"
    val shortName = process.name.substringBefore(' ').substringAfterLast('/')
    val fullLabel = measurer.measure("$shortName ($times)", style, maxLines = 1)
    val timeLabel = measurer.measure(times, style, maxLines = 1)
    val description = "${process.name}, PID ${process.pid}, estimated start ${timelineSeconds(process.startSeconds)} seconds, " +
        "last observed ${timelineSeconds(process.barEndSeconds)} seconds. Tick rate assumes 100 Hz."
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(description, style = TelemetryStyle) } },
        state = rememberTooltipState(),
    ) {
        TimelineRow(
            labelWidth, chartWidth, horizontal, 24.dp,
            modifier = Modifier.background(
                if (selected) colors.surfaceContainerHighest else if (index % 2 == 0) colors.surfaceContainerLowest else colors.surfaceContainerLow.copy(alpha = 0.5f),
            ).clickable(onClick = onSelect).semantics {
                contentDescription = description
                this.selected = selected
            },
            labelBackground = if (selected) colors.surfaceContainerHighest else null,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(5.dp).background(tint, RoundedCornerShape(1.dp)))
                    Text(shortName, Modifier.weight(1f), style = TelemetryStyle, color = if (selected) colors.primary else colors.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${process.pid} | ${timelineSeconds(process.barDurationSeconds)}s",
                        style = TelemetryStyle.copy(fontSize = 10.sp), color = colors.outline)
                }
            },
        ) {
            Chart(end, cursor) { scale ->
                val startX = scale.x(process.startSeconds).coerceIn(0f, size.width)
                val right = scale.x(process.barEndSeconds).coerceIn(startX, size.width)
                val width = (right - startX).coerceAtLeast(2.dp.toPx()).coerceAtMost(size.width)
                val left = startX.coerceAtMost(size.width - width)
                val top = 4.dp.toPx()
                val height = size.height - top * 2
                val radius = CornerRadius(2.dp.toPx())
                val opacity = if (processCategory(process) == ProcessCategory.Kernel) 0.25f else 0.7f
                drawRoundRect(tint.copy(alpha = opacity), Offset(left, top), Size(width, height), radius)
                drawRoundRect(if (selected) colors.onSurface else tint.copy(alpha = 0.8f),
                    Offset(left, top), Size(width, height), radius, style = Stroke(1.dp.toPx()))
                val padding = 4.dp.toPx()
                val text = if (fullLabel.size.width + padding * 2 <= width) fullLabel else timeLabel
                val fitsInside = text.size.width + padding * 2 <= width
                val textX = when {
                    fitsInside -> left + padding
                    right + padding + text.size.width <= size.width -> right + padding
                    else -> (left - padding - text.size.width).coerceAtLeast(0f)
                }
                // Short bars keep their true width; their range is drawn alongside, never stretched.
                drawText(text, color = if (fitsInside) barTextColor else colors.onSurface,
                    topLeft = Offset(textX, (size.height - text.size.height) / 2f))
            }
        }
    }
}

@Composable
private fun BootCompletionMilestone(seconds: Double?, end: Double, cursor: Double) {
    if (seconds == null) {
        MissingTrack("-- Boot completion unknown: SystemUI not observed")
        return
    }
    val color = MaterialTheme.colorScheme.secondary
    val measurer = rememberTextMeasurer()
    val style = TelemetryStyle.copy(color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val label = "BOOT COMPLETED (EST.) ${timelineSeconds(seconds)}s"
    Box(Modifier.fillMaxSize().semantics {
        contentDescription = "$label. SystemUI first observed; not an Android BOOT_COMPLETED event."
    }) {
        Chart(end, cursor) { scale ->
            val x = scale.x(seconds)
            drawLine(color, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            val text = measurer.measure(label, style, maxLines = 1)
            val left = (x + 6.dp.toPx()).coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
            drawText(text, topLeft = Offset(left, (size.height - text.size.height) / 2f))
        }
    }
}

@Composable
private fun AxisChart(end: Double, cursor: Double) {
    val measurer = rememberTextMeasurer()
    val label = TelemetryStyle.copy(color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
    Chart(end, cursor) { scale ->
        scale.ticks().forEach {
            val text = measurer.measure("${timelineSeconds(it)}s", label, maxLines = 1)
            drawText(text, topLeft = Offset((scale.x(it) + 4.dp.toPx()).coerceAtMost((size.width - text.size.width).coerceAtLeast(0f)), 4.dp.toPx()))
        }
    }
}

@Composable
private fun Chart(end: Double, cursor: Double, draw: DrawScope.(TimeScale) -> Unit) {
    val colors = LocalChartColors.current
    val cursorColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val scale = TimeScale(end, size.width)
        clipRect {
            scale.ticks().forEach { drawLine(colors.grid.copy(alpha = 0.7f), Offset(scale.x(it), 0f), Offset(scale.x(it), size.height), 0.5.dp.toPx()) }
            draw(scale)
            if (end > 0.0) {
                val x = scale.x(cursor)
                drawLine(cursorColor.copy(alpha = 0.1f), Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
                drawLine(cursorColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
        }
    }
}

private fun DrawScope.areaSeries(points: List<Pair<Float, Float>>, color: Color) {
    if (points.size < 2) return
    val line = Path().apply {
        points.forEachIndexed { index, (x, value) ->
            val y = size.height - value.coerceIn(0f, 1f) * size.height
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    val fill = Path().apply {
        addPath(line)
        lineTo(points.last().first, size.height)
        lineTo(points.first().first, size.height)
        close()
    }
    drawPath(fill, color.copy(alpha = 0.25f))
    drawPath(line, color, style = Stroke(1.dp.toPx()))
}

/** Always-visible draggable scrollbar, shared on JVM and Wasm rather than desktop-only widgets. */
@Composable
private fun TimelineScrollbar(state: ScrollState, chartWidth: Dp, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val viewportPx = constraints.maxWidth.toFloat()
        val contentPx = with(androidx.compose.ui.platform.LocalDensity.current) { chartWidth.toPx() }
        val (left, width) = scrollbarThumb(viewportPx, contentPx, state.value.toFloat())
        val travel = (viewportPx - width).coerceAtLeast(1f)
        var draggingOffset by remember { mutableStateOf(0f) }
        Canvas(
            Modifier.fillMaxSize()
                .semantics {
                    contentDescription = "Horizontal timeline scroll"
                    progressBarRangeInfo = ProgressBarRangeInfo(state.value.toFloat(), 0f..state.maxValue.toFloat().coerceAtLeast(1f))
                    setProgress { value -> scope.launch { state.scrollTo(value.toInt()) }; true }
                }
                .pointerInput(state, travel) {
                    detectTapGestures { point ->
                        scope.launch { state.scrollTo(((point.x - width / 2) / travel * state.maxValue).toInt().coerceIn(0, state.maxValue)) }
                    }
                }
                .pointerInput(state, travel) {
                    detectDragGestures(
                        onDragStart = { draggingOffset = state.value.toFloat() },
                    ) { change, delta ->
                        change.consume()
                        draggingOffset = (draggingOffset + delta.x / travel * state.maxValue).coerceIn(0f, state.maxValue.toFloat())
                        scope.launch { state.scrollTo(draggingOffset.toInt()) }
                    }
                },
        ) {
            drawRect(colors.surfaceContainerLowest)
            drawRoundRect(colors.outlineVariant, Offset(left, 4.dp.toPx()), Size(width, 8.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable
private fun Inspector(report: BootchartReport, process: BootchartProcess?, cursor: Double) {
    val cpu = report.cpu.firstOrNull { it.timeSeconds >= cursor }?.takeIf { cursor >= report.cpu.first().timeSeconds }
    val disk = report.disk.firstOrNull { it.timeSeconds >= cursor }?.takeIf { cursor >= report.disk.first().timeSeconds }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PROCESS INSPECTOR", style = CaptionStyle, color = MaterialTheme.colorScheme.primary)
            Text("At ${timelineSeconds(cursor)}s | CPU ${cpu?.let { "${(it.busyFraction * 100).toInt()}%" } ?: "--"} | " +
                "Disk ${disk?.let { "${((it.readKibPerSecond + it.writeKibPerSecond) / 1024).toInt()} MiB/s" } ?: "--"}", style = TelemetryStyle)
        }
        if (process == null) {
            Text("Select a process. Bar times: estimated start (100 Hz ticks) to last observation, not confirmed exit.",
                style = TelemetryStyle, color = MaterialTheme.colorScheme.outline)
        } else {
            Text("${process.name} | PID ${process.pid} | Parent ${process.parentPid} | Last state ${process.state}", style = TelemetryStyle, maxLines = 1)
            Text("Start ~${timelineSeconds(process.startSeconds)}s  |  Last seen ${timelineSeconds(process.barEndSeconds)}s  |  " +
                "Span ${timelineSeconds(process.barDurationSeconds)}s  |  ${process.samples} samples",
                style = TelemetryStyle, color = MaterialTheme.colorScheme.secondary)
        }
        Text("Bar end is last observation, not process exit. Boot completion is estimated from SystemUI first observation, not a broadcast. Playback: full capture in 10s.",
            style = TelemetryStyle.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.outline)
    }
}
