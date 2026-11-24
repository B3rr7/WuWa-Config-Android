package com.wuwaconfig.app.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.asImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.ui.theme.*
import com.wuwaconfig.app.util.isLocalOnlyImageUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Themed card surface.
 *
 * Modifier contract: [GlassCard]'s own `fillMaxWidth()` is applied FIRST, then
 * the caller's [modifier] chain. Callers must therefore pass only decoration
 * (padding, background, clip, clickable, semantics) — passing another width
 * constraint such as `weight(1f)` or `width(...)` is redundant and, combined
 * with the built-in `fillMaxWidth`, produces conflicting width constraints.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    shape: Shape = RoundedCornerShape(LocalThemeShapeTokens.current.cardCorner),
    glowWidth: Float = LocalThemeShapeTokens.current.glowWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    // One renderer for both modes.
    //
    // The old split — a dark branch here, a light branch in [SurfaceCard] —
    // existed because the dark treatment (white at low alpha over a near-black
    // page) is invisible in light mode, so the two modes genuinely needed
    // different fills.
    //
    // The rebuilt renderer does not need the split, because it no longer fuses
    // "fill" and "edge" into a single decision. The fill is a *stack* (body,
    // accent wash, frosted interior, sheen) and the edge is drawn separately,
    // so the same stack is legible in both modes: light mode simply gets a
    // denser body (see [surfaceFillFor]) and a brighter rim.
    SurfaceCard(
        modifier = modifier,
        accentColor = accentColor,
        shape = shape,
        glowWidth = glowWidth,
        content = content,
    )
}

/**
 * The card surface renderer, shared by every mode.
 *
 * ## The layer stack, from the bottom up
 *
 * 1. **Body** — [surfaceFillFor] paints the base fill. Opaque for the flat and
 *    tonal styles, genuinely translucent for the glass styles.
 * 2. **Accent wash** — a low-alpha gradient of the card's accent colour. This
 *    is what stops every card from being the same flat rectangle with only
 *    the text carrying its identity.
 * 3. **Frosted interior** — glass only. An isolated layer holding soft pools
 *    of palette colour, diffused with `Modifier.blur`. See
 *    [GlassRefractionLayer] for why this is a *sibling* of the content rather
 *    than its parent.
 * 4. **Refraction sheen** — glass only. A crisp diagonal streak, drawn
 *    *outside* the blurred layer so it stays sharp: a soft interior under a
 *    hard highlight is what reads as polished glass rather than frosted
 *    plastic.
 * 5. **Content** — text, icons and controls. Never blurred.
 *
 * ## Do not "simplify" by dropping the tokens
 *
 * An earlier version of this function read [ThemeShapeTokens] but used only
 * `cardPadding`, ignoring `useBlurEffects` and `useCardBorder` and never drawing
 * the accent tint at all. Every style then rendered as the same flat opaque
 * rectangle, so selecting GLASSMORPHISM in light mode visibly did nothing — the
 * picker entry and the result disagreed. If you add a token-driven property,
 * wire it; if you remove one, say why in the enum.
 */
@Composable
private fun SurfaceCard(
    modifier: Modifier = Modifier,
    accentColor: Color,
    shape: Shape,
    glowWidth: Float,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val depth = LocalSurfaceDepth.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val fill = surfaceFillFor(tokens.surfaceMode, depth, MaterialTheme.colorScheme)

    Card(
        modifier = Modifier.fillMaxWidth().then(modifier),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = fill,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        // No elevation, and no shadow-bearing graphics layer.
        //
        // A shadow needs an offscreen layer. On Android 16, wrapping this
        // translucent body together with its foreground rows in one made the
        // renderer expose that layer's rectangular dirty regions as bands
        // behind individual text, headers and buttons whenever a wallpaper
        // was set. A/B isolation on the device confirmed that removing the
        // shadow layer is the only variant that clears the artifact.
        //
        // Depth stays in the specular rim, accent wash, frosted interior and
        // sheen — none of which needs an offscreen shadow layer.
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .surfaceEdge(tokens, accentColor, glowWidth, shape, isLight),
        ) {
            // Layer 2 — the accent wash. Identical in both modes, so a style
            // switch never changes a card's colour identity.
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            brush = accentWashBrush(tokens, accentColor, isLight),
                            shape = shape,
                        ),
            )
            // Layers 3 and 4 — the glass optics. Only a GLASS surface has a
            // transmissive material, so only a GLASS surface gets them.
            if (tokens.surfaceMode == SurfaceMode.GLASS) {
                GlassRefractionLayer(
                    tokens = tokens,
                    accentColor = accentColor,
                    isLight = isLight,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // Layer 5 — the content. Sharp, always.
            //
            // Provide the next depth so a card nested inside this one is
            // tonally raised relative to its parent.
            CompositionLocalProvider(LocalSurfaceDepth provides depth + 1) {
                Column(modifier = Modifier.padding(tokens.cardPadding)) {
                    content()
                }
            }
        }
    }
}

/**
 * The accent wash that every card carries.
 *
 * Two modes, one reason: without it every card is the same flat rectangle and
 * only the text carries the card's identity. The dark variant fades towards a
 * faint white (a lit-from-above sheen) and the light variant fades to
 * transparent (a tint over an already-bright body).
 */
private fun accentWashBrush(
    tokens: ThemeShapeTokens,
    accentColor: Color,
    isLight: Boolean,
): Brush {
    val translucency = if (tokens.useBlurEffects) 1f else 0.55f
    return Brush.horizontalGradient(
        colors =
            if (isLight) {
                listOf(accentColor.copy(alpha = ACCENT_TINT_ALPHA), Color.Transparent)
            } else {
                listOf(
                    accentColor.copy(alpha = 0.06f * translucency),
                    Color.White.copy(alpha = 0.02f * translucency),
                )
            },
    )
}

/**
 * The isolated refractive layer that sits BEHIND a glass card's content.
 *
 * ## Why it is a sibling of the content, not its parent
 *
 * A frosted panel needs two things that cannot live in one layer: a *diffused*
 * material and a *razor-sharp* foreground. `Modifier.blur` applied to the
 * card's own container blurs everything inside it — text included — which is
 * the classic "glass card with unreadable labels" failure.
 *
 * So the blur lives on a dedicated background layer that is a **sibling** of
 * the content. The content is composited on top and never enters the blurred
 * layer's render pass, so the text and icons stay perfectly sharp.
 *
 * ## What is actually blurred
 *
 * Compose cannot sample the pixels behind a node, so a layer-local blur
 * refracts *this layer's own* content. That is why the layer deliberately
 * contains light with structure — soft radial pools of palette colour —
 * rather than a flat wash: blurring a flat gradient changes almost nothing,
 * while blurring pooled light produces the diffused interior that reads as
 * refraction. The colours those pools carry come from the active palette
 * ([NeonPurple], [NeonCyan]) and the card's own accent, and the chromatic
 * backdrop in [GradientBackground] supplies the page tone they sit against.
 *
 * ## Below API 31
 *
 * `Modifier.blur` needs a RenderEffect, which arrived in API 31. On API 26–30
 * the modifier is skipped entirely and the pools are drawn unblurred, which is
 * why they are authored with a very wide radial falloff: the fallback has to
 * arrive at the same softness by geometry rather than by filter.
 */
@Composable
private fun GlassRefractionLayer(
    tokens: ThemeShapeTokens,
    accentColor: Color,
    isLight: Boolean,
    modifier: Modifier = Modifier,
) {
    // Dark mode can use the saturated accents directly: they read as
    // coloured light over a near-black body. Light mode lifts the same accents
    // toward white, otherwise three overlapping pools turn a white glass card
    // into a muddy slate rectangle.
    val violet = frostedPoolColor(NeonPurple, isLight)
    val aqua = frostedPoolColor(NeonCyan, isLight)
    val poolAlpha = if (isLight) 0.16f else 0.22f
    val frosted = tokens.useBlurEffects && tokens.blurRadius > 0.dp
    val blurModifier =
        if (frosted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(tokens.blurRadius, BlurredEdgeTreatment.Unbounded)
        } else {
            Modifier
        }

    // Layer 3 — the frosted interior. One canvas, three pools, one offscreen
    // pass: three separate composables would each force their own layer.
    Box(modifier = modifier.then(blurModifier)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit = size.minDimension
            val pools =
                listOf(
                    Triple(frostedPoolColor(accentColor, isLight), Offset(size.width * 0.16f, size.height * 0.08f), unit * 0.95f),
                    Triple(violet, Offset(size.width * 0.92f, size.height * 0.30f), unit * 0.80f),
                    Triple(aqua, Offset(size.width * 0.30f, size.height * 1.02f), unit * 0.90f),
                )
            pools.forEach { (color, center, radius) ->
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = poolAlpha), Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                    radius = radius,
                    center = center,
                )
            }
        }
    }

    // Layer 4 — the refraction sheen. Outside the blur on purpose: a crisp
    // diagonal streak over a diffused interior is what sells the material as
    // polished glass rather than frosted plastic.
    Box(
        modifier =
            modifier.background(
                brush =
                    Brush.linearGradient(
                        colorStops =
                            arrayOf(
                                0f to Color.White.copy(alpha = GLASS_SHEEN_HEAD_ALPHA),
                                0.5f to Color.Transparent,
                                1f to Color.White.copy(alpha = GLASS_SHEEN_TAIL_ALPHA),
                            ),
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
            ),
    )
}

/**
 * The few surface tones this app's cards actually use, most-raised first.
 *
 * ## Why MATERIAL_YOU's ramp is hand-picked rather than the container ramp
 *
 * The container ramp is interpolated from `surface` toward `outline`, which lands
 * within a few percent of the base surface: LightFallbackColors has surface
 * #F1F2F6 and outline #C4C6D0, so the top step is only ~8% away. That is correct
 * for Material's own components but useless as the *identity* of a style — four
 * cards would differ by a couple of percent and still read as one flat surface,
 * which is the exact problem the tonal mode exists to solve.
 *
 * These steps are deliberately wider than Material's. They are still monotone
 * (asserted in `ThemeEngineTest`) and still derived from the palette, so a
 * palette change still repaints them — they just occupy enough of the available
 * tonal range to be seen.
 *
 * The dark steps reach toward `outline`, which is the brightest neutral the
 * palettes define, so an elevated dark card rises rather than sinking.
 */
internal fun tonalStepsFor(scheme: ColorScheme): List<Color> {
    val base = scheme.surface
    val target = scheme.outline

    // Direction is chosen from the *background's* luminance, not from a mode
    // flag. `isDark` is the wrong input because it describes the theme mode,
    // not where this surface sits: MIDNIGHT_OLED resolves to pure black (#000000)
    // for both modes, and against a black page elevation has to climb *lighter*,
    // which is the opposite of what "light mode" would imply. Asserting
    // direction from the background makes that case correct for free and keeps
    // the invariant in one place.
    //
    // Exactly TONAL_STEPS entries, and that coupling is load-bearing:
    // `surfaceFillFor` indexes with `depth.coerceIn(0, TONAL_STEPS - 1)`, so a
    // longer ramp would silently drop its tail — while the ramp's own
    // monotonicity test asserted over every entry, i.e. over a step no card can
    // ever be drawn with.
    val climbLighter = scheme.background.luminance() < 0.5f
    val ramp =
        if (climbLighter) {
            listOf(base, lerp(base, target, 0.45f), lerp(base, target, 0.80f))
        } else {
            // Descend: surface is lighter than the page, so a deeper card must go
            // darker. Toward black, because `outline` is the palette's *darkest*
            // neutral only on a dark scheme — on a light one it is darker than
            // surfaceVariant and would run the ramp backwards.
            listOf(base, lerp(base, Color.Black, 0.06f), lerp(base, Color.Black, 0.12f))
        }

    // If the chosen direction cannot actually move (a pure-white surface on a
    // light page has nowhere to descend to), fall back rather than emit a ramp of
    // identical colours, which would make every elevation step look the same.
    val moves = ramp.zipWithNext().any { (a, b) -> a != b }
    return if (moves) ramp else listOf(base, target, target)
}

/**
 * The fill for a [SurfaceMode] at nesting [depth].
 *
 * Takes the scheme as a parameter rather than reading `MaterialTheme` so it stays
 * pure and unit-testable — tonal elevation is a claim about nesting depth that
 * nothing else can check.
 */
internal fun surfaceFillFor(
    mode: SurfaceMode,
    depth: Int,
    scheme: ColorScheme,
): Color {
    val step = depth.coerceIn(0, TONAL_STEPS - 1)
    return when (mode) {
        // The glass body. Light mode needs a denser body: white at low alpha
        // over a near-white page is invisible, so the panel has to carry more
        // of its own surface tone there or it disappears into the backdrop.
        SurfaceMode.GLASS ->
            scheme.surface.copy(
                alpha = if (scheme.background.luminance() > 0.5f) GLASS_FILL_ALPHA_LIGHT else GLASS_FILL_ALPHA_DARK,
            )
        SurfaceMode.TONAL -> tonalStepsFor(scheme)[step]
        SurfaceMode.OUTLINED,
        SurfaceMode.FLAT,
        -> scheme.surface
        // The neumorphic pillow: the page tone itself, raised purely by
        // lighting (see SurfaceCard's shadow pass). Not keyed on `surface` but
        // on `background` so a card over a flat page melts into it — the whole
        // point of the style.
        SurfaceMode.RELIEF -> scheme.background
        // The skeuomorphic recess: pressed into the page, so it takes the
        // palette's *variant* tone, which sits between surface and outline.
        SurfaceMode.SUNKEN -> scheme.surfaceVariant
    }
}

/**
 * The outline for a card, or no modifier when the style draws none.
 *
 * [EdgeMode.SPECULAR] is the important one: a uniform alpha fill reads as flat
 * wash however transparent it is, so glass gets a gradient edge that is bright
 * at the top-left and darkens toward the bottom-right. Apple added exactly this
 * in 2026 to "establish more depth and separation" (WWDC26).
 */
@Composable
private fun Modifier.surfaceEdge(
    tokens: ThemeShapeTokens,
    accentColor: Color,
    glowWidth: Float,
    shape: Shape,
    isLight: Boolean,
): Modifier {
    val width = maxOf(tokens.borderStrokeWidth, glowWidth.dp, 0.5f.dp)
    return when (tokens.edgeMode) {
        EdgeMode.NONE -> this
        EdgeMode.HAIRLINE ->
            if (tokens.useCardBorder) {
                border(width, accentColor.copy(alpha = CARD_BORDER_ALPHA), shape)
            } else {
                this
            }
        EdgeMode.HARD ->
            if (tokens.useCardBorder) {
                border(tokens.borderStrokeWidth, accentColor.copy(alpha = CARD_BORDER_ALPHA), shape)
            } else {
                this
            }
        EdgeMode.SPECULAR -> {
            if (!tokens.useCardBorder) {
                this
            } else {
                // A three-stop diagonal rim: a bright catchlight at the
                // top-left, the scheme's own outline colour in the middle, and
                // a faint reflection at the bottom-right. Drawn rather than
                // stroked so the mid-stop can come from the palette instead of
                // a hard-coded neutral.
                val rimWidth = maxOf(tokens.borderStrokeWidth, GLASS_RIM_WIDTH)
                // A white rim is visible on a dark body but disappears on a
                // white one. Light mode therefore uses the palette's outline
                // tone through the middle and a restrained neutral shade at
                // the trailing edge; dark mode keeps the white highlights.
                val mid =
                    if (isLight) {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = GLASS_RIM_MID_ALPHA_LIGHT)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = GLASS_RIM_MID_ALPHA)
                    }
                val trailing =
                    if (isLight) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = GLASS_RIM_TRAILING_ALPHA_LIGHT)
                    } else {
                        Color.White.copy(alpha = GLASS_RIM_TRAILING_ALPHA)
                    }
                val rimBrush =
                    Brush.linearGradient(
                        colorStops =
                            arrayOf(
                                0f to Color.White.copy(alpha = GLASS_RIM_CATCHLIGHT_ALPHA),
                                0.5f to mid,
                                1f to trailing,
                            ),
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    )
                this.drawWithContent {
                    drawContent()
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val path = outline.toPath()
                    // The rim itself.
                    drawPath(path, brush = rimBrush, style = Stroke(width = rimWidth.toPx()))
                    // The inner catchlight: a crisp 1dp highlight running in
                    // from the top edge and fading out over a couple of dp.
                    // This is what reads as the *thickness* of the glass rather
                    // than as a painted outline.
                    val band = (CATCHLIGHT_BAND.toPx() / size.height).coerceIn(0.001f, 1f)
                    drawPath(
                        path,
                        brush =
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0f to Color.White.copy(alpha = GLASS_CATCHLIGHT_ALPHA),
                                        band to Color.Transparent,
                                        1f to Color.Transparent,
                                    ),
                            ),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }
        }
        EdgeMode.BEVEL -> {
            // Chiselled edge, oriented down the card so the highlight enters at
            // the top-left and the shade pools at the bottom-right — lightplay
            // on an edge rather than a drawn line. \uFE0FIn light mode the shade half
            // does the defining work; in dark mode the highlight does (same
            // inversion a milled part actually shows).
            val highlight = if (isLight) Color.White.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.38f)
            val shade = if (isLight) Color.Black.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.55f)
            border(
                width = maxOf(width, 1.dp),
                brush =
                    Brush.linearGradient(
                        0f to highlight,
                        0.5f to Color.Transparent,
                        1f to shade,
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
                shape = shape,
            )
        }
    }
}

/**
 * Converts a shape's outline into a path that can be stroked.
 *
 * `Modifier.border` can only draw one uniform or gradient stroke. The
 * specular rim needs to stroke the *same* outline twice with two different
 * brushes — a diagonal rim and a differently-oriented inner catchlight — so it
 * needs the outline as a path.
 */
private fun Outline.toPath(): Path =
    when (this) {
        is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(rect) }
        is Outline.Generic -> path
    }

/**
 * Alpha of the glass body in dark mode.
 *
 * Low enough that the page's own gradient and the panel's frosted interior
 * both read through it, high enough that a card is still distinguishable from
 * a flat panel of the same palette.
 */
private const val GLASS_FILL_ALPHA_DARK = 0.38f

/**
 * Alpha of the glass body in light mode.
 *
 * Denser than the dark value on purpose: white at low alpha over a near-white
 * page is invisible, so in light mode the *edge* and the *sheen* carry the
 * panel's boundary while the body stays translucent enough to transmit.
 */
private const val GLASS_FILL_ALPHA_LIGHT = 0.65f

/** Accent wash over the light card fill. Mirrors the dark branch's intent. */
private const val ACCENT_TINT_ALPHA = 0.10f

/** Width of the specular rim on a glass surface. */
private val GLASS_RIM_WIDTH = 1.2.dp

/** The bright catchlight at the rim's leading (top-left) end. */
private const val GLASS_RIM_CATCHLIGHT_ALPHA = 0.55f

/** The soft mid-edge, taken from the palette's own outline colour. */
private const val GLASS_RIM_MID_ALPHA = 0.18f

/** Light mode needs a visible neutral mid-edge instead of a white one. */
private const val GLASS_RIM_MID_ALPHA_LIGHT = 0.55f

/** The faint reflection at the rim's trailing (bottom-right) end. */
private const val GLASS_RIM_TRAILING_ALPHA = 0.22f

/** The restrained neutral shade that closes a light-mode glass edge. */
private const val GLASS_RIM_TRAILING_ALPHA_LIGHT = 0.10f

/** The inner catchlight that simulates the thickness of the glass. */
private const val GLASS_CATCHLIGHT_ALPHA = 0.45f

/** How far the inner catchlight fades below the top edge. */
private val CATCHLIGHT_BAND = 2.dp

/** The bright head of the diagonal refraction sheen. */
private const val GLASS_SHEEN_HEAD_ALPHA = 0.16f

/** The faint tail of the diagonal refraction sheen. */
private const val GLASS_SHEEN_TAIL_ALPHA = 0.04f

/** Light-mode card outline. Lower than the dark branch's because it sits on a
 *  busy background already and a heavy stroke competes with the content. */
private const val CARD_BORDER_ALPHA = 0.28f

/**
 * Opacity of the [GlassTopBar] scrim, as a top-to-bottom ramp.
 *
 * The bar is made of the same material as a card, so under GLASSMORPHISM it has
 * to transmit the page instead of painting a slab over it — the old flat 0.94
 * of `background` is exactly why the header read as a white band while every
 * panel below it was translucent.
 *
 * A single value cannot do both of the bar's jobs, though. The status-bar strip
 * sits behind the system icons and has to stay light enough for them, while the
 * rest of the bar should be as transmissive as the panels below it. So the ramp
 * stays dense for the height of the inset and then drops to a card's own body
 * alpha for the title row — see [TOP_BAR_STATUS_STRIP].
 *
 * The body stop sits a hair above [GLASS_FILL_ALPHA_LIGHT] /
 * [GLASS_FILL_ALPHA_DARK] on purpose: rows scroll *under* this bar, and at a
 * card's exact alpha their labels ghost through the title.
 */
private const val TOP_BAR_SCRIM_TOP_LIGHT = 0.94f

private const val TOP_BAR_SCRIM_TOP_DARK = 0.86f

/** Body alpha over the bar's own copy of the wallpaper. */
private const val TOP_BAR_SCRIM_BODY_LIGHT = 0.62f

private const val TOP_BAR_SCRIM_BODY_DARK = 0.42f

/**
 * Body alpha when there is no backdrop copy for the bar to be made of.
 *
 * That is the static-gradient page and every video wallpaper (a video is played
 * by an AndroidView, so there is no painter to re-draw). Denser than the image
 * case by necessity, not by taste: with nothing opaque behind the scrim, rows
 * scrolling under the bar show through it, and at card-level alpha they ghost
 * across the title. Still short of fully opaque, so a dark page does not gain a
 * hard band at the top.
 */
private const val TOP_BAR_SCRIM_BODY_NO_BACKDROP_LIGHT = 0.95f

private const val TOP_BAR_SCRIM_BODY_NO_BACKDROP_DARK = 0.90f

/**
 * Where the status-bar strip ends, as a fraction of the bar's height.
 *
 * The strip is the one part of the bar that cannot be as transmissive as a
 * card: it sits behind dark system icons, and over a dark wallpaper a 0.66 body
 * would leave them unreadable. Roughly 24dp of an 80dp bar.
 */
private const val TOP_BAR_STATUS_STRIP = 0.30f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val bar: @Composable () -> Unit = {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = accentColor,
                    navigationIconContentColor = accentColor,
                    actionIconContentColor = accentColor,
                ),
        )
    }

    // The bar wears the active style's material, exactly like a card.
    //
    // It used to hard-code `background` at a fixed alpha, which meant it read
    // the *theme mode* but not the *style*: choosing Glassmorphism changed
    // every panel on the screen except the one at the top of it, and the header
    // stayed a flat opaque band over the wallpaper. Now a glass style gets the
    // translucent surface ramp and every other style gets the opaque fill
    // [surfaceFillFor] resolves for it — the tonal step, the flat surface, the
    // page tone for RELIEF.
    //
    // Scaffold's contentPadding only offsets the list at rest; once scrolled,
    // rows pass under the bar. That is why the glass bar re-draws the backdrop
    // itself (see [LocalBackdropPainter]) instead of relying on what is behind
    // it: at card-level alpha the title would otherwise sit on a ghost of the
    // rows sliding underneath. The scrim over that copy is then free to be as
    // thin as a card's.
    val tokens = LocalThemeShapeTokens.current
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.background.luminance() > 0.5f
    val isGlass = tokens.surfaceMode == SurfaceMode.GLASS
    val backdrop = LocalBackdropPainter.current
    val backdropSize = LocalBackdropSize.current
    val wallpaperAlpha = LocalBackgroundSettings.current.opacity
    val hasBackdrop =
        isGlass &&
            backdrop != null &&
            backdropSize.width > 0f &&
            backdropSize.height > 0f
    val bodyLight = if (hasBackdrop) TOP_BAR_SCRIM_BODY_LIGHT else TOP_BAR_SCRIM_BODY_NO_BACKDROP_LIGHT
    val bodyDark = if (hasBackdrop) TOP_BAR_SCRIM_BODY_DARK else TOP_BAR_SCRIM_BODY_NO_BACKDROP_DARK
    val body =
        if (isGlass) {
            val top = scheme.surface.copy(alpha = if (isLight) TOP_BAR_SCRIM_TOP_LIGHT else TOP_BAR_SCRIM_TOP_DARK)
            val rest = scheme.surface.copy(alpha = if (isLight) bodyLight else bodyDark)
            Brush.verticalGradient(
                colorStops = arrayOf(0f to top, TOP_BAR_STATUS_STRIP to rest, 1f to rest),
            )
        } else {
            SolidColor(surfaceFillFor(tokens.surfaceMode, 0, scheme))
        }
    Box(modifier = modifier.fillMaxWidth()) {
        if (hasBackdrop && backdrop != null) {
            Canvas(
                modifier =
                    Modifier
                        .matchParentSize()
                        // No-op below API 31, where the copy is simply drawn
                        // sharp — still opaque, so still hides the rows.
                        .blur(tokens.blurRadius, BlurredEdgeTreatment.Rectangle),
            ) {
                drawRect(scheme.background)
                val intrinsic = backdrop.intrinsicSize
                if (intrinsic.width > 0f && intrinsic.height > 0f) {
                    // Reproduce ContentScale.Crop against the *screen*, then let
                    // the bar's own bounds clip it to the top slice.
                    val scale = maxOf(backdropSize.width / intrinsic.width, backdropSize.height / intrinsic.height)
                    val drawn = Size(intrinsic.width * scale, intrinsic.height * scale)
                    translate((backdropSize.width - drawn.width) / 2f, (backdropSize.height - drawn.height) / 2f) {
                        with(backdrop) { draw(size = drawn, alpha = wallpaperAlpha) }
                    }
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(brush = body)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        // The accent wash is a touch stronger on a
                                        // glass bar: the surface behind it is now
                                        // partly transparent, so the tint is the
                                        // main thing tying the bar to the cards.
                                        accentColor.copy(alpha = if (isGlass) 0.14f else 0.10f),
                                        Color.White.copy(alpha = 0.02f),
                                        Color.Transparent,
                                    ),
                            ),
                    ),
        )
        bar()
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        accentColor.copy(alpha = 0.5f),
                                        Color.Transparent,
                                    ),
                            ),
                    ),
        )
    }
}

// The terminal card keeps its dark palette in BOTH themes on purpose: it is a
// literal log-console mock (monospace output on a near-black surface) and the
// same "recent.log" / "status.log" chrome appears in light and dark mode.
private val TerminalBg = Color(0xFF0C0E14)
private val TerminalBorder = Color(0xFF1E2530)

@Composable
fun TerminalLogCard(
    modifier: Modifier = Modifier,
    title: String = "recent.log",
    accentColor: Color = NeonGreen,
    maxLines: Int = 5,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val logs by LogRepository.entries.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(modifier)
                .clip(shape)
                .background(TerminalBg)
                .border(1.dp, TerminalBorder, shape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(NeonRed.copy(alpha = 0.85f)))
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(NeonAmber.copy(alpha = 0.85f)))
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(NeonGreen.copy(alpha = 0.85f)))
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.weight(1f))
            trailing?.invoke(this)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            if (logs.isEmpty()) {
                Text(
                    "\u276F no logs yet.",
                    fontSize = 10.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.4f),
                )
            } else {
                logs.takeLast(maxLines).forEach { log ->
                    val c =
                        when (log.level) {
                            LogLevel.SUCCESS -> NeonGreen
                            LogLevel.ERROR -> NeonRed
                            LogLevel.WARNING -> NeonAmber
                            LogLevel.INFO -> Color(0xFFB6C2D9)
                        }
                    Row {
                        Text(
                            "\u276F ",
                            fontSize = 10.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = accentColor.copy(alpha = 0.7f),
                        )
                        Text(
                            "[${log.timestamp}] ${log.message}",
                            fontSize = 10.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Themed toggle used throughout the Config Generator.
 * Dark theme -> frosted glass pill; Light theme -> palette surface track.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    enabled: Boolean = true,
) {
    val tokens = LocalThemeShapeTokens.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val trackWidth = 54.dp
    val trackHeight = 30.dp
    val thumbSize = 24.dp
    val gap = (trackHeight - thumbSize) / 2

    // Bouncy travel, like the button: a switch is a physical toggle, and a
    // critically-damped slide reads as a UI transition rather than as a
    // mechanism. The spring overshoots very slightly at the end of the travel
    // and settles back, which is what a physical detent does.
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - gap else gap,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "thumbOffset",
    )
    // The track's specular rim. Brightened when the switch is on, so the "on"
    // state reads as a lit glass surface rather than just a coloured pill.
    val trackRim by animateFloatAsState(
        targetValue = if (checked) 0.7f else 0.2f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "trackRim",
    )
    val trackShape = RoundedCornerShape(trackHeight / 2)

    if (isLight) {
        // The dark branch's white-on-transparent track is invisible over a light
        // background, so light mode needs an opaque one. Colours come from the
        // scheme rather than hard-coded greys, so this still follows the palette.
        // Tonal modes (MATERIAL_YOU) step off the current surface so the
        // control is visibly raised even though it draws no border.
        val trackBase =
            if (LocalThemeShapeTokens.current.surfaceMode == SurfaceMode.TONAL) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        val trackBrush =
            Brush.verticalGradient(
                listOf(
                    if (checked) accentColor.copy(alpha = 0.55f) else trackBase,
                    if (checked) accentColor.copy(alpha = 0.40f) else trackBase,
                ),
            )
        val thumbFill =
            when {
                !enabled -> MaterialTheme.colorScheme.surfaceVariant
                checked -> accentColor
                else -> MaterialTheme.colorScheme.surface
            }
        Box(
            modifier =
                modifier
                    .width(trackWidth)
                    .height(trackHeight)
                    .clip(trackShape)
                    .background(brush = trackBrush)
                    .border(
                        1.dp,
                        if (checked) Color.White.copy(alpha = trackRim + 0.15f) else MaterialTheme.colorScheme.outline,
                        trackShape,
                    )
                    .toggleable(
                        value = checked,
                        onValueChange = onCheckedChange,
                        role = Role.Switch,
                        enabled = enabled,
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            SwitchThumb(
                offset = thumbOffset,
                size = thumbSize,
                fill = thumbFill,
                checked = checked,
                enabled = enabled,
                elevation = 3.dp,
                uncheckedRim = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    // Dark: frosted glass track with glow when on; bright raised thumb.
    Box(
        modifier =
            modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(trackShape)
                .background(
                    brush =
                        Brush.horizontalGradient(
                            colors =
                                if (checked) {
                                    listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.55f))
                                } else {
                                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                                },
                        ),
                ).border(
                    1.dp,
                    if (checked) Color.White.copy(alpha = trackRim) else Color.White.copy(alpha = 0.22f),
                    trackShape,
                ).toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch,
                    enabled = enabled,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        SwitchThumb(
            offset = thumbOffset,
            size = thumbSize,
            fill = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            checked = checked,
            enabled = enabled,
            elevation = 6.dp,
            uncheckedRim = Color.White.copy(alpha = 0.3f),
        )
    }
}

/**
 * The switch's moving bead.
 *
 * A small glass bead rather than a flat disc: it carries its own catchlight in
 * the upper-left, which is what makes it read as a raised, polished object
 * instead of a circle of paint.
 */
@Composable
private fun SwitchThumb(
    offset: Dp,
    size: Dp,
    fill: Color,
    checked: Boolean,
    enabled: Boolean,
    elevation: Dp,
    uncheckedRim: Color,
) {
    Box(
        modifier =
            Modifier
                .offset(x = offset)
                .size(size)
                .shadow(elevation, CircleShape, clip = false)
                .clip(CircleShape)
                .background(fill)
                .border(
                    1.dp,
                    if (checked) Color.White.copy(alpha = 0.85f) else uncheckedRim,
                    CircleShape,
                ),
    ) {
        if (checked && enabled) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
fun GlassCardHeader(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.8f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = NeonCyan,
    // White, not black: in dark mode the container is `accentColor` at 0.12 alpha
    // over a near-black page, so a black label is unreadable. In light mode
    // `resolvedContentColor` ignores this and uses the accent, so the default is
    // only ever reached by dark mode — where it must be light.
    contentColor: Color = Color.White,
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val isGlass = tokens.surfaceMode == SurfaceMode.GLASS
    // A glass button is a glass surface: the same translucent body the cards
    // use, so the control and the panel it sits on are made of the same
    // material instead of the button looking like a sticker on the glass.
    val buttonContainer =
        when {
            isGlass && isLight -> MaterialTheme.colorScheme.surface.copy(alpha = GLASS_FILL_ALPHA_LIGHT)
            isGlass -> MaterialTheme.colorScheme.surface.copy(alpha = GLASS_FILL_ALPHA_DARK)
            isLight -> accentColor.copy(alpha = 0.20f)
            else -> accentColor.copy(alpha = 0.12f)
        }
    val disabledContainer = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.04f)
    val disabledContent = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    val resolvedContentColor = if (isLight) accentColor else contentColor
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Bouncy press physics. A glass control is a physical object being
    // depressed, so it should overshoot slightly on the way back rather than
    // easing to a stop — a critically-damped scale reads as a UI animation,
    // not as a material.
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnScale",
    )
    // The rim's specular highlight, brightened while the button is held down:
    // pressing a glass panel tilts it towards the light.
    val rimAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.55f else 0.18f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btnRim",
    )

    val buttonShape = RoundedCornerShape(tokens.buttonCorner)
    Button(
        onClick = onClick,
        modifier = modifier.height(height).graphicsLayer(scaleX = scale, scaleY = scale),
        enabled = enabled,
        shape = buttonShape,
        interactionSource = interactionSource,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = buttonContainer,
                contentColor = resolvedContentColor,
                disabledContainerColor = disabledContainer,
                disabledContentColor = disabledContent,
            ),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 2.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(accentColor.copy(alpha = 0.08f), Color.Transparent),
                            ),
                        shape = buttonShape,
                    )
                    .then(
                        if (tokens.useCardBorder) {
                            if (isGlass) {
                                // The specular rim, same construction as the
                                // card's: three stops along the diagonal,
                                // brightened under the finger.
                                Modifier.border(
                                    width = maxOf(tokens.borderStrokeWidth, 0.5f.dp),
                                    brush =
                                        Brush.linearGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to Color.White.copy(alpha = rimAlpha),
                                                    0.5f to MaterialTheme.colorScheme.outlineVariant.copy(alpha = GLASS_RIM_MID_ALPHA),
                                                    1f to Color.White.copy(alpha = rimAlpha * 0.4f),
                                                ),
                                            start = Offset.Zero,
                                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                                        ),
                                    shape = buttonShape,
                                )
                            } else {
                                Modifier.border(
                                    maxOf(tokens.borderStrokeWidth, 0.5f.dp),
                                    accentColor.copy(alpha = 0.2f),
                                    buttonShape,
                                )
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = NeonRed,
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val disabledContent = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "outBtnScale",
    )
    // The rim brightens under the finger, the same way the filled button's
    // specular highlight does.
    val rimAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.75f else 0.3f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "outBtnRim",
    )
    val borderColor =
        when {
            isLight -> accentColor.copy(alpha = if (isPressed && enabled) 0.9f else 0.65f)
            tokens.surfaceMode == SurfaceMode.GLASS -> Color.White.copy(alpha = rimAlpha)
            else -> accentColor.copy(alpha = 0.3f)
        }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(height).graphicsLayer(scaleX = scale, scaleY = scale),
        enabled = enabled,
        shape = RoundedCornerShape(tokens.buttonCorner),
        interactionSource = interactionSource,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = accentColor,
                disabledContentColor = disabledContent,
            ),
        // Gate on the token flag exactly like GlassButton's border does: the
        // 0.5dp floor here drew a hairline border under FLAT_DESIGN_2 /
        // MINIMALIST, whose whole point is "no borders", and MATERIAL_YOU
        // (borderStrokeWidth = 0.dp) got an accent edge it never asked for.
        border =
            if (tokens.useCardBorder) {
                BorderStroke(maxOf(tokens.borderStrokeWidth, 0.5f.dp), borderColor)
            } else {
                null
            },
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun BackendStatusCard(
    status: BackendStatus,
    onToggle: () -> Unit,
) {
    val accentColor by animateColorAsState(
        targetValue =
            when {
                status.connected -> NeonGreen
                status.errorMessage.isNotBlank() -> NeonRed
                else -> NeonAmber
            },
        label = "accent",
    )

    GlassCard(accentColor = accentColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor)
                            .border(1.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Access: ${status.method.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            status.connected -> "Connected"
                            status.errorMessage.isNotBlank() -> status.errorMessage
                            else -> "Not connected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            SuggestionChip(
                onClick = onToggle,
                label = {
                    val text =
                        if (status.connected) {
                            "Switch"
                        } else {
                            when (status.method) {
                                AccessMethod.ADB -> "SHIZUKU"
                                AccessMethod.SHIZUKU -> "ROOT"
                                AccessMethod.ROOT -> "SAF"
                                AccessMethod.SAF -> "ADB"
                            }
                        }
                    Text(text, fontWeight = FontWeight.Bold)
                },
                colors =
                    SuggestionChipDefaults.suggestionChipColors(
                        containerColor = accentColor.copy(alpha = 0.1f),
                        labelColor = accentColor,
                    ),
                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = accentColor.copy(alpha = 0.2f)),
            )
        }
    }
}

@Composable
fun MiniLogViewer(modifier: Modifier = Modifier) {
    // No second collection of LogRepository.entries here: this used to exist only
    // to hide the card when empty, while TerminalLogCard collects the identical
    // flow itself — two subscriptions and two invalidations per log line. The
    // "no logs yet" placeholder inside TerminalLogCard covers the empty case.
    TerminalLogCard(modifier = modifier, title = "status.log", accentColor = NeonAmber)
}

@Composable
fun BouncingOrb(
    color: Color,
    index: Int,
) {
    // Reduced motion is honoured here specifically because OrbLoadingCard — the
    // documented reduced-motion fallback (GeoLoading returns to it, ProfileScreen
    // uses it as its fallback) — composes these orbs with no gating of its own:
    // a "remove animations" user otherwise got three orbs bouncing every 520ms.
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        // Static: the orb keeps its presence and position, minus the loop.
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.radialGradient(listOf(color, color.copy(alpha = 0.35f))),
                ),
        )
        return
    }
    val transition = rememberInfiniteTransition(label = "orb$index")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 520, delayMillis = index * 160, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "offset$index",
    )
    Box(
        Modifier
            .size(14.dp)
            .offset(y = offset.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.radialGradient(listOf(color, color.copy(alpha = 0.35f))),
            ),
    )
}

@Composable
fun OrbLoadingCard(
    text: String,
    accentColor: Color,
    colors: List<Color> = listOf(NeonCyan, NeonGold, NeonPurple),
    progress: Int = 0,
) {
    GlassCard(accentColor = accentColor) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colors.forEachIndexed { index, color ->
                    BouncingOrb(color, index)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        }
    }
}

/**
 * Scrim alphas laid over a user-supplied wallpaper.
 *
 * ## Why this exists
 *
 * The wallpaper used to be scrimmed at **0.15 alpha**, which is decorative rather
 * than protective. On a bright photo that left body text sitting directly on the
 * image: on a device with a light wallpaper the login screen's Kuro email and
 * password fields were effectively unreadable, and every list row fought the
 * picture behind it. The app stopped looking like WuWaConfig and started looking
 * like a wallpaper with text on it.
 *
 * A wallpaper is a *backdrop*. It has to stay recognisable — that is the point of
 * setting one — while never competing with text that sits on top of it. The scrim
 * achieves that on its own: the image still supplies its colour and composition,
 * but far less of its detail competes with the foreground.
 *
 * A blur was tried here as well and **removed** — it was judged to flatten the
 * wallpaper too much. The scrim alone is enough to carry contrast, which is the
 * only job this layer has.
 *
 * Deliberately **not** scaled by [BackgroundSettings.opacity]. That setting is the
 * user's "how much wallpaper do I want", and honouring it here would let a
 * legitimate choice for legibility be undone by a slider meant for taste. The
 * scrim is the floor; `opacity` shapes what shows through it.
 *
 * Top is heavier because the app bar and the first two cards sit there, which is
 * where the foreground/background contest is tightest; the bottom is lighter so
 * the wallpaper still reads as a backdrop rather than being erased.
 */
private const val WALLPAPER_SCRIM_TOP = 0.82f

private const val WALLPAPER_SCRIM_BOTTOM = 0.66f

/**
 * App-scoped background preference holder. Exposed as a CompositionLocal rather
 * than reaching into the `WuWaConfigApp.instance` global, so [GradientBackground]
 * has no hidden dependency on `Application.onCreate` having already run (and no
 * UninitializedPropertyAccessException if a preview or an early composition gets
 * there first). MainActivity provides the real value.
 */
data class BackgroundSettings(
    val imageUri: String?,
    val videoUri: String?,
    val opacity: Float,
)

val LocalBackgroundSettings = staticCompositionLocalOf { BackgroundSettings(null, null, 1f) }

/**
 * The wallpaper image currently painted by [GradientBackground], or null when
 * the backdrop is the static gradient (or a video).
 *
 * Published so [GlassTopBar] can paint its own copy of the same image inside
 * itself. Compose cannot sample the pixels behind a node, so a bar translucent
 * enough to read as glass would otherwise let the rows scrolling underneath it
 * ghost through its own title. Re-drawing the backdrop, opaque, gives the bar
 * something to be made *of* — and gives the blur something to blur — while the
 * rows stay hidden behind it.
 *
 * Held as the painter rather than the URI because the bar needs the decoded
 * image to reproduce its crop; it never needs to know where it came from.
 */
val LocalBackdropPainter = compositionLocalOf<Painter?> { null }

/**
 * Pixel size of the backdrop, published with [LocalBackdropPainter].
 *
 * The bar has to reproduce the backdrop's `ContentScale.Crop` transform, and
 * that transform is defined against the *screen*, not against the bar's own
 * bounds — drawing the painter into the bar's box directly would crop it to the
 * bar and show a different part of the image than the one behind it.
 */
val LocalBackdropSize = compositionLocalOf { Size.Zero }

/**
 * The legibility layer over a wallpaper.
 *
 * Scrim only. Applied just when the user has chosen a wallpaper — the default
 * gradient needs nothing, and this is not worth paying for otherwise.
 */
@Composable
private fun Modifier.wallpaperTreatment(): Modifier {
    val themeBg = MaterialTheme.colorScheme.background
    return this.background(
        brush =
            Brush.verticalGradient(
                colors =
                    listOf(
                        themeBg.copy(alpha = WALLPAPER_SCRIM_TOP),
                        themeBg.copy(alpha = WALLPAPER_SCRIM_BOTTOM),
                    ),
            ),
    )
}

@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    val themeBg = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val bg = LocalBackgroundSettings.current
    val imageUri = bg.imageUri
    val videoUri = bg.videoUri
    val bgAlpha = bg.opacity

    val hasVideo = videoUri != null
    val hasImage = !hasVideo && imageUri != null

    // Measured rather than assumed: the bar reproduces this node's crop, and it
    // needs the real pixel size (which also covers multi-window and rotation).
    var backdropPx by remember { mutableStateOf(IntSize.Zero) }

    // Built above the branches, not inside the image one, because the bar has to
    // be given the same painter through [LocalBackdropPainter].
    val backdropPainter =
        if (hasImage) {
            val bgImageContext = LocalContext.current
            // LocalResources, not LocalContext: reading a resource through the
            // Context does not invalidate this composition when the Configuration
            // changes, so a locale or density change would leave a stale drawable.
            val bgImageResources = LocalResources.current
            val imageRequest =
                remember(imageUri) {
                    // Fail closed to the error drawable for anything that is not
                    // local-only, so a persisted or injected remote URL can never
                    // become an image request.
                    ImageRequest.Builder(bgImageContext)
                        .data(if (isLocalOnlyImageUri(imageUri)) imageUri else null)
                        .crossfade(true)
                        .error(
                            requireNotNull(
                                bgImageResources.getDrawable(
                                    android.R.drawable.stat_notify_error,
                                    bgImageContext.theme,
                                ),
                            ).asImage(),
                        )
                        .build()
                }
            rememberAsyncImagePainter(imageRequest)
        } else {
            null
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(themeBg)
                .onSizeChanged { backdropPx = it },
    ) {
        if (hasVideo) {
            VideoBackground(
                videoUri = videoUri!!,
                alpha = bgAlpha,
                // Video and image are treated identically now: a scrim carries
                // the contrast and neither is blurred.
                modifier = Modifier.fillMaxSize().wallpaperTreatment(),
            )
        } else if (backdropPainter != null) {
            Image(
                painter = backdropPainter,
                contentDescription = null,
                // The scrim is applied by `wallpaperTreatment`; `bgAlpha` is the
                // user's own "how much wallpaper" slider and still applies.
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = bgAlpha).wallpaperTreatment(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Static gradient backdrop for every remaining style. The animated
            // Aurora backdrop was removed together with the AURORA_FLUID style.
            run {
                // Cached, not rebuilt: the backdrop renders on every screen, and a
                // fresh Brush allocation per recomposition of the root composable
                // was pure garbage churn. 3-stop ramp: bg -> an accent-tinted mid
                // (the "resonance" wash, ~4% of primary) -> surface. 40% of screen
                // height for the tint, then it fades before content begins.
                val accent = MaterialTheme.colorScheme.primary
                val uiStyle = LocalUiStyle.current
                // The chromatic backdrop. A glass panel can only refract colour
                // that is actually on the page, and the default ramp is a single
                // accent at 4% — nowhere near enough for the refraction to
                // register. Under GLASSMORPHISM the ramp is deepened with the
                // palette's own violet and cyan so the glass cards above have
                // something to bend.
                //
                // The accents are read through the `NeonXxx` getters, which are
                // backed by the accent palette's state, so the saturation slider
                // still reaches the backdrop and the `remember` key below
                // invalidates with it.
                val violet = NeonPurple
                val aqua = NeonCyan
                val backdropBrush =
                    remember(themeBg, surface, accent, uiStyle, violet, aqua) {
                        val mid = lerp(themeBg, surface, 0.45f)
                        if (uiStyle == UiStyle.GLASSMORPHISM) {
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0f to lerp(themeBg, violet, 0.16f),
                                        0.30f to lerp(mid, aqua, 0.10f),
                                        0.62f to lerp(mid, violet, 0.13f),
                                        1f to lerp(surface, aqua, 0.05f),
                                    ),
                            )
                        } else {
                            Brush.verticalGradient(
                                colorStops =
                                    arrayOf(
                                        0f to themeBg,
                                        0.4f to lerp(mid, accent, 0.04f),
                                        0.75f to lerp(mid, accent, 0.06f),
                                        1f to surface,
                                    ),
                            )
                        }
                    }
                Box(modifier = Modifier.fillMaxSize().background(brush = backdropBrush))
            }
        }
        CompositionLocalProvider(
            LocalBackdropPainter provides backdropPainter,
            LocalBackdropSize provides Size(backdropPx.width.toFloat(), backdropPx.height.toFloat()),
        ) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

/**
 * Three large, slowly drifting radial colour blobs behind a translucent wash.
 *
 * Drawn rather than composed from `Box`es so the whole backdrop is one draw and
 * one layer: three overlapping translucent Boxes would each force their own
 * offscreen pass, which on the low-end GPUs this app targets is the difference
 * between a smooth background and a dropped frame on every animation.
 *
 * Driven by [rememberInfiniteTransition] rather than a coroutine + StateFlow, so
 * it stops producing frames the moment nothing is observing the composition —
 * a continuous `setState` loop here would keep the screen awake.
 */
@Composable
private fun AuroraBackdrop() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val themeBg = MaterialTheme.colorScheme.background
    // Reduced motion: hold the phase at a fixed (still pleasing, mid-sweep)
    // point instead of looping an 18s drift for 18s-worth of decorative frames
    // the user asked not to see. The phase read is already draw-confined (no
    // per-frame recomposition), this only stops the animator itself.
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "auroraPhase",
    )

    Canvas(modifier = Modifier.fillMaxSize().background(themeBg)) {
        // Fixed blob geometry; only the phase moves. Scaled off the canvas size
        // so the composition reads the same on a phone and a tablet.
        // Reduced motion pins the sweep instead of animating it.
        val sweep = if (reducedMotion) 0.35f else phase
        val w = size.width
        val h = size.height
        val unit = minOf(w, h)
        val blobs =
            listOf(
                Triple(primary, Offset(w * (0.25f + 0.18f * sin(sweep * TWO_PI)), h * (0.20f + 0.14f * cos(sweep * TWO_PI))), unit * 0.85f),
                Triple(secondary, Offset(w * (0.75f + 0.15f * cos(sweep * TWO_PI + 1f)), h * (0.35f + 0.16f * sin(sweep * TWO_PI + 2f))), unit * 0.75f),
                Triple(tertiary, Offset(w * (0.50f + 0.20f * sin(sweep * TWO_PI + 3f)), h * (0.78f + 0.12f * cos(sweep * TWO_PI + 4f))), unit * 0.70f),
            )
        blobs.forEach { (color, center, radius) ->
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(color.copy(alpha = 0.22f), Color.Transparent), center = center, radius = radius),
                radius = radius,
                center = center,
            )
        }
    }
}

private const val TWO_PI = 6.2831855f

/**
 * The RETRO_FUTURISM backdrop: a static wireframe grid with a horizon glow.
 *
 * Deliberately NOT animated (the grid is the look; Aurora earns its motion
 * because drifting blobs are its entire identity, and this style's identity is
 * one rendered plane.) Cost is one fullscreen draw on first composition only:
 * geometry derives from size, lines are hairlines, no per-frame work.
 */

@Composable
// `UnstableApi` is an `androidx.annotation.RequiresOptIn` marker, NOT a
// `kotlin.RequiresOptIn` one, so Kotlin's `@OptIn` has no effect on it (and
// the compiler warned accordingly). The opt-in that actually applies here is
// `@SuppressLint("UnsafeOptInUsageError")` below — there is no
// `androidx.annotation.OptIn` class in androidx.annotation 1.9.1, verified with
// javap — so the Kotlin-side marker is a plain string-keyed @Suppress.
@Suppress("UnstableApiUsage")
@SuppressLint("UnsafeOptInUsageError")
private fun VideoBackground(
    videoUri: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The player is BUILT here, not in composition: creating an ExoPlayer is a
    // side effect (codec/IO init) and used to happen inside `remember(videoUri)`.
    // The holder is a state so the AndroidView below can render it once ready.
    val playerHolder = remember(videoUri) { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(videoUri) {
        val player =
            try {
                ExoPlayer.Builder(context)
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(videoUri))
                        repeatMode = Player.REPEAT_MODE_ALL
                        volume = 0f
                        prepare()
                        playWhenReady = true
                    }
            } catch (e: Exception) {
                LogRepository.add("ExoPlayer init failed: ${e.message}", LogLevel.WARNING)
                null
            }
        playerHolder.value = player
        if (player != null) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> player.pause()
                        Lifecycle.Event.ON_RESUME -> player.play()
                        else -> {}
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                player.release()
                playerHolder.value = null
            }
        } else {
            onDispose { playerHolder.value = null }
        }
    }

    val player = playerHolder.value

    if (player == null) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                setPlayer(player)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view -> if (view.getPlayer() != player) view.setPlayer(player) },
        modifier = modifier,
    )
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - alpha)))
}

val GLITCH_NAMES =
    listOf(
        "WuWaConfig",
        "Rover's Tool",
        "Config Forge",
        "Pulse Engine",
        "Wave Weaver",
        "Crystal Core",
        "Echo Terminal",
        "Signal Boost",
        "Resonance Kit",
        "Tuning Fork",
    )

@Composable
fun GlitchText(
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    intervalMs: Long = 30000L,
    names: List<String> = GLITCH_NAMES,
    style: androidx.compose.ui.text.TextStyle? = null,
) {
    var currentIndex by remember { mutableStateOf(0) }
    var displayText by remember { mutableStateOf(names.getOrElse(0) { "" }) }
    var glitchActive by remember { mutableStateOf(false) }
    var shakeOffsetX by remember { mutableStateOf(0f) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // One shared source of truth for "user asked for less motion" — see the
    // helper's doc for why it reads all three animation scales, not just one.
    val reduceMotion = rememberReducedMotion()

    LaunchedEffect(names, intervalMs, reduceMotion) {
        currentIndex = 0
        displayText = names.getOrElse(0) { "" }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(intervalMs)
                if (names.size < 2) continue
                val nextIndex = (currentIndex + 1) % names.size
                val target = names[nextIndex]
                if (reduceMotion) {
                    displayText = target
                    currentIndex = nextIndex
                    continue
                }
                glitchActive = true
                shakeOffsetX = Random.nextFloat() * 4f - 2f

                val len = maxOf(displayText.length, target.length)
                val scrambleStart = System.currentTimeMillis()
                // Hoist the buffer + glyph set out of the loop: rebuilding a
                // StringBuilder (and the chars literal) on every 40ms frame was
                // the alloc_in_loop smell flagged by codebase-memory-mcp.
                val sb = StringBuilder(len)
                val chars = "!@#$%^&*{}[]|\\/~`"
                // try/finally: cancellation can land mid-scramble (backgrounded
                // exactly during the glitch). repeatOnLifecycle then restarts the
                // block at delay(intervalMs) — without the finally, backgrounding
                // at the wrong instant froze a █/glyph soup on screen for up to a
                // full interval (15–30s) after resume.
                try {
                    while (System.currentTimeMillis() - scrambleStart < 300) {
                        sb.setLength(0)
                        for (i in 0 until len) {
                            when {
                                i >= target.length -> sb.append('█')
                                Random.nextFloat() < 0.25f ->
                                    sb.append(chars[Random.nextInt(chars.length)])
                                else -> sb.append(target[i])
                            }
                        }
                        displayText = sb.toString()
                        delay(40)
                    }
                } finally {
                    // Runs on both the normal path (no-op overwrite) and on
                    // cancellation unwinding out of the delay — in both cases the
                    // next thing the reader sees is a clean name, and glitchActive
                    // is dropped so the resume restart is not stuck shaking.
                    displayText = target
                    currentIndex = nextIndex
                    glitchActive = false
                }
            }
        }
    }

    val offsetX by animateFloatAsState(
        targetValue = if (glitchActive) shakeOffsetX else 0f,
        animationSpec = if (glitchActive) tween(80) else spring(dampingRatio = 0.3f),
    )

    val finalMod =
        modifier.then(
            if (glitchActive) Modifier.offset(x = offsetX.dp) else Modifier,
        )
    if (style != null) {
        Text(text = displayText, fontWeight = fontWeight, style = style, modifier = finalMod)
    } else {
        Text(text = displayText, fontWeight = fontWeight, modifier = finalMod)
    }
}

// Refcounted decorView blur. Previously every dialog independently set AND
// cleared the blur, so two simultaneously-open dialogs shared one effect and
// disposing either one cleared the blur for both. The depth counter keeps the
// effect alive until the last dialog goes away.
private var dialogBlurDepth = 0

private fun acquireDialogBlur(activity: Activity?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activity == null) return
    if (dialogBlurDepth++ == 0) {
        activity.window.decorView.setRenderEffect(
            RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP),
        )
    }
}

private fun releaseDialogBlur(activity: Activity?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activity == null) return
    if (--dialogBlurDepth <= 0) {
        dialogBlurDepth = 0
        activity.window.decorView.setRenderEffect(null)
    }
}

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    accentColor: Color = NeonCyan,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val tokens = LocalThemeShapeTokens.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val activity = LocalView.current.context as? Activity
    DisposableEffect(isLight, activity) {
        val blurs = !isLight
        if (blurs) acquireDialogBlur(activity)
        onDispose { if (blurs) releaseDialogBlur(activity) }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        val shape = RoundedCornerShape(tokens.dialogCorner)
        val titleColor = accentColor
        // Both branches read the scheme: the light branch hardcoded Material's
        // default ink (#1C1B1F), so palettes with a different light-mode ink
        // (PAPER_INK, SOFT_CLAY) got dialog bodies that quietly disagreed with
        // every other text surface fed by onSurface.
        val bodyColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)

        if (isLight) {
            // Opaque scheme surface: a dialog has to occlude what is behind it,
            // and the dark branch's translucent white gradients cannot do that
            // over a light window background.
            Box(
                modifier =
                    Modifier
                        .widthIn(min = 260.dp, max = 380.dp)
                        .shadow(24.dp, shape, clip = false)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surface, shape)
                        .border(1.dp, accentColor.copy(alpha = 0.18f), shape),
            ) {
                GlassDialogContent(accentColor, isLight, titleColor, bodyColor, icon, title, text, confirmButton, dismissButton)
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .widthIn(min = 260.dp, max = 380.dp)
                        .shadow(24.dp, shape, clip = false)
                        .clip(shape)
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.White.copy(alpha = 0.14f),
                                            Color.White.copy(alpha = 0.06f),
                                            accentColor.copy(alpha = 0.05f),
                                        ),
                                ),
                            shape = shape,
                        )
                        .border(
                            width = 1.dp,
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.White.copy(alpha = 0.4f),
                                            Color.White.copy(alpha = 0.08f),
                                            accentColor.copy(alpha = 0.25f),
                                        ),
                                ),
                            shape = shape,
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.10f), Color.Transparent),
                                    radius = 700f,
                                ),
                            ),
                )
                GlassDialogContent(accentColor, isLight, titleColor, bodyColor, icon, title, text, confirmButton, dismissButton)
            }
        }
    }
}

@Composable
private fun GlassDialogContent(
    accentColor: Color,
    isLight: Boolean,
    titleColor: Color,
    bodyColor: Color,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(LocalThemeShapeTokens.current.dialogPadding)) {
        icon?.let {
            Box(
                modifier =
                    Modifier
                        .padding(bottom = 12.dp)
                        .align(Alignment.CenterHorizontally),
            ) { it() }
        }
        title?.let {
            CompositionLocalProvider(LocalContentColor provides titleColor) {
                Box(
                    modifier =
                        Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                ) { it() }
            }
        }
        // The body is height-capped and scrollable so tall dialogs (e.g. Backup's
        // "text field + 5 checkboxes") can never push the button row off-screen on
        // a small display or in landscape. The buttons stay OUTSIDE the scroll
        // container so they remain reachable.
        text?.let {
            CompositionLocalProvider(LocalContentColor provides bodyColor) {
                Box(
                    modifier =
                        Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                ) { it() }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissButton != null) {
                dismissButton()
                Spacer(Modifier.width(8.dp))
            }
            confirmButton()
        }
    }
}

/**
 * Renders a RAM figure given in MB.
 *
 * Single formatter because the same `LogInfo.ramMb` was rendered two ways: the
 * Profile screen did integer division (`it / 1024`), so a device reporting
 * 5642 MB — 6 GB of RAM — was shown as "5 GB", while ConfigGen showed the raw
 * "5642 MB". `%.1f` keeps the real figure visible instead of rounding down to a
 * different whole number of gigabytes.
 */
fun formatRam(ramMb: Int): String = "%.1f GB".format(ramMb / 1024.0)

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun StatusChip(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) { // Container/on-container pairs from the scheme, not raw accent paints:
    // `NeonGreen` on its own 20% tint over white measured 2.95:1 in light mode
    // — under the AA floor. The scheme slots are the ones the palette already
    // guarantees contrast for, in both modes.
    val backgroundColor = if (isActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(LocalSpacing.current.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Animated ring gauge for a 0..1 progress value (SmartBrain score, pity count,
 * task completion).
 *
 * Drawn as two arcs on one small Canvas — a full-circle track and the swept
 * foreground — so the gauge stays one layer and one draw pass, cheap on the
 * low-end GPUs this app targets. The sweep animates on first appearance and on
 * every change; the track is a flat scheme colour, not alpha-composited, so it
 * reads identically in both modes.
 */
@Composable
fun ProgressRing(
    progress: Float,
    label: String,
    sublabel: String? = null,
    modifier: Modifier = Modifier,
    color: Color = NeonCyan,
    ringSize: Dp = 96.dp,
) {
    val coerced = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = coerced,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "progressRing",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(4.dp),
        ) {
            val stroke = this.size.minDimension * 0.09f
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color)),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(LocalSpacing.current.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.md),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonRed,
        )
        if (onRetry != null) {
            GlassButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(LocalSpacing.current.lg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = NeonCyan)
    }
}

@Preview(name = "SectionHeader Light", showBackground = true)
@Composable
private fun SectionHeaderLightPreview() {
    WuWaConfigTheme(themeMode = "light") {
        SectionHeader("Section Title")
    }
}

@Preview(name = "SectionHeader Dark", showBackground = true)
@Composable
private fun SectionHeaderDarkPreview() {
    WuWaConfigTheme(themeMode = "dark") {
        SectionHeader("Section Title")
    }
}

@Preview(name = "StatusChip Light", showBackground = true)
@Composable
private fun StatusChipLightPreview() {
    WuWaConfigTheme(themeMode = "light") {
        Row {
            StatusChip("Connected", isActive = true)
            StatusChip("Disconnected", isActive = false)
        }
    }
}

@Preview(name = "StatusChip Dark", showBackground = true)
@Composable
private fun StatusChipDarkPreview() {
    WuWaConfigTheme(themeMode = "dark") {
        Row {
            StatusChip("Connected", isActive = true)
            StatusChip("Disconnected", isActive = false)
        }
    }
}

@Preview(name = "ProgressRing Light", showBackground = true)
@Composable
private fun ProgressRingLightPreview() {
    WuWaConfigTheme(themeMode = "light") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(progress = 0.72f, label = "72", sublabel = "SCORE")
            ProgressRing(progress = 0.44f, label = "44", sublabel = "PITY", color = NeonAmber)
        }
    }
}

@Preview(name = "ProgressRing Dark", showBackground = true)
@Composable
private fun ProgressRingDarkPreview() {
    WuWaConfigTheme(themeMode = "dark") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(progress = 0.72f, label = "72", sublabel = "SCORE")
            ProgressRing(progress = 0.44f, label = "44", sublabel = "PITY", color = NeonAmber)
        }
    }
}
