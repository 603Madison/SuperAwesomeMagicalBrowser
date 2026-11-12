package app.superawesomemagical.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superawesomemagical.browser.Address
import app.superawesomemagical.browser.Browser
import app.superawesomemagical.browser.BrowserTab
import app.superawesomemagical.browser.History
import app.superawesomemagical.browser.PageDialog
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** What the screen asks of the activity: sharing and copying. */
class ScreenActions(
    val share: (String, String) -> Unit,
    val copy: (String) -> Unit,
)

/**
 * The browser: tabs down the left (a drawer on a phone, opened with the tabs button; always there
 * on a tablet), the address bar above the page, and the page.
 */
@Composable
fun BrowserScreen(
    browser: Browser,
    history: History,
    messages: SharedFlow<String>,
    actions: ScreenActions,
    drawerOpen: Boolean = false,
    page: @Composable (Modifier) -> Unit,
) {
    val tab = browser.selected ?: return
    val sites by history.top.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(messages) { messages.collect { snackbar.showSnackbar(it) } }

    // A Surface, so text and icons take the theme's colours wherever nothing else sets them.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val drawer = rememberDrawerState(if (drawerOpen) DrawerValue.Open else DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

        val content = @Composable {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(!browser.fullScreen) {
                    TopBar(
                        browser, tab, actions,
                        showTabsButton = !wide,
                        onTabs = { scope.launch { drawer.open() } },
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    page(Modifier.fillMaxSize())
                    if (tab.isStart) {
                        StartPage(sites, onGo = { browser.go(it, tab) }, onRemove = history::remove)
                    }
                    if (tab.loading && !tab.isStart) {
                        LinearProgressIndicator(
                            progress = { tab.progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent,
                        )
                    }
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                if (!browser.fullScreen) {
                    TabsPanel(browser, Modifier.width(300.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {}
                }
                Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding().imePadding()) { content() }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                // Swipes belong to the page (carousels, maps), so the drawer opens from the tabs
                // button and only a swipe to close it is the drawer's.
                gesturesEnabled = drawer.isOpen,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    ) {
                        TabsPanel(browser, Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
                            scope.launch { drawer.close() }
                        }
                    }
                },
            ) {
                Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) { content() }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
    }

    browser.dialog?.let { PageDialogView(it, browser, actions) }
}

// ---- The address bar ----

@Composable
private fun TopBar(browser: Browser, tab: BrowserTab, actions: ScreenActions, showTabsButton: Boolean, onTabs: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val glow = LocalGlow.current
    Box(
        Modifier.fillMaxWidth()
            .background(scheme.background)
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(glow.a, Color.Transparent, glow.b)))
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showTabsButton) {
                // The tab count in a rounded square, at the left: tabs live on the left.
                IconButton(onClick = onTabs) {
                    Box(
                        Modifier.size(26.dp).border(2.dp, scheme.onSurfaceVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val count = browser.tabs.size
                        Text(if (count > 99) ":D" else "$count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    }
                }
            }
            AddressField(browser, tab, Modifier.weight(1f).padding(horizontal = 4.dp))
            Menu(browser, tab, actions)
        }
    }
}

@Composable
private fun AddressField(browser: Browser, tab: BrowserTab, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var editing by remember { mutableStateOf(false) }
    val shown = when {
        tab.isStart -> ""
        else -> Address.searchTerms(tab.url) ?: tab.url
    }
    var value by remember(tab.id, shown, editing) { mutableStateOf(TextFieldValue(shown, TextRange(0, shown.length))) }

    BasicTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface, fontSize = 16.sp),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = {
            browser.go(value.text, tab)
            focus.clearFocus()
        }),
        modifier = modifier.focusRequester(requester).onFocusChanged { editing = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(CircleShape)
                    .background(if (editing) scheme.surfaceContainer else scheme.surfaceContainerHigh)
                    .border(if (editing) 2.dp else 1.dp, if (editing) scheme.primary else scheme.outlineVariant, CircleShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = when {
                    tab.private -> PrivateGlyph
                    !editing && tab.secure && !tab.isStart -> Icons.Rounded.Lock
                    else -> Icons.Rounded.Search
                }
                Icon(icon, contentDescription = if (tab.private) "Private tab" else null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (!editing && !tab.isStart) {
                        // Not editing: the site's name, as most browsers on phones show it.
                        Text(
                            Address.searchTerms(tab.url) ?: Address.host(tab.url),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface,
                        )
                    } else {
                        if (value.text.isEmpty()) {
                            Text("Search with Ecosia or enter address", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                        }
                        inner()
                    }
                }
                if (editing && value.text.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close, contentDescription = "Clear",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { value = TextFieldValue("") },
                    )
                }
            }
        },
    )
}

@Composable
private fun Menu(browser: Browser, tab: BrowserTab, actions: ScreenActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Menu") }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { tab.session.goBack(); open = false }, enabled = tab.canGoBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { tab.session.goForward(); open = false }, enabled = tab.canGoForward) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { if (tab.loading) tab.session.stop() else tab.session.reload(); open = false }, enabled = !tab.isStart) {
                    Icon(if (tab.loading) Icons.Rounded.Close else Icons.Rounded.Refresh, contentDescription = if (tab.loading) "Stop" else "Reload")
                }
                IconButton(onClick = { actions.share(tab.title, tab.url); open = false }, enabled = !tab.isStart) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem("New tab", Icons.Rounded.Add) { browser.newTab(); open = false }
            MenuItem("New private tab", PrivateGlyph) { browser.newTab(private = true); open = false }
            MenuItem("Close tab", Icons.Rounded.Close) { browser.close(tab); open = false }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

// ---- Tabs down the left ----

@Composable
private fun TabsPanel(browser: Browser, modifier: Modifier, onPicked: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tabs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { browser.newTab(); onPicked() }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New tab")
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(browser.tabs, key = { it.id }) { tab ->
                val selected = tab == browser.selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (selected) scheme.primaryContainer else Color.Transparent)
                        .clickable { browser.select(tab); onPicked() }
                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabAvatar(tab, selected)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                        )
                        if (!tab.isStart) {
                            Text(
                                (if (tab.private) "Private · " else "") + Address.host(tab.pending ?: tab.url),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { browser.close(tab) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close tab", modifier = Modifier.size(18.dp),
                            tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabAvatar(tab: BrowserTab, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when {
        tab.private -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        selected -> scheme.primary to scheme.onPrimary
        else -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
        if (tab.private) {
            Icon(PrivateGlyph, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        } else {
            Text(tab.label.take(1).uppercase(), color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

// ---- A page's questions ----

@Composable
private fun PageDialogView(dialog: PageDialog, browser: Browser, actions: ScreenActions) {
    when (dialog) {
        is PageDialog.Alert -> AlertDialog(
            onDismissRequest = dialog.ok,
            title = dialog.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            text = { Text(dialog.message) },
            confirmButton = { TextButton(onClick = dialog.ok) { Text("OK") } },
        )
        is PageDialog.Confirm -> AlertDialog(
            onDismissRequest = { dialog.answer(false) },
            title = dialog.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            text = { Text(dialog.message) },
            confirmButton = { TextButton(onClick = { dialog.answer(true) }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { dialog.answer(false) }) { Text("Cancel") } },
        )
        is PageDialog.Text -> {
            var text by remember(dialog) { mutableStateOf(dialog.value) }
            AlertDialog(
                onDismissRequest = { dialog.answer(null) },
                title = dialog.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                text = {
                    Column {
                        if (dialog.message.isNotBlank()) Text(dialog.message, Modifier.padding(bottom = 12.dp))
                        OutlinedTextField(text, { text = it }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { dialog.answer(text) }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { dialog.answer(null) }) { Text("Cancel") } },
            )
        }
        is PageDialog.Choice -> {
            val chosen = remember(dialog) { mutableStateListOf(*dialog.options.filter { it.selected }.toTypedArray()) }
            AlertDialog(
                onDismissRequest = { dialog.answer(null) },
                title = dialog.title?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                text = {
                    LazyColumn {
                        items(dialog.options) { option ->
                            val on = option in chosen
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !option.disabled) {
                                        if (dialog.multiple) {
                                            if (on) chosen.remove(option) else chosen.add(option)
                                        } else {
                                            dialog.answer(listOf(option))
                                        }
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (dialog.multiple) Checkbox(on, null, enabled = !option.disabled) else RadioButton(option.selected, null, enabled = !option.disabled)
                                Text(option.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {
                    if (dialog.multiple) TextButton(onClick = { dialog.answer(chosen.toList()) }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { dialog.answer(null) }) { Text("Cancel") } },
            )
        }
        is PageDialog.LinkMenu -> {
            val close = { browser.dialog = null }
            AlertDialog(
                onDismissRequest = close,
                title = { Text(dialog.text?.takeIf { it.isNotBlank() } ?: Address.host(dialog.link ?: dialog.image.orEmpty()), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column {
                        dialog.link?.let { link ->
                            MenuItem("Open in new tab", Icons.Rounded.Add) { browser.newTab(link, dialog.tab.private, dialog.tab.id, select = false); close() }
                            if (!dialog.tab.private) {
                                MenuItem("Open in private tab", PrivateGlyph) { browser.newTab(link, private = true, openerId = dialog.tab.id, select = false); close() }
                            }
                            MenuItem("Copy link", CopyGlyph) { actions.copy(link); close() }
                            MenuItem("Share link", Icons.Rounded.Share) { actions.share("", link); close() }
                        }
                        dialog.image?.let { image ->
                            MenuItem("Open image in new tab", Icons.Rounded.Add) { browser.newTab(image, dialog.tab.private, dialog.tab.id, select = false); close() }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = close) { Text("Close") } },
            )
        }
    }
}
