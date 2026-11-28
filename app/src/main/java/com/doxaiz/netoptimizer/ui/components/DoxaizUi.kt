package com.doxaiz.netoptimizer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.ui.theme.AccentAmber
import com.doxaiz.netoptimizer.ui.theme.AccentCyan
import com.doxaiz.netoptimizer.ui.theme.AccentGreen
import com.doxaiz.netoptimizer.ui.theme.AccentRed
import com.doxaiz.netoptimizer.ui.theme.AccentViolet

/** Shared visual language: one place for cards, section labels and status pills. */
object DoxaizStyle {
    val CardShape = RoundedCornerShape(20.dp)
    val PillShape = CircleShape
    val HeroShape = RoundedCornerShape(28.dp)
    val Gap = 12.dp
}

/**
 * Section heading above a group of cards. The label keeps the exact uppercase text the
 * emulator checks look for, while the optional caption explains the group.
 */
@Composable
fun SectionHeader(label: String, caption: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(listOf(AccentCyan, AccentViolet)))
            )
            Spacer(Modifier.size(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** Standard surface for content, with a hairline border for definition on dark themes. */
@Composable
fun DoxaizCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DoxaizStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.35f) ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** Hero panel used for the single most important value on a screen. */
@Composable
fun HeroPanel(
    title: String,
    value: String,
    caption: String?,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.primary,
    accent: Color = AccentCyan,
    content: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DoxaizStyle.HeroShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.22f), Color.Transparent, MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
                )
            )
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                content?.invoke()
            }
        }
    }
}

enum class PillTone { GOOD, WARNING, BAD, NEUTRAL }

@Composable
private fun toneColor(tone: PillTone): Color = when (tone) {
    PillTone.GOOD -> AccentGreen
    PillTone.WARNING -> AccentAmber
    PillTone.BAD -> AccentRed
    PillTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Compact status chip; colour is always paired with text so it stays readable. */
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val color = toneColor(tone)
    Surface(
        modifier = modifier,
        shape = DoxaizStyle.PillShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.size(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

/** Small metric tile: label, value and one explanatory line. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Thin divider with breathing room, used between unrelated blocks. */
@Composable
fun SectionGap(height: Int = 4) = Spacer(Modifier.height(height.dp))
