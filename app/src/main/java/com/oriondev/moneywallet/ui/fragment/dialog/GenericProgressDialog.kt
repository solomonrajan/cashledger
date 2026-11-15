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
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import java.text.NumberFormat

/**
 * Created by andre on 22/03/2018.
 */
class GenericProgressDialog : DialogFragment() {

    private var mProgressIndicator: BaseProgressIndicator<*>? = null
    private var mMessageTextView: TextView? = null
    private var mPercentageTextView: TextView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        val arguments = arguments
        val titleRes = arguments?.getInt(ARG_TITLE_RES) ?: 0
        val contentRes = arguments?.getInt(ARG_CONTENT_RES) ?: 0
        val indeterminate = arguments?.getBoolean(ARG_INDETERMINATE) == true
        val layoutRes = if (indeterminate) {
            R.layout.dialog_progress_indeterminate
        } else {
            R.layout.dialog_progress_determinate
        }
        val builder = ThemedDialog.buildMaterialDialog(currentActivity)
        if (titleRes != 0) {
            builder.setTitle(titleRes)
        }
        val dialog = builder.setCancelable(false).create()
        val view = layoutInflater.cloneInContext(dialog.context).inflate(layoutRes, null)
        val indicator: BaseProgressIndicator<*> = view.findViewById(R.id.dialog_progress_indicator)
        mProgressIndicator = indicator
        indicator.max = 100
        indicator.setIndicatorColor(ThemedDialog.getAccentColor())
        if (!indeterminate) {
            indicator.trackColor = ThemedDialog.getIdleColor()
        }
        mMessageTextView = view.findViewById(R.id.dialog_progress_message)
        mPercentageTextView = view.findViewById(R.id.dialog_progress_percentage)
        setMessage(contentRes)
        setProgress(0)
        dialog.setView(view)
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mProgressIndicator = null
        mMessageTextView = null
        mPercentageTextView = null
    }

    fun updateProgress(contentRes: Int, progress: Int) {
        if (mProgressIndicator == null) {
            return
        }
        setMessage(contentRes)
        setProgress(progress)
    }

    private fun setMessage(contentRes: Int) {
        mMessageTextView?.text = if (contentRes != 0) getText(contentRes) else null
    }

    private fun setProgress(progress: Int) {
        val indicator = mProgressIndicator ?: return
        indicator.progress = progress
        val percentageText = mPercentageTextView
        if (percentageText != null) {
            val format = NumberFormat.getPercentInstance()
            percentageText.text = format.format(progress / indicator.max.toFloat())
        }
    }

    companion object {
        private const val ARG_TITLE_RES = "GenericProgressDialog::Arguments::TitleRes"
        private const val ARG_CONTENT_RES = "GenericProgressDialog::Arguments::ContentRes"
        private const val ARG_INDETERMINATE = "GenericProgressDialog::Arguments::Indeterminate"

        @JvmStatic
        fun newInstance(title: Int, content: Int, indeterminate: Boolean): GenericProgressDialog {
            val dialog = GenericProgressDialog()
            val arguments = Bundle()
            arguments.putInt(ARG_TITLE_RES, title)
            arguments.putInt(ARG_CONTENT_RES, content)
            arguments.putBoolean(ARG_INDETERMINATE, indeterminate)
            dialog.arguments = arguments
            dialog.isCancelable = false
            return dialog
        }
    }
}
