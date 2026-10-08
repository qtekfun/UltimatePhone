package com.qtekfun.ultimatephone.feature.recording

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatephone.R
import com.qtekfun.ultimatephone.core.recording.RecordingFormatters
import com.qtekfun.ultimatephone.core.recording.RecordingNames
import com.qtekfun.ultimatephone.core.recording.RecordingStorage

/** Settings > Call recording > Recordings. */
@Suppress("DEPRECATION") // The Slider overload with a plain value is the simple one; SliderState needs hoisting for nothing here.
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recording_list_title)) },
                windowInsets = WindowInsets(0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.recording_back)) }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> Unit
                !state.hasFolder -> EmptyText(R.string.recording_list_no_folder)
                state.items.isEmpty() -> EmptyText(R.string.recording_list_empty)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.uri.toString() }) { item ->
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

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.recording_delete_title)) },
            text = { Text(stringResource(R.string.recording_delete_message, item.name)) },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    viewModel.delete(item)
                }) { Text(stringResource(R.string.recording_delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.recording_cancel)) } }
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
private fun EmptyText(text: Int) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    modifier = Modifier.padding(end = 8.dp).semantics {
                        contentDescription = positionLabel
                        stateDescription = time
                    }
                )
                Text(time, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onPlay) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (playing) R.string.recording_pause else R.string.recording_play)
                    )
                }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.recording_share)) }
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.recording_rename)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.recording_delete)) }
            }
        }
    }
}

@Composable
private fun RenameDialog(item: RecordingItem, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var typed by remember { mutableStateOf(item.name.removeSuffix(RecordingNames.extensionOf(item.name))) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = { onConfirm(typed) }, enabled = RecordingNames.renamed(typed, item.name) != null) {
                Text(stringResource(R.string.recording_rename_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.recording_cancel)) } }
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
