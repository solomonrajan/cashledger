package com.oriondev.moneywallet.ui.activity.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.utils.SystemBars
import com.oriondev.moneywallet.utils.Utils

abstract class SinglePanelActivity : BaseActivity(), Toolbar.OnMenuItemClickListener {

    private var toolbar: Toolbar? = null
    private var floatingActionButton: FloatingActionButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onInflateRootLayout()
        // Only the width qualified copies of these layouts carry one.
        SystemBars.keepGuidelineBelowToolbar(
            findViewById(R.id.toolbar_delimiter_horizontal_guideline),
            findViewById(R.id.primary_toolbar)
        )
        onSetupRootLayout()
        onConfigureRootLayout(savedInstanceState)
        onSetupFloatingActionButton(floatingActionButton)
        onViewCreated(savedInstanceState)
        setupToolbar()
    }

    protected open fun onInflateRootLayout() {
        setContentView(R.layout.activity_single_panel)
    }

    protected open fun onSetupRootLayout() {
        toolbar = findViewById(R.id.primary_toolbar)
        floatingActionButton = findViewById(R.id.floating_action_button)
    }

    private fun setupToolbar() {
        val tb = toolbar ?: return
        val titleRes = getActivityTitleRes()
        if (titleRes > 0) {
            tb.setTitle(titleRes)
        }
        val iconRes = getNavigationIcon()
        if (iconRes > 0) {
            tb.setNavigationIcon(iconRes)
            tb.setNavigationOnClickListener {
                @Suppress("DEPRECATION")
                onBackPressed()
            }
        }
        val menuRes = onInflateMenu()
        if (menuRes > 0) {
            tb.inflateMenu(menuRes)
            tb.setOnMenuItemClickListener(this)
            onMenuCreated(tb.menu)
        }
        onToolbarReady(tb)
    }

    protected open fun onConfigureRootLayout(savedInstanceState: Bundle?) {
        val inflater = layoutInflater
        val parent = Utils.findViewGroupByIds(
            this,
            R.id.primary_panel_container_frame_layout,
            R.id.primary_panel_container_card_view,
            R.id.primary_panel_container_linear_layout,
            R.id.primary_panel_container_coordinator_layout
        )
        onCreatePanelView(inflater, parent, savedInstanceState)
    }

    protected open fun onSetupFloatingActionButton(floatingActionButton: FloatingActionButton?) {
        if (floatingActionButton != null) {
            if (isFloatingActionButtonEnabled()) {
                floatingActionButton.setOnClickListener {
                    onFloatingActionButtonClick()
                }
            } else {
                floatingActionButton.isVisible = false
            }
        }
    }

    protected abstract fun onCreatePanelView(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        savedInstanceState: Bundle?
    )

    protected open fun onViewCreated(savedInstanceState: Bundle?) {
    }

    protected open fun onMenuCreated(menu: Menu) {
    }

    protected open fun onToolbarReady(toolbar: Toolbar) {
    }

    @StringRes
    protected abstract fun getActivityTitleRes(): Int

    protected open fun setToolbarSubtitle(subtitle: String?) {
        toolbar?.subtitle = subtitle
    }

    @DrawableRes
    protected open fun getNavigationIcon(): Int {
        return R.drawable.ic_arrow_back_black_24dp
    }

    @MenuRes
    protected open fun onInflateMenu(): Int {
        return 0
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return false
    }

    protected open fun isFloatingActionButtonEnabled(): Boolean {
        return true
    }

    protected open fun onFloatingActionButtonClick() {
        // override this method if you have to handle the floating action button click event
    }
}
