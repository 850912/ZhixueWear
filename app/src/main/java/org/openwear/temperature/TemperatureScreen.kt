package org.openwear.temperature

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedCard
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextToggleButton
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

@Composable
internal fun TemperatureScreen(
    result: MeasurementResult,
    mode: MeasurementMode,
    trendRange: TrendRange,
    history: List<TemperatureRecord>,
    wearState: WearState,
    autoMeasure: Boolean,
    backgroundInterval: BackgroundInterval,
    permissionMessage: String,
    onModeChanged: (MeasurementMode) -> Unit,
    onTrendRangeChanged: (TrendRange) -> Unit,
    onAutoMeasureChanged: (Boolean) -> Unit,
    onBackgroundIntervalChanged: (BackgroundInterval) -> Unit,
    onMeasure: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val now = System.currentTimeMillis()
    val filtered = history.filter { it.timeMillis >= now - trendRange.millis }
    val values = filtered.map { it.skinCelsius }
    val showDate = trendRange == TrendRange.WEEK || trendRange == TrendRange.MONTH
    val trendMean = if (values.isNotEmpty()) values.average().toFloat() else null
    val trendMin = values.minOrNull()
    val trendMax = values.maxOrNull()

    val isActiveMeasuring = result.status == MeasurementStatus.CONNECTING ||
        result.status == MeasurementStatus.MEASURING
    val minRequiredSamples = TemperatureQuality.minSamples(mode).coerceAtLeast(1)
    val rawProgress = when (result.status) {
        MeasurementStatus.IDLE -> 0f
        MeasurementStatus.CONNECTING -> 0.18f
        MeasurementStatus.MEASURING -> (result.sampleCount.toFloat() / minRequiredSamples.toFloat())
            .coerceIn(0.22f, 0.95f)
        MeasurementStatus.COMPLETE -> 1f
        else -> 0f
    }
    val animatedArcProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "m3eBezelArcProgress"
    )

    ScreenScaffold(
        scrollState = listState,
        edgeButtonSpacing = 12.dp,
        edgeButton = {
            EdgeButton(
                onClick = onMeasure,
                buttonSize = EdgeButtonSize.Medium,
                colors = if (isActiveMeasuring) {
                    ButtonDefaults.filledTonalButtonColors()
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.scrollable(
                    state = listState,
                    orientation = Orientation.Vertical,
                    reverseDirection = true
                )
            ) {
                Text(
                    text = if (isActiveMeasuring) "重新${mode.label}" else "开始${mode.label}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (animatedArcProgress > 0.01f) {
                CircularProgressIndicator(
                    progress = { animatedArcProgress },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    strokeWidth = 4.dp
                )
            }

            TransformingLazyColumn(
                state = listState,
                contentPadding = contentPadding,
                flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(listState),
                rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(listState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ListHeaderDefaults.minimumTopListContentPadding
                            ),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(
                            text = "皮肤温度",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    ExpressiveThermalHeroCard(
                        result = result,
                        arcProgress = animatedArcProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                CardDefaults.minimumVerticalListContentPadding
                            ),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }

                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(
                            text = "测量模式",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        RowModeButtons(mode = mode, onModeChanged = onModeChanged)
                    }
                }

                item {
                    Text(
                        text = "预计用时：${mode.estimatedSeconds} 秒",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        WearStatusRow(wearState = wearState)
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        AutoMeasureToggle(
                            enabled = autoMeasure,
                            onToggle = { onAutoMeasureChanged(!autoMeasure) }
                        )
                    }
                }

                if (autoMeasure) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                        ) {
                            RowBackgroundIntervalButtons(
                                interval = backgroundInterval,
                                onIntervalChanged = onBackgroundIntervalChanged
                            )
                        }
                    }
                    item {
                        Text(
                            text = "后台每 ${backgroundInterval.label} 采样一次；未佩戴或读数无效时不会记录，采样结束立即释放传感器。",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyExtraSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (permissionMessage.isNotBlank()) {
                    item {
                        TitleCard(
                            onClick = onMeasure,
                            title = {
                                Text(
                                    text = "权限提示",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(
                                    CardDefaults.minimumVerticalListContentPadding
                                ),
                            transformation = SurfaceTransformation(transformationSpec),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                text = permissionMessage,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (result.status !in setOf(MeasurementStatus.IDLE, MeasurementStatus.COMPLETE)) {
                    item {
                        Card(
                            onClick = onMeasure,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(
                                    CardDefaults.minimumVerticalListContentPadding
                                ),
                            transformation = SurfaceTransformation(transformationSpec),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = result.detail,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(
                            text = "个人趋势 · ${trendRange.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    ) {
                        RowRangeButtons(range = trendRange, onRangeChanged = onTrendRangeChanged)
                    }
                }

                if (filtered.isNotEmpty()) {
                    item {
                        Text(
                            text = "样本 ${filtered.size} · 均值 ${"%.1f°".format(trendMean)}",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .height(116.dp)
                        ) {
                            TrendChart(records = filtered)
                        }
                    }
                    filtered.takeLast(8).reversed().forEach { record ->
                        item {
                            Card(
                                onClick = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .minimumVerticalContentPadding(
                                        CardDefaults.minimumVerticalListContentPadding
                                    ),
                                transformation = SurfaceTransformation(transformationSpec),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatTime(record.timeMillis, showDate)}  ${"%.1f°".format(record.skinCelsius)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (trendMean != null) {
                                            val delta = record.skinCelsius - trendMean
                                            when {
                                                delta > 0.05f -> "+%.1f°".format(delta)
                                                delta < -0.05f -> "%.1f°".format(delta)
                                                else -> "基准"
                                            }
                                        } else {
                                            "--"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        OutlinedCard(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(
                                    CardDefaults.minimumVerticalListContentPadding
                                ),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Text(
                                text = "该时段暂无历史记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "静坐、贴合佩戴；运动、洗澡或温差环境后等待 30 分钟。",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Text(
                        text = "重要：腕部皮肤温度 ≠ 核心体温。仅供趋势观察，不用于发烧判断、诊断或治疗。",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveThermalHeroCard(
    result: MeasurementResult,
    arcProgress: Float,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation
) {
    val animatedValue by animateFloatAsState(
        targetValue = result.meanCelsius ?: 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "m3eHeroValue"
    )
    val valueColor by animateColorAsState(
        targetValue = when (result.status) {
            MeasurementStatus.COMPLETE -> MaterialTheme.colorScheme.primary
            MeasurementStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.tertiary
        },
        animationSpec = tween(400),
        label = "m3eHeroValueColor"
    )

    Card(
        onClick = {},
        modifier = modifier,
        transformation = transformation,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = result.meanCelsius?.let { "%.1f°".format(it) } ?: "--°",
                style = MaterialTheme.typography.numeralExtraLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = "腕部皮肤温度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ReadingCell(
                    label = "环境",
                    value = result.ambientCelsius?.let { "%.1f°".format(it) } ?: "--°"
                )
                ReadingCell(
                    label = "波动",
                    value = result.spreadCelsius?.let { "%.1f°".format(it) } ?: "--°"
                )
                ReadingCell(
                    label = "状态",
                    value = statusLabel(result.status)
                )
            }
            if (result.meanCelsius != null) {
                Text(
                    text = "${result.sampleCount} 个样本 · 波动 ${"%.1f°".format(result.spreadCelsius ?: 0f)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ReadingCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WearStatusRow(wearState: WearState) {
    val (label, hint, container) = when {
        !wearState.isWearing -> Triple(
            "未佩戴手表",
            "请将手表贴合佩戴于手腕",
            MaterialTheme.colorScheme.errorContainer
        )
        wearState.isMoving -> Triple(
            "手腕运动中",
            "请保持静息后再测量",
            MaterialTheme.colorScheme.tertiaryContainer
        )
        else -> Triple(
            "佩戴正常",
            "可以开始测量",
            MaterialTheme.colorScheme.secondaryContainer
        )
    }

    Card(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AutoMeasureToggle(enabled: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    TextToggleButton(
        checked = enabled,
        onCheckedChange = { onToggle() },
        modifier = Modifier.fillMaxWidth(),
        interactionSource = interactionSource
    ) {
        Text(
            text = "\u9759\u606f\u81ea\u52a8\u6d4b\u91cf",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RowBackgroundIntervalButtons(
    interval: BackgroundInterval,
    onIntervalChanged: (BackgroundInterval) -> Unit
) {
    ButtonGroup(modifier = Modifier.fillMaxWidth()) {
        BackgroundInterval.values().forEach { option ->
            val selected = option == interval
            val interactionSource = remember(option) { MutableInteractionSource() }
            if (selected) {
                Button(
                    onClick = { onIntervalChanged(option) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(26.dp),
                    interactionSource = interactionSource
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Button(
                    onClick = { onIntervalChanged(option) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(26.dp),
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RowModeButtons(
    mode: MeasurementMode,
    onModeChanged: (MeasurementMode) -> Unit
) {
    ButtonGroup(
        modifier = Modifier.fillMaxWidth()
    ) {
        MeasurementMode.values().forEach { m ->
            val selected = m == mode
            val interactionSource = remember(m) { MutableInteractionSource() }
            val animatedWeight by animateFloatAsState(
                targetValue = if (selected) 1.26f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "m3eModeWeight_${m.name}"
            )
            val cornerRadius by animateDpAsState(
                targetValue = if (selected) 14.dp else 26.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "m3eModeCorner_${m.name}"
            )
            val buttonModifier = Modifier
                .animateWidth(interactionSource)
                .weight(animatedWeight)

            if (selected) {
                Button(
                    onClick = { onModeChanged(m) },
                    modifier = buttonModifier,
                    shape = RoundedCornerShape(cornerRadius),
                    interactionSource = interactionSource
                ) {
                    Text(
                        text = "✓ ${m.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Button(
                    onClick = { onModeChanged(m) },
                    modifier = buttonModifier,
                    shape = RoundedCornerShape(cornerRadius),
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(
                        text = m.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RowRangeButtons(
    range: TrendRange,
    onRangeChanged: (TrendRange) -> Unit
) {
    ButtonGroup(
        modifier = Modifier.fillMaxWidth()
    ) {
        TrendRange.values().forEach { r ->
            val selected = r == range
            val interactionSource = remember(r) { MutableInteractionSource() }
            val animatedWeight by animateFloatAsState(
                targetValue = if (selected) 1.22f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "m3eRangeWeight_${r.name}"
            )
            val cornerRadius by animateDpAsState(
                targetValue = if (selected) 12.dp else 24.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "m3eRangeCorner_${r.name}"
            )
            val buttonModifier = Modifier
                .animateWidth(interactionSource)
                .weight(animatedWeight)

            if (selected) {
                Button(
                    onClick = { onRangeChanged(r) },
                    modifier = buttonModifier,
                    shape = RoundedCornerShape(cornerRadius),
                    interactionSource = interactionSource
                ) {
                    Text(
                        text = "✓ ${r.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Button(
                    onClick = { onRangeChanged(r) },
                    modifier = buttonModifier,
                    shape = RoundedCornerShape(cornerRadius),
                    interactionSource = interactionSource,
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(
                        text = r.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: MeasurementStatus): String = when (status) {
    MeasurementStatus.IDLE -> "准备就绪"
    MeasurementStatus.CONNECTING -> "连接中"
    MeasurementStatus.MEASURING -> "采集中"
    MeasurementStatus.COMPLETE -> "读数稳定"
    MeasurementStatus.UNSTABLE -> "不稳定"
    MeasurementStatus.SERVICE_MISSING -> "缺健康平台"
    MeasurementStatus.SERVICE_OLD -> "需更新"
    MeasurementStatus.CONNECTION_FAILED -> "连接失败"
    MeasurementStatus.UNSUPPORTED -> "不支持"
    MeasurementStatus.PERMISSION_DENIED -> "缺权限"
    MeasurementStatus.SDK_POLICY -> "策略拒绝"
    MeasurementStatus.TIMEOUT -> "超时"
    MeasurementStatus.ERROR -> "测量错误"
    MeasurementStatus.NOT_WEARING -> "未佩戴"
    MeasurementStatus.MOVING -> "运动中"
}

private fun formatTime(millis: Long, withDate: Boolean = false): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val time = "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
    if (!withDate) return time
    return "%02d/%02d %s".format(
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH),
        time
    )
}

private fun Modifier.animateWidth(interactionSource: MutableInteractionSource): Modifier = this

@Composable
private fun TrendChart(records: List<TemperatureRecord>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val points = TrendStats.bucket(records)
    val summary = TrendStats.summarize(records)

    if (points.size < 2) {
        Text(
            text = "\u6570\u636e\u70b9\u4e0d\u8db3\uff0c\u7ee7\u7eed\u6d4b\u91cf\u540e\u5373\u53ef\u770b\u5230\u8d8b\u52bf\u66f2\u7ebf",
            style = MaterialTheme.typography.bodySmall,
            color = axisColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            val values = points.map { it.meanCelsius }
            val lowRaw = minOf(values.min(), summary.min ?: values.min())
            val highRaw = maxOf(values.max(), summary.max ?: values.max())
            val pad = maxOf((highRaw - lowRaw) * 0.18f, 0.25f)
            val low = lowRaw - pad
            val high = highRaw + pad
            val span = (high - low).coerceAtLeast(0.01f)
            val startMillis = points.first().startMillis
            val endMillis = points.last().endMillis
            val timeSpan = (endMillis - startMillis).coerceAtLeast(1L)

            fun xOf(millis: Long): Float =
                ((millis - startMillis).toFloat() / timeSpan) * size.width

            fun yOf(value: Float): Float =
                size.height - ((value - low) / span) * size.height

            // \u53c2\u8003\u7ebf\uff1a\u6700\u9ad8 / \u5747\u503c / \u6700\u4f4e
            listOfNotNull(summary.max, summary.mean, summary.min).forEach { level ->
                val y = yOf(level)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val linePath = Path()
            val areaPath = Path()
            points.forEachIndexed { index, point ->
                val x = xOf(point.startMillis)
                val y = yOf(point.meanCelsius)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    areaPath.moveTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
            }
            areaPath.lineTo(size.width, size.height)
            areaPath.lineTo(0f, size.height)
            areaPath.close()

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lineColor.copy(alpha = 0.32f),
                        lineColor.copy(alpha = 0.02f)
                    )
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            points.forEach { point ->
                val center = Offset(xOf(point.startMillis), yOf(point.meanCelsius))
                drawCircle(color = dotColor.copy(alpha = 0.4f), radius = 5.dp.toPx(), center = center)
                drawCircle(color = lineColor, radius = 2.5.dp.toPx(), center = center)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(points.first().startMillis, true),
                style = MaterialTheme.typography.labelSmall,
                color = axisColor
            )
            Text(
                text = "\u5747 ${"%.1f\u00b0".format(summary.mean ?: 0f)}  \u9ad8 ${"%.1f\u00b0".format(summary.max ?: 0f)}  \u4f4e ${"%.1f\u00b0".format(summary.min ?: 0f)}",
                style = MaterialTheme.typography.labelSmall,
                color = lineColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatTime(points.last().endMillis, true),
                style = MaterialTheme.typography.labelSmall,
                color = axisColor
            )
        }
    }
}
