package org.openwear.temperature

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedCard
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Switch
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

@Composable
internal fun TemperatureScreen(
    result: MeasurementResult,
    mode: MeasurementMode,
    trendRange: TrendRange,
    history: List<Pair<Long, Float>>,
    wearState: WearState,
    autoMeasure: Boolean,
    permissionMessage: String,
    onModeChanged: (MeasurementMode) -> Unit,
    onTrendRangeChanged: (TrendRange) -> Unit,
    onAutoMeasureChanged: (Boolean) -> Unit,
    onMeasure: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    val now = System.currentTimeMillis()
    val filtered = history.filter { (t, _) -> now - t <= trendRange.millis }
    val values = filtered.map { it.second }
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
            // Material 3 Expressive perimeter progress ring around the circular watch bezel
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

                // M3E Hero Thermal Readout Card with shape morphing & spring physics
                item {
                    ExpressiveThermalHeroCard(
                        result = result,
                        mode = mode,
                        arcProgress = animatedArcProgress,
                        onMeasure = onMeasure,
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
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 佩戴状态与运动状态
                item {
                    WearStatusCard(wearState = wearState)
                }

                item {
                    Button(
                        onClick = onMeasure,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = wearState.isWearing && !wearState.isMoving
                    ) {
                        Text(
                            text = when {
                                wearState.isMoving -> "请保持静息后测量"
                                !wearState.isWearing -> "请贴合佩戴手表"
                                isActiveMeasuring -> "重新${mode.label}"
                                else -> "开始${mode.label}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalArrangement(Arrangement.spacedBy(4.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "范围",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${"%.1f°".format(trendMin)} – ${"%.1f°".format(trendMax)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    filtered.takeLast(8).reversed().forEach { (time, value) ->
                        item {
                            val delta = if (trendMean != null) value - trendMean else 0f
                            val deltaText = when {
                                delta > 0.05f -> "+%.1f°".format(delta)
                                delta < -0.05f -> "%.1f°".format(delta)
                                else -> "基准"
                            }
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
                                        text = "${formatTime(time)}  ${"%.1f°".format(value)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = deltaText,
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
                }

                // 自动测量开关
                item {
                    Switch(
                        checked = autoMeasure,
                        onCheckedChange = { onAutoMeasureChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                CardDefaults.minimumVerticalListContentPadding
                            ),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text(
                            text = "静息自动测量",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "手表贴合且静息时自动测量",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        text = "仅供趋势观察，不用于发烧判断、诊断或治疗。",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyExtraSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun WearStatusCard(wearState: WearState) {
    val isWearing = wearState.isWearing
    val isMoving = wearState.isMoving
    val cardColor = when {
        !isWearing -> MaterialTheme.colorScheme.errorContainer
        isMoving -> MaterialTheme.colorScheme.warningContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = when {
        !isWearing -> MaterialTheme.colorScheme.onErrorContainer
        isMoving -> MaterialTheme.colorScheme.onWarningContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val iconText = when {
        !isWearing -> "⌚ 未佩戴"
        isMoving -> "🏃 运动中"
        else -> "✅ 已佩戴"
    }
    val detailText = when {
        !isWearing -> "请将手表贴合佩戴于手腕"
        isMoving -> "检测到手腕运动，请保持静息"
        else -> "佩戴良好，可进行测量"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardColor,
            contentColor = contentColor
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = iconText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
            Text(
                text = if (isWearing && !isMoving) "可测量" else "不可测量",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun RowModeButtons(mode: MeasurementMode, onModeChanged: (MeasurementMode) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MeasurementMode.values().forEach { m ->
            Button(
                onClick = { onModeChanged(m) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (m == mode) "✓ ${m.label}" else m.label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RowRangeButtons(range: TrendRange, onRangeChanged: (TrendRange) -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        TrendRange.values().forEach { r ->
            Button(
                onClick = { onRangeChanged(r) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (r == range) "✓ ${r.label}" else r.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

private fun statusLabel(status: MeasurementStatus): String = when (status) {
    MeasurementStatus.IDLE -> "准备就绪"
    MeasurementStatus.CONNECTING -> "正在连接三星服务"
    MeasurementStatus.MEASURING -> "服务已连接 · 等待读数"
    MeasurementStatus.COMPLETE -> "读数稳定"
    MeasurementStatus.UNSTABLE -> "读数不稳定"
    MeasurementStatus.SERVICE_MISSING -> "未安装健康平台"
    MeasurementStatus.SERVICE_OLD -> "健康平台需更新"
    MeasurementStatus.CONNECTION_FAILED -> "服务连接失败"
    MeasurementStatus.UNSUPPORTED -> "此手表不支持"
    MeasurementStatus.PERMISSION_DENIED -> "传感器权限未授予"
    MeasurementStatus.SDK_POLICY -> "平台策略未允许"
    MeasurementStatus.TIMEOUT -> "测量超时"
    MeasurementStatus.ERROR -> "测量错误"
    MeasurementStatus.NOT_WEARING -> "未佩戴手表"
    MeasurementStatus.MOVING -> "手腕运动中"
}

private fun formatTime(millis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d:%02d".format(
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE)
    )
}
