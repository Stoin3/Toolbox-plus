package nl.toolboxplus.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.ContextCompat
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.toolboxplus.Lights
import nl.toolboxplus.Lights.Connection
import nl.toolboxplus.R
import nl.toolboxplus.agui.AguiService
import nl.toolboxplus.agui.ProjectorService
import nl.toolboxplus.tiles.lampModeLabel
import kotlin.math.roundToInt

@Composable
fun App() {
    val context = LocalContext.current
    val connection by Lights.connection.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }

    LaunchedEffect(Unit) {
        Lights.errors.collect { showMessage(it) }
    }

    val connected = connection == Connection.Connected
    val projector by Lights.projectorState.collectAsStateWithLifecycle()
    val range by Lights.range.collectAsStateWithLifecycle()
    val tabs = buildList {
        add(Tab.Lamp)
        add(Tab.Signal)
        if (projector.available) add(Tab.Projector)
        if (range.available) add(Tab.Range)
    }
    var selected by rememberSaveable { mutableStateOf(Tab.Lamp) }
    val tab = selected.takeIf { it in tabs } ?: Tab.Lamp

    Scaffold(
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (connected) {
                NavigationBar(containerColor = Palette.Surface) {
                    tabs.forEach { item ->
                        NavigationBarItem(
                            selected = item == tab,
                            onClick = { selected = item },
                            icon = { Icon(painterResource(item.icon), null) },
                            label = { Text(stringResource(item.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF15110A),
                                selectedTextColor = Palette.Text,
                                indicatorColor = item.accent,
                                unselectedIconColor = Palette.Muted,
                                unselectedTextColor = Palette.Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(if (connected) stringResource(tab.label) else stringResource(R.string.app_name), connected)
            if (connected) {
                when (tab) {
                    Tab.Lamp -> LampCard()
                    Tab.Signal -> WarningCard(showMessage)
                    Tab.Projector -> ProjectorScreen(projector)
                    Tab.Range -> RangeScreen(range)
                }
            } else {
                SetupCard(connection)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private enum class Tab(@StringRes val label: Int, @DrawableRes val icon: Int, val accent: Color) {
    Lamp(R.string.tab_lamp, R.drawable.ic_lamp, Palette.Amber),
    Signal(R.string.tab_signal, R.drawable.ic_beacon, Palette.Red),
    Projector(R.string.tab_projector, R.drawable.ic_projector, Palette.Blue),
    Range(R.string.tab_range, R.drawable.ic_ruler, Palette.Green),
}

@Composable
private fun Header(title: String, connected: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.weight(1f))
        if (connected) {
            Pill(stringResource(R.string.connected), Palette.Green, active = true)
        }
    }
}

@Composable
private fun LampCard() {
    val lamp by Lights.lamp.collectAsStateWithLifecycle()
    val modes = listOf(
        AguiService.LAMP_QUARTER,
        AguiService.LAMP_HALF,
        AguiService.LAMP_FULL,
        AguiService.LAMP_FLASH,
        AguiService.LAMP_SOS,
    )

    SectionCard(
        title = stringResource(R.string.camping_lamp),
        icon = R.drawable.ic_lamp,
        accent = Palette.Amber,
        status = stringResource(lampModeLabel(lamp.mode)),
        active = lamp.isOn,
    ) {
        GlowButton(
            active = lamp.isOn,
            glow = Palette.Amber,
            icon = R.drawable.ic_lamp,
            description = stringResource(R.string.lamp_toggle),
            onClick = Lights::toggleLamp,
        )
        // Continuous full brightness runs in a foreground service whose notification has an off button.
        val context = LocalContext.current
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            Lights.setLampMode(AguiService.LAMP_FULL)
        }
        Segmented(
            options = modes.map { it to stringResource(lampModeLabel(it)) },
            selected = lamp.mode,
            accent = Palette.Amber,
            onSelect = { mode ->
                val needsPermission = mode == AguiService.LAMP_FULL && mode != lamp.mode &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Lights.setLampMode(if (mode == lamp.mode) AguiService.LAMP_OFF else mode)
                }
            },
        )
        AnimatedVisibility(lamp.mode == AguiService.LAMP_FULL) {
            Text(
                stringResource(R.string.lamp_full_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun WarningCard(showMessage: (String) -> Unit) {
    val warning by Lights.warning.collectAsStateWithLifecycle()
    val keepOneColor = stringResource(R.string.keep_one_color)
    val lights = listOf(
        Triple(AguiService.LIGHT_RED, R.string.color_red, Palette.Red),
        Triple(AguiService.LIGHT_BLUE, R.string.color_blue, Palette.Blue),
        Triple(AguiService.LIGHT_GREEN, R.string.color_green, Palette.Green),
    )
    val enabledColors = lights.filter { warning.isEnabled(it.first) }.map { it.third }.ifEmpty { listOf(Palette.Red) }

    // Cycle through the enabled colours like the real beacon while it is running.
    val transition = rememberInfiniteTransition(label = "beacon")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = enabledColors.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(450 * enabledColors.size, easing = LinearEasing)),
        label = "phase",
    )
    val glow = if (warning.playing && warning.style == AguiService.STYLE_FLASHING) {
        enabledColors[phase.toInt().coerceIn(0, enabledColors.lastIndex)]
    } else {
        enabledColors.first()
    }

    SectionCard(
        title = stringResource(R.string.warning_lights),
        icon = R.drawable.ic_beacon,
        accent = Palette.Red,
        status = stringResource(if (warning.playing) R.string.state_active else R.string.state_off),
        active = warning.playing,
    ) {
        GlowButton(
            active = warning.playing,
            glow = glow,
            icon = R.drawable.ic_beacon,
            description = stringResource(R.string.warning_toggle),
            onClick = Lights::toggleWarning,
        )

        Label(stringResource(R.string.colors))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            lights.forEach { (id, label, color) ->
                val enabled = warning.isEnabled(id)
                ColorChip(
                    label = stringResource(label),
                    color = color,
                    selected = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val others = warning.enabledLights and id.inv()
                        if (enabled && others == 0) showMessage(keepOneColor) else Lights.setLightEnabled(id, !enabled)
                    },
                )
            }
        }

        Label(stringResource(R.string.pattern))
        Segmented(
            options = listOf(
                AguiService.STYLE_FLASHING to stringResource(R.string.style_flashing),
                AguiService.STYLE_ALWAYS_ON to stringResource(R.string.style_always_on),
            ),
            selected = warning.style,
            accent = Palette.Text,
            onSelect = Lights::setWarningStyle,
        )

        Label(stringResource(R.string.siren))
        SirenPicker(warning.siren)
    }
}

@Composable
internal fun SectionCard(
    title: String,
    @DrawableRes icon: Int,
    accent: Color,
    status: String,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.Outline),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Pill(status, accent, active)
            }
            content()
        }
    }
}

@Composable
internal fun GlowButton(
    active: Boolean,
    glow: Color,
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
) {
    val fill by animateColorAsState(if (active) glow else Palette.SurfaceHigh, tween(250), label = "fill")
    val tint by animateColorAsState(if (active) Color(0xFF15110A) else Palette.Muted, tween(250), label = "tint")
    val glowAlpha by animateFloatAsState(if (active) 1f else 0f, tween(400), label = "glow")

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(fill.copy(alpha = 0.55f * glowAlpha), Color.Transparent),
                        center = center,
                        radius = size.height / 2,
                    ),
                    radius = size.height / 2,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, if (active) Color.Transparent else Palette.Outline, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(56.dp))
        }
    }
}

@Composable
internal fun Segmented(
    options: List<Pair<Int, String>>,
    selected: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.SurfaceHigh)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val background by animateColorAsState(if (isSelected) accent else Color.Transparent, label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(background)
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color(0xFF15110A) else Palette.Text,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ColorChip(
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(if (selected) color.copy(alpha = 0.18f) else Palette.SurfaceHigh, label = "chip")
    val border by animateColorAsState(if (selected) color else Palette.Outline, label = "chipBorder")
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (selected) color else Palette.Muted.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Text else Palette.Muted)
    }
}

@Composable
internal fun Pill(text: String, color: Color, active: Boolean) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (active) color.copy(alpha = 0.15f) else Palette.SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) color else Palette.Muted),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (active) color else Palette.Muted)
    }
}

@Composable
internal fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Palette.Muted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SirenPicker(current: String) {
    val titles = stringArrayResource(R.array.siren_titles)
    val noneTitle = stringResource(R.string.siren_none)
    val sirens = remember(titles) {
        listOf(AguiService.SIREN_NONE to noneTitle) +
            (1..AguiService.SIREN_COUNT).map { "siren_$it" to titles[it - 1] }
    }
    val currentTitle = sirens.firstOrNull { it.first == current }?.second ?: current
    var open by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceHigh)
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(currentTitle, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.Muted)
    }

    if (open) {
        val context = LocalContext.current
        val preview = remember { SirenPreview(context) }
        DisposableEffect(Unit) { onDispose { preview.stop() } }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = Palette.Surface,
        ) {
            Text(
                stringResource(R.string.choose_siren),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
                items(sirens, key = { it.first }) { (name, title) ->
                    val selected = name == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { Lights.setSiren(name) }
                            .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(32.dp)) {
                            if (selected) Icon(Icons.Rounded.Check, null, tint = Palette.Amber)
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) Palette.Amber else Palette.Text,
                            modifier = Modifier.weight(1f),
                        )
                        if (name != AguiService.SIREN_NONE) {
                            val playing = preview.playing == name
                            IconButton(onClick = { preview.toggle(name) }) {
                                Icon(
                                    if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    stringResource(if (playing) R.string.stop_preview else R.string.preview),
                                    tint = if (playing) Palette.Amber else Palette.Muted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Plays the stock app's siren sounds so they can be auditioned without starting the beacon. */
private class SirenPreview(private val context: Context) {
    var playing by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    fun toggle(name: String) {
        if (playing == name) stop() else play(name)
    }

    private fun play(name: String) {
        stop()
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
                )
                setDataSource(context, "android.resource://com.agui.warninglights/raw/$name".toUri())
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.onSuccess {
            player = it
            playing = name
        }
    }

    fun stop() {
        player?.release()
        player = null
        playing = null
    }
}

@Composable
private fun SetupCard(connection: Connection) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.Outline),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Palette.Amber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_lamp), null, tint = Palette.Amber, modifier = Modifier.size(36.dp))
            }

            if (connection == Connection.Connecting) {
                CircularProgressIndicator(color = Palette.Amber)
                Text(stringResource(R.string.setup_connecting), color = Palette.Muted)
                return@Column
            }

            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.setup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                textAlign = TextAlign.Center,
            )
            Text(
                when (connection) {
                    Connection.ShizukuMissing -> stringResource(R.string.setup_missing)
                    Connection.ShizukuStopped -> stringResource(R.string.setup_stopped)
                    Connection.PermissionNeeded -> stringResource(R.string.setup_permission)
                    Connection.PermissionDenied -> stringResource(R.string.setup_denied)
                    is Connection.Failed -> stringResource(R.string.setup_failed, connection.message)
                    else -> ""
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )

            val primaryModifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
            when (connection) {
                Connection.ShizukuMissing -> Button(
                    onClick = { openShizukuDownload(context) },
                    modifier = primaryModifier,
                ) { Text(stringResource(R.string.get_shizuku)) }

                Connection.ShizukuStopped -> Button(
                    onClick = { openShizuku(context) },
                    modifier = primaryModifier,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start_shizuku))
                }

                Connection.PermissionDenied -> Button(
                    onClick = { openShizuku(context) },
                    modifier = primaryModifier,
                ) { Text(stringResource(R.string.open_shizuku)) }

                Connection.PermissionNeeded -> Button(
                    onClick = Lights::requestPermission,
                    modifier = primaryModifier,
                ) { Text(stringResource(R.string.grant_permission)) }

                else -> Unit
            }
            OutlinedButton(onClick = Lights::connect, modifier = primaryModifier) {
                Text(stringResource(R.string.retry))
            }

            var showGuide by remember { mutableStateOf(connection == Connection.ShizukuMissing) }
            TextButton(onClick = { showGuide = true }) {
                Icon(Icons.AutoMirrored.Rounded.HelpOutline, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.show_guide))
            }
            if (showGuide) {
                ShizukuGuide(onDismiss = { showGuide = false })
            }
        }
    }
}

fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(Lights.SHIZUKU_PACKAGE)
    if (launch != null) context.startActivity(launch) else openShizukuDownload(context)
}

private fun openShizukuDownload(context: Context) {
    val store = Intent(Intent.ACTION_VIEW, "market://details?id=${Lights.SHIZUKU_PACKAGE}".toUri())
    try {
        context.startActivity(store)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/RikkaApps/Shizuku/releases".toUri()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuGuide(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val setupTitles = stringArrayResource(R.array.guide_setup_titles)
    val setupBodies = stringArrayResource(R.array.guide_setup_bodies)
    val rebootSteps = stringArrayResource(R.array.guide_reboot_bodies)
    val tips = stringArrayResource(R.array.guide_tips)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            item {
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
            item { Label(stringResource(R.string.guide_setup_header)) }
            items(setupTitles.indices.toList()) { i ->
                GuideStep(number = i + 1, title = setupTitles[i], body = setupBodies[i])
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openShizukuDownload(context) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.get_shizuku), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.developer_options), maxLines = 1)
                    }
                }
            }
            item { Label(stringResource(R.string.guide_reboot_header)) }
            items(rebootSteps.indices.toList()) { i ->
                GuideStep(number = i + 1, title = null, body = rebootSteps[i])
            }
            item { Label(stringResource(R.string.guide_tips_header)) }
            items(tips.toList()) { tip ->
                Row {
                    Text("•", color = Palette.Amber, modifier = Modifier.width(20.dp))
                    Text(tip, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: String?, body: String) {
    Row {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Palette.Amber.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = Palette.Amber)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = if (title != null) Palette.Muted else Palette.Text)
        }
    }
}
