package com.example.shoppingassisstantv2.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shoppingassisstantv2.data.AmazonInSite
import com.example.shoppingassisstantv2.data.FlipkartSite
import com.example.shoppingassisstantv2.data.Product
import com.example.shoppingassisstantv2.data.ProductSites
import com.example.shoppingassisstantv2.ui.theme.AmazonInk
import com.example.shoppingassisstantv2.ui.theme.FlipkartBlue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SiteBadge(url: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val site = ProductSites.forUrl(url)
    val color = when (site) {
        AmazonInSite -> AmazonInk
        FlipkartSite -> FlipkartBlue
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = site?.initial ?: "?",
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun ProductStatusPill(product: Product) {
    val colors = MaterialTheme.colorScheme
    when {
        product.lastError != null ->
            StatusPill("Check failed", colors.errorContainer, colors.onErrorContainer)
        product.lastCheckedAt == 0L ->
            StatusPill("Awaiting check", colors.surfaceContainerHigh, colors.onSurfaceVariant)
        product.lastInStock == false ->
            StatusPill("Out of stock", colors.tertiaryContainer, colors.onTertiaryContainer)
        product.lastPrice != null && product.targetPrice != null &&
            product.lastPrice <= product.targetPrice ->
            StatusPill("Target met", colors.secondaryContainer, colors.onSecondaryContainer)
        product.lastInStock == true ->
            StatusPill("In stock", colors.secondaryContainer, colors.onSecondaryContainer)
        else ->
            StatusPill("Watching", colors.surfaceContainerHigh, colors.onSurfaceVariant)
    }
}

@Composable
fun NotificationPermissionCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Turn on alerts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Allow notifications so price drops and restocks reach you.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colors.onTertiaryContainer,
                ),
            ) {
                Text("Enable")
            }
        }
    }
}

fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    if (at <= 0L) return "Not checked yet"
    val diff = (now - at).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L} min ago"
        diff < 86_400_000L -> "${diff / 3_600_000L} hr ago"
        diff < 172_800_000L -> "yesterday"
        else -> "${diff / 86_400_000L} days ago"
    }
}

fun formatDateTime(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.getDefault()))

fun formatDate(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

fun formatChartLabel(at: Long, spanMillis: Long): String {
    val pattern = if (spanMillis < 86_400_000L) "h:mm a" else "d MMM"
    return Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
