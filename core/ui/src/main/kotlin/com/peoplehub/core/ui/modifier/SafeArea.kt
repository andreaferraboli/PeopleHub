package com.peoplehub.core.ui.modifier

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

/**
 * Bottom padding for a screen's own action bar (the `bottomBar` slot of a `Scaffold`).
 *
 * The app draws edge to edge, and `Scaffold` does **not** inset its `bottomBar` for you — it hands the
 * bar the full width at the very bottom of the window and only reserves the bar's measured height for
 * the content above it. Without this, a save button sits underneath the phone's navigation buttons (or
 * gesture pill) and cannot be tapped. The keyboard inset is applied on top, so the bar rides above the
 * soft keyboard instead of being covered by it.
 *
 * Insets already consumed by an ancestor are not applied twice, so a bar inside the bottom-nav
 * scaffold (which consumes what its own navigation bar covers) correctly adds nothing.
 */
fun Modifier.safeBottomBarPadding(): Modifier = navigationBarsPadding().imePadding()

/**
 * Padding that keeps a full-width dialog clear of every system bar, the display cutout and the
 * keyboard. Dialogs opened with `usePlatformDefaultWidth = false` are laid out against the raw window,
 * so a tall one would otherwise run under the status bar and the navigation buttons.
 */
fun Modifier.safeDialogPadding(): Modifier = safeDrawingPadding()
