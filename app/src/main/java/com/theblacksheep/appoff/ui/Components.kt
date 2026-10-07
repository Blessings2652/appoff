package com.theblacksheep.appoff.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.theblacksheep.appoff.core.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalCardColor = staticCompositionLocalOf { Color.White }

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") baseColor: Color = LocalCardColor.current,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

// Small, size-capped bitmap cache so scrolling never re-rasterises the same icon.
private val iconBitmapCache = LruCache<String, ImageBitmap>(400)

/**
 * Loads an app icon lazily and off the main thread. Returns null for the first frame(s) while the
 * icon loads, so the list itself can be shown immediately.
 */
@Composable
fun rememberAppIcon(packageName: String?, preloaded: Drawable? = null): ImageBitmap? {
    val pkg = packageName.orEmpty()
    val context = LocalContext.current.applicationContext
    val state = produceState<ImageBitmap?>(initialValue = iconBitmapCache.get(pkg), pkg) {
        if (value == null && pkg.isNotEmpty()) {
            value = withContext(Dispatchers.Default) {
                val drawable = preloaded ?: AppRepository.loadIcon(context, pkg)
                drawable?.toBitmap(128, 128)?.asImageBitmap()?.also { iconBitmapCache.put(pkg, it) }
            }
        }
    }
    return state.value
}
