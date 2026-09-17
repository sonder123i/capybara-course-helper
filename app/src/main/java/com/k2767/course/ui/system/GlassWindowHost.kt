package com.k2767.course.ui.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.k2767.course.manager.AppearanceSettingsManager
import com.k2767.course.ui.system.glass.LocalGlassLensAnchor
import com.k2767.course.ui.system.glass.LocalGlassLensModalAnchor
import com.k2767.course.ui.system.glass.drawBackdropSource
import com.k2767.course.ui.system.glass.drawBlurred
import com.k2767.course.ui.system.glass.glassLensAnchor
import com.k2767.course.ui.system.glass.rememberGlassLensRegion

private val LocalGlassWindowOwner = staticCompositionLocalOf<android.view.View?> { null }

/** Each actual window owns its sources; pages inside that window reuse the provided locals. */
@Composable
fun GlassWindowHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val view = LocalView.current
    val appWallpaper = com.k2767.course.ui.theme.rememberAppWallpaperStyle()
    if (LocalGlassWindowOwner.current === view) {
        Box(modifier.fillMaxSize()) { content() }
        return
    }
    val wallpaper = if (isBackdropSupported()) rememberLayerBackdrop() else null
    val page = if (wallpaper != null) rememberLayerBackdrop() else null
    val modal = if (wallpaper != null && page != null) rememberCombinedBackdrop(wallpaper, page) else null
    val density = LocalDensity.current
    val dialogs = rememberDialogHostState()
    val controlAnchor = if (wallpaper != null) rememberGlassLensRegion("window-control") { coordinates ->
        drawBackdropSource(wallpaper, density, coordinates)
    } else null
    val modalAnchor = if (modal != null) rememberGlassLensRegion("window-modal", dialogs.currentDialog) { coordinates ->
        drawBlurred(GlassRecipe.DialogBlurDp * density.density) { drawBackdropSource(modal, density, coordinates) }
    } else null
    CompositionLocalProvider(
        LocalGlassWindowOwner provides view,
        LocalAppBackdrop provides wallpaper,
        LocalControlBackdrop provides wallpaper,
        LocalModalBackdrop provides modal,
        LocalGlassLensAnchor provides controlAnchor,
        LocalGlassLensModalAnchor provides modalAnchor,
        LocalDialogHost provides dialogs
    ) {
        GlassOverlayHost(modifier) {
            Canvas(Modifier.fillMaxSize().then(if (wallpaper != null) Modifier.layerBackdrop(wallpaper) else Modifier)) {
                drawWallpaperPattern(appWallpaper, microTexture = wallpaper != null)
            }
            Box(Modifier.fillMaxSize().glassLensAnchor(controlAnchor).glassLensAnchor(modalAnchor)
                .then(if (page != null) Modifier.layerBackdrop(page) else Modifier)) { content() }
            DialogHost(dialogs)
        }
    }
}
