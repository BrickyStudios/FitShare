package com.fitshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitshare.BuildConfig
import com.fitshare.data.UpdateState
import com.fitshare.data.tr
import com.fitshare.ui.theme.Bone
import com.fitshare.ui.theme.Hairline
import com.fitshare.ui.theme.Muted
import com.fitshare.ui.theme.Raised2
import com.fitshare.ui.theme.Type
import com.fitshare.ui.theme.Volt
import com.fitshare.ui.theme.pressable

/** Update notice across the top, like Freehub's: what's new, its size or the download progress, Later / Install. */
@Composable
fun UpdateBar(state: UpdateState, onInstall: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val p = state.progress
    val mb = "%.0f MB".format(state.release.bytes / 1_000_000.0)
    // the first update leads through Android's "install unknown apps" screen; say so before it shows up
    val firstTime = !LocalContext.current.packageManager.canRequestPackageInstalls()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Raised2)
            .drawBehind { drawRoundRect(Hairline, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(1.dp.toPx())) }
            .padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(tr("FitShare ${state.release.version} ist da", "FitShare ${state.release.version} is out"), style = Type.button.copy(fontSize = Type.body.fontSize, color = Bone))
            Text(
                when {
                    state.failed -> tr("Download hat nicht geklappt", "Download failed")
                    p != null -> tr("Lädt … ${(p * 100).toInt()} %", "Downloading … ${(p * 100).toInt()} %")
                    firstTime -> tr("$mb · Android fragt einmal nach Erlaubnis", "$mb · Android asks for permission once")
                    else -> tr("Du hast ${BuildConfig.VERSION_NAME} · $mb", "You have ${BuildConfig.VERSION_NAME} · $mb")
                },
                style = Type.body.copy(fontSize = Type.label.fontSize * 1.1f, lineHeight = Type.label.fontSize * 1.5f, color = Muted),
                maxLines = 2
            )
        }
        if (p == null) {
            BarAction(tr("Später", "Later"), Muted, onLater)
            BarAction(if (state.failed) tr("Nochmal", "Retry") else tr("Installieren", "Install"), Volt, onInstall)
        }
    }
}

@Composable
private fun BarAction(text: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.pressable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Type.button.copy(fontSize = Type.body.fontSize, color = color))
    }
}
