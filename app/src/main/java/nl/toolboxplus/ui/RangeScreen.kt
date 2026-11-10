package nl.toolboxplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import nl.toolboxplus.Lights
import nl.toolboxplus.R
import java.util.Locale

@Composable
internal fun RangeScreen(state: Lights.RangeState) {
    // The sensor must not keep running when the screen is left.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { Lights.stopMeasuring() }

    SectionCard(
        title = stringResource(R.string.rangefinder),
        icon = R.drawable.ic_ruler,
        accent = Palette.Green,
        status = stringResource(if (state.measuring) R.string.state_measuring else R.string.state_off),
        active = state.measuring,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cm = state.lastCm
            when {
                state.noReading -> Text(
                    stringResource(R.string.no_reading),
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Muted,
                    textAlign = TextAlign.Center,
                )
                cm == null -> Text("—", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = Palette.Muted)
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatCm(cm),
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.measuring) Palette.Green else Palette.Text,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("cm", style = MaterialTheme.typography.titleLarge, color = Palette.Muted,
                            modifier = Modifier.padding(bottom = 14.dp))
                    }
                    Text(formatMetres(cm), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { Lights.measure(continuous = false) },
                enabled = !state.measuring,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Green, contentColor = Color(0xFF07140C)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(Icons.Rounded.Straighten, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.measure_once))
            }
            OutlinedButton(
                onClick = { if (state.continuous) Lights.stopMeasuring() else Lights.measure(continuous = true) },
                enabled = state.continuous || !state.measuring,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Text(stringResource(if (state.continuous) R.string.measure_stop else R.string.measure_live))
            }
        }
        if (state.continuous) {
            TextButton(onClick = Lights::keepReading, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.keep_reading))
            }
        }
        Text(
            stringResource(R.string.rangefinder_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Muted,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
        )
        ToggleRow(
            title = stringResource(R.string.measure_from_bottom),
            subtitle = stringResource(R.string.measure_from_bottom_hint),
            checked = state.fromBottomEdge,
            accent = Palette.Green,
            onCheckedChange = Lights::setMeasureFromBottomEdge,
        )

        if (state.history.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.history))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = Lights::clearHistory, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.clear))
                }
            }
            state.history.forEachIndexed { i, value ->
                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Text("${i + 1}.", color = Palette.Muted, modifier = Modifier.width(32.dp))
                    Text("${formatCm(value)} cm", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(formatMetres(value), color = Palette.Muted)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun formatCm(cm: Float) =
    if (cm % 1f == 0f) "%.0f".format(Locale.ENGLISH, cm) else "%.1f".format(Locale.ENGLISH, cm)

private fun formatMetres(cm: Float) = "%.2f m".format(Locale.ENGLISH, cm / 100f)
