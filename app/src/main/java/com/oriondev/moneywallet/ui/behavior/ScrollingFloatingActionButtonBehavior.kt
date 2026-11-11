/*
 * Copyright (c) 2018.
 * Copyright (c) 2026 solomonrajan.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.behavior

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlin.math.min

/**
 * A custom behavior for the Floating Action Button when it is attached to a CoordinatorLayout. It
 * shrinks the button as a snackbar comes up under it, so the two do not overlap.
 */
@SuppressLint("RestrictedApi")
class ScrollingFloatingActionButtonBehavior(
    context: Context,
    attrs: AttributeSet?
) : CoordinatorLayout.Behavior<FloatingActionButton>(context, attrs) {

    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        fab: FloatingActionButton,
        dependency: View
    ): Boolean = dependency is Snackbar.SnackbarLayout

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        fab: FloatingActionButton,
        dependency: View
    ): Boolean {
        if (dependency is Snackbar.SnackbarLayout) {
            val height = dependency.height
            val scaleFactor = if (height > 0) {
                val translationY = getFabTranslationYForSnackBar(parent, fab)
                val percentComplete = -translationY / height
                1f - percentComplete
            } else {
                1f
            }
            fab.scaleX = scaleFactor
            fab.scaleY = scaleFactor
        }
        return true
    }

    private fun getFabTranslationYForSnackBar(
        parent: CoordinatorLayout,
        fab: FloatingActionButton
    ): Float {
        var minOffset = 0f
        for (view in parent.getDependencies(fab)) {
            if (view is Snackbar.SnackbarLayout && parent.doViewsOverlap(fab, view)) {
                minOffset = min(minOffset, view.translationY - view.height)
            }
        }
        return minOffset
    }
}
