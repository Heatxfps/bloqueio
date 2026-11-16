package dev.foco.bloqueio.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.foco.bloqueio.ui.theme.LocalBottomBarInset
import dev.foco.bloqueio.ui.theme.LocalTilt
import dev.foco.bloqueio.ui.theme.pressScale
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
    /** Campo de cor da camada de conteúdo (ex.: aura das cotas); o vidro das barras o reflete. */
    aura: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val background = Bloqueio.colors.background
    Box(modifier.fillMaxSize().background(background)) {
        if (aura != null) {
            Box(Modifier.graphicsLayer { translationY = -scroll.value * 0.45f }) { aura() }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.screen),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, style = BloqueioType.largeTitle, color = Bloqueio.colors.label,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                trailing()
            }
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(Spacing.section + LocalBottomBarInset.current))
        }
        // Efeito de borda de rolagem: o conteúdo esmaece ao passar sob o topo (HIG › Layout).
        val edge by animateFloatAsState(if (scroll.value > 0) 1f else 0f, label = "edge")
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .graphicsLayer { alpha = edge }
                .background(Brush.verticalGradient(listOf(background, background.copy(alpha = 0f)))),
        )
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
                Text(header, style = BloqueioType.footnote, fontWeight = FontWeight.SemiBold, color = Bloqueio.colors.secondaryLabel)
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
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 29.dp, glyph: Color = Color.White) {
    val tilt = LocalTilt.current
    val corner = size * 0.24f
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        // Camada de luz (estilo ícones em camadas): brilho no topo que acompanha a inclinação.
        // A leitura de tilt fica no graphicsLayer: só redesenha, não recompõe.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = tilt.value.x * size.toPx() * 0.08f
                    translationY = tilt.value.y * size.toPx() * 0.08f
                }
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.30f),
                        0.55f to Color.White.copy(alpha = 0f),
                        1f to Color.Black.copy(alpha = 0.10f),
                    ),
                ),
        )
        Icon(
            icon, contentDescription = null, tint = glyph,
            modifier = Modifier
                .size(size * 0.62f)
                .graphicsLayer {
                    // O símbolo fica "acima" da luz: desloca um pouco menos (profundidade).
                    translationX = tilt.value.x * size.toPx() * 0.03f
                    translationY = tilt.value.y * size.toPx() * 0.03f
                },
        )
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
    val haptic = LocalHapticFeedback.current
    Cell(
        title = title,
        subtitle = subtitle,
        showDivider = showDivider,
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch) { v ->
            haptic.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
            onCheckedChange(v)
        },
        trailing = { IosSwitch(checked, onCheckedChange = null, enabled = enabled) },
    )
}

/**
 * Botão principal (preenchido), cápsula como no iOS 26. Use um por tela, para a ação mais
 * provável (HIG › Buttons). [loading] mostra um indicador de atividade no lugar do ícone.
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Bloqueio.colors.accentFill,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .pressScale(source)
            .heightIn(min = 54.dp)
            .clip(CircleShape)
            .background(if (enabled) color else color.copy(alpha = 0.35f))
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        PrimaryButtonContent(text, icon, loading)
    }
}

/** Controle segmentado do iOS, com semântica de grupo selecionável e alvos de 44 dp. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Bloqueio.colors.cellSecondary)
            .padding(2.dp),
    ) {
        // O "polegar" branco desliza até a opção escolhida (com mola), como no iOS.
        val segment = maxWidth / options.size
        val thumbX by animateDpAsState(
            segment * selected,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        val thumbColor = Bloqueio.colors.cell
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(thumbX.toPx(), 0f),
                    size = Size(segment.toPx(), size.height),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
            }
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .pressScale(source, pressed = 0.95f)
                    .clip(RoundedCornerShape(10.dp))
                    .selectable(
                        selected = isSel,
                        interactionSource = source,
                        indication = null,
                        role = Role.Tab,
                        onClick = {
                            if (!isSel) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(i)
                        },
                    )
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
}

@Composable
fun LockBadge() {
    Icon(Icons.Rounded.Lock, "Só o admin edita", tint = Bloqueio.colors.secondaryLabel, modifier = Modifier.size(12.dp))
}
