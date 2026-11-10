package nl.toolboxplus.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.toolboxplus.Lights
import nl.toolboxplus.R
import nl.toolboxplus.agui.ProjectorService
import kotlin.math.roundToInt

@Composable
internal fun ProjectorScreen(state: Lights.ProjectorState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProjectorCard(state)
        ProjectorExtras(state)
        if (state.fanAvailable) FanCard(state)
    }
}

@Composable
private fun ProjectorCard(state: Lights.ProjectorState) {
    SectionCard(
        title = stringResource(R.string.projector),
        icon = R.drawable.ic_projector,
        accent = Palette.Blue,
        status = stringResource(if (state.on) R.string.state_on else R.string.state_off),
        active = state.on,
    ) {
        GlowButton(
            active = state.on,
            glow = Palette.Blue,
            icon = R.drawable.ic_projector,
            description = stringResource(R.string.projector_toggle),
            onClick = { Lights.setProjectorPower(!state.on) },
        )
        Text(
            stringResource(R.string.projector_power_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        // Keep the slider under the finger while dragging; polling would otherwise reset it.
        var dragging by remember { mutableStateOf<Float?>(null) }
        val brightness = dragging ?: state.brightness.toFloat()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.brightness))
            Spacer(Modifier.weight(1f))
            Text(
                "${(brightness / ProjectorService.BRIGHTNESS_MAX * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Text,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Slider(
            value = brightness,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { Lights.setProjectorBrightness(it.roundToInt()) }
                dragging = null
            },
            valueRange = 0f..ProjectorService.BRIGHTNESS_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Palette.Blue,
                activeTrackColor = Palette.Blue,
                inactiveTrackColor = Palette.SurfaceHigh,
            ),
        )
        if (!state.on) {
            Text(
                stringResource(R.string.brightness_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
            )
        }

        Label(stringResource(R.string.keystone))
        ToggleRow(
            title = stringResource(R.string.auto_keystone),
            subtitle = stringResource(R.string.auto_keystone_hint),
            checked = state.autoKeystone,
            accent = Palette.Blue,
            onCheckedChange = Lights::setAutoKeystone,
        )
    }
}

/** Features that only the stock projector app or Android itself can provide. */
@Composable
private fun ProjectorExtras(state: Lights.ProjectorState) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.Outline),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.focusAvailable) {
                ActionRow(
                    title = stringResource(R.string.focus),
                    subtitle = stringResource(if (state.on) R.string.focus_hint else R.string.focus_needs_projector),
                    enabled = state.on,
                    onClick = { context.launch(PROJECTOR_PACKAGE, "$PROJECTOR_PACKAGE.calibration.Nds03FocalizationActivity") },
                )
            }
            ActionRow(
                title = stringResource(R.string.projector_all_settings),
                subtitle = stringResource(R.string.projector_all_settings_hint),
                onClick = { context.launch(PROJECTOR_PACKAGE, "$PROJECTOR_PACKAGE.settings.MainActivity") },
            )
            ActionRow(
                title = stringResource(R.string.pin_tip_title),
                subtitle = stringResource(R.string.pin_tip),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
        }
    }
}

@Composable
private fun FanCard(state: Lights.ProjectorState) {
    SectionCard(
        title = stringResource(R.string.fan),
        icon = R.drawable.ic_fan,
        accent = Palette.Green,
        status = stringResource(if (state.fanRunning) R.string.state_on else R.string.state_off),
        active = state.fanRunning,
    ) {
        Spacer(Modifier.height(16.dp))
        ToggleRow(
            title = stringResource(R.string.fan_cooling),
            subtitle = stringResource(R.string.fan_hint),
            checked = state.fanRunning,
            accent = Palette.Green,
            onCheckedChange = Lights::setFanRunning,
        )
        Label(stringResource(R.string.fan_speed))
        Segmented(
            options = ProjectorService.FAN_LEVELS.mapIndexed { i, level -> level to "${i + 1}" },
            selected = if (state.fanRunning) state.fanLevel else -1,
            accent = Palette.Green,
            onSelect = Lights::setFanLevel,
        )
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceHigh)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF15110A),
                checkedTrackColor = accent,
                uncheckedThumbColor = Palette.Muted,
                uncheckedTrackColor = Palette.Surface,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.SurfaceHigh)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Palette.Muted)
    }
}

private const val PROJECTOR_PACKAGE = "com.agui.projector"

private fun Context.launch(packageName: String, className: String) {
    try {
        startActivity(Intent().setClassName(packageName, className).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
    }
}
