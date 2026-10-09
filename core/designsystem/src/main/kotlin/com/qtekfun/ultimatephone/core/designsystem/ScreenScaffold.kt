package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.qtekfun.ultimatephone.core.designsystem.R

/**
 * The frame of every full screen: a large title that collapses into a small top bar when the content scrolls.
 *
 * Insets: the scaffold uses the standard `safeDrawing` insets minus whatever a parent already consumed. Inside the app's
 * `NavHost` the top, side and bottom-bar insets are already consumed, so nothing is padded twice; as a stand-alone screen
 * (an activity) it pads for the status bar and the cutout itself. Pass the `PaddingValues` it gives you to the scrolling
 * content (`contentPadding` of a `LazyColumn`, or `Modifier.padding` of a scrolling `Column`), so the last item can scroll
 * clear of the bottom bar and the content starts under the title.
 *
 * Scrolling: the collapse is driven by the nested scroll of the content, so the content must be (or contain) a scrollable.
 * A screen that does not scroll just keeps the large title.
 *
 * The title is announced as a heading; the back button, shown when [onBack] is not null, is at least 48 dp and is named.
 *
 * ```
 * ScreenScaffold(title = stringResource(R.string.settings_title), onBack = { navController.popBackStack() }) { padding ->
 *     LazyColumn(contentPadding = padding) { ... }
 * }
 * ```
 *
 * @param actions icon buttons at the end of the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.design_back))
                        }
                    }
                },
                actions = actions,
                // One colour for both states: the bar and the status bar behind it never differ while scrolling.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        content = content
    )
}
