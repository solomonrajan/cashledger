/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.ui.fragment.stub

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment

/**
 * This class is a stub implementation of a simple multi panel fragment and it is useful until
 * a fragment for each section is not ready
 */
open class StubMultiPanelFragment : MultiPanelFragment() {

    override fun onCreatePrimaryPanel(
        inflater: LayoutInflater,
        primaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val textView = TextView(primaryPanel.context)
        textView.text = "Primary panel"
        textView.setOnClickListener {
            showSecondaryPanel()
        }
        textView.setBackgroundColor(Color.RED)

        val params = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        params.behavior = AppBarLayout.ScrollingViewBehavior()

        primaryPanel.addView(textView, params)
    }

    override fun onCreateSecondaryPanel(
        inflater: LayoutInflater,
        secondaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val textView = TextView(secondaryPanel.context)
        textView.text = "Secondary panel"
        textView.setOnClickListener {
            hideSecondaryPanel()
        }
        secondaryPanel.addView(
            textView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun getTitleRes(): Int = R.string.app_name
}
