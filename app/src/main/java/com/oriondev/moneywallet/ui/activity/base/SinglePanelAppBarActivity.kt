package com.oriondev.moneywallet.ui.activity.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.utils.Utils

abstract class SinglePanelAppBarActivity : SinglePanelActivity() {

    override fun onInflateRootLayout() {
        setContentView(R.layout.activity_single_panel_appbar)
    }

    override fun onConfigureRootLayout(savedInstanceState: Bundle?) {
        val inflater = layoutInflater
        val headerContainer = findViewById<ViewGroup>(R.id.app_bar_container)
        val panelContainer = Utils.findViewGroupByIds(
            this,
            R.id.primary_panel_container_frame_layout,
            R.id.primary_panel_container_card_view,
            R.id.primary_panel_container_linear_layout,
            R.id.primary_panel_container_coordinator_layout
        )
        onCreateHeaderView(inflater, headerContainer, savedInstanceState)
        onCreatePanelView(inflater, panelContainer, savedInstanceState)
    }

    protected abstract fun onCreateHeaderView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    )
}
