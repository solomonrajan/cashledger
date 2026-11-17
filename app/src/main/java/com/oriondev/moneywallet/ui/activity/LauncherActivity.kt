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

package com.oriondev.moneywallet.ui.activity

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.service.UpgradeLegacyEditionIntentService
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.base.ThemedActivity
import com.oriondev.moneywallet.ui.view.theme.ITheme
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.SystemBars

/**
 * Created by andrea on 30/07/18.
 */
class LauncherActivity : ThemedActivity() {

    private var mUpgradeLegacyEditionError: String? = null
    private var mProgressWheel: View? = null

    /**
     * Neither screen this activity shows has a toolbar. Both paint the window foreground across the
     * whole window, so that is what is behind both bars and the icons have to be picked for it.
     */
    override fun getColorBehindStatusBar(theme: ITheme): Int {
        return theme.colorWindowForeground
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isMainScreenAtTheBaseOfThisTask()) {
            // the running main screen is left as it was instead of stacking a second one
            finish()
            return
        }
        if (UpgradeLegacyEditionIntentService.isLegacyEditionDetected(this)) {
            setContentView(R.layout.activity_launcher_legacy_edition_upgrade)
            SystemBars.pad(findViewById(R.id.legacy_upgrade_layout), true, true, true)
            mProgressWheel = findViewById(R.id.progress_wheel)
            // prepare the broadcast receiver
            val intentFilter = IntentFilter().apply {
                addAction(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_STARTED)
                addAction(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FINISHED)
                addAction(LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FAILED)
            }
            LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter)
            // start the service
            if (savedInstanceState == null) {
                mProgressWheel?.visibility = View.INVISIBLE
                startService(Intent(this, UpgradeLegacyEditionIntentService::class.java))
            } else {
                mUpgradeLegacyEditionError = savedInstanceState.getString(SS_UPGRADE_ERROR)
                showUpgradeLegacyEditionErrorMessage()
            }
        } else {
            if (isFirstStart()) {
                setContentView(R.layout.activity_launcher_first_start)
                SystemBars.pad(findViewById(R.id.first_start_layout), true, true, true)
                val firstStartButton: Button = findViewById(R.id.first_start_button)
                val restoreBackupButton: Button = findViewById(R.id.restore_backup_button)
                firstStartButton.setOnClickListener {
                    val intent = Intent(this@LauncherActivity, TutorialActivity::class.java)
                    startActivityForResult(intent, REQUEST_FIRST_START)
                }
                restoreBackupButton.setOnClickListener {
                    val intent = Intent(this@LauncherActivity, BackupListActivity::class.java).apply {
                        putExtra(BackupListActivity.BACKUP_MODE, BackupListActivity.RESTORE_ONLY)
                    }
                    startActivityForResult(intent, REQUEST_FIRST_START)
                }
            } else {
                startMainActivity()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SS_UPGRADE_ERROR, mUpgradeLegacyEditionError)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver)
    }

    /**
     * Whether the welcome screen is the right thing to show.
     *
     * The preference cannot answer on its own. shared_prefs is deliberately outside the backup
     * rules because four of the files there hold a secret in the clear, so an Android restore
     * brings the ledger back with the preference missing and the welcome screen has no safe
     * exit: the tutorial inserts a second copy of every default category, and a backup import
     * cleans the attachment folder and renames the restored database away.
     *
     * A wallet is the signal, because a fresh install has none: the database seeds the system
     * categories when it is created and never a wallet. The preference is read first, so the
     * wallets are counted only on a launch that still believes the setup is unfinished.
     *
     * A provider that cannot be reached answers no and records nothing, since
     * [MainActivity] is recoverable and the two exits from the welcome screen are not.
     */
    private fun isFirstStart(): Boolean {
        if (PreferenceManager.isFirstStartDone()) {
            return false
        }
        val wallets = walletCount()
        if (wallets == 0) {
            return true
        }
        if (wallets > 0) {
            PreferenceManager.setIsFirstStartDone(true)
        }
        return false
    }

    /**
     * The number of wallets, or -1 when the provider could not be reached at all.
     */
    private fun walletCount(): Int {
        contentResolver.query(
            DataContentProvider.CONTENT_WALLETS,
            arrayOf(Contract.Wallet.ID),
            null,
            null,
            null
        )?.use { cursor ->
            return cursor.count
        }
        return -1
    }

    @Suppress("DEPRECATION")
    private fun isMainScreenAtTheBaseOfThisTask(): Boolean {
        if (isTaskRoot) {
            return false
        }
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val appTasks = manager.appTasks ?: return false
        for (task in appTasks) {
            val info = try {
                task.taskInfo
            } catch (e: IllegalArgumentException) {
                continue
            }
            if (info != null && info.persistentId == taskId) {
                val base = info.baseActivity
                if (base != null && MainActivity::class.java.name == base.className) {
                    return true
                }
            }
        }
        return false
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        publishShortcuts()
        finish()
    }

    /**
     * Publish the launcher shortcuts. Every route into the app passes through
     * [startMainActivity], which is why they are published from there, and after the
     * hand off rather than before it so that publishing them is never in front of startup.
     *
     * The intents are built from the activity classes, so the package is this build's own in every
     * variant. The static definition these replace named the release application id as a literal,
     * which is not the package of any build carrying an applicationIdSuffix.
     *
     * The rank is the ordering decision made when these were last touched: the launcher sorts by
     * rank ascending, and recording a transaction is what people open this app to do, while a
     * transfer between their own wallets is the rarer of the two.
     *
     * The ids are the ones the static definition used, so a pinned copy of either is addressed by
     * the same id rather than becoming a second entry beside it.
     */
    private fun publishShortcuts() {
        val transaction = ShortcutInfoCompat.Builder(this, "fast_transaction")
            .setShortLabel(getString(R.string.title_activity_new_transaction))
            .setLongLabel(getString(R.string.title_activity_new_transaction))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_add_24dp))
            .setIntent(Intent(Intent.ACTION_VIEW, null, this, NewEditTransactionActivity::class.java))
            .setRank(0)
            .build()
        val transfer = ShortcutInfoCompat.Builder(this, "fast_transfer")
            .setShortLabel(getString(R.string.title_activity_new_transfer))
            .setLongLabel(getString(R.string.title_activity_new_transfer))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_transfer_24dp))
            .setIntent(Intent(Intent.ACTION_VIEW, null, this, NewEditTransferActivity::class.java))
            .setRank(1)
            .build()
        ShortcutManagerCompat.addDynamicShortcuts(this, listOf(transaction, transfer))
    }

    private fun showUpgradeLegacyEditionErrorMessage() {
        mProgressWheel?.visibility = View.INVISIBLE
        // both buttons do the same thing, as the onAny callback this replaces did
        val listener = DialogInterface.OnClickListener { _, _ ->
            startMainActivity()
        }
        ThemedDialog.buildMaterialDialog(this@LauncherActivity)
            .setTitle(R.string.title_failed)
            .setMessage(getString(R.string.message_error_legacy_upgrade_failed, mUpgradeLegacyEditionError))
            .setPositiveButton(android.R.string.ok, listener)
            .setNegativeButton(android.R.string.cancel, listener)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_FIRST_START) {
            if (resultCode == RESULT_OK) {
                PreferenceManager.setIsFirstStartDone(true)
                startMainActivity()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && intent.action != null) {
                when (intent.action) {
                    LocalAction.ACTION_LEGACY_EDITION_UPGRADE_STARTED -> {
                        mProgressWheel?.visibility = View.VISIBLE
                    }
                    LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FINISHED -> {
                        startMainActivity()
                    }
                    LocalAction.ACTION_LEGACY_EDITION_UPGRADE_FAILED -> {
                        mUpgradeLegacyEditionError = intent.getStringExtra(UpgradeLegacyEditionIntentService.ERROR_MESSAGE)
                        showUpgradeLegacyEditionErrorMessage()
                    }
                }
            }
        }
    }

    companion object {
        private const val SS_UPGRADE_ERROR = "LauncherActivity::SavedState::UpgradeLegacyEditionError"
        private const val REQUEST_FIRST_START = 273
    }
}
