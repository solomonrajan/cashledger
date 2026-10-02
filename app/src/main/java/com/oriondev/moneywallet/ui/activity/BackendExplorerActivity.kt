/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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

class BackendExplorerActivity : SinglePanelActivity(), SwipeRefreshLayout.OnRefreshListener, BackupFileAdapter.Controller {

    private lateinit var advancedRecyclerView: AdvancedRecyclerView
    private lateinit var adapter: BackupFileAdapter

    private var backendId: String? = null
    private val fileStack = mutableListOf<IFile>()

    private lateinit var localBroadcastManager: LocalBroadcastManager
    private var reloadOnStart = false

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_STARTED)) {
                val operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0)
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    advancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING)
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_FINISHED)) {
                val operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0)
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    val files: List<IFile>? = intent.getParcelableArrayListExtra(BackendHandlerIntentService.FOLDER_CONTENT)
                    adapter.setFileList(files, fileStack.isNotEmpty())
                    if (adapter.itemCount == 0) {
                        advancedRecyclerView.setState(AdvancedRecyclerView.State.EMPTY)
                    } else {
                        advancedRecyclerView.setState(AdvancedRecyclerView.State.READY)
                    }
                } else if (operation == BackendHandlerIntentService.ACTION_CREATE_FOLDER) {
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        loadCurrentFolder()
                    } else {
                        reloadOnStart = true
                    }
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_FAILED)) {
                val operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0)
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    adapter.setFileList(null, false)
                    advancedRecyclerView.setErrorText(R.string.message_error_backend_recoverable)
                    advancedRecyclerView.setState(AdvancedRecyclerView.State.ERROR)
                } else if (operation == BackendHandlerIntentService.ACTION_CREATE_FOLDER) {
                    Toast.makeText(context, R.string.message_error_backend_recoverable, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_activity_single_panel_body_list, parent, true)
        advancedRecyclerView = view.findViewById(R.id.advanced_recycler_view)
        advancedRecyclerView.layoutManager = LinearLayoutManager(this)
        advancedRecyclerView.setEmptyText(R.string.message_no_file_found)
        adapter = BackupFileAdapter(this)
        advancedRecyclerView.adapter = adapter
        advancedRecyclerView.setOnRefreshListener(this)

        val currentIntent = intent
        backendId = currentIntent.getStringExtra(BACKEND_ID)
        
        val defaultFolder = BackendServiceFactory.getFile(backendId, null)
        if (defaultFolder != null) {
            fileStack.add(defaultFolder)
        }

        val intentFilter = IntentFilter().apply {
            addAction(LocalAction.ACTION_BACKEND_SERVICE_STARTED)
            addAction(LocalAction.ACTION_BACKEND_SERVICE_FINISHED)
            addAction(LocalAction.ACTION_BACKEND_SERVICE_FAILED)
        }
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(localBroadcastReceiver, intentFilter)
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
        if (reloadOnStart) {
            reloadOnStart = false
            loadCurrentFolder()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        localBroadcastManager.unregisterReceiver(localBroadcastReceiver)
    }

    override fun getActivityTitleRes(): Int = R.string.title_activity_backend_folder_picker

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_backend_explorer

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_select_folder) {
            var folder = currentFolder
            if (folder == null) {
                folder = BackendServiceFactory.getFile(backendId, null)
            }
            if (folder == null) {
                Toast.makeText(this, R.string.message_backend_open_a_folder, Toast.LENGTH_LONG).show()
                return false
            }
            val intent = Intent()
            intent.putExtra(RESULT_FILE, folder)
            setResult(RESULT_OK, intent)
            finish()
        }
        return false
    }

    override fun navigateBack() {
        if (fileStack.isNotEmpty()) {
            fileStack.removeAt(fileStack.size - 1)
            advancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING)
            loadCurrentFolder()
        }
    }

    override fun onFileClick(file: IFile) {
        if (file.isDirectory) {
            fileStack.add(file)
            advancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING)
            loadFolder(file)
        }
    }

    override fun onRefresh() {
        loadCurrentFolder()
    }

    private val currentFolder: IFile?
        get() = if (fileStack.isEmpty()) ROOT_FOLDER else fileStack.last()

    private fun loadCurrentFolder() {
        loadFolder(currentFolder)
    }

    private fun loadFolder(folder: IFile?) {
        val intent = Intent(this, BackendHandlerIntentService::class.java).apply {
            putExtra(BackendHandlerIntentService.BACKEND_ID, backendId)
            putExtra(BackendHandlerIntentService.ACTION, BackendHandlerIntentService.ACTION_LIST)
            putExtra(BackendHandlerIntentService.PARENT_FOLDER, folder)
        }
        startService(intent)
    }

    override fun onFloatingActionButtonClick() {
        val inputView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null)
        val inputEditText = inputView.findViewById<EditText>(R.id.dialog_input_edit_text)
        inputEditText.setHint(R.string.hint_new_folder)
        inputEditText.inputType = InputType.TYPE_CLASS_TEXT
        
        val dialog = ThemedDialog.buildMaterialDialog(this)
            .setTitle(R.string.title_backend_create_folder)
            .setMessage(R.string.message_backend_create_folder)
            .setView(inputView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val intent = Intent(this, BackendHandlerIntentService::class.java).apply {
                    putExtra(BackendHandlerIntentService.BACKEND_ID, backendId)
                    putExtra(BackendHandlerIntentService.ACTION, BackendHandlerIntentService.ACTION_CREATE_FOLDER)
                    putExtra(BackendHandlerIntentService.PARENT_FOLDER, currentFolder)
                    putExtra(BackendHandlerIntentService.FOLDER_NAME, inputEditText.text.toString())
                }
                startService(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        ThemedDialog.showWithInput(dialog, inputEditText, false)
    }

    companion object {
        const val BACKEND_ID = "BackendExplorerActivity::Arguments::BackendId"
        const val RESULT_FILE = "BackendExplorerActivity::Result::File"
        private val ROOT_FOLDER: IFile? = null
    }
}
