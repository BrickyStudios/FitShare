package com.fitshare.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0C0D0B)
val Raised = Color(0xFF171815)
val Raised2 = Color(0xFF22231F)
val Bone = Color(0xFFF4F1EA)
val Muted = Bone.copy(alpha = 0.6f)
val Hairline = Bone.copy(alpha = 0.12f)
val Volt = Color(0xFFC6F432)
val OnAccent = Color(0xFF14140F)

class FitType(
    val hero: TextStyle,
    val title: TextStyle,
    val label: TextStyle,
    val body: TextStyle,
    val button: TextStyle
)

/** Apple-style response ≈ 0.3 s, critically damped: no wobble on plain UI. */
fun <T> uiSpring() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

lateinit var Type: FitType
    private set

@Composable
fun FitShareTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    Type = remember {
        val display = FontFamily(Fonts.display(ctx, 900))
        FitType(
            // large type: tight leading, negative tracking; small labels: open tracking
            hero = TextStyle(fontFamily = display, fontSize = 84.sp, lineHeight = 76.sp, letterSpacing = (-0.01).em, color = Bone),
            title = TextStyle(fontFamily = FontFamily(Fonts.display(ctx, 800)), fontSize = 26.sp, lineHeight = 28.sp, color = Bone),
            label = TextStyle(fontFamily = FontFamily(Fonts.text(ctx, 700)), fontSize = 11.sp, letterSpacing = 0.18.em, color = Muted),
            body = TextStyle(fontFamily = FontFamily(Fonts.text(ctx, 450)), fontSize = 15.sp, lineHeight = 22.sp, color = Muted),
            button = TextStyle(fontFamily = FontFamily(Fonts.text(ctx, 700)), fontSize = 16.sp, letterSpacing = 0.02.em)
        )
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Volt, onPrimary = OnAccent, background = Ink, surface = Raised,
            onBackground = Bone, onSurface = Bone, surfaceVariant = Raised2, onSurfaceVariant = Muted
        ),
        content = content
    )
}


@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, uiSpring(), label = "press")
    return scale
}

/** Shrinks on touch-down (not on release) and springs back; the press feedback for every tappable surface. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // combinedClickable with an interaction source
fun Modifier.pressable(enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    composed {
        val source = remember { MutableInteractionSource() }
        val scale = pressScale(source)
        this
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(source, indication = null, enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
    }

@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Volt,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(color, uiSpring(), label = "btn")
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .pressable(enabled = !busy, onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(color = OnAccent, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
        else Text(text, style = Type.button, color = OnAccent)
    }
}
