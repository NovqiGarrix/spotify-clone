package dev.novqigarrix.mymusic.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.novqigarrix.mymusic.R
import dev.novqigarrix.mymusic.data.Song
import dev.novqigarrix.mymusic.playback.QueueItemKind
import dev.novqigarrix.mymusic.ui.collection.titleRes
import dev.novqigarrix.mymusic.ui.components.SongRow
import dev.novqigarrix.mymusic.ui.components.displayArtist
import dev.novqigarrix.mymusic.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSkipTo: (Song) -> Unit,
    onMore: (Song) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        val sourceTitle = when (val label = state.sourceLabel) {
            is SourceLabel.Album -> label.title
            is SourceLabel.Artist -> label.name
            is SourceLabel.Playlist -> stringResource(label.kind.titleRes())
            null -> null
        }
        val sourceHeader = if (sourceTitle != null) {
            stringResource(R.string.next_in_queue, sourceTitle)
        } else {
            stringResource(R.string.up_next)
        }
        LazyColumn(modifier = Modifier.fillMaxHeight(0.9f)) {
            item(key = "nowHeader") { QueueHeader(stringResource(R.string.now_playing)) }
            state.song?.let { song ->
                item(key = "now-${song.id}") {
                    SongRow(
                        title = song.title,
                        subtitle = song.displayArtist(),
                        isCurrent = true,
                        isPlaying = state.isPlaying,
                        isLiked = state.isLiked,
                        onClick = {},
                        onMoreClick = { onMore(song) },
                        artworkUri = song.albumArtUri,
                    )
                }
            }
            queueSections(state.upNext).forEachIndexed { sectionIndex, section ->
                item(key = "header-$sectionIndex") {
                    when (section.kind) {
                        QueueSection.Kind.Queued -> QueueHeader(stringResource(R.string.queue_added_by_you))
                        QueueSection.Kind.Source -> QueueHeader(sourceHeader)
                        QueueSection.Kind.Autoplay -> QueueHeader(
                            text = stringResource(R.string.queue_autoplay),
                            body = stringResource(R.string.queue_autoplay_body),
                        )
                    }
                }
                items(section.items, key = { it.key }) { (item, _) ->
                    val song = item.song
                    SongRow(
                        title = song.title,
                        subtitle = song.displayArtist(),
                        isCurrent = false,
                        isPlaying = false,
                        isLiked = song.id in state.likedIds,
                        onClick = { onSkipTo(song) },
                        onMoreClick = { onMore(song) },
                        artworkUri = song.albumArtUri,
                        isRecommended = item.kind == QueueItemKind.SmartPick,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** A run of consecutive queue items shown under one header, as in Spotify's queue. */
private class QueueSection(val kind: Kind, val items: List<KeyedItem>) {
    enum class Kind { Queued, Source, Autoplay }
}

private data class KeyedItem(val item: UpNextItem, val key: String)

private fun queueSections(upNext: List<UpNextItem>): List<QueueSection> {
    // The same song can be queued twice; number repeats so list keys stay unique.
    val seen = HashMap<Long, Int>()
    val keyed = upNext.map { item ->
        val occurrence = seen.merge(item.song.id, 1, Int::plus)!!
        KeyedItem(item, "next-${item.song.id}-$occurrence")
    }
    val sections = mutableListOf<QueueSection>()
    var run = mutableListOf<KeyedItem>()
    var runKind: QueueSection.Kind? = null
    for (entry in keyed) {
        val kind = when (entry.item.kind) {
            QueueItemKind.Queued -> QueueSection.Kind.Queued
            QueueItemKind.Source, QueueItemKind.SmartPick -> QueueSection.Kind.Source
            QueueItemKind.Autoplay -> QueueSection.Kind.Autoplay
        }
        if (kind != runKind && run.isNotEmpty()) {
            sections += QueueSection(runKind!!, run)
            run = mutableListOf()
        }
        runKind = kind
        run += entry
    }
    if (run.isNotEmpty()) sections += QueueSection(runKind!!, run)
    return sections
}

@Composable
private fun QueueHeader(text: String, body: String? = null) {
    Column(modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.medium)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
