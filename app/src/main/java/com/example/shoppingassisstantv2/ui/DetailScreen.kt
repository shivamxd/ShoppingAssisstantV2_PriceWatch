package com.example.shoppingassisstantv2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shoppingassisstantv2.data.PricePoint
import com.example.shoppingassisstantv2.data.Product
import com.example.shoppingassisstantv2.data.ProductSites
import com.example.shoppingassisstantv2.data.asPrice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    product: Product,
    history: List<PricePoint>?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCheckNow: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    val site = ProductSites.labelFor(product.url)

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(site, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit product")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete product",
                            tint = colors.error,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ProductHeader(product)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onCheckNow,
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Check now")
                    }
                    OutlinedButton(
                        onClick = onOpenInBrowser,
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open")
                    }
                }
            }
            item {
                AlertsCard(product)
            }
            product.lastError?.let { message ->
                item {
                    CheckErrorCard(message)
                }
            }
            item {
                PriceHistoryCard(product, history)
            }
            item {
                RecentChecksCard(history)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove from watchlist?") },
            text = {
                Text(
                    "“${product.title.ifBlank { "This product" }}” and its price history will be deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Remove", color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ProductHeader(product: Product) {
    val colors = MaterialTheme.colorScheme
    val targetMet = product.targetPrice != null &&
        product.lastPrice != null &&
        product.lastPrice <= product.targetPrice
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SiteBadge(product.url, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                ProductSites.labelFor(product.url),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            ProductStatusPill(product)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = product.title.ifBlank { "Product on ${ProductSites.labelFor(product.url)}" },
            style = MaterialTheme.typography.headlineSmall,
            overflow = TextOverflow.Ellipsis,
            maxLines = 4,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = product.lastPrice?.asPrice() ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (targetMet) colors.secondary else colors.onSurface,
                )
                Text(
                    "Current price",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = product.targetPrice?.asPrice() ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (product.targetPrice != null) "Your target" else "Stock watch",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (product.lastCheckedAt == 0L) {
                "Not checked yet. The first check will run shortly."
            } else {
                "Last checked ${relativeTime(product.lastCheckedAt)}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlertsCard(product: Product) {
    val colors = MaterialTheme.colorScheme
    val alerts = buildList {
        product.targetPrice?.let { add("Price at or below ${it.asPrice()}") }
        if (product.watchStock) add("Back in stock")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Alerts are on", style = MaterialTheme.typography.titleSmall)
                Text(
                    alerts.joinToString(" · ").ifEmpty { "No alerts configured." },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckErrorCard(message: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Last check failed", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "It will try again on the next hourly check.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun PriceHistoryCard(product: Product, history: List<PricePoint>?) {
    val colors = MaterialTheme.colorScheme
    val priced = history?.filter { it.price != null }.orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Price trend", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Since ${formatDate(product.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(
                    "All time",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            when {
                history == null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                priced.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No price data yet", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Tap Check now to start building the price history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                else -> {
                    val lowest = priced.mapNotNull { it.price }.minOrNull()
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatBlock("Lowest", lowest?.asPrice() ?: "—", Modifier.weight(1f))
                        StatBlock("Checks", history.size.toString(), Modifier.weight(1f))
                        StatBlock(
                            "Target",
                            product.targetPrice?.asPrice() ?: "—",
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(colors.primary)
                        Text("Price", style = MaterialTheme.typography.labelSmall)
                        if (product.targetPrice != null) {
                            LegendDot(colors.tertiary)
                            Text(
                                "Target ${product.targetPrice.asPrice()}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PriceChart(history = history, target = product.targetPrice)
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun RecentChecksCard(history: List<PricePoint>?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent checks", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (history != null) {
                    Text(
                        "${history.size} total",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                history == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Loading checks…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                history.isEmpty() -> {
                    Text(
                        "No checks recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                else -> {
                    val recent = history.asReversed().take(30)
                    recent.forEachIndexed { index, point ->
                        CheckRow(point)
                        if (index != recent.lastIndex) {
                            HorizontalDivider(
                                color = colors.outlineVariant.copy(alpha = 0.35f),
                            )
                        }
                    }
                    if (history.size > recent.size) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Showing the latest ${recent.size} of ${history.size} checks.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(point: PricePoint) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(formatDateTime(point.at), style = MaterialTheme.typography.labelMedium)
            Text(
                when (point.inStock) {
                    true -> "In stock"
                    false -> "Out of stock"
                    null -> "Stock unknown"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when (point.inStock) {
                    true -> colors.secondary
                    false -> colors.tertiary
                    null -> colors.onSurfaceVariant
                },
            )
        }
        Text(
            point.price?.asPrice() ?: "—",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
