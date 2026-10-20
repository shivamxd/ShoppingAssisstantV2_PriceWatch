package com.example.shoppingassisstantv2.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.shoppingassisstantv2.data.PricePoint
import com.example.shoppingassisstantv2.data.asPrice

private data class ChartPoint(val at: Long, val price: Double)

@Composable
fun PriceChart(
    history: List<PricePoint>,
    target: Double?,
    modifier: Modifier = Modifier,
) {
    val points = remember(history) {
        history.mapNotNull { point -> point.price?.let { ChartPoint(point.at, it) } }
    }
    if (points.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val targetColor = colors.tertiary
    val gridColor = colors.outlineVariant.copy(alpha = 0.55f)

    val minPrice = points.minOf { it.price }
    val maxPrice = points.maxOf { it.price }
    val low = target?.let { minOf(minPrice, it) } ?: minPrice
    val high = target?.let { maxOf(maxPrice, it) } ?: maxPrice
    val spread = (high - low).takeIf { it > 0.0 } ?: (high * 0.1).coerceAtLeast(1.0)
    val floor = low - spread * 0.08
    val ceiling = high + spread * 0.08
    val firstAt = points.first().at
    val lastAt = points.last().at
    val span = (lastAt - firstAt).coerceAtLeast(0L)
    val chartDescription = buildString {
        append("Price history chart. ")
        append("Lowest ${minPrice.asPrice()}. ")
        append("Latest ${points.last().price.asPrice()}. ")
        target?.let { append("Target ${it.asPrice()}. ") }
        append("${points.size} checks.")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(200.dp)) {
            Column(
                modifier = Modifier
                    .width(62.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = high.asPrice(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = low.asPrice(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = chartDescription },
            ) {
                val top = 8.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                val left = 2.dp.toPx()
                val right = size.width - 2.dp.toPx()
                val chartHeight = (bottom - top).coerceAtLeast(1f)

                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = top + chartHeight * fraction
                    drawLine(
                        color = gridColor,
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                fun x(at: Long): Float =
                    if (span <= 0L) (left + right) / 2f
                    else left + ((at - firstAt).toFloat() / span.toFloat()) * (right - left)

                fun y(price: Double): Float =
                    top + (1.0 - ((price - floor) / (ceiling - floor))).toFloat() * chartHeight

                val line = Path()
                val area = Path()
                points.forEachIndexed { index, point ->
                    val px = x(point.at)
                    val py = y(point.price)
                    if (index == 0) {
                        line.moveTo(px, py)
                        area.moveTo(px, bottom)
                        area.lineTo(px, py)
                    } else {
                        line.lineTo(px, py)
                        area.lineTo(px, py)
                    }
                }
                area.lineTo(x(points.last().at), bottom)
                area.close()

                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(primary.copy(alpha = 0.28f), primary.copy(alpha = 0.02f)),
                        startY = top,
                        endY = bottom,
                    ),
                )
                drawPath(
                    path = line,
                    color = primary,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

                if (target != null) {
                    val targetY = y(target)
                    if (targetY in top..bottom) {
                        drawLine(
                            color = targetColor,
                            start = Offset(left, targetY),
                            end = Offset(right, targetY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
                            ),
                        )
                    }
                }

                val last = points.last()
                drawCircle(
                    color = primary.copy(alpha = 0.2f),
                    radius = 8.dp.toPx(),
                    center = Offset(x(last.at), y(last.price)),
                )
                drawCircle(
                    color = primary,
                    radius = 4.dp.toPx(),
                    center = Offset(x(last.at), y(last.price)),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 70.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatChartLabel(firstAt, span),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                formatChartLabel(lastAt, span),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (points.size == 1) {
            Text(
                "The trend will grow with each hourly check.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 70.dp, top = 8.dp),
            )
        }
    }
}
