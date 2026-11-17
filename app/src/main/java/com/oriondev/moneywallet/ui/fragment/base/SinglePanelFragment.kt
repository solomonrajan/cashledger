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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
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
import com.oriondev.moneywallet.ui.activity.ToolbarController
import com.oriondev.moneywallet.utils.SystemBars
import com.oriondev.moneywallet.utils.Utils

/**
 * Created by andrea on 17/08/18.
 */
abstract class SinglePanelFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    companion object {
        private const val CURRENT_WALLET_LOADER_ID = 60002
    }

    private var mToolbar: Toolbar? = null
    private var mCurrentWalletObserver: BroadcastReceiver? = null

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
        val view = inflater.inflate(R.layout.activity_single_panel, container, false)
        SystemBars.keepGuidelineBelowToolbar(
            view.findViewById(R.id.toolbar_delimiter_horizontal_guideline),
            view.findViewById(R.id.primary_toolbar)
        )
        val toolbar: Toolbar? = view.findViewById(R.id.primary_toolbar)
        mToolbar = toolbar
        val parent = Utils.findViewGroupByIds(
            view,
            R.id.primary_panel_container_frame_layout,
            R.id.primary_panel_container_card_view,
            R.id.primary_panel_container_linear_layout,
            R.id.primary_panel_container_coordinator_layout
        ) ?: (view as ViewGroup)
        onCreatePanelView(inflater, parent, savedInstanceState)
        val floatingActionButton: FloatingActionButton? = view.findViewById(R.id.floating_action_button)
        onSetupFloatingActionButton(floatingActionButton)
        if (toolbar != null) {
            setupPrimaryToolbar(toolbar)
        }
        return view
    }

    protected abstract fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?)

    @StringRes
    protected abstract fun getTitleRes(): Int

    protected open fun onSetupFloatingActionButton(floatingActionButton: FloatingActionButton?) {
        if (floatingActionButton != null) {
            if (isFloatingActionButtonEnabled()) {
                floatingActionButton.setOnClickListener {
                    onFloatingActionButtonClick()
                }
            } else {
                floatingActionButton.visibility = View.GONE
            }
        }
    }

    protected open fun setupPrimaryToolbar(toolbar: Toolbar) {
        toolbar.setTitle(getTitleRes())
        showCurrentWalletInToolbar(false, null)
        val menuResId = onInflateMenu()
        if (menuResId > 0) {
            toolbar.inflateMenu(menuResId)
            toolbar.setOnMenuItemClickListener(this)
            onMenuCreated(toolbar.menu)
        }
        val currentActivity = activity
        if (currentActivity is ToolbarController) {
            currentActivity.setToolbar(toolbar)
        }
    }

    protected open fun onMenuCreated(menu: Menu) {
    }

    protected open fun setToolbarSubtitle(subtitle: String?) {
        mToolbar?.subtitle = subtitle
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
            mToolbar?.let {
                it.title = Utils.readWalletName(data)
                it.subtitle = null
            }
        }

        override fun onLoaderReset(loader: Loader<Cursor>) {
        }
    }

    private fun showCurrentWalletInToolbar(reload: Boolean, walletName: String?) {
        val toolbar = mToolbar
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
            val uri = loader.uri
            return ContentUris.parseId(uri) != walletId
        }
        return false
    }

    @MenuRes
    protected open fun onInflateMenu(): Int = 0

    override fun onMenuItemClick(item: MenuItem): Boolean = false

    protected open fun isFloatingActionButtonEnabled(): Boolean = true

    protected open fun onFloatingActionButtonClick() {
    }
}
