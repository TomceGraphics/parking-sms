@file:OptIn(ExperimentalMaterial3Api::class)

package app.parkingsms.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.parkingsms.R

/** Design primitives shared by all screens: header panel, panel cards, plate badge, timer ring, big buttons. */

val PlateInk = Color(0xFF11161C)
val PlateBlue = Color(0xFF1E4FA3)

@Composable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Bold blue panel with rounded bottom corners, like a road-sign plate. */
@Composable
fun HeaderPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.primaryContainer,
        contentColor = cs.onPrimaryContainer,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp),
            content = content
        )
    }
}

@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    HeaderPanel {
        Row(modifier = Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                HeaderIconButton(onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, description = stringResource(R.string.cd_back))
                Spacer(Modifier.width(14.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1)
            actions()
        }
    }
}

@Composable
fun HeaderIconButton(onClick: () -> Unit, icon: ImageVector, description: String) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
fun LogoMark(modifier: Modifier = Modifier, size: Dp = 42.dp) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.sign_p),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.62f).sp
        )
    }
}

@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (isDarkSurface()) cs.surfaceContainer else cs.surfaceContainerLowest
    val shape = RoundedCornerShape(24.dp)
    val border = BorderStroke(1.dp, cs.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = bg, contentColor = cs.onSurface, border = border) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = bg, contentColor = cs.onSurface, border = border) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun StatusPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = false
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(content))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A license plate. Light theme: white body, dark border. Dark theme: dark body, light border. */
@Composable
fun PlateBadge(text: String, modifier: Modifier = Modifier, large: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    val body = if (dark) cs.surfaceContainerHigh else Color.White
    val ink = if (dark) cs.onSurface else PlateInk
    val borderColor = if (dark) cs.outline else PlateInk
    val strip = if (dark) cs.primaryContainer else PlateBlue
    val stripInk = if (dark) cs.onPrimaryContainer else Color.White
    val plateHeight = if (large) 50.dp else 34.dp
    val shape = RoundedCornerShape(if (large) 12.dp else 9.dp)
    Row(
        modifier = modifier.height(plateHeight).clip(shape).background(body).border(if (large) 2.dp else 1.5.dp, borderColor, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(if (large) 22.dp else 15.dp).fillMaxHeight().background(strip),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(if (large) 7.dp else 5.dp).clip(CircleShape).background(stripInk))
        }
        Text(
            text = text.ifEmpty { "-" },
            color = ink,
            fontWeight = FontWeight.Black,
            fontSize = if (large) 24.sp else 16.sp,
            letterSpacing = if (large) 2.5.sp else 1.5.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (large) 14.dp else 10.dp)
        )
    }
}

/** Circular progress ring with content in the middle. */
@Composable
fun TimerRing(
    progress: Float,
    color: Color,
    track: Color,
    diameter: Dp,
    modifier: Modifier = Modifier,
    stroke: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), animationSpec = tween(600), label = "ring")
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2f, s / 2f)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = s)
            )
            drawArc(
                color = color, startAngle = -90f, sweepAngle = 360f * animated, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = s, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun BigButton(
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    enabled: Boolean = true,
    showArrow: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.35f),
            disabledContentColor = content.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.fillMaxWidth().height(if (sublabel != null) 84.dp else 72.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1)
            if (sublabel != null) {
                Text(sublabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        if (showArrow) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
    }
}
