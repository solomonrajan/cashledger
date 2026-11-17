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

package com.oriondev.moneywallet.api.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.fragment.app.Fragment
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.AbstractBackendServiceDelegate
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

/**
 * Backend service used to access files over the android storage access framework.
 */
class SAFBackendService(
    listener: BackendServiceStatusListener?
) : AbstractBackendServiceDelegate(listener) {

    private var mFolderLauncher: ActivityResultLauncher<Uri?>? = null

    /**
     * Extension of the [OpenDocumentTree] contract including
     * extra [FLAG_URI_READ_WRITE] and [Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION]
     * flags.
     */
    private class DocumentTreeContract : OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            val intent = super.createIntent(context, input)
            intent.flags = FLAG_URI_READ_WRITE or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            return intent
        }
    }

    override fun getId(): String = BackendServiceFactory.SERVICE_ID_SAF

    override fun getName(): Int = R.string.service_backup_storage_access_framework

    override fun getBackupCoverMessage(): Int = R.string.cover_message_backup_storage_access_framework_title

    override fun getBackupCoverAction(): Int = R.string.cover_message_backup_storage_access_framework_button

    override fun isServiceEnabled(context: Context): Boolean = getUri(context) != null

    override fun registerLaunchers(fragment: Fragment) {
        mFolderLauncher = fragment.registerForActivityResult(
            DocumentTreeContract()
        ) { uri ->
            var enabled = false
            if (uri != null) {
                val activity = fragment.requireActivity()
                activity.contentResolver.takePersistableUriPermission(uri, FLAG_URI_READ_WRITE)
                storeUri(activity, uri)
                enabled = true
            }
            setBackendServiceEnabled(enabled)
        }
    }

    override fun setup(activity: ComponentActivity) {
        mFolderLauncher?.launch(null)
    }

    override fun teardown(activity: ComponentActivity) {
        val uri = getUri(activity) ?: return
        ThemedDialog.buildMaterialDialog(activity)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_backup_service_storage_access_framework_disconnect)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                activity.contentResolver.releasePersistableUriPermission(uri, FLAG_URI_READ_WRITE)
                clearUri(activity)
                setBackendServiceEnabled(false)
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    override fun handleActivityResult(
        context: Context,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        // Do nothing. This is handled by the ActivityResultCallback.
        return false
    }

    companion object {
        /**
         * Flag containing both [Intent.FLAG_GRANT_READ_URI_PERMISSION] and
         * [Intent.FLAG_GRANT_WRITE_URI_PERMISSION].
         */
        private const val FLAG_URI_READ_WRITE =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val PREFERENCE_FILE = "storage_access_framework"

        /**
         * Preference key were the root uri is stored.
         */
        private const val URI = "uri"

        @JvmStatic
        internal fun getUri(context: Context): Uri? {
            val preferences = context.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)
            val accessToken = preferences.getString(URI, null) ?: return null
            return Uri.parse(accessToken)
        }

        @JvmStatic
        internal fun clearUri(context: Context) {
            val preferences = context.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)
            preferences.edit().clear().apply()
        }

        @JvmStatic
        internal fun storeUri(context: Context, uri: Uri) {
            val preferences = context.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)
            preferences.edit().putString(URI, uri.toString()).apply()
        }
    }
}
