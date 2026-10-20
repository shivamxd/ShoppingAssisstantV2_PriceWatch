package com.example.shoppingassisstantv2.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.example.shoppingassisstantv2.data.PricePoint
import com.example.shoppingassisstantv2.data.Product
import com.example.shoppingassisstantv2.data.SharedLink
import com.example.shoppingassisstantv2.data.WatchlistRepository
import com.example.shoppingassisstantv2.data.normalizeProductUrl
import com.example.shoppingassisstantv2.notifications.PriceNotifier
import com.example.shoppingassisstantv2.work.PriceCheckWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_DETAIL = "detail"
private const val ROUTE_EDITOR = "editor"

@Composable
fun PriceWatchApp(
    sharedLink: SharedLink?,
    onSharedLinkHandled: () -> Unit,
    notificationsEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val repository = remember { WatchlistRepository.get(context.applicationContext) }
    val products by repository.products.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var route by rememberSaveable { mutableStateOf(ROUTE_HOME) }
    var selectedId by rememberSaveable { mutableLongStateOf(0L) }
    var editorId by rememberSaveable { mutableLongStateOf(0L) }
    var editorUrl by rememberSaveable { mutableStateOf("") }
    var editorTitle by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf<List<PricePoint>?>(null) }

    LaunchedEffect(Unit) { repository.refresh() }

    LaunchedEffect(sharedLink) {
        val link = sharedLink ?: return@LaunchedEffect
        val url = link.url
        if (url == null) {
            scope.launch { snackbarHostState.showSnackbar("No product link found in that share.") }
        } else {
            val existing = normalizeProductUrl(url)?.let { repository.findByUrl(it) }
            if (existing != null) {
                selectedId = existing.id
                history = null
                route = ROUTE_DETAIL
                scope.launch { snackbarHostState.showSnackbar("Already on your watchlist.") }
            } else {
                selectedId = 0L
                editorId = 0L
                editorUrl = url
                editorTitle = link.title
                route = ROUTE_EDITOR
            }
        }
        onSharedLinkHandled()
    }

    LaunchedEffect(products, route) {
        val list = products ?: return@LaunchedEffect
        if (route == ROUTE_DETAIL && list.none { it.id == selectedId }) {
            route = ROUTE_HOME
            selectedId = 0L
        }
        if (route == ROUTE_EDITOR && editorId > 0L && list.none { it.id == editorId }) {
            route = ROUTE_HOME
            editorId = 0L
        }
    }

    val selected = products?.firstOrNull { it.id == selectedId }
    LaunchedEffect(route, selectedId, selected?.lastCheckedAt) {
        history = if (route == ROUTE_DETAIL && selected != null) {
            repository.history(selected.id)
        } else {
            null
        }
    }

    fun openProduct(product: Product) {
        selectedId = product.id
        history = null
        route = ROUTE_DETAIL
    }

    fun addProduct() {
        selectedId = 0L
        editorId = 0L
        editorUrl = ""
        editorTitle = ""
        route = ROUTE_EDITOR
    }

    fun editProduct(product: Product, fromDetail: Boolean) {
        if (!fromDetail) selectedId = 0L
        editorId = product.id
        editorUrl = product.url
        editorTitle = product.title
        route = ROUTE_EDITOR
    }

    fun leaveEditor() {
        route = if (selectedId != 0L && selectedId == editorId) ROUTE_DETAIL else ROUTE_HOME
    }

    fun openInBrowser(product: Product) {
        runCatching { uriHandler.openUri(product.url) }.onFailure {
            scope.launch { snackbarHostState.showSnackbar("No browser app found.") }
        }
    }

    fun deleteProduct(product: Product) {
        scope.launch {
            try {
                repository.delete(product.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Couldn't remove that product. Try again.")
                return@launch
            }
            PriceCheckWork.cancel(context, product.id)
            PriceNotifier.cancel(context, product.id)
            if (selectedId == product.id) {
                selectedId = 0L
                route = ROUTE_HOME
            }
            snackbarHostState.showSnackbar("Removed from watchlist.")
        }
    }

    BackHandler(enabled = route != ROUTE_HOME) {
        when (route) {
            ROUTE_DETAIL -> {
                route = ROUTE_HOME
                selectedId = 0L
            }
            ROUTE_EDITOR -> leaveEditor()
        }
    }

    when (route) {
        ROUTE_DETAIL -> {
            val product = selected
            if (product == null) {
                LoadingScreen()
            } else {
                ProductDetailScreen(
                    product = product,
                    history = history,
                    snackbarHostState = snackbarHostState,
                    onBack = {
                        route = ROUTE_HOME
                        selectedId = 0L
                    },
                    onEdit = { editProduct(product, fromDetail = true) },
                    onCheckNow = {
                        PriceCheckWork.checkNow(context, product.id)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Checking now. This page updates when the check finishes.",
                            )
                        }
                    },
                    onOpenInBrowser = { openInBrowser(product) },
                    onDelete = { deleteProduct(product) },
                )
            }
        }

        ROUTE_EDITOR -> {
            val editing = products?.firstOrNull { it.id == editorId }
            if (products == null || (editorId > 0L && editing == null)) {
                LoadingScreen()
            } else {
                key(editorId, editorUrl) {
                    ProductEditorScreen(
                        existing = editing,
                        initialUrl = editorUrl,
                        initialTitle = editorTitle,
                        snackbarHostState = snackbarHostState,
                        onBack = { leaveEditor() },
                        onSave = { draft ->
                            if (editorId == 0L) {
                                val newId = repository.add(
                                    url = draft.url,
                                    title = draft.title,
                                    targetPrice = draft.targetPrice,
                                    watchStock = draft.watchStock,
                                )
                                PriceCheckWork.checkNow(context, newId)
                                route = ROUTE_HOME
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Added. Fetching the current price…",
                                    )
                                }
                            } else {
                                val id = editorId
                                repository.update(
                                    id = id,
                                    title = draft.title,
                                    targetPrice = draft.targetPrice,
                                    watchStock = draft.watchStock,
                                )
                                PriceCheckWork.checkNow(context, id)
                                route = if (selectedId == id) ROUTE_DETAIL else ROUTE_HOME
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Saved. Checking the price now…",
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        else -> {
            WatchlistScreen(
                products = products,
                notificationsEnabled = notificationsEnabled,
                snackbarHostState = snackbarHostState,
                onAdd = ::addProduct,
                onOpen = ::openProduct,
                onEdit = { editProduct(it, fromDetail = false) },
                onOpenInBrowser = ::openInBrowser,
                onDelete = ::deleteProduct,
                onOpenNotificationSettings = onOpenNotificationSettings,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
