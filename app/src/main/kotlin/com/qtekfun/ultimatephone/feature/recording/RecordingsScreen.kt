package com.qtekfun.ultimatephone.feature.recording

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.designsystem.ConfirmDialog
import com.qtekfun.ultimatephone.core.designsystem.EmptyState
import com.qtekfun.ultimatephone.core.designsystem.GroupedItem
import com.qtekfun.ultimatephone.core.designsystem.ScreenScaffold
import com.qtekfun.ultimatephone.core.designsystem.Spacing
import com.qtekfun.ultimatephone.core.recording.RecordingFormatters
import com.qtekfun.ultimatephone.core.recording.RecordingNames
import com.qtekfun.ultimatephone.core.recording.RecordingStorage

/** Settings > Call recording > Recordings. */
@Suppress("DEPRECATION") // The Slider overload with a plain value is the simple one; SliderState needs hoisting for nothing here.
@Composable
fun RecordingsRoute(onBack: () -> Unit, viewModel: RecordingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var toRename by remember { mutableStateOf<RecordingItem?>(null) }
    val context = LocalContext.current

    val message = state.message?.let {
        stringResource(
            if (it ==
                ListMessage.DELETE_FAILED
            ) {
                R.string.recording_delete_failed
            } else {
                R.string.recording_rename_failed
            }
        )
    }
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.messageShown()
        }
    }
    val playFailed = player?.failed == true
    val playFailedText = stringResource(R.string.recording_play_failed)
    LaunchedEffect(playFailed) { if (playFailed) Toast.makeText(context, playFailedText, Toast.LENGTH_LONG).show() }

    ScreenScaffold(title = stringResource(R.string.recording_list_title), onBack = onBack) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> Unit
                !state.hasFolder -> EmptyState(
                    icon = Icons.Filled.FolderOff,
                    title = stringResource(R.string.recording_list_title),
                    body = stringResource(R.string.recording_list_no_folder),
                    modifier = Modifier.align(Alignment.Center)
                )
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Mic,
                    title = stringResource(R.string.recording_list_title),
                    body = stringResource(R.string.recording_list_empty),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.Small)
                ) {
                    items(state.items.size, key = { state.items[it].uri.toString() }) { index ->
                        val item = state.items[index]
                        GroupedItem(index = index, count = state.items.size) {
                            RecordingCard(
                                item = item,
                                player = player?.takeIf { it.uri == item.uri },
                                onPlay = { viewModel.togglePlay(item) },
                                onSeek = viewModel::seek,
                                onShare = { share(context, item) },
                                onRename = { toRename = item },
                                onDelete = { toDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.recording_delete_title),
            body = stringResource(R.string.recording_delete_message, item.name),
            confirmLabel = stringResource(R.string.recording_delete),
            dismissLabel = stringResource(R.string.recording_cancel),
            onConfirm = { viewModel.delete(item) },
            onDismiss = { toDelete = null },
            destructive = true
        )
    }
    toRename?.let { item ->
        RenameDialog(
            item = item,
            onDismiss = { toRename = null },
            onConfirm = {
                toRename = null
                viewModel.rename(item, it)
            }
        )
    }
}

@Composable
private fun RecordingCard(
    item: RecordingItem,
    player: PlayerState?,
    onPlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val title = when (item.incoming) {
        true -> stringResource(R.string.recording_item_incoming, item.label.orEmpty())
        false -> stringResource(R.string.recording_item_outgoing, item.label.orEmpty())
        null -> item.name
    }
    val date = DateUtils.formatDateTime(context, item.dateMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR)
    val size = RecordingFormatters.size(item.sizeBytes)
    val subtitle = item.durationMs?.let { stringResource(R.string.recording_item_subtitle, date, RecordingFormatters.duration(it), size) }
        ?: stringResource(R.string.recording_item_subtitle_no_duration, date, size)
    val playing = player?.playing == true

    Column(modifier = Modifier.padding(start = Spacing.Medium, end = Spacing.Small, top = Spacing.Medium - Spacing.XSmall, bottom = Spacing.XSmall)) {
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (player != null && player.ready) {
            val time =
                stringResource(
                    R.string.recording_player_time,
                    RecordingFormatters.duration(player.positionMs.toLong()),
                    RecordingFormatters.duration(player.durationMs.toLong())
                )
            val positionLabel = stringResource(R.string.recording_position)
            Slider(
                value = player.positionMs.toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..player.durationMs.coerceAtLeast(1).toFloat(),
                modifier = Modifier.padding(end = Spacing.Small).semantics {
                    contentDescription = positionLabel
                    stateDescription = time
                }
            )
            Text(time, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = Spacing.XSmall))
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            // Each button names the recording it acts on, since TalkBack reads them one by one.
            val playLabel =
                stringResource(R.string.recording_action_for, stringResource(if (playing) R.string.recording_pause else R.string.recording_play), title)
            IconButton(onClick = onPlay) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = playLabel)
            }
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.recording_action_for, stringResource(R.string.recording_share), title)
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.recording_action_for, stringResource(R.string.recording_rename), title)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.recording_action_for, stringResource(R.string.recording_delete), title)
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(item: RecordingItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var typed by remember { mutableStateOf(item.name.removeSuffix(RecordingNames.extensionOf(item.name))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(R.string.recording_rename_title)) },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.recording_rename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(typed) },
                enabled = RecordingNames.renamed(typed, item.name) != null,
                modifier = Modifier.heightIn(min = Spacing.MinTarget)
            ) { Text(stringResource(R.string.recording_rename_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.MinTarget)) { Text(stringResource(R.string.recording_cancel)) }
        }
    )
}

/** Hands the recording to another app. The file stays where it is; the receiving app gets read access to it only. */
private fun share(context: Context, item: RecordingItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = RecordingStorage.mimeOf(item.name)
        putExtra(Intent.EXTRA_STREAM, item.uri)
        clipData = ClipData.newRawUri("", item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // ActivityNotFoundException when no app takes audio; anything else the chooser refuses is the same answer.
    val shown = runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.recording_share_chooser))) }.isSuccess
    if (!shown) Toast.makeText(context, R.string.recording_share_failed, Toast.LENGTH_LONG).show()
}
