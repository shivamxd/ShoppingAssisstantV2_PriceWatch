package com.example.shoppingassisstantv2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shoppingassisstantv2.data.DuplicateProductException
import com.example.shoppingassisstantv2.data.Product
import com.example.shoppingassisstantv2.data.ProductSites
import com.example.shoppingassisstantv2.data.normalizeProductUrl
import com.example.shoppingassisstantv2.data.parseTargetPrice
import com.example.shoppingassisstantv2.data.toPriceInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ProductDraft(
    val url: String,
    val title: String,
    val targetPrice: Double?,
    val watchStock: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditorScreen(
    existing: Product?,
    initialUrl: String,
    initialTitle: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSave: suspend (ProductDraft) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val isNew = existing == null

    var url by rememberSaveable { mutableStateOf(existing?.url ?: initialUrl) }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: initialTitle) }
    var targetText by rememberSaveable {
        mutableStateOf(existing?.targetPrice?.toPriceInput().orEmpty())
    }
    var watchStock by rememberSaveable { mutableStateOf(existing?.watchStock ?: true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val normalizedUrl = remember(url) { normalizeProductUrl(url) }
    val site = remember(url) { ProductSites.forUrl(url) }
    val urlHint = when {
        url.isBlank() -> "Paste a link from ${ProductSites.supportedLabels}."
        normalizedUrl != null -> "${ProductSites.labelFor(url)} link detected."
        else -> "Only ${ProductSites.supportedLabels} links are supported right now."
    }

    fun save() {
        if (saving) return
        val link = if (isNew) normalizedUrl else existing?.url
        when {
            link == null -> {
                error = "Paste a valid ${ProductSites.supportedLabels} product link."
                return
            }
            targetText.isNotBlank() && parseTargetPrice(targetText) == null -> {
                error = "That target price doesn't look right. Try a number like 1299."
                return
            }
        }
        val target = targetText.takeIf { it.isNotBlank() }?.let(::parseTargetPrice)
        if (target == null && !watchStock) {
            error = "Set a target price or keep back-in-stock alerts on."
            return
        }
        focusManager.clearFocus()
        scope.launch {
            saving = true
            error = null
            try {
                onSave(
                    ProductDraft(
                        url = link,
                        title = title.trim().take(300),
                        targetPrice = target,
                        watchStock = watchStock,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateProductException) {
                error = "This product is already on your watchlist."
            } catch (e: Exception) {
                error = e.message ?: "Couldn't save. Try again."
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Add product" else "Edit product") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        bottomBar = {
            EditorBottomBar(isNew = isNew, saving = saving, onSave = ::save)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            if (isNew) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it.take(2000)
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Product link") },
                    placeholder = { Text("https://www.amazon.in/dp/…") },
                    supportingText = { Text(urlHint) },
                    isError = url.isNotBlank() && normalizedUrl == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
            } else {
                LinkCard(url = url, siteLabel = site?.label ?: "Product")
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Product name (optional)") },
                supportingText = { Text("We'll fill this in from the page if you leave it empty.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = targetText,
                onValueChange = {
                    if (it.length <= 14) {
                        targetText = it
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Target price (optional)") },
                prefix = { Text("₹") },
                placeholder = { Text("1299") },
                supportingText = {
                    Text("We'll notify you when the price is at or below this amount.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )

            Spacer(Modifier.height(16.dp))
            StockAlertRow(
                watchStock = watchStock,
                onToggle = {
                    watchStock = it
                    error = null
                },
            )

            error?.let {
                Spacer(Modifier.height(14.dp))
                ErrorBanner(it)
            }

            Spacer(Modifier.height(16.dp))
            InfoCard(
                "Checks run hourly in the background, so they need an internet connection. " +
                    "Android may delay a check to save battery.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorBottomBar(isNew: Boolean, saving: Boolean, onSave: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.onPrimary,
                    )
                } else {
                    Icon(
                        if (isNew) Icons.Filled.Add else Icons.Filled.Check,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isNew) "Add to watchlist" else "Save changes")
                }
            }
        }
    }
}

@Composable
private fun LinkCard(url: String, siteLabel: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteBadge(url, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(siteLabel, style = MaterialTheme.typography.labelMedium)
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "The link can't be changed after adding.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StockAlertRow(watchStock: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = watchStock, role = Role.Switch, onValueChange = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Back-in-stock alerts",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Tell me when this product is available again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(checked = watchStock, onCheckedChange = null)
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.primaryContainer.copy(alpha = 0.55f),
        contentColor = colors.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
