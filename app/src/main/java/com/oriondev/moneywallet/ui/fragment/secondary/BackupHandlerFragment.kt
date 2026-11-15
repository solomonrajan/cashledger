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

package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.MenuRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.AbstractBackendServiceDelegate
import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.backup.BackupManager
import com.oriondev.moneywallet.ui.adapter.recycler.BackupFileAdapter
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.base.NavigableFragment
import com.oriondev.moneywallet.ui.fragment.dialog.AutoBackupSettingDialog
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import java.util.ArrayList

/**
 * Created by andrea on 21/11/18.
 */
class BackupHandlerFragment :
    Fragment(),
    BackupFileAdapter.Controller,
    SwipeRefreshLayout.OnRefreshListener,
    Toolbar.OnMenuItemClickListener,
    AbstractBackendServiceDelegate.BackendServiceStatusListener {

    private var mAllowBackup = false
    private var mAllowRestore = false
    private var mBackendService: AbstractBackendServiceDelegate? = null
    private val mFileStack: MutableList<IFile> = ArrayList()

    private var mAdvancedRecyclerView: AdvancedRecyclerView? = null
    private var mBackupAdapter: BackupFileAdapter? = null

    private var mCoverLayout: View? = null
    private var mPrimaryLayout: View? = null

    private var mToolbar: Toolbar? = null
    private var mCoverToolbar: Toolbar? = null

    private var mAutoBackupSettingDialog: AutoBackupSettingDialog? = null

    private var mLocalBroadcastManager: LocalBroadcastManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = arguments ?: throw IllegalStateException(
            "Arguments bundle is null, please instantiate the fragment using the newInstance() method instead."
        )
        mAllowBackup = arguments.getBoolean(ARG_ALLOW_BACKUP, true)
        mAllowRestore = arguments.getBoolean(ARG_ALLOW_RESTORE, true)
        val backendId = arguments.getString(ARG_BACKEND_ID, null)
        mBackendService = BackendServiceFactory.getServiceById(backendId, this)?.also {
            it.registerLaunchers(this)
        }
        mFileStack.clear()
        openOnDefaultFolder()

        // initialize the dialog fragment used to schedule auto-backups
        mAutoBackupSettingDialog = AutoBackupSettingDialog()
        // bind to local broadcast manager to get notified of background operations
        val act = activity
        if (act != null) {
            val intentFilter = IntentFilter().apply {
                addAction(LocalAction.ACTION_BACKUP_SERVICE_STARTED)
                addAction(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
                addAction(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
            }
            mLocalBroadcastManager = LocalBroadcastManager.getInstance(act).also {
                it.registerReceiver(mLocalBroadcastReceiver, intentFilter)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val service = mBackendService
        if (service != null) {
            if (service.isServiceEnabled(activity)) {
                hideCoverView()
                loadCurrentFolder()
            } else {
                showCoverView()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mLocalBroadcastManager?.unregisterReceiver(mLocalBroadcastReceiver)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_secondary_panel_backup_list, container, false) as ViewGroup
        mPrimaryLayout = view.findViewById(R.id.primary_layout)
        val toolbar: Toolbar = view.findViewById(R.id.secondary_toolbar)
        mToolbar = toolbar
        mCoverLayout = view.findViewById(R.id.cover_layout)
        val coverToolbar: Toolbar? = view.findViewById(R.id.cover_toolbar)
        mCoverToolbar = coverToolbar
        toolbar.setTitle(getTitle())

        val parent = parentFragment
        if (parent is MultiPanelFragment && !parent.isExtendedLayout) {
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp)
            toolbar.setNavigationOnClickListener {
                val p = parentFragment
                if (p is NavigableFragment) {
                    p.navigateBack()
                }
            }
            toolbar.inflateMenu(R.menu.menu_backup_service_remote)
            toolbar.setOnMenuItemClickListener(this)
            coverToolbar?.apply {
                setNavigationIcon(R.drawable.ic_arrow_back_black_24dp)
                setNavigationOnClickListener {
                    val p = parentFragment
                    if (p is NavigableFragment) {
                        p.navigateBack()
                    }
                }
            }
        }

        val recyclerView: AdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view)
        mAdvancedRecyclerView = recyclerView
        val floatingActionButton: FloatingActionButton = view.findViewById(R.id.floating_action_button)
        recyclerView.layoutManager = LinearLayoutManager(activity)
        val adapter = BackupFileAdapter(this)
        mBackupAdapter = adapter
        recyclerView.setEmptyText(R.string.message_no_file_found)
        recyclerView.adapter = adapter
        recyclerView.setOnRefreshListener(this)

        if (mAllowBackup) {
            floatingActionButton.setOnClickListener { v ->
                val inputView = LayoutInflater.from(v.context).inflate(R.layout.dialog_input, null)
                val inputEditText: EditText = inputView.findViewById(R.id.dialog_input_edit_text)
                inputEditText.setHint(R.string.hint_password)
                inputEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                val dialog = ThemedDialog.buildMaterialDialog(v.context)
                    .setTitle(R.string.title_backup_create)
                    .setMessage(R.string.message_backup_create)
                    .setView(inputView)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val act = activity
                        val service = mBackendService
                        if (act != null && service != null) {
                            val input = inputEditText.text
                            val folder = if (mFileStack.isEmpty()) ROOT_FOLDER else mFileStack[mFileStack.size - 1]
                            val intent = Intent(act, BackupHandlerIntentService::class.java).apply {
                                putExtra(BackupHandlerIntentService.BACKEND_ID, service.id)
                                putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_BACKUP)
                                putExtra(BackupHandlerIntentService.PARENT_FOLDER, folder)
                                if (!input.isNullOrEmpty()) {
                                    putExtra(BackupHandlerIntentService.PASSWORD, input.toString())
                                }
                                putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID)
                            }
                            act.startService(intent)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()
                ThemedDialog.showWithInput(dialog, inputEditText, true)
            }
        } else {
            floatingActionButton.visibility = View.GONE
        }

        val coverTextView: TextView = view.findViewById(R.id.cover_text_view)
        val coverActionButton: Button = view.findViewById(R.id.cover_action_button)
        val service = mBackendService
        if (service != null) {
            coverTextView.setText(service.backupCoverMessage)
            coverActionButton.setText(service.backupCoverAction)
            coverActionButton.setOnClickListener {
                try {
                    service.setup(activity)
                } catch (e: BackendException) {
                    e.printStackTrace()
                }
            }
        }
        return view
    }

    protected fun showCoverView() {
        mCoverLayout?.let {
            mPrimaryLayout?.visibility = View.GONE
            it.visibility = View.VISIBLE
        }
    }

    protected fun hideCoverView() {
        mCoverLayout?.let {
            // setup menu item visibility
            setMenuItemVisibility(R.id.action_disconnect, mBackendService?.isDisconnectable == true)
            // setup layout visibility
            it.visibility = View.GONE
            mPrimaryLayout?.visibility = View.VISIBLE
        }
    }

    protected fun getTitle(): Int {
        return mBackendService?.name ?: 0
    }

    @MenuRes
    protected fun onInflateMenu(): Int {
        return R.menu.menu_backup_service_remote
    }

    protected fun setMenuItemVisibility(id: Int, visible: Boolean) {
        val menu = mToolbar?.menu
        menu?.findItem(id)?.isVisible = visible
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_disconnect) {
            try {
                mBackendService?.teardown(activity)
            } catch (e: BackendException) {
                e.printStackTrace()
            }
        } else if (itemId == R.id.action_auto_backup) {
            val service = mBackendService
            if (service != null) {
                val tag = "${tag}::AutoBackupSettingDialog"
                mAutoBackupSettingDialog?.show(childFragmentManager, tag, service.id)
            }
        }
        return false
    }

    protected fun isStackEmpty(): Boolean {
        return mFileStack.isEmpty()
    }

    fun loadCurrentFolder() {
        loadFolder(if (mFileStack.isEmpty()) ROOT_FOLDER else mFileStack[mFileStack.size - 1])
    }

    protected fun loadFolder(folder: IFile?) {
        val act = activity
        val service = mBackendService
        if (act != null && service != null) {
            val intent = Intent(act, BackupHandlerIntentService::class.java).apply {
                putExtra(BackupHandlerIntentService.BACKEND_ID, service.id)
                putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_LIST)
                putExtra(BackupHandlerIntentService.PARENT_FOLDER, folder)
                putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID)
            }
            act.startService(intent)
        }
    }

    override fun navigateBack() {
        val stackSize = mFileStack.size
        if (stackSize > 0) {
            mFileStack.removeAt(stackSize - 1)
            mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.LOADING)
            loadCurrentFolder()
        }
    }

    override fun onFileClick(file: IFile) {
        if (file.isDirectory) {
            mFileStack.add(file)
            mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.LOADING)
            loadFolder(file)
        } else {
            val act = activity
            val service = mBackendService
            if (mAllowRestore && act != null && service != null) {
                if (file.name.endsWith(BackupManager.BACKUP_EXTENSION_STANDARD)) {
                    ThemedDialog.buildMaterialDialog(act)
                        .setTitle(R.string.title_backup_restore)
                        .setMessage(R.string.message_backup_restore_standard)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val intent = Intent(activity, BackupHandlerIntentService::class.java).apply {
                                putExtra(BackupHandlerIntentService.BACKEND_ID, service.id)
                                putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE)
                                putExtra(BackupHandlerIntentService.BACKUP_FILE, file)
                                putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID)
                            }
                            activity?.startService(intent)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else if (file.name.endsWith(BackupManager.BACKUP_EXTENSION_PROTECTED)) {
                    val inputView = LayoutInflater.from(act).inflate(R.layout.dialog_input, null)
                    val inputEditText: EditText = inputView.findViewById(R.id.dialog_input_edit_text)
                    inputEditText.setHint(R.string.hint_password)
                    inputEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    val dialog = ThemedDialog.buildMaterialDialog(act)
                        .setTitle(R.string.title_backup_restore)
                        .setMessage(R.string.message_backup_restore_protected)
                        .setView(inputView)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val intent = Intent(activity, BackupHandlerIntentService::class.java).apply {
                                putExtra(BackupHandlerIntentService.BACKEND_ID, service.id)
                                putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE)
                                putExtra(BackupHandlerIntentService.BACKUP_FILE, file)
                                putExtra(BackupHandlerIntentService.PASSWORD, inputEditText.text.toString())
                                putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID)
                            }
                            activity?.startService(intent)
                        }
                        .create()
                    ThemedDialog.showWithInput(dialog, inputEditText, false)
                } else if (file.name.endsWith(BackupManager.BACKUP_EXTENSION_LEGACY)) {
                    ThemedDialog.buildMaterialDialog(act)
                        .setTitle(R.string.title_backup_restore)
                        .setMessage(R.string.message_backup_restore_legacy)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val intent = Intent(activity, BackupHandlerIntentService::class.java).apply {
                                putExtra(BackupHandlerIntentService.BACKEND_ID, service.id)
                                putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE)
                                putExtra(BackupHandlerIntentService.BACKUP_FILE, file)
                                putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID)
                            }
                            activity?.startService(intent)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    override fun onRefresh() {
        loadCurrentFolder()
    }

    override fun onBackendStatusChange(enabled: Boolean) {
        if (enabled) {
            hideCoverView()
            if (isStackEmpty()) {
                openOnDefaultFolder()
                loadCurrentFolder()
            }
        } else {
            showCoverView()
        }
    }

    private fun openOnDefaultFolder() {
        val service = mBackendService
        if (mFileStack.isNotEmpty() || service == null) {
            return
        }
        val defaultFolder = BackendServiceFactory.getFile(service.id, null)
        if (defaultFolder != null) {
            mFileStack.add(defaultFolder)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val service = mBackendService
        if (service == null || !service.handlePermissionsResult(activity, requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val service = mBackendService
        if (service == null || !service.handleActivityResult(activity, requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private val mLocalBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val callerId = intent.getStringExtra(BackupHandlerIntentService.CALLER_ID)
            if (BACKUP_SERVICE_CALLER_ID != callerId) {
                return
            }
            val recyclerView = mAdvancedRecyclerView
            val adapter = mBackupAdapter
            if (TextUtils.equals(action, LocalAction.ACTION_BACKUP_SERVICE_STARTED)) {
                val operation = intent.getIntExtra(BackupHandlerIntentService.ACTION, 0)
                if (operation == BackupHandlerIntentService.ACTION_LIST) {
                    recyclerView?.setState(AdvancedRecyclerView.State.LOADING)
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKUP_SERVICE_FINISHED)) {
                val operation = intent.getIntExtra(BackupHandlerIntentService.ACTION, 0)
                if (operation == BackupHandlerIntentService.ACTION_LIST) {
                    val files = intent.getParcelableArrayListExtra<IFile>(BackupHandlerIntentService.FOLDER_CONTENT)
                    adapter?.setFileList(files, mFileStack.isNotEmpty())
                    if (adapter?.itemCount == 0) {
                        recyclerView?.setState(AdvancedRecyclerView.State.EMPTY)
                    } else {
                        recyclerView?.setState(AdvancedRecyclerView.State.READY)
                    }
                } else if (operation == BackupHandlerIntentService.ACTION_BACKUP) {
                    val backup = intent.getParcelableExtra<IFile>(BackupHandlerIntentService.BACKUP_FILE)
                    if (backup != null) {
                        adapter?.addFileToList(backup)
                    }
                    if ((adapter?.itemCount ?: 0) > 0) {
                        recyclerView?.setState(AdvancedRecyclerView.State.READY)
                    }
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKUP_SERVICE_FAILED)) {
                val operation = intent.getIntExtra(BackupHandlerIntentService.ACTION, 0)
                if (operation == BackupHandlerIntentService.ACTION_LIST) {
                    val exception = intent.getSerializableExtra(BackupHandlerIntentService.EXCEPTION) as? Exception
                    if (exception is BackendException && exception.isRecoverable) {
                        adapter?.setFileList(null, false)
                        recyclerView?.setErrorText(R.string.message_error_backend_recoverable)
                        recyclerView?.setState(AdvancedRecyclerView.State.ERROR)
                    } else {
                        try {
                            mBackendService?.teardown(activity)
                        } catch (e: BackendException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_ALLOW_BACKUP = "BackupHandlerFragment::Arguments::AllowBackup"
        private const val ARG_ALLOW_RESTORE = "BackupHandlerFragment::Arguments::AllowRestore"
        private const val ARG_BACKEND_ID = "BackupHandlerFragment::Arguments::BackendId"

        const val BACKUP_SERVICE_CALLER_ID: String = "BackupHandlerFragment"

        private val ROOT_FOLDER: IFile? = null

        @JvmStatic
        fun newInstance(backendId: String?, allowBackup: Boolean, allowRestore: Boolean): BackupHandlerFragment {
            val fragment = BackupHandlerFragment()
            val arguments = Bundle().apply {
                putBoolean(ARG_ALLOW_BACKUP, allowBackup)
                putBoolean(ARG_ALLOW_RESTORE, allowRestore)
                putString(ARG_BACKEND_ID, backendId)
            }
            fragment.arguments = arguments
            return fragment
        }
    }
}
