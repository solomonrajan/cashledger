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

package com.oriondev.moneywallet.ui.fragment.base

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.CurrentWalletController
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.DrawerController
import com.oriondev.moneywallet.ui.activity.ToolbarController
import com.oriondev.moneywallet.ui.activity.base.ThemedActivity
import com.oriondev.moneywallet.utils.SystemBars
import com.oriondev.moneywallet.utils.Utils

/**
 * Created by andrea on 09/02/18.
 */
abstract class MultiPanelFragment : Fragment(), MultiPanelController, Toolbar.OnMenuItemClickListener {

    companion object {
        private const val SAVED_STATE_SECONDARY_PANEL_VISIBLE = "MultiPanelFragment::SecondaryPanelVisible"
        private const val CURRENT_WALLET_LOADER_ID = 60001
    }

    private var mPrimaryToolbar: Toolbar? = null
    private var mPrimaryPanel: ViewGroup? = null
    private var mSecondaryPanel: ViewGroup? = null
    private var mPrimaryPanelBodyContainer: ViewGroup? = null
    private var mFloatingActionButton: FloatingActionButton? = null
    private var mExtendedLayout = false
    private var mSecondaryPanelVisible = false

    private var mCurrentWalletObserver: BroadcastReceiver? = null
    private var mSelectionMode: TransactionSelectionMode? = null

    var selectionMode: TransactionSelectionMode?
        get() = mSelectionMode
        set(value) {
            mSelectionMode = value
            onSelectionModeChanged(value != null)
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (showsCurrentWallet()) {
            mCurrentWalletObserver = PreferenceManager.registerCurrentWalletObserver(
                context,
                object : CurrentWalletController {
                    override fun onCurrentWalletChanged(walletId: Long) {
                        onCurrentWalletChanged(walletId, null)
                    }

                    override fun onCurrentWalletChanged(walletId: Long, walletName: String?) {
                        showCurrentWalletInToolbar(true, walletName)
                    }
                }
            )
        }
    }

    override fun onDetach() {
        val observer = mCurrentWalletObserver
        if (observer != null) {
            PreferenceManager.unregisterCurrentWalletObserver(activity, observer)
            mCurrentWalletObserver = null
        }
        super.onDetach()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = onInflateRootLayout(inflater, container, savedInstanceState)
        SystemBars.keepGuidelineBelowToolbar(
            view.findViewById(R.id.toolbar_delimiter_horizontal_guideline),
            view.findViewById(R.id.primary_toolbar)
        )
        onSetupRootLayout(view)
        onConfigureRootLayout(inflater, container, savedInstanceState)
        setupPrimaryToolbar(mPrimaryToolbar)
        setupPanelVisibility(savedInstanceState)
        return view
    }

    protected open fun onInflateRootLayout(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_multi_panel, container, false)
    }

    protected open fun onSetupRootLayout(view: View) {
        mPrimaryToolbar = view.findViewById(R.id.primary_toolbar)
        mPrimaryPanel = Utils.findViewGroupByIds(
            view,
            R.id.primary_panel_constraint_layout,
            R.id.primary_panel_card_view,
            R.id.primary_panel_coordinator_layout
        )
        mSecondaryPanel = Utils.findViewGroupByIds(
            view,
            R.id.secondary_panel_frame_layout,
            R.id.secondary_panel_card_view
        )
        mPrimaryPanelBodyContainer = Utils.findViewGroupByIds(
            view,
            R.id.primary_panel_body_container_frame_layout,
            R.id.primary_panel_body_container_card_view
        )
        mExtendedLayout = view.findViewById<View>(R.id.half_screen_vertical_guideline) != null
        val fab: FloatingActionButton? = view.findViewById(R.id.floating_action_button)
        mFloatingActionButton = fab
        if (fab != null) {
            if (isFloatingActionButtonEnabled()) {
                fab.setOnClickListener {
                    onFloatingActionButtonClick()
                }
            } else {
                fab.visibility = View.GONE
            }
        }
    }

    protected open fun onConfigureRootLayout(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) {
        val primaryContainer = mPrimaryPanelBodyContainer ?: mPrimaryPanel
        if (primaryContainer != null) {
            onCreatePrimaryPanel(inflater, primaryContainer, savedInstanceState)
        }
        val secondary = mSecondaryPanel
        if (secondary != null) {
            inflater.inflate(R.layout.layout_secondary_panel_container, secondary, true)
            val secondaryContainer: ViewGroup? = secondary.findViewById(R.id.secondary_panel_container)
            if (secondaryContainer != null) {
                onCreateSecondaryPanel(inflater, secondaryContainer, savedInstanceState)
            }
        }
    }

    protected abstract fun onCreatePrimaryPanel(
        inflater: LayoutInflater,
        primaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    )

    protected abstract fun onCreateSecondaryPanel(
        inflater: LayoutInflater,
        secondaryPanel: ViewGroup,
        savedInstanceState: Bundle?
    )

    protected open fun setupPrimaryToolbar(toolbar: Toolbar?) {
        if (toolbar == null) {
            return
        }
        toolbar.setTitle(getTitleRes())
        showCurrentWalletInToolbar(false, null)
        val menuResId = onInflateMenu()
        if (menuResId > 0) {
            toolbar.inflateMenu(menuResId)
            toolbar.setOnMenuItemClickListener(this)
        }
        val currentActivity = activity
        if (currentActivity is ToolbarController) {
            currentActivity.setToolbar(toolbar)
        }
    }

    /**
     * Block the navigation drawer if a drawer controller is registered in the background activity.
     * @param locked state to apply to the drawer.
     */
    protected open fun setDrawerLocked(locked: Boolean) {
        val currentActivity = activity
        if (currentActivity is DrawerController) {
            currentActivity.setDrawerLockMode(
                if (locked) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED
            )
        }
    }

    private fun setupPanelVisibility(savedInstanceState: Bundle?) {
        val primary = mPrimaryPanel
        val secondary = mSecondaryPanel
        if (mExtendedLayout) {
            primary?.visibility = View.VISIBLE
            secondary?.visibility = View.VISIBLE
            mSecondaryPanelVisible = savedInstanceState != null &&
                savedInstanceState.getBoolean(SAVED_STATE_SECONDARY_PANEL_VISIBLE, false)
            setDrawerLocked(false)
        } else {
            if (savedInstanceState != null) {
                if (savedInstanceState.getBoolean(SAVED_STATE_SECONDARY_PANEL_VISIBLE, false)) {
                    showSecondaryPanel()
                } else {
                    hideSecondaryPanel()
                }
            } else {
                hideSecondaryPanel()
            }
        }
    }

    open fun showSecondaryPanel() {
        mSecondaryPanelVisible = true
        if (!mExtendedLayout) {
            mPrimaryPanel?.visibility = View.GONE
            mSecondaryPanel?.visibility = View.VISIBLE
            setDrawerLocked(true)
        }
    }

    open fun hideSecondaryPanel() {
        val currentActivity = activity
        if (currentActivity is ThemedActivity) {
            currentActivity.resetStatusBarIconsToAppBar()
        }
        if (!mExtendedLayout) {
            mSecondaryPanelVisible = false
            mPrimaryPanel?.visibility = View.VISIBLE
            mSecondaryPanel?.visibility = View.GONE
            setDrawerLocked(false)
        }
    }

    protected open fun showsCurrentWallet(): Boolean = false

    private val mCurrentWalletCallbacks = object : LoaderManager.LoaderCallbacks<Cursor> {
        override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
            val uri = ContentUris.withAppendedId(
                DataContentProvider.CONTENT_WALLETS,
                PreferenceManager.getCurrentWallet()
            )
            return CursorLoader(requireActivity(), uri, arrayOf(Contract.Wallet.NAME), null, null, null)
        }

        override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
            mPrimaryToolbar?.let {
                it.title = Utils.readWalletName(data)
                it.subtitle = null
            }
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
        }
    }

    private fun showCurrentWalletInToolbar(reload: Boolean, walletName: String?) {
        val toolbar = mPrimaryToolbar
        if (!showsCurrentWallet() || toolbar == null || !isAdded) {
            return
        }
        val walletId = PreferenceManager.getCurrentWallet()
        if (walletId == PreferenceManager.TOTAL_WALLET_ID) {
            toolbar.setTitle(R.string.total_wallet_name)
            toolbar.subtitle = null
            LoaderManager.getInstance(this).destroyLoader(CURRENT_WALLET_LOADER_ID)
        } else if (walletId == PreferenceManager.NO_CURRENT_WALLET) {
            toolbar.setTitle(getTitleRes())
            toolbar.subtitle = null
            LoaderManager.getInstance(this).destroyLoader(CURRENT_WALLET_LOADER_ID)
        } else if (reload) {
            toolbar.title = walletName ?: getString(getTitleRes())
            toolbar.subtitle = null
            LoaderManager.getInstance(this).restartLoader(CURRENT_WALLET_LOADER_ID, null, mCurrentWalletCallbacks)
        } else if (isLoaderBuiltForAnotherWallet(walletId)) {
            LoaderManager.getInstance(this).restartLoader(CURRENT_WALLET_LOADER_ID, null, mCurrentWalletCallbacks)
        } else {
            LoaderManager.getInstance(this).initLoader(CURRENT_WALLET_LOADER_ID, null, mCurrentWalletCallbacks)
        }
    }

    private fun isLoaderBuiltForAnotherWallet(walletId: Long): Boolean {
        val loader = LoaderManager.getInstance(this).getLoader<Cursor>(CURRENT_WALLET_LOADER_ID)
        if (loader is CursorLoader) {
            return ContentUris.parseId(loader.uri) != walletId
        }
        return false
    }

    open fun getPrimaryToolbar(): Toolbar? = mPrimaryToolbar

    open fun getPrimaryPanel(): ViewGroup? = mPrimaryPanel

    @StringRes
    protected abstract fun getTitleRes(): Int

    @MenuRes
    protected open fun onInflateMenu(): Int = 0

    override fun onMenuItemClick(item: MenuItem): Boolean = false

    override fun isExtendedLayout(): Boolean = mExtendedLayout

    protected open fun isFloatingActionButtonEnabled(): Boolean = true

    protected open fun onFloatingActionButtonClick() {
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SAVED_STATE_SECONDARY_PANEL_VISIBLE, mSecondaryPanelVisible)
    }

    override fun navigateBack(): Boolean {
        if (closeSecondaryPanel()) {
            return true
        }
        val selection = mSelectionMode
        if (selection != null) {
            selection.finish()
            return true
        }
        return false
    }

    override fun closeSecondaryPanel(): Boolean {
        val secondary = mSecondaryPanel ?: return false
        if (!mExtendedLayout) {
            val visible = secondary.visibility == View.VISIBLE
            hideSecondaryPanel()
            return visible
        }
        return false
    }

    protected open fun onSelectionModeChanged(active: Boolean) {
    }

    protected open fun finishSelectionMode() {
        mSelectionMode?.finish()
    }
}
