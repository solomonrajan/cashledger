/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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

@file:Suppress("ReturnCount")

package com.oriondev.moneywallet.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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

class WalletWidgetConfigureActivity : NewEditItemActivity(), WalletPicker.SingleWalletController {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var walletEditText: MaterialEditText
    private lateinit var showWhenLockedCheckBox: ThemedCheckBox

    private var walletPicker: WalletPicker? = null

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        // The wallet and the one checkbox both sit in the body, so there is nothing to put here.
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_widget_configure, parent, true)
        walletEditText = view.findViewById(R.id.wallet_edit_text)
        showWhenLockedCheckBox = view.findViewById(R.id.show_when_locked_checkbox)
        walletEditText.setTextViewMode(true)
        walletEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_wallet)

            override fun isValid(charSequence: CharSequence): Boolean {
                return walletPicker != null && walletPicker!!.isSelected
            }

            override fun autoValidate(): Boolean = false
        })
        walletEditText.setOnClickListener {
            walletPicker?.showSingleWalletPicker()
        }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val launched = intent
        if (launched != null) {
            launched.putExtra(MODE, Mode.NEW_ITEM)
        }
        super.onViewCreated(savedInstanceState)
        
        val currentIntent = intent
        if (currentIntent?.extras != null) {
            appWidgetId = currentIntent.extras!!.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }
        
        setResult(Activity.RESULT_CANCELED, resultIntent())
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        
        showWhenLockedCheckBox.isChecked = WalletWidgetPreferences.isShowWhenLocked(this, appWidgetId)
        
        walletPicker = WalletPicker.createPicker(supportFragmentManager, TAG_WALLET_PICKER, configuredWallet())
    }

    private fun configuredWallet(): Wallet? {
        val walletId = WalletWidgetPreferences.getWallet(this, appWidgetId)
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
            if (!it.moveToFirst()) return null
            Wallet(
                it.getLong(it.getColumnIndexOrThrow(Contract.Wallet.ID)),
                it.getString(it.getColumnIndexOrThrow(Contract.Wallet.NAME)),
                IconLoader.parse(it.getString(it.getColumnIndexOrThrow(Contract.Wallet.ICON))),
                CurrencyManager.getCurrency(it.getString(it.getColumnIndexOrThrow(Contract.Wallet.CURRENCY))),
                it.getLong(it.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)),
                it.getLong(it.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY))
            )
        }
    }

    override fun getActivityTileRes(mode: Mode?): Int = R.string.title_activity_widget_configure

    override fun onSaveChanges(mode: Mode?) {
        if (!walletEditText.validate()) {
            return
        }
        walletPicker?.currentWallet?.id?.let {
            WalletWidgetPreferences.save(
                this, appWidgetId, it,
                showWhenLockedCheckBox.isChecked
            )
        }
        AppWidgetManager.getInstance(this).updateAppWidget(
            appWidgetId,
            WalletWidgetProvider.buildViews(this, appWidgetId)
        )
        setResult(Activity.RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent(): Intent {
        val intent = Intent()
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        return intent
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        walletEditText.setText(wallet?.name)
    }

    companion object {
        private const val TAG_WALLET_PICKER = "WalletWidgetConfigureActivity::Tag::WalletPicker"
    }
}
