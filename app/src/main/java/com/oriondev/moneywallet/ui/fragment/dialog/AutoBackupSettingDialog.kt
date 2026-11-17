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

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.broadcast.AutoBackupBroadcastReceiver
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.storage.preference.BackendManager
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.BackendExplorerActivity
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.Utils

/**
 * Created by andrea on 26/11/18.
 */
class AutoBackupSettingDialog : DialogFragment() {

    private var mBackendId: String? = null

    private var mServiceEnabledSwitchCompat: SwitchCompat? = null
    private var mOnlyWiFiCheckBox: CheckBox? = null
    private var mOnlyDataChangedCheckBox: CheckBox? = null
    private var mExportCsvCheckBox: CheckBox? = null
    private var mOffsetTextView: TextView? = null
    private var mOffsetSeekBar: SeekBar? = null
    private var mFolderTextView: TextView? = null
    private var mFailureTextView: TextView? = null
    private var mPasswordEditText: EditText? = null
    private var mCsvPasswordTextView: TextView? = null

    private var mFolder: IFile? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentActivity = activity ?: return super.onCreateDialog(savedInstanceState)
        if (savedInstanceState != null) {
            mBackendId = savedInstanceState.getString(SS_BACKEND_ID)
            mFolder = savedInstanceState.getParcelable(SS_FOLDER)
        }
        val view = ThemedDialog.inflateScrollableView(currentActivity, R.layout.dialog_auto_backup_setting)
        val dialog = ThemedDialog.buildMaterialDialog(currentActivity)
            .setTitle(R.string.dialog_auto_backup_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        mServiceEnabledSwitchCompat = view.findViewById(R.id.auto_backup_enable_switch)
        mOnlyWiFiCheckBox = view.findViewById(R.id.auto_backup_wifi_check_box)
        mOnlyDataChangedCheckBox = view.findViewById(R.id.auto_backup_data_change_check_box)
        mExportCsvCheckBox = view.findViewById(R.id.auto_backup_export_csv_check_box)
        mOffsetTextView = view.findViewById(R.id.auto_backup_offset_text_view)
        mOffsetSeekBar = view.findViewById(R.id.auto_backup_offset_seek_bar)
        mFolderTextView = view.findViewById(R.id.auto_backup_folder_text_view)
        mFailureTextView = view.findViewById(R.id.auto_backup_failure_text_view)
        mPasswordEditText = view.findViewById(R.id.auto_backup_password_edit_text)
        mCsvPasswordTextView = view.findViewById(R.id.auto_backup_csv_password_text_view)

        // set listeners
        mOffsetSeekBar?.max = (OFFSET_MAX_HOURS - OFFSET_MIN_HOURS) / OFFSET_BETWEEN_HOURS
        mOffsetSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                this@AutoBackupSettingDialog.onProgressChanged(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // not used
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // not used
            }
        })
        mServiceEnabledSwitchCompat?.setOnCheckedChangeListener { _, _ ->
            onServiceEnabledChanged()
        }
        mExportCsvCheckBox?.setOnCheckedChangeListener { _, _ ->
            onCsvPasswordChanged()
        }
        mPasswordEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // not used
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // not used
            }

            override fun afterTextChanged(s: Editable?) {
                this@AutoBackupSettingDialog.onCsvPasswordChanged()
            }
        })
        mFolderTextView?.setOnClickListener {
            val act = activity
            if (act != null) {
                val intent = Intent(act, BackendExplorerActivity::class.java)
                intent.putExtra(BackendExplorerActivity.BACKEND_ID, mBackendId)
                startActivityForResult(intent, REQUEST_CODE_FOLDER_PICKER)
            }
        }
        val backend = mBackendId
        if (savedInstanceState == null && backend != null) {
            mServiceEnabledSwitchCompat?.isChecked = BackendManager.isAutoBackupEnabled(backend)
            mOnlyWiFiCheckBox?.isChecked = BackendManager.isAutoBackupOnWiFiOnly(backend)
            mOnlyDataChangedCheckBox?.isChecked = BackendManager.isAutoBackupWhenDataIsChangedOnly(backend)
            mExportCsvCheckBox?.isChecked = BackendManager.isAutoBackupExportCsv(backend)
            mOffsetSeekBar?.progress = (BackendManager.getAutoBackupHoursOffset(backend) - OFFSET_MIN_HOURS) / OFFSET_BETWEEN_HOURS
            mFolder = BackendServiceFactory.getFile(backend, BackendManager.getAutoBackupFolder(backend))
            mPasswordEditText?.setText(BackendManager.getAutoBackupPassword(backend))
        }
        onProgressChanged(mOffsetSeekBar?.progress ?: 0)
        onFolderChanged()
        onServiceEnabledChanged()
        onCsvPasswordChanged()
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? AlertDialog ?: return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (onSaveSetting()) {
                dialog.dismiss()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SS_BACKEND_ID, mBackendId)
        outState.putParcelable(SS_FOLDER, mFolder)
    }

    fun show(fragmentManager: FragmentManager, tag: String?, backendId: String?) {
        mBackendId = backendId
        show(fragmentManager, tag)
    }

    private fun onProgressChanged(progress: Int) {
        val hours = OFFSET_MIN_HOURS + (progress * OFFSET_BETWEEN_HOURS)
        mOffsetTextView?.text = getString(R.string.hint_auto_backup_every_n_hours, hours)
    }

    private fun onServiceEnabledChanged() {
        val backend = mBackendId ?: return
        val show = (mServiceEnabledSwitchCompat?.isChecked == false) &&
            BackendManager.isAutoBackupDisabledByFailure(backend)
        mFailureTextView?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun onCsvPasswordChanged() {
        val password = mPasswordEditText?.text
        val show = (mExportCsvCheckBox?.isChecked == true) && (!password.isNullOrEmpty())
        mCsvPasswordTextView?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun onFolderChanged() {
        val folder = mFolder
        val backend = mBackendId
        when {
            folder != null -> mFolderTextView?.text = folder.getName()
            backend != null && BackendManager.getAutoBackupFolder(backend) != null -> {
                mFolderTextView?.setText(R.string.hint_auto_backup_folder_unavailable)
            }
            else -> mFolderTextView?.setText(R.string.hint_auto_backup_folder_not_chosen)
        }
    }

    private fun onSaveSetting(): Boolean {
        val backend = mBackendId ?: return false
        val isServiceEnabled = mServiceEnabledSwitchCompat?.isChecked == true
        if (isServiceEnabled && mFolder == null) {
            Toast.makeText(context, R.string.message_auto_backup_folder_required, Toast.LENGTH_LONG).show()
            return false
        }
        BackendManager.setAutoBackupEnabled(backend, isServiceEnabled)
        BackendManager.setAutoBackupOnWiFiOnly(backend, mOnlyWiFiCheckBox?.isChecked == true)
        BackendManager.setAutoBackupWhenDataIsChangedOnly(backend, mOnlyDataChangedCheckBox?.isChecked == true)
        BackendManager.setAutoBackupExportCsv(backend, mExportCsvCheckBox?.isChecked == true)
        val progress = mOffsetSeekBar?.progress ?: 0
        BackendManager.setAutoBackupHoursOffset(backend, OFFSET_MIN_HOURS + (progress * OFFSET_BETWEEN_HOURS))
        BackendManager.setAutoBackupFolder(backend, mFolder?.encodeToString())
        BackendManager.setAutoBackupPassword(backend, mPasswordEditText?.text?.toString() ?: "")
        AutoBackupBroadcastReceiver.scheduleAutoBackupTask(activity)
        val act = activity
        if (isServiceEnabled && act != null && Utils.shouldAskNotificationPermission(act)) {
            PreferenceManager.setAskedNotificationPermission()
            ActivityCompat.requestPermissions(
                act,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION_PERMISSION
            )
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_FOLDER_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                mFolder = data?.getParcelableExtra(BackendExplorerActivity.RESULT_FILE)
                onFolderChanged()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        private const val SS_BACKEND_ID = "AutoBackupSettingDialog::SavedState::BackendId"
        private const val SS_FOLDER = "AutoBackupSettingDialog::SavedState::Folder"

        private const val REQUEST_CODE_FOLDER_PICKER = 35625

        private const val OFFSET_MIN_HOURS = 24
        private const val OFFSET_MAX_HOURS = 168
        private const val OFFSET_BETWEEN_HOURS = 4

        private const val REQUEST_CODE_NOTIFICATION_PERMISSION = 35626
    }
}
