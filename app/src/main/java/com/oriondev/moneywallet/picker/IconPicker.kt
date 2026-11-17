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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.VectorIcon
import com.oriondev.moneywallet.ui.activity.IconListActivity
import com.oriondev.moneywallet.ui.fragment.dialog.ColorChooserDialog
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.Utils

/**
 * Created by andrea on 01/02/18.
 */
class IconPicker : Fragment(), ColorChooserDialog.Callback {

    private var mController: Controller? = null

    private var mCurrentIcon: Icon? = null
    private var mLastBackgroundColor: String = DEFAULT_BACKGROUND_COLOR

    private var mBindEditText: EditText? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(Controller::class.java, context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mCurrentIcon = savedInstanceState.readParcelable(SS_CURRENT_ICON)
            mLastBackgroundColor = savedInstanceState.getString(SS_LAST_BG_COLOR)
                ?: DEFAULT_BACKGROUND_COLOR
        } else {
            val arguments = arguments
            if (arguments != null) {
                val defaultIcon: Icon? = arguments.readParcelable(ARG_DEFAULT_ICON)
                if (defaultIcon != null) {
                    mCurrentIcon = defaultIcon
                    mLastBackgroundColor = if (defaultIcon is ColorIcon) {
                        Utils.getHexColor(defaultIcon.color)
                    } else {
                        DEFAULT_BACKGROUND_COLOR
                    }
                } else {
                    mCurrentIcon = ColorIcon(DEFAULT_BACKGROUND_COLOR, "?")
                    mLastBackgroundColor = DEFAULT_BACKGROUND_COLOR
                }
            } else {
                mCurrentIcon = ColorIcon(DEFAULT_BACKGROUND_COLOR, "?")
                mLastBackgroundColor = DEFAULT_BACKGROUND_COLOR
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fireCallbackSafely()
    }

    private fun fireCallbackSafely() {
        mController?.onIconChanged(tag, mCurrentIcon)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(SS_CURRENT_ICON, mCurrentIcon)
        outState.putString(SS_LAST_BG_COLOR, mLastBackgroundColor)
    }

    fun listenOn(editText: EditText) {
        mBindEditText = editText
        editText.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) = Unit

            override fun onTextChanged(sequence: CharSequence, i: Int, i1: Int, i2: Int) {
                val current = mCurrentIcon
                if (current is ColorIcon) {
                    val text = IconLoader.getColorIconString(sequence.toString())
                    mCurrentIcon = ColorIcon(current, text)
                    fireCallbackSafely()
                }
            }

            override fun afterTextChanged(editable: Editable) = Unit
        })
    }

    var currentIcon: Icon?
        get() = mCurrentIcon
        set(icon) {
            mCurrentIcon = icon
            fireCallbackSafely()
        }

    /**
     * Analyzes the current icon and offers actions matching its type:
     * - [ColorIcon]: choose an icon instead or change the background color.
     * - [VectorIcon]: change the icon, change its background color, or remove the icon.
     */
    fun showPicker() {
        val activity = activity ?: return
        when (mCurrentIcon) {
            is ColorIcon -> {
                ThemedDialog.buildBottomSheet(activity)
                    .addTitleItem(R.string.bottom_sheet_icon_picker_title)
                    .addItem(1, R.string.bottom_sheet_icon_picker_action_select_icon, R.drawable.ic_add_24dp)
                    .addItem(2, R.string.bottom_sheet_icon_picker_action_change_bg_color, R.drawable.ic_format_color_fill_black_24dp)
                    .setItemClickListener { item: MenuItem ->
                        when (item.itemId) {
                            1 -> startIconPickerActivity()
                            2 -> openColorPicker()
                        }
                        true
                    }
                    .createDialog()
                    .show()
            }
            is VectorIcon -> {
                ThemedDialog.buildBottomSheet(activity)
                    .addTitleItem(R.string.bottom_sheet_icon_picker_title)
                    .addItem(1, R.string.bottom_sheet_icon_picker_action_change_icon, R.drawable.ic_add_24dp)
                    .addItem(2, R.string.bottom_sheet_icon_picker_action_change_bg_color, R.drawable.ic_format_color_fill_black_24dp)
                    .addItem(3, R.string.bottom_sheet_icon_picker_action_remove_icon, R.drawable.ic_delete_black_24dp)
                    .setItemClickListener { item: MenuItem ->
                        when (item.itemId) {
                            1 -> startIconPickerActivity()
                            2 -> openColorPicker()
                            3 -> restoreColorIcon()
                        }
                        true
                    }
                    .createDialog()
                    .show()
            }
        }
    }

    private fun startIconPickerActivity() {
        val intent = Intent(activity, IconListActivity::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_ICON_PICKER)
    }

    private fun openColorPicker() {
        if (!isAdded) {
            return
        }
        // showNow adds the dialog before it returns, so a second tap in the same frame finds it.
        if (childFragmentManager.findFragmentByTag(TAG_COLOR_CHOOSER) == null) {
            ColorChooserDialog.newInstance(
                R.string.dialog_color_picker_title_icon_picker_background_color,
                true,
                Color.parseColor(mLastBackgroundColor)
            ).showNow(childFragmentManager, TAG_COLOR_CHOOSER)
        }
    }

    private fun restoreColorIcon() {
        if (mCurrentIcon !is ColorIcon) {
            var text = mBindEditText?.text?.toString() ?: "?"
            if (text.isEmpty()) {
                text = "?"
            }
            mCurrentIcon = ColorIcon(mLastBackgroundColor, text.substring(0, 1))
            fireCallbackSafely()
        }
    }

    override fun onDetach() {
        super.onDetach()
        mController = null
    }

    override fun onColorSelection(dialog: ColorChooserDialog?, selectedColor: Int) {
        mLastBackgroundColor = Utils.getHexColor(selectedColor)
        when (val current = mCurrentIcon) {
            is ColorIcon -> {
                val text = IconLoader.getColorIconString(mBindEditText?.text?.toString())
                mCurrentIcon = ColorIcon(mLastBackgroundColor, text)
                fireCallbackSafely()
            }
            is VectorIcon -> {
                mCurrentIcon = VectorIcon(current, mLastBackgroundColor)
                fireCallbackSafely()
            }
        }
    }

    override fun onColorChooserDismissed(dialog: ColorChooserDialog?) {
        // User dismissed without choosing a color.
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode == REQUEST_ICON_PICKER) {
            if (resultCode == Activity.RESULT_OK && intent != null) {
                mCurrentIcon = intent.getParcelableExtra(IconListActivity.RESULT_ICON)
                fireCallbackSafely()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent)
        }
    }

    interface Controller {
        fun onIconChanged(tag: String?, icon: Icon?)
    }

    companion object {

        private const val SS_CURRENT_ICON = "IconPicker::SavedState::CurrentIcon"
        private const val SS_LAST_BG_COLOR = "IconPicker::SavedState::LastBackgroundColor"
        private const val ARG_DEFAULT_ICON = "IconPicker::Arguments::DefaultIcon"

        private const val REQUEST_ICON_PICKER = 57

        private const val DEFAULT_BACKGROUND_COLOR = "#000000"

        private const val TAG_COLOR_CHOOSER = "IconPicker::Tag::ColorChooserDialog"

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?): IconPicker {
            return createPicker(fragmentManager, tag, ColorIcon(DEFAULT_BACKGROUND_COLOR, "?"))
        }

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            defaultIcon: Icon?
        ): IconPicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                arguments.putParcelable(ARG_DEFAULT_ICON, defaultIcon)
                val picker = IconPicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}

