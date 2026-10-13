package com.cbulock.sinkpoint.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cbulock.sinkpoint.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Finite celebration: settles on a trophy and follows the system animation scale. */
@Composable fun VictoryCelebration(matchId: String) {
    val progress = remember(matchId) { Animatable(0f) }
    LaunchedEffect(matchId) { progress.animateTo(1f, tween(2800, easing = LinearEasing)) }
    val particles = remember(matchId) {
        val random = Random(matchId.hashCode())
        List(48) { floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()) }
    }
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primary, colors.tertiary, Color(0xFFFFB83E), Color(0xFFF06A42))
    val label = stringResource(R.string.victory_celebration)
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = label }) {
        val p = progress.value
        val bounce = sin((p * 5f).coerceAtMost(1f) * PI).toFloat() * 12.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2 - bounce)
        val unit = 22.dp.toPx()
        drawCircle(colors.secondaryContainer, unit * 2.3f, center)
        val gold = Color(0xFFE6A11A)
        val cup = Path().apply {
            moveTo(center.x - unit, center.y - unit)
            lineTo(center.x + unit, center.y - unit)
            cubicTo(center.x + unit, center.y + unit * .65f, center.x - unit, center.y + unit * .65f, center.x - unit, center.y - unit)
            close()
        }
        drawArc(gold, 270f, -180f, false, center + Offset(-unit * 1.55f, -unit * .85f), Size(unit, unit), style = Stroke(4.dp.toPx()))
        drawArc(gold, 270f, 180f, false, center + Offset(unit * .55f, -unit * .85f), Size(unit, unit), style = Stroke(4.dp.toPx()))
        drawPath(cup, Color(0xFFFFC34D))
        drawPath(cup, gold, style = Stroke(2.dp.toPx()))
        drawLine(gold, center + Offset(0f, unit * .2f), center + Offset(0f, unit), 5.dp.toPx(), StrokeCap.Round)
        drawLine(gold, center + Offset(-unit * .65f, unit * 1.1f), center + Offset(unit * .65f, unit * 1.1f), 5.dp.toPx(), StrokeCap.Round)
        if (p < 1f) particles.forEachIndexed { index, seed ->
            val travel = (p * 1.45f - seed[1] * .35f).coerceAtLeast(0f)
            val position = Offset(seed[0] * size.width + sin(travel * 8f + seed[2] * 6f) * 12.dp.toPx(), -12.dp.toPx() + travel * (size.height + 40.dp.toPx()))
            rotate(seed[3] * 360f + travel * 300f, position) {
                drawRect(palette[index % palette.size].copy(alpha = ((1f - p) * 4f).coerceIn(0f, 1f)),
                    position, Size(5.dp.toPx(), (4f + seed[2] * 5f).dp.toPx()))
            }
        }
    }
}
