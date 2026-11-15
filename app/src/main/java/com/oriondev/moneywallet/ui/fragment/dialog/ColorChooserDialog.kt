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

package com.oriondev.moneywallet.ui.fragment.dialog

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.ColorSwatchView
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import java.util.Locale

/**
 * The app's color chooser, replacing the one material-dialogs provided. The palette is the same
 * one, in ColorPalette; the two levels and the drill in are the same behavior. What it drops is
 * the alpha channel, the RGB sliders and the Custom/Presets toggle, and in their place the hex
 * field is on both levels and is the only way to reach a color the palette does not carry.
 */
class ColorChooserDialog : DialogFragment() {

    private var mCallback: Callback? = null

    private var mHues: IntArray = intArrayOf()
    private var mShades: Array<IntArray> = emptyArray()

    private var mInShadeLevel = false
    private var mPreviewing = false
    private var mWritingHex = false
    private var mHueIndex = -1
    private var mShadeIndex = -1
    private var mSelectedColor = Color.BLACK

    private var mGridView: GridView? = null
    private var mHexEditText: EditText? = null
    private var mPreviewView: ColorSwatchView? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val parent = parentFragment
        if (parent !is Callback) {
            throw IllegalStateException("ColorChooserDialog must be shown from a parent fragment implementing Callback")
        }
        mCallback = parent
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = arguments
        val accentPalette = arguments?.getBoolean(ARG_ACCENT_PALETTE) == true
        mHues = if (accentPalette) ColorPalette.ACCENT_COLORS else ColorPalette.PRIMARY_COLORS
        mShades = if (accentPalette) ColorPalette.ACCENT_COLORS_SUB else ColorPalette.PRIMARY_COLORS_SUB
        if (savedInstanceState != null) {
            mInShadeLevel = savedInstanceState.getBoolean(SS_IN_SHADE_LEVEL)
            mHueIndex = savedInstanceState.getInt(SS_HUE_INDEX)
            mShadeIndex = savedInstanceState.getInt(SS_SHADE_INDEX)
            mSelectedColor = savedInstanceState.getInt(SS_SELECTED_COLOR)
            mPreviewing = savedInstanceState.getBoolean(SS_PREVIEWING)
        } else {
            val preselect = arguments?.getInt(ARG_PRESELECT_COLOR) ?: Color.BLACK
            mSelectedColor = 0xFF000000.toInt() or preselect
            findPreselect(mSelectedColor)
        }
        val dialog = ThemedDialog.buildMaterialDialog(requireActivity())
            .setTitle(arguments?.getInt(ARG_TITLE) ?: R.string.dialog_color_picker_title)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                mCallback?.onColorSelection(this, mSelectedColor)
                dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        val view = layoutInflater.cloneInContext(dialog.context)
            .inflate(R.layout.dialog_color_chooser, null)
        mGridView = view.findViewById(R.id.color_chooser_grid_view)
        mHexEditText = view.findViewById(R.id.color_chooser_hex_edit_text)
        mPreviewView = view.findViewById(R.id.color_chooser_preview_view)
        mGridView?.adapter = SwatchAdapter()
        mHexEditText?.filters = arrayOf(InputFilter.LengthFilter(HEX_DIGITS))
        mHexEditText?.let { ThemedDialog.tintInput(it) }
        mHexEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                onHexTyped(text?.toString() ?: "")
            }

            override fun afterTextChanged(text: Editable?) {}
        })
        writeHex(mSelectedColor)
        mPreviewView?.setColor(mSelectedColor)
        dialog.setView(view)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? AlertDialog ?: return
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            if (mInShadeLevel) {
                mInShadeLevel = false
                findPreselect(mSelectedColor)
                invalidate()
            } else {
                dialog.cancel()
            }
        }
        invalidateNegativeButton()
        if (mPreviewing) {
            previewSelection()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_IN_SHADE_LEVEL, mInShadeLevel)
        outState.putInt(SS_HUE_INDEX, mHueIndex)
        outState.putInt(SS_SHADE_INDEX, mShadeIndex)
        outState.putInt(SS_SELECTED_COLOR, mSelectedColor)
        outState.putBoolean(SS_PREVIEWING, mPreviewing)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mCallback?.onColorChooserDismissed(this)
    }

    private fun findPreselect(@ColorInt color: Int) {
        for (hue in mHues.indices) {
            if (mHues[hue] == color) {
                mHueIndex = hue
                mShadeIndex = findShade(hue, color)
                return
            }
            val shade = findShade(hue, color)
            if (shade >= 0) {
                mHueIndex = hue
                mShadeIndex = shade
                return
            }
        }
        mHueIndex = -1
        mShadeIndex = -1
    }

    private fun findShade(hue: Int, @ColorInt color: Int): Int {
        if (hue in mShades.indices) {
            val shadeArray = mShades[hue]
            for (shade in shadeArray.indices) {
                if (shadeArray[shade] == color) {
                    return shade
                }
            }
        }
        return -1
    }

    private fun onHexTyped(text: String) {
        if (mWritingHex || text.length != HEX_DIGITS) {
            return
        }
        try {
            mSelectedColor = Color.parseColor("#$text")
        } catch (e: IllegalArgumentException) {
            return
        }
        previewSelection()
        if (mInShadeLevel) {
            mShadeIndex = if (mHueIndex >= 0) findShade(mHueIndex, mSelectedColor) else -1
        } else {
            findPreselect(mSelectedColor)
        }
        (mGridView?.adapter as? BaseAdapter)?.notifyDataSetChanged()
    }

    private fun writeHex(@ColorInt color: Int) {
        mWritingHex = true
        mHexEditText?.setText(String.format(Locale.US, "%06X", 0xFFFFFF and color))
        mWritingHex = false
    }

    private fun onSwatchClicked(position: Int) {
        if (mInShadeLevel) {
            mShadeIndex = position
            if (mHueIndex in mShades.indices && position in mShades[mHueIndex].indices) {
                mSelectedColor = mShades[mHueIndex][position]
            }
        } else {
            if (mHueIndex != position) {
                if (position in mHues.indices) {
                    mSelectedColor = mHues[position]
                }
                mShadeIndex = findShade(position, mSelectedColor)
            }
            mHueIndex = position
            mInShadeLevel = true
        }
        writeHex(mSelectedColor)
        previewSelection()
        invalidate()
    }

    private fun previewSelection() {
        mPreviewView?.setColor(mSelectedColor)
        mPreviewing = mCallback?.onColorPreview(this, mSelectedColor) == true
        val dialogWindow = dialog?.window ?: return
        if (mPreviewing) {
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        } else {
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun invalidate() {
        (mGridView?.adapter as? BaseAdapter)?.notifyDataSetChanged()
        invalidateNegativeButton()
    }

    private fun invalidateNegativeButton() {
        val dialog = dialog as? AlertDialog
        if (dialog != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(
                if (mInShadeLevel) R.string.action_back else android.R.string.cancel
            )
        }
    }

    interface Callback {
        fun onColorSelection(dialog: ColorChooserDialog?, @ColorInt color: Int)

        fun onColorChooserDismissed(dialog: ColorChooserDialog?)

        fun onColorPreview(dialog: ColorChooserDialog?, @ColorInt color: Int): Boolean = false
    }

    private inner class SwatchAdapter : BaseAdapter() {

        override fun getCount(): Int {
            return if (mInShadeLevel && mHueIndex in mShades.indices) {
                mShades[mHueIndex].size
            } else {
                mHues.size
            }
        }

        override fun getItem(position: Int): Any {
            return colorAt(position)
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        @ColorInt
        private fun colorAt(position: Int): Int {
            return if (mInShadeLevel && mHueIndex in mShades.indices && position in mShades[mHueIndex].indices) {
                mShades[mHueIndex][position]
            } else if (position in mHues.indices) {
                mHues[position]
            } else {
                0
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val swatch: ColorSwatchView = if (convertView is ColorSwatchView) {
                convertView
            } else {
                val size = resources.getDimensionPixelSize(R.dimen.color_chooser_swatch_size)
                val newView = ColorSwatchView(parent.context)
                newView.layoutParams = GridView.LayoutParams(size, size)
                newView
            }
            swatch.setColor(colorAt(position))
            swatch.setSwatchSelected(position == (if (mInShadeLevel) mShadeIndex else mHueIndex))
            swatch.setOnClickListener {
                onSwatchClicked(position)
            }
            return swatch
        }
    }

    companion object {
        private const val ARG_TITLE = "ColorChooserDialog::Arguments::Title"
        private const val ARG_ACCENT_PALETTE = "ColorChooserDialog::Arguments::AccentPalette"
        private const val ARG_PRESELECT_COLOR = "ColorChooserDialog::Arguments::PreselectColor"

        private const val SS_IN_SHADE_LEVEL = "ColorChooserDialog::SavedState::InShadeLevel"
        private const val SS_HUE_INDEX = "ColorChooserDialog::SavedState::HueIndex"
        private const val SS_SHADE_INDEX = "ColorChooserDialog::SavedState::ShadeIndex"
        private const val SS_SELECTED_COLOR = "ColorChooserDialog::SavedState::SelectedColor"
        private const val SS_PREVIEWING = "ColorChooserDialog::SavedState::Previewing"

        private const val HEX_DIGITS = 6

        @JvmStatic
        fun newInstance(
            @StringRes title: Int,
            accentPalette: Boolean,
            @ColorInt preselectColor: Int
        ): ColorChooserDialog {
            val dialog = ColorChooserDialog()
            val arguments = Bundle()
            arguments.putInt(ARG_TITLE, title)
            arguments.putBoolean(ARG_ACCENT_PALETTE, accentPalette)
            arguments.putInt(ARG_PRESELECT_COLOR, preselectColor)
            dialog.arguments = arguments
            return dialog
        }
    }
}
