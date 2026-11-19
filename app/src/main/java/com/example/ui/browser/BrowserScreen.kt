package com.example.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.security.UrlSecurityValidator
import com.example.security.UrlValidationResult
import com.example.ui.components.MediaAnalysisSheet
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalVortexColors

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onStreamUrl: (String, String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalVortexColors.current
    val snackbarHostState = remember { SnackbarHostState() }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
        }
    }

    LaunchedEffect(state.isPrivateMode) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(!state.isPrivateMode)
        if (state.isPrivateMode) {
            cookieManager.removeAllCookies(null)
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
        }
    }

    LaunchedEffect(state.securityAlert) {
        state.securityAlert?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSecurityAlert()
        }
    }

    DisposableEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString() ?: return false
                val check = UrlSecurityValidator.validate(target, checkPlatformPolicy = false)
                return check !is UrlValidationResult.Valid
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (!url.isNullOrBlank()) viewModel.onPageStarted(url)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                if (!url.isNullOrBlank()) {
                    viewModel.onMediaUrlDetected(url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!url.isNullOrBlank()) {
                    viewModel.onPageFinished(
                        url = url,
                        title = view?.title,
                        canGoBack = view?.canGoBack() ?: false,
                        canGoForward = view?.canGoForward() ?: false
                    )
                    // Only inspect public <video>/<audio> src URLs, never forms/inputs/cookies
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var urls = [];
                            var nodes = document.querySelectorAll('video[src], video source[src], audio[src], audio source[src]');
                            for (var i = 0; i < Math.min(nodes.length, 10); i++) {
                                if (nodes[i].src && nodes[i].src.indexOf('https://') === 0) {
                                    urls.push(nodes[i].src);
                                }
                            }
                            return JSON.stringify(urls);
                        })();
                        """.trimIndent()
                    ) { json ->
                        if (!json.isNullOrBlank() && json != "null") {
                            Regex("https://[^\"\\\\]+").findAll(json).forEach { match ->
                                viewModel.onMediaUrlDetected(match.value)
                            }
                        }
                    }
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.onProgressChanged(newProgress)
            }
        }
        if (webView.url == null) {
            webView.loadUrl(BrowserUiState.DEFAULT_HOME_URL)
        }
        onDispose {
            if (state.isPrivateMode) {
                CookieManager.getInstance().removeAllCookies(null)
                webView.clearCache(true)
                webView.clearHistory()
            }
        }
    }

    BackHandler(enabled = state.canGoBack) {
        if (webView.canGoBack()) {
            webView.goBack()
            viewModel.onNavigationStateChanged(webView.canGoBack(), webView.canGoForward())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Address Bar & Security Status
            GlassCard(
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = state.addressInput,
                            onValueChange = viewModel::onAddressInputChange,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (state.isPrivateMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Lock,
                                    contentDescription = if (state.isPrivateMode) "Private mode" else "HTTPS secure",
                                    tint = if (state.isPrivateMode) MaterialTheme.colorScheme.primary else extra.success,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            placeholder = { Text("Search or enter https:// URL") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    viewModel.resolveNavigationUrl(state.addressInput)?.let { webView.loadUrl(it) }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("browser_address_input")
                        )

                        IconButton(
                            onClick = { viewModel.togglePrivateMode() },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (state.isPrivateMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .testTag("browser_private_toggle")
                        ) {
                            Icon(
                                Icons.Rounded.VisibilityOff,
                                contentDescription = "Toggle private browsing",
                                tint = if (state.isPrivateMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            IconButton(
                                onClick = { if (webView.canGoBack()) webView.goBack() },
                                enabled = state.canGoBack,
                                modifier = Modifier.testTag("browser_back_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                            IconButton(
                                onClick = { if (webView.canGoForward()) webView.goForward() },
                                enabled = state.canGoForward,
                                modifier = Modifier.testTag("browser_forward_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                            }
                            IconButton(
                                onClick = { webView.reload() },
                                modifier = Modifier.testTag("browser_refresh_btn")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                            }
                            IconButton(
                                onClick = { webView.loadUrl(BrowserUiState.DEFAULT_HOME_URL) },
                                modifier = Modifier.testTag("browser_home_btn")
                            ) {
                                Icon(Icons.Rounded.Home, contentDescription = "Browser home")
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.isPrivateMode) {
                                Text(
                                    text = "PRIVATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.analyzeUrl(state.currentUrl) },
                                modifier = Modifier.testTag("browser_analyze_page_btn")
                            ) {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = "Scan current page for authorized media",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setShowHistorySheet(true) },
                                modifier = Modifier.testTag("browser_history_btn")
                            ) {
                                Icon(Icons.Rounded.History, contentDescription = "Browser history")
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { (state.progress / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Floating Detected Media Pill
        AnimatedVisibility(
            visible = state.detectedMediaUrls.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = { viewModel.setShowDetectedSheet(true) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                text = { Text("Media (${state.detectedMediaUrls.size})") },
                modifier = Modifier.testTag("browser_detected_media_fab")
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Detected Media Sheet
    if (state.showDetectedSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowDetectedSheet(false) },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = extra.success)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorized Media Detected", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(260.dp)
                ) {
                    items(state.detectedMediaUrls) { mediaUrl ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.analyzeUrl(mediaUrl) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = mediaUrl,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(onClick = { viewModel.analyzeUrl(mediaUrl) }) {
                                    Text("Analyze")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Browser History Sheet
    if (state.showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setShowHistorySheet(false) },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Browser History", style = MaterialTheme.typography.titleLarge)
                    if (state.history.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearHistory) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear browser history")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (state.history.isEmpty()) {
                    Text(
                        text = "No browsing history saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(280.dp)
                    ) {
                        items(state.history, key = { it.id }) { entry ->
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setShowHistorySheet(false)
                                        viewModel.resolveNavigationUrl(entry.url)?.let { webView.loadUrl(it) }
                                    }
                            ) {
                                Column {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showAnalysisSheet) {
        MediaAnalysisSheet(
            isAnalyzing = state.isAnalyzing,
            result = state.analysisResult,
            onDismiss = viewModel::dismissAnalysisSheet,
            onStartDownload = viewModel::startDownload,
            onStreamPlay = { streamUrl, title ->
                viewModel.dismissAnalysisSheet()
                onStreamUrl(streamUrl, title)
            }
        )
    }
}
