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

package com.oriondev.moneywallet.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.ui.view.theme.ThemedCheckBox
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Asks which wallet a widget being placed should show.
 *
 * The launcher starts this before the widget exists and takes RESULT_CANCELED as a refusal, so
 * the placement is undone if the user backs out. That is why the cancelled result is set first
 * and only replaced once a wallet has actually been chosen.
 */
class WalletWidgetConfigureActivity :
    NewEditItemActivity(),
    WalletPicker.SingleWalletController {

    private var mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var mWalletEditText: MaterialEditText? = null
    private var mShowWhenLockedCheckBox: ThemedCheckBox? = null

    private var mWalletPicker: WalletPicker? = null

    override fun onCreateHeaderView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        // The wallet and the one checkbox both sit in the body, so there is nothing to put here.
    }

    override fun onCreatePanelView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val view = inflater.inflate(R.layout.layout_panel_widget_configure, parent, true)
        val walletEditText: MaterialEditText = view.findViewById(R.id.wallet_edit_text)
        mWalletEditText = walletEditText
        mShowWhenLockedCheckBox = view.findViewById(R.id.show_when_locked_checkbox)
        walletEditText.setTextViewMode(true)
        walletEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String =
                getString(R.string.error_input_missing_wallet)

            override fun isValid(charSequence: CharSequence): Boolean {
                // Null until onViewCreated has a widget id to work with, and this activity is
                // exported, so it can be started without one and torn down with the toolbar's
                // save item already live.
                return mWalletPicker?.isSelected == true
            }

            override fun autoValidate(): Boolean = false
        })
        walletEditText.setOnClickListener {
            mWalletPicker?.showSingleWalletPicker()
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        // Never the inherited edit mode. This activity is exported, because a launcher below
        // Android 12 starts it itself, so any app on the device can send it an intent. The base
        // class throws on an edit mode carrying no id, and there is nothing here to edit anyway,
        // so the mode is fixed before it is read.
        intent?.putExtra(MODE, Mode.NEW_ITEM)
        super.onViewCreated(savedInstanceState)
        val extras = intent?.extras
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        // Set before anything can finish this activity. Backing out has to leave the launcher
        // holding nothing, and the launcher reads that from the result and not from us.
        setResult(Activity.RESULT_CANCELED, resultIntent())
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // Seeded with whatever this placement already holds, which is nothing the first time and
        // the chosen wallet when the user comes back through the launcher to change it. An empty
        // field there would read as though the setting had been lost.
        mShowWhenLockedCheckBox?.isChecked =
            WalletWidgetPreferences.isShowWhenLocked(this, mAppWidgetId)
        mWalletPicker = WalletPicker.createPicker(
            supportFragmentManager,
            TAG_WALLET_PICKER,
            configuredWallet()
        )
    }

    /**
     * The wallet this placement is set to, or null when it has none or the one it named is gone.
     * A wallet that has been deleted leaves the field empty and the save refused, which is the
     * same place a first time setup starts from.
     */
    private fun configuredWallet(): Wallet? {
        val walletId = WalletWidgetPreferences.getWallet(this, mAppWidgetId)
        if (walletId == WalletWidgetPreferences.NO_WALLET) {
            return null
        }
        val projection = arrayOf(
            Contract.Wallet.ID,
            Contract.Wallet.NAME,
            Contract.Wallet.ICON,
            Contract.Wallet.CURRENCY,
            Contract.Wallet.START_MONEY,
            Contract.Wallet.TOTAL_MONEY
        )
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId)
        val cursor = contentResolver.query(uri, projection, null, null, null) ?: return null
        return cursor.use {
            if (!it.moveToFirst()) {
                return@use null
            }
            Wallet(
                it.getLong(it.getColumnIndexOrThrow(Contract.Wallet.ID)),
                it.getString(it.getColumnIndexOrThrow(Contract.Wallet.NAME)),
                IconLoader.parse(it.getString(it.getColumnIndexOrThrow(Contract.Wallet.ICON))),
                CurrencyManager.getCurrency(
                    it.getString(it.getColumnIndexOrThrow(Contract.Wallet.CURRENCY))
                ),
                it.getLong(it.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)),
                it.getLong(it.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY))
            )
        }
    }

    override fun getActivityTileRes(mode: Mode?): Int =
        R.string.title_activity_widget_configure

    override fun onSaveChanges(mode: Mode?) {
        val walletEditText = mWalletEditText ?: return
        if (!walletEditText.validate()) {
            return
        }
        val wallet = mWalletPicker?.currentWallet ?: return
        WalletWidgetPreferences.save(
            this,
            mAppWidgetId,
            wallet.id,
            mShowWhenLockedCheckBox?.isChecked == true
        )
        // The launcher does not draw a widget it has just been given, so the first picture of it
        // has to be pushed from here. Without this the widget sits blank until something else
        // writes a transaction.
        AppWidgetManager.getInstance(this).updateAppWidget(
            mAppWidgetId,
            WalletWidgetProvider.buildViews(this, mAppWidgetId)
        )
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent(): Intent {
        return Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        mWalletEditText?.setText(wallet?.name)
    }

    companion object {

        private const val TAG_WALLET_PICKER =
            "WalletWidgetConfigureActivity::Tag::WalletPicker"
    }
}
