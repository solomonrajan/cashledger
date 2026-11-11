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

package com.oriondev.moneywallet.picker

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.ui.fragment.dialog.WalletPickerDialog

/**
 * Created by andrea on 09/03/18.
 */
class WalletPicker : Fragment(), WalletPickerDialog.Callback {

    private var mSingleWalletController: SingleWalletController? = null
    private var mMultiWalletController: MultiWalletController? = null

    private var mSingleWallet = false
    private var mCurrentWallet: Wallet? = null
    private var mCurrentWallets: Array<Wallet>? = null

    private var mWalletPickerDialog: WalletPickerDialog? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mSingleWalletController = findController(SingleWalletController::class.java, context, checkParent = true)
        mMultiWalletController = findController(MultiWalletController::class.java, context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mSingleWallet = savedInstanceState.getBoolean(SS_SINGLE_WALLET)
            if (mSingleWallet) {
                mCurrentWallet = savedInstanceState.readParcelable(SS_CURRENT_WALLET)
            } else {
                // This is required because some devices have issues unmarshaling Parcelable[] directly.
                @Suppress("DEPRECATION")
                val parcelables = savedInstanceState.getParcelableArray(SS_CURRENT_WALLET)
                mCurrentWallets = parcelables.toTypedParcelableArray() ?: emptyArray()
            }
        } else {
            val arguments = arguments
            if (arguments != null) {
                mSingleWallet = arguments.getBoolean(ARG_SINGLE_WALLET)
                if (mSingleWallet) {
                    mCurrentWallet = arguments.readParcelable(ARG_DEFAULT_WALLET)
                } else {
                    @Suppress("DEPRECATION")
                    val parcelables = arguments.getParcelableArray(ARG_DEFAULT_WALLET)
                    mCurrentWallets = parcelables.toTypedParcelableArray()
                }
            } else {
                mSingleWallet = true
                mCurrentWallets = null
            }
        }
        mWalletPickerDialog = findOrCreateDialog(WalletPickerDialog::class.java) { WalletPickerDialog.newInstance() }
        mWalletPickerDialog?.setCallback(this)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        if (mSingleWallet) {
            mSingleWalletController?.onWalletChanged(tag, mCurrentWallet)
        } else {
            mMultiWalletController?.onWalletListChanged(tag, mCurrentWallets)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_SINGLE_WALLET, mSingleWallet)
        if (mSingleWallet) {
            outState.putParcelable(SS_CURRENT_WALLET, mCurrentWallet)
        } else {
            outState.putParcelableArray(SS_CURRENT_WALLET, mCurrentWallets)
        }
    }

    val isSelected: Boolean
        get() = if (mSingleWallet) mCurrentWallet != null else !mCurrentWallets.isNullOrEmpty()

    val currentWallet: Wallet?
        get() = mCurrentWallet

    val currentWallets: Array<Wallet>?
        get() = mCurrentWallets

    fun showSingleWalletPicker() {
        mWalletPickerDialog?.showSinglePicker(childFragmentManager, getDefaultDialogTag(), mCurrentWallet)
    }

    fun showMultiWalletPicker() {
        mWalletPickerDialog?.showMultiPicker(childFragmentManager, getDefaultDialogTag(), mCurrentWallets)
    }

    override fun onDetach() {
        super.onDetach()
        mSingleWalletController = null
        mMultiWalletController = null
    }

    override fun onWalletSelected(wallet: Wallet?) {
        mCurrentWallet = wallet
        fireCallbackSafely()
    }

    override fun onWalletsSelected(wallets: Array<Wallet>?) {
        mCurrentWallets = wallets
        fireCallbackSafely()
    }

    interface SingleWalletController {
        fun onWalletChanged(tag: String?, wallet: Wallet?)
    }

    interface MultiWalletController {
        fun onWalletListChanged(tag: String?, wallets: Array<Wallet>?)
    }

    companion object {

        private const val SS_SINGLE_WALLET = "WalletPicker::SavedState::SingleWallet"
        private const val SS_CURRENT_WALLET = "WalletPicker::SavedState::CurrentWallet"

        private const val ARG_SINGLE_WALLET = "WalletPicker::Arguments::SingleWallet"
        private const val ARG_DEFAULT_WALLET = "WalletPicker::Arguments::DefaultWallet"

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultWallet: Wallet?
        ): WalletPicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putBoolean(ARG_SINGLE_WALLET, true)
                arguments.putParcelable(ARG_DEFAULT_WALLET, defaultWallet)
                val picker = WalletPicker()
                picker.arguments = arguments
                picker
            }
        }

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultWallets: Array<Wallet>?
        ): WalletPicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putBoolean(ARG_SINGLE_WALLET, false)
                arguments.putParcelableArray(ARG_DEFAULT_WALLET, defaultWallets)
                val picker = WalletPicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}

internal inline fun <reified T : Fragment> FragmentManager.findOrCreatePicker(
    tag: String?,
    create: () -> T
): T {
    val existing = findFragmentByTag(tag)
    return if (existing is T) {
        existing
    } else {
        val picker = create()
        beginTransaction().add(picker, tag).commit()
        picker
    }
}

internal fun <T> Fragment.findController(
    controllerClass: Class<T>,
    context: Context,
    checkParent: Boolean = false,
    parentFirst: Boolean = false
): T? {
    val parent = parentFragment
    if (parentFirst && controllerClass.isInstance(parent)) {
        return controllerClass.cast(parent)
    }
    if (controllerClass.isInstance(context)) {
        return controllerClass.cast(context)
    }
    if (checkParent && !parentFirst && controllerClass.isInstance(parent)) {
        return controllerClass.cast(parent)
    }
    return null
}

internal fun Fragment.getDefaultDialogTag(): String = "${tag}::DialogFragment"

internal fun <D : DialogFragment> Fragment.findOrCreateDialog(
    dialogClass: Class<D>,
    dialogTag: String = getDefaultDialogTag(),
    create: () -> D
): D {
    val existing = childFragmentManager.findFragmentByTag(dialogTag)
    return if (dialogClass.isInstance(existing)) {
        dialogClass.cast(existing) ?: create()
    } else {
        create()
    }
}

internal inline fun <reified T : Parcelable> Array<Parcelable>?.toTypedParcelableArray(): Array<T>? {
    return this?.filterIsInstance<T>()?.toTypedArray()
}

@Suppress("DEPRECATION")
internal inline fun <reified T> Bundle?.readSerializable(key: String): T? {
    return this?.getSerializable(key) as? T
}

@Suppress("DEPRECATION")
internal inline fun <reified T : Parcelable> Bundle?.readParcelable(key: String): T? {
    return this?.getParcelable(key)
}
