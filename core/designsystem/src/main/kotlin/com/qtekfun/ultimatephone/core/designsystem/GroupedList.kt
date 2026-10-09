package com.qtekfun.ultimatephone.core.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val InnerCorner = CornerSize(4.dp)

/**
 * The shape of item [index] of [count] in a list drawn as one rounded group: the first item has the large corners on top, the
 * last one at the bottom, the ones between are almost square, and a single item is fully rounded.
 */
@Composable
fun groupedItemShape(index: Int, count: Int): Shape {
    val large = MaterialTheme.shapes.large as CornerBasedShape
    val first = index == 0
    val last = index == count - 1
    return large.copy(
        topStart = if (first) large.topStart else InnerCorner,
        topEnd = if (first) large.topEnd else InnerCorner,
        bottomStart = if (last) large.bottomStart else InnerCorner,
        bottomEnd = if (last) large.bottomEnd else InnerCorner
    )
}

/**
 * One item of a lazy list that should look like a row of a [SettingsGroup]: a `surfaceContainer` card with the side margin,
 * rounded only at the ends of the group and separated from its neighbours by a hairline gap instead of a divider line.
 *
 * Use it inside `LazyColumn { itemsIndexed(list) { index, item -> GroupedItem(index, list.size) { Row(...) } } }`, where a real
 * [SettingsGroup] cannot wrap the lazy items. [color] is the selected-row colour when a row is selected.
 */
@Composable
fun GroupedItem(index: Int, count: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = groupedItemShape(index, count),
        color = color,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = 1.dp)
    ) {
        Column(content = content)
    }
}
