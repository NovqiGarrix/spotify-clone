package dev.novqigarrix.mymusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.ui.theme.Sizes
import dev.novqigarrix.mymusic.ui.theme.Spacing
import kotlin.math.max

val TopBarHeight = 56.dp
private const val TITLE_FADE_START = 0.75f
private const val BAR_DARKEN = 0.45f
private const val BACK_SCRIM_ALPHA = 0.35f

/**
 * How far the first list item (the header) has scrolled away, 0..1. Read it only inside
 * layout/draw lambdas (graphicsLayer, drawBehind, offset) so scrolling never recomposes.
 */
fun LazyListState.headerCollapseFraction(): Float {
    if (firstVisibleItemIndex > 0) return 1f
    val header = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
    if (header.size == 0) return 0f
    return (firstVisibleItemScrollOffset / header.size.toFloat()).coerceIn(0f, 1f)
}

/** Header scroll offset in px while the header is the first visible item. */
fun LazyListState.headerScrollPx(): Int = if (firstVisibleItemIndex == 0) firstVisibleItemScrollOffset else Int.MAX_VALUE

/**
 * Spotify's detail-page top bar: transparent over the header, then fades to the artwork color
 * and reveals the title once the header has scrolled away.
 */
@Composable
fun CollapsingTopBar(
    title: String,
    color: State<Color>,
    listState: LazyListState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackScrim: Boolean = false,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val fraction = listState.headerCollapseFraction()
                val barAlpha = ((fraction - TITLE_FADE_START) / (1f - TITLE_FADE_START)).coerceIn(0f, 1f)
                drawRect(lerp(color.value, background, BAR_DARKEN).copy(alpha = barAlpha))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(TopBarHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.screen + TopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = Spacing.xs)) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .graphicsLayer {
                            alpha = if (showBackScrim) 1f - listState.headerCollapseFraction() else 0f
                        }
                        .background(Color.Black.copy(alpha = BACK_SCRIM_ALPHA), CircleShape),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.xs)
                    .graphicsLayer {
                        val fraction = listState.headerCollapseFraction()
                        val progress = ((fraction - TITLE_FADE_START) / (1f - TITLE_FADE_START)).coerceIn(0f, 1f)
                        alpha = progress
                        translationY = (1f - progress) * 12.dp.toPx()
                    },
            )
        }
    }
}

/**
 * The big green play button that scrolls with its row and then pins to the bottom edge of the
 * top bar, exactly like Spotify. Place it in the same Box as the list, aligned TopEnd.
 */
@Composable
fun PinnedPlayButton(
    listState: LazyListState,
    anchorKey: Any,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.playButton,
) {
    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current)
    PlayPauseButton(
        isPlaying = isPlaying,
        onClick = onClick,
        size = size,
        modifier = modifier
            .padding(end = Spacing.screen)
            .offset {
                val sizePx = size.roundToPx()
                val pinnedY = statusBarTop + TopBarHeight.roundToPx() - sizePx / 2
                val items = listState.layoutInfo.visibleItemsInfo
                val anchor = items.firstOrNull { it.key == anchorKey }
                val y = when {
                    anchor != null -> max(anchor.offset + (anchor.size - sizePx) / 2, pinnedY)
                    items.isNotEmpty() && listState.firstVisibleItemIndex > 0 -> pinnedY
                    else -> -sizePx * 4
                }
                IntOffset(0, y)
            },
    )
}
