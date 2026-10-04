@file:Suppress("LongMethod", "FunctionName", "FunctionNaming", "WildcardImport", "MaxLineLength", "MagicNumber", "UnusedParameter", "LongParameterList", "ReturnCount")
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

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.appcompat.widget.Toolbar
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.ui.activity.base.BaseActivity
import com.oriondev.moneywallet.ui.fragment.base.NavigableFragment
import com.oriondev.moneywallet.ui.fragment.dialog.GenericProgressDialog
import com.oriondev.moneywallet.ui.fragment.multipanel.BackupMultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.secondary.BackupHandlerFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import java.lang.Exception

class BackupListActivity : BaseActivity(), ToolbarController {

    private var mFragment: Fragment? = null
    private var mProgressDialog: GenericProgressDialog? = null

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            
            val callerId = intent.getStringExtra(BackupHandlerIntentService.CALLER_ID)
            if (BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID != callerId) {
                // the service has sent a message using the local broadcast manager but it
                // is not directed to this fragment. we can simply ignore it. this is useful
                // to avoid that the dialog appear when the auto backup is fired by the
                // system and the user is browsing the backup section of the application.
                return
            }
            
            when (action) {
                LocalAction.ACTION_BACKUP_SERVICE_STARTED -> {
                    if (mProgressDialog == null) {
                        when (intent.getIntExtra(BackupHandlerIntentService.ACTION, 0)) {
                            BackupHandlerIntentService.ACTION_BACKUP -> {
                                mProgressDialog = GenericProgressDialog.newInstance(R.string.title_backup_creation, R.string.message_async_init, false)
                            }
                            BackupHandlerIntentService.ACTION_RESTORE -> {
                                mProgressDialog = GenericProgressDialog.newInstance(R.string.title_backup_restoring, R.string.message_async_init, false)
                            }
                            else -> return
                        }
                    }
                    mProgressDialog?.show(supportFragmentManager, TAG_PROGRESS_DIALOG)
                }
                LocalAction.ACTION_BACKUP_SERVICE_RUNNING -> {
                    mProgressDialog?.let {
                        val status = intent.getIntExtra(BackupHandlerIntentService.PROGRESS_STATUS, 0)
                        val value = intent.getIntExtra(BackupHandlerIntentService.PROGRESS_VALUE, 0)
                        var contentRes = 0
                        when (status) {
                            BackupHandlerIntentService.STATUS_BACKUP_CREATION -> contentRes = R.string.message_backup_status_creation
                            BackupHandlerIntentService.STATUS_BACKUP_UPLOADING -> contentRes = R.string.message_backup_status_uploading
                            BackupHandlerIntentService.STATUS_BACKUP_DOWNLOADING -> contentRes = R.string.message_backup_status_downloading
                            BackupHandlerIntentService.STATUS_BACKUP_RESTORING -> contentRes = R.string.message_backup_status_restoring
                        }
                        it.updateProgress(contentRes, value)
                    }
                }
                LocalAction.ACTION_BACKUP_SERVICE_FINISHED -> {
                    mProgressDialog?.let {
                        it.dismissAllowingStateLoss()
                        mProgressDialog = null
                    }
                    val titleRes = R.string.title_success
                    val messageRes = when (intent.getIntExtra(BackupHandlerIntentService.ACTION, 0)) {
                        BackupHandlerIntentService.ACTION_BACKUP -> R.string.message_backup_creation_success
                        BackupHandlerIntentService.ACTION_RESTORE -> R.string.message_backup_restoring_success
                        else -> return
                    }
                    ThemedDialog.buildMaterialDialog(this@BackupListActivity)
                        .setTitle(titleRes)
                        .setMessage(messageRes)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val currentIntent = this@BackupListActivity.intent
                            if (currentIntent != null) {
                                val mode = currentIntent.getIntExtra(BACKUP_MODE, FULL)
                                if (mode == RESTORE_ONLY) {
                                    // we are probably waiting for the backup to be restored
                                    // in a parent activity, so we have to return an ok result
                                    this@BackupListActivity.setResult(Activity.RESULT_OK)
                                    finish()
                                }
                            }
                        }
                        .show()
                }
                LocalAction.ACTION_BACKUP_SERVICE_FAILED -> {
                    mProgressDialog?.let {
                        it.dismissAllowingStateLoss()
                        mProgressDialog = null
                    }
                    val exception = intent.getSerializableExtra(BackupHandlerIntentService.EXCEPTION) as? Exception
                    val message = exception?.message ?: ""
                    val titleRes = R.string.title_failed
                    val messageString = when (intent.getIntExtra(BackupHandlerIntentService.ACTION, 0)) {
                        BackupHandlerIntentService.ACTION_BACKUP -> getString(R.string.message_backup_creation_failed, message)
                        BackupHandlerIntentService.ACTION_RESTORE -> getString(R.string.message_backup_restoring_failed, message)
                        else -> return
                    }
                    ThemedDialog.buildMaterialDialog(this@BackupListActivity)
                        .setTitle(titleRes)
                        .setMessage(messageString)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_root_container)
        val fragmentManager = supportFragmentManager
        mFragment = fragmentManager.findFragmentByTag(TAG_FRAGMENT_BACKUP)
        mProgressDialog = fragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG) as? GenericProgressDialog
        
        if (mFragment != null) {
            fragmentManager.beginTransaction().show(mFragment!!).commit()
        } else {
            val intent = intent
            val mode = intent.getIntExtra(BACKUP_MODE, FULL)
            mFragment = BackupMultiPanelFragment.newInstance(mode == FULL, true)
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, mFragment!!, TAG_FRAGMENT_BACKUP)
                .commit()
        }
        
        val intentFilter = IntentFilter().apply {
            addAction(LocalAction.ACTION_BACKUP_SERVICE_STARTED)
            addAction(LocalAction.ACTION_BACKUP_SERVICE_RUNNING)
            addAction(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
            addAction(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver)
    }

    override fun onBackPressed() {
        if (mFragment is NavigableFragment) {
            if (!(mFragment as NavigableFragment).navigateBack()) {
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun setToolbar(toolbar: Toolbar) {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    companion object {
        const val BACKUP_MODE = "BackupListActivity::BackupMode"

        const val FULL = 0
        const val RESTORE_ONLY = 1

        private const val TAG_FRAGMENT_BACKUP = "BackupListActivity::tag::BackupMultiPanelFragment"
        private const val TAG_PROGRESS_DIALOG = "BackupListActivity::tag::GenericProgressDialog"
    }
}
