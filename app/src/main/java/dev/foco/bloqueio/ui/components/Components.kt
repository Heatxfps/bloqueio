package dev.foco.bloqueio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.foco.bloqueio.ui.theme.Bloqueio
import dev.foco.bloqueio.ui.theme.BloqueioType
import dev.foco.bloqueio.ui.theme.Radius
import dev.foco.bloqueio.ui.theme.Spacing

/** Título grande à esquerda + conteúdo rolável sobre o fundo agrupado. */
@Composable
fun LargeTitleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Bloqueio.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = BloqueioType.largeTitle, color = Bloqueio.colors.label, modifier = Modifier.weight(1f))
            trailing()
        }
        Spacer(Modifier.height(12.dp))
        content()
        Spacer(Modifier.height(Spacing.section))
    }
}

/** Seção agrupada: cabeçalho em caption, bloco arredondado e rodapé. */
@Composable
fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    locked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.padding(top = 20.dp)) {
        if (header != null) {
            Row(Modifier.padding(start = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(header.uppercase(), style = BloqueioType.caption, color = Bloqueio.colors.secondaryLabel)
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    LockBadge()
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.cell))
                .background(Bloqueio.colors.cell),
            content = content,
        )
        if (footer != null) {
            Text(
                footer, style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

/** Quadrado arredondado colorido com ícone branco (estilo Ajustes do iOS). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 29.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/** Ícone real de um app instalado (ou um quadrado cinza se não estiver instalado). */
@Composable
fun AppIcon(packageName: String, size: Dp = 29.dp) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }
            .getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(7.dp)).background(Bloqueio.colors.gray))
    }
}

@Composable
fun Cell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    titleColor: Color? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(modifier.then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.cellMinHeight)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title, style = BloqueioType.body, color = titleColor ?: Bloqueio.colors.label,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle, style = BloqueioType.footnote, color = Bloqueio.colors.secondaryLabel,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    value, style = BloqueioType.body, color = valueColor ?: Bloqueio.colors.secondaryLabel,
                    textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 180.dp),
                )
            }
            trailing?.invoke()
            if (chevron) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = Bloqueio.colors.secondaryLabel.copy(alpha = 0.5f),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                Modifier.padding(start = if (leading != null) 59.dp else 16.dp),
                thickness = 0.5.dp, color = Bloqueio.colors.separator,
            )
        }
    }
}

/**
 * Toggle verde estilo iOS (51×31). Com [onCheckedChange] ele mesmo é focável pelo leitor
 * de tela (Role.Switch); dentro de uma linha prefira [SwitchCell], que torna a linha toda tocável.
 */
@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) Bloqueio.colors.success else Bloqueio.colors.cellSecondary, label = "track")
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, label = "thumb")
    Box(
        Modifier
            .then(
                if (onCheckedChange != null) {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(51.dp, 31.dp)
                .clip(CircleShape)
                .background(if (enabled) track else track.copy(alpha = 0.4f)),
        ) {
            Box(
                Modifier
                    .padding(start = offset, top = 2.dp)
                    .size(27.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** Linha com switch: a linha inteira é o alvo de toque e o título é o rótulo lido pelo TalkBack. */
@Composable
fun SwitchCell(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    Cell(
        title = title,
        subtitle = subtitle,
        showDivider = showDivider,
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = { IosSwitch(checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Bloqueio.colors.accentFill,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(Radius.button))
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BloqueioType.headline, color = Color.White, textAlign = TextAlign.Center)
    }
}

/** Controle segmentado do iOS, com semântica de grupo selecionável e alvos de 44 dp. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Bloqueio.colors.cellSecondary)
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) Bloqueio.colors.cell else Color.Transparent)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = BloqueioType.callout,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    color = Bloqueio.colors.label,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun LockBadge() {
    Icon(Icons.Rounded.Lock, "Só o admin edita", tint = Bloqueio.colors.secondaryLabel, modifier = Modifier.size(12.dp))
}

/**
 * Anel duplo estilo Atividade: externo = redes (rosa), interno = jogos (índigo).
 * Progresso = fração RESTANTE.
 */
@Composable
fun QuotaRing(socialLeft: Float, gamesLeft: Float, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val social = Bloqueio.colors.social
    val games = Bloqueio.colors.games
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.12f
        fun ring(inset: Float, color: Color, fraction: Float) {
            val d = this.size.minDimension - inset * 2 - stroke
            val topLeft = Offset(inset + stroke / 2, inset + stroke / 2)
            drawArc(color.copy(alpha = 0.2f), 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            drawArc(
                color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, Size(d, d),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        ring(0f, social, socialLeft)
        ring(stroke * 1.15f, games, gamesLeft)
    }
}
