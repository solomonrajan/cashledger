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
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.service.BackendHandlerIntentService
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity
import com.oriondev.moneywallet.ui.adapter.recycler.BackupFileAdapter
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import java.util.ArrayList

/**
 * Created by andrea on 26/11/18.
 */
class BackendExplorerActivity : SinglePanelActivity(), SwipeRefreshLayout.OnRefreshListener, BackupFileAdapter.Controller {

    private var mAdvancedRecyclerView: AdvancedRecyclerView? = null
    private var mAdapter: BackupFileAdapter? = null

    private var mBackendId: String? = null
    private val mFileStack: MutableList<IFile> = ArrayList()

    private var mLocalBroadcastManager: LocalBroadcastManager? = null
    private var mReloadOnStart = false

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_activity_single_panel_body_list, parent, true)
        val recyclerView: AdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view)
        mAdvancedRecyclerView = recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setEmptyText(R.string.message_no_file_found)
        val adapter = BackupFileAdapter(this)
        mAdapter = adapter
        recyclerView.adapter = adapter
        recyclerView.setOnRefreshListener(this)
        // unpack intent info
        val intent = intent
        mBackendId = intent?.getStringExtra(BACKEND_ID)
        mFileStack.clear()
        // open where a backup would go, the same folder the backup screen opens on
        val defaultFolder = BackendServiceFactory.getFile(mBackendId, null)
        if (defaultFolder != null) {
            mFileStack.add(defaultFolder)
        }
        // attach the activity to the service
        val intentFilter = IntentFilter().apply {
            addAction(LocalAction.ACTION_BACKEND_SERVICE_STARTED)
            addAction(LocalAction.ACTION_BACKEND_SERVICE_FINISHED)
            addAction(LocalAction.ACTION_BACKEND_SERVICE_FAILED)
        }
        val lbm = LocalBroadcastManager.getInstance(this)
        mLocalBroadcastManager = lbm
        lbm.registerReceiver(mLocalBroadcastReceiver, intentFilter)
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        loadCurrentFolder()
    }

    override fun onSetupFloatingActionButton(floatingActionButton: FloatingActionButton?) {
        super.onSetupFloatingActionButton(floatingActionButton)
        floatingActionButton?.setImageResource(R.drawable.ic_create_new_folder_black_24dp)
    }

    override fun onStart() {
        super.onStart()
        if (mReloadOnStart) {
            mReloadOnStart = false
            loadCurrentFolder()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mLocalBroadcastManager?.unregisterReceiver(mLocalBroadcastReceiver)
    }

    @StringRes
    override fun getActivityTitleRes(): Int = R.string.title_activity_backend_folder_picker

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_backend_explorer

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_select_folder) {
            var folder = getCurrentFolder()
            if (folder == null) {
                // this backend's own default, rather than a local path it cannot decode
                folder = BackendServiceFactory.getFile(mBackendId, null)
            }
            if (folder == null) {
                // The top of the list is not a folder this backend can name. Choosing it used
                // to send back a result carrying no file, and the settings dialog read that as
                // the answer and dropped the folder it already held.
                Toast.makeText(this, R.string.message_backend_open_a_folder, Toast.LENGTH_LONG).show()
                return false
            }
            val intent = Intent().apply {
                putExtra(RESULT_FILE, folder)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
        return false
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
        if (file.isDirectory()) {
            mFileStack.add(file)
            mAdvancedRecyclerView?.setState(AdvancedRecyclerView.State.LOADING)
            loadFolder(file)
        }
    }

    override fun onRefresh() {
        loadCurrentFolder()
    }

    private fun getCurrentFolder(): IFile? {
        return if (mFileStack.isEmpty()) ROOT_FOLDER else mFileStack[mFileStack.size - 1]
    }

    private fun loadCurrentFolder() {
        loadFolder(getCurrentFolder())
    }

    private fun loadFolder(folder: IFile?) {
        val intent = Intent(this, BackendHandlerIntentService::class.java).apply {
            putExtra(BackendHandlerIntentService.BACKEND_ID, mBackendId)
            putExtra(BackendHandlerIntentService.ACTION, BackendHandlerIntentService.ACTION_LIST)
            putExtra(BackendHandlerIntentService.PARENT_FOLDER, folder)
        }
        startService(intent)
    }

    override fun onFloatingActionButtonClick() {
        val inputView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null)
        val inputEditText: EditText = inputView.findViewById(R.id.dialog_input_edit_text)
        inputEditText.setHint(R.string.hint_new_folder)
        inputEditText.inputType = InputType.TYPE_CLASS_TEXT
        val dialog = ThemedDialog.buildMaterialDialog(this)
            .setTitle(R.string.title_backend_create_folder)
            .setMessage(R.string.message_backend_create_folder)
            .setView(inputView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent = Intent(this@BackendExplorerActivity, BackendHandlerIntentService::class.java).apply {
                    putExtra(BackendHandlerIntentService.BACKEND_ID, mBackendId)
                    putExtra(BackendHandlerIntentService.ACTION, BackendHandlerIntentService.ACTION_CREATE_FOLDER)
                    putExtra(BackendHandlerIntentService.PARENT_FOLDER, getCurrentFolder())
                    putExtra(BackendHandlerIntentService.FOLDER_NAME, inputEditText.text.toString())
                }
                startService(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        ThemedDialog.showWithInput(dialog, inputEditText, false)
    }

    private val mLocalBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val recyclerView = mAdvancedRecyclerView
            val adapter = mAdapter
            if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_STARTED)) {
                val operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0)
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    recyclerView?.setState(AdvancedRecyclerView.State.LOADING)
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_FINISHED)) {
                val operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0)
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    val files: List<IFile>? = intent.getParcelableArrayListExtra(BackendHandlerIntentService.FOLDER_CONTENT)
                    adapter?.setFileList(files, mFileStack.isNotEmpty())
                    if (adapter?.itemCount == 0) {
                        recyclerView?.setState(AdvancedRecyclerView.State.EMPTY)
                    } else {
                        recyclerView?.setState(AdvancedRecyclerView.State.READY)
                    }
                } else if (operation == BackendHandlerIntentService.ACTION_CREATE_FOLDER) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        loadCurrentFolder()
                    } else {
                        // starting a service from the background can throw, so reload once the screen is back
                        mReloadOnStart = true
                    }
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_FAILED)) {
                val operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0)
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    adapter?.setFileList(null, false)
                    recyclerView?.setErrorText(R.string.message_error_backend_recoverable)
                    recyclerView?.setState(AdvancedRecyclerView.State.ERROR)
                } else if (operation == BackendHandlerIntentService.ACTION_CREATE_FOLDER) {
                    Toast.makeText(context, R.string.message_error_backend_recoverable, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val BACKEND_ID = "BackendExplorerActivity::Arguments::BackendId"
        const val RESULT_FILE = "BackendExplorerActivity::Result::File"

        private val ROOT_FOLDER: IFile? = null
    }
}
