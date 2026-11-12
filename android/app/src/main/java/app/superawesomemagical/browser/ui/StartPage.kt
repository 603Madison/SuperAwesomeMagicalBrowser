package app.superawesomemagical.browser.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superawesomemagical.browser.Address
import app.superawesomemagical.browser.History
import app.superawesomemagical.browser.R
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The start page (a new tab), in the look of SuperAwesomeMagicalLinux's sign-in screen and the
 * desktop browser's: a big Lexend clock and a rounded slate card with search and the most visited
 * sites, over the northern lights (dark) or soft aurora glows (light).
 */
@Composable
fun StartPage(
    sites: List<History.Site>,
    onGo: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDark.current
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (dark) {
            Image(
                painterResource(R.drawable.aurora), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x1A0B1220), 0.55f to Color(0x590B1220), 1f to Color(0xCC0B1220),
                    ),
                ),
            )
        } else {
            LightGlows(Modifier.fillMaxSize())
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Clock(dark)
            Spacer(Modifier.height(36.dp))
            SearchCard(sites, onGo, onRemove, dark)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun LightGlows(modifier: Modifier) {
    Box(
        modifier.drawBehind {
            fun glow(color: Color, x: Float, y: Float, r: Float) = drawRect(
                Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(size.width * x, size.height * y), radius = size.maxDimension * r),
            )
            glow(Color(0x8CB9A6FF), 0.12f, 0.06f, 0.55f)
            glow(Color(0x808FE6C8), 0.9f, 0.16f, 0.5f)
            glow(Color(0x478DB4FF), 0.3f, 1.02f, 0.6f)
        },
    )
}

@Composable
private fun Clock(dark: Boolean) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (now.second * 1000L + now.nano / 1_000_000L) + 50)
        }
    }
    val twentyFour = DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (twentyFour) "H:mm" else "h:mm"))
    val period = if (twentyFour) null else now.format(DateTimeFormatter.ofPattern("a"))
    val muted = if (dark) Color(0xFFC3CBD8) else MaterialTheme.colorScheme.onSurfaceVariant
    val ink = if (dark) Color(0xFFF5F7FB) else MaterialTheme.colorScheme.onBackground
    Text(
        buildAnnotatedString {
            append(time)
            if (period != null) {
                withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, color = muted)) { append(" $period") }
            }
        },
        fontFamily = Lexend, fontWeight = FontWeight.Light, fontSize = 88.sp, lineHeight = 88.sp,
        letterSpacing = (-1.5).sp, color = ink,
    )
    Text(
        now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)).substringBeforeLast(", 2"),
        style = MaterialTheme.typography.titleMedium, color = muted, fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun SearchCard(sites: List<History.Site>, onGo: (String) -> Unit, onRemove: (String) -> Unit, dark: Boolean) {
    val card = if (dark) Color(0xB8131D30) else Color(0xC7FFFFFF)
    val outline = if (dark) Color(0xB33B4A63) else Color(0xF2DDE3EC)
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(card)
            .border(1.dp, outline, RoundedCornerShape(28.dp))
            .padding(18.dp),
    ) {
        SearchField(onGo, dark)
        if (sites.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            val columns = 4
            sites.chunked(columns).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEachIndexed { c, site -> SiteTile(site, r * columns + c, onGo, onRemove, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(onGo: (String) -> Unit, dark: Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val field = if (dark) Color(0xD91A2539) else Color.White
    val outline = if (dark) Color(0xE63B4A63) else scheme.outlineVariant
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface, fontSize = 17.sp),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = {
            if (text.isNotBlank()) onGo(text)
            text = ""
            focus.clearFocus()
        }),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(54.dp)
                    .clip(CircleShape).background(field).border(1.dp, outline, CircleShape)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text("Search with Ecosia or enter address", color = scheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    }
                    inner()
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(site: History.Site, index: Int, onGo: (String) -> Unit, onRemove: (String) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    // Tinted like the apps' avatars, in turn: purple, green, blue.
    val (bg, fg) = when (index % 3) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val name = site.name ?: Address.siteName(site.url)
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = { onGo(site.url) }, onLongClick = { onRemove(site.url) }, onLongClickLabel = "Remove from start page")
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(name.take(1), color = fg, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        }
        Text(
            name, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
