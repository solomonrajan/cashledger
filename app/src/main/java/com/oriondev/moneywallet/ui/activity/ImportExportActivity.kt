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

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MenuRes
import androidx.documentfile.provider.DocumentFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.DataFormat
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.ExportColumnsPicker
import com.oriondev.moneywallet.picker.ImportExportFormatPicker
import com.oriondev.moneywallet.picker.WalletPicker
import com.oriondev.moneywallet.service.ImportExportIntentService
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity
import com.oriondev.moneywallet.ui.fragment.dialog.GenericProgressDialog
import com.oriondev.moneywallet.ui.view.text.MaterialEditText
import com.oriondev.moneywallet.ui.view.text.Validator
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.DateFormatter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Created by andrea on 19/12/18.
 */
class ImportExportActivity : SinglePanelActivity(),
    ImportExportFormatPicker.Controller,
    DateTimePicker.Controller,
    WalletPicker.MultiWalletController,
    WalletPicker.SingleWalletController,
    ExportColumnsPicker.Controller {

    private var importFormatEditText: MaterialEditText? = null
    private var exportFormatEditText: MaterialEditText? = null
    private var exportCsvNoTransfersTextView: View? = null
    private var startDateEditText: MaterialEditText? = null
    private var endDateEditText: MaterialEditText? = null
    private var walletsEditText: MaterialEditText? = null
    private var importFileEditText: MaterialEditText? = null
    private var exportFolderEditText: MaterialEditText? = null
    private var exportColumnsEditText: MaterialEditText? = null
    private var uniqueWalletCheckbox: CheckBox? = null
    private var importMappingLayout: View? = null
    private var importWalletEditText: MaterialEditText? = null
    private var dateColumnEditText: MaterialEditText? = null
    private var amountColumnEditText: MaterialEditText? = null
    private var descriptionColumnEditText: MaterialEditText? = null
    private var noteColumnEditText: MaterialEditText? = null
    private var categoryColumnEditText: MaterialEditText? = null
    private var dateFormatEditText: MaterialEditText? = null
    private var decimalSeparatorEditText: MaterialEditText? = null
    private var spendingPositiveCheckbox: CheckBox? = null

    private var dataFormatPicker: ImportExportFormatPicker? = null
    private var startDateTimePicker: DateTimePicker? = null
    private var endDateTimePicker: DateTimePicker? = null
    private var walletPicker: WalletPicker? = null
    private var exportColumnsPicker: ExportColumnsPicker? = null
    private var importWalletPicker: WalletPicker? = null

    private val importFileLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                onImportFileSelected(uri)
            }
        }

    private val exportFolderLauncher: ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                onExportFolderSelected(uri)
            }
        }

    private var importFile: File? = null
    private var exportFolderUri: Uri? = null

    private var importHeader: CsvImportMapping.Header? = null
    private var importMapping: CsvImportMapping = CsvImportMapping()

    private var mode = 0
    private var tokens = ArrayList<String>()

    private var progressDialog: GenericProgressDialog? = null
    private var localBroadcastManager: LocalBroadcastManager? = null

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val token = intent.getStringExtra(ImportExportIntentService.TOKEN)
            if (!tokens.contains(token)) {
                return
            }
            when (intent.action) {
                LocalAction.ACTION_IMPORT_SERVICE_STARTED -> {
                    if (progressDialog == null) {
                        progressDialog = GenericProgressDialog.newInstance(
                            R.string.title_data_importing,
                            R.string.message_data_import_running,
                            true
                        )
                    }
                    progressDialog?.show(supportFragmentManager, TAG_PROGRESS_DIALOG)
                }
                LocalAction.ACTION_IMPORT_SERVICE_FINISHED -> {
                    progressDialog?.dismissAllowingStateLoss()
                    progressDialog = null

                    val roundedAmounts = intent.getIntExtra(ImportExportIntentService.ROUNDED_AMOUNTS, 0)
                    var message = if (roundedAmounts > 0) {
                        getString(R.string.message_data_import_success_rounded, roundedAmounts)
                    } else {
                        getString(R.string.message_data_import_success)
                    }
                    val alreadySavedRows = intent.getIntExtra(ImportExportIntentService.ALREADY_SAVED_ROWS, 0)
                    if (alreadySavedRows > 0) {
                        message += " " + getString(R.string.message_data_import_already_saved, alreadySavedRows)
                    }
                    ThemedDialog.buildMaterialDialog(this@ImportExportActivity)
                        .setTitle(R.string.title_success)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                LocalAction.ACTION_IMPORT_SERVICE_FAILED -> {
                    progressDialog?.dismissAllowingStateLoss()
                    progressDialog = null
                    val exception = intent.getSerializableExtra(ImportExportIntentService.EXCEPTION) as? Exception
                    ThemedDialog.buildMaterialDialog(this@ImportExportActivity)
                        .setTitle(R.string.title_failed)
                        .setMessage(getString(R.string.message_data_import_failed, exception?.message ?: ""))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                LocalAction.ACTION_EXPORT_SERVICE_STARTED -> {
                    if (progressDialog == null) {
                        progressDialog = GenericProgressDialog.newInstance(
                            R.string.title_data_exporting,
                            R.string.message_data_export_running,
                            true
                        )
                    }
                    progressDialog?.show(supportFragmentManager, TAG_PROGRESS_DIALOG)
                }
                LocalAction.ACTION_EXPORT_SERVICE_FINISHED -> {
                    progressDialog?.dismissAllowingStateLoss()
                    progressDialog = null
                    val resultUri: Uri? = intent.getParcelableExtra(ImportExportIntentService.RESULT_FILE_URI)
                    val resultType = intent.getStringExtra(ImportExportIntentService.RESULT_FILE_TYPE)
                    try {
                        saveExportToSelectedFolder(resultUri, resultType)
                    } catch (e: IOException) {
                        ThemedDialog.buildMaterialDialog(this@ImportExportActivity)
                            .setTitle(R.string.title_failed)
                            .setMessage(getString(R.string.message_data_export_failed, e.message ?: ""))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        return
                    }
                    ThemedDialog.buildMaterialDialog(this@ImportExportActivity)
                        .setTitle(R.string.title_success)
                        .setMessage(R.string.message_data_export_success)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            if (resultUri != null) {
                                val target = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(resultUri, resultType)
                                    flags = Intent.FLAG_ACTIVITY_NO_HISTORY
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooser = Intent.createChooser(target, getString(R.string.action_open))
                                try {
                                    startActivity(chooser)
                                } catch (_: ActivityNotFoundException) {
                                    // no activity to handle this type of file
                                }
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                LocalAction.ACTION_EXPORT_SERVICE_FAILED -> {
                    progressDialog?.dismissAllowingStateLoss()
                    progressDialog = null
                    val exception = intent.getSerializableExtra(ImportExportIntentService.EXCEPTION) as? Exception
                    ThemedDialog.buildMaterialDialog(this@ImportExportActivity)
                        .setTitle(R.string.title_failed)
                        .setMessage(getString(R.string.message_data_export_failed, exception?.message ?: ""))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            val savedTokens = savedInstanceState.getStringArrayList(SS_TOKEN)
            if (savedTokens != null) {
                tokens = savedTokens
            }
            val importPath = savedInstanceState.getString(SS_IMPORT_FILE)
            if (importPath != null) {
                val file = File(importPath)
                importFile = file
                importFileEditText?.setText(file.name)
                try {
                    importHeader = CsvImportMapping.readHeader(file)
                    val mapping = savedInstanceState.getSerializable(SS_IMPORT_MAPPING) as? CsvImportMapping
                    onImportHeaderRead(mapping)
                } catch (_: IOException) {
                    importFile = null
                    importFileEditText?.setText(null)
                } catch (_: RuntimeException) {
                    importFile = null
                    importFileEditText?.setText(null)
                }
            }
            val exportUriStr = savedInstanceState.getString(SS_EXPORT_FOLDER_URI)
            if (exportUriStr != null) {
                val uri = Uri.parse(exportUriStr)
                exportFolderUri = uri
                val folder = DocumentFile.fromTreeUri(this, uri)
                exportFolderEditText?.setText(folder?.name)
            }
        }
        val intentFilter = IntentFilter().apply {
            addAction(LocalAction.ACTION_IMPORT_SERVICE_STARTED)
            addAction(LocalAction.ACTION_IMPORT_SERVICE_FINISHED)
            addAction(LocalAction.ACTION_IMPORT_SERVICE_FAILED)
            addAction(LocalAction.ACTION_EXPORT_SERVICE_STARTED)
            addAction(LocalAction.ACTION_EXPORT_SERVICE_FINISHED)
            addAction(LocalAction.ACTION_EXPORT_SERVICE_FAILED)
        }
        val broadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager = broadcastManager
        broadcastManager.registerReceiver(localBroadcastReceiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        localBroadcastManager?.unregisterReceiver(localBroadcastReceiver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        importFile?.let { outState.putString(SS_IMPORT_FILE, it.absolutePath) }
        exportFolderUri?.let { outState.putString(SS_EXPORT_FOLDER_URI, it.toString()) }
        outState.putSerializable(SS_IMPORT_MAPPING, importMapping)
        outState.putStringArrayList(SS_TOKEN, tokens)
    }

    override fun onCreatePanelView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_import_export, parent, true)
        val importFormatEt: MaterialEditText = view.findViewById(R.id.import_format_edit_text)
        val exportFormatEt: MaterialEditText = view.findViewById(R.id.export_format_edit_text)
        val exportCsvNoTransfersTv: View = view.findViewById(R.id.export_csv_no_transfers_text_view)
        val startDateEt: MaterialEditText = view.findViewById(R.id.start_date_edit_text)
        val endDateEt: MaterialEditText = view.findViewById(R.id.end_date_edit_text)
        val walletsEt: MaterialEditText = view.findViewById(R.id.wallets_edit_text)
        val importFileEt: MaterialEditText = view.findViewById(R.id.import_file_edit_text)
        val exportFolderEt: MaterialEditText = view.findViewById(R.id.export_folder_edit_text)
        val exportColumnsEt: MaterialEditText = view.findViewById(R.id.export_optional_columns_edit_text)
        val uniqueWalletCb: CheckBox = view.findViewById(R.id.export_unique_wallet_checkbox)
        val importMappingLay: View = view.findViewById(R.id.import_mapping_layout)
        val importWalletEt: MaterialEditText = view.findViewById(R.id.import_wallet_edit_text)
        val dateColumnEt: MaterialEditText = view.findViewById(R.id.import_date_column_edit_text)
        val amountColumnEt: MaterialEditText = view.findViewById(R.id.import_amount_column_edit_text)
        val descriptionColumnEt: MaterialEditText = view.findViewById(R.id.import_description_column_edit_text)
        val noteColumnEt: MaterialEditText = view.findViewById(R.id.import_note_column_edit_text)
        val categoryColumnEt: MaterialEditText = view.findViewById(R.id.import_category_column_edit_text)
        val dateFormatEt: MaterialEditText = view.findViewById(R.id.import_date_format_edit_text)
        val decimalSeparatorEt: MaterialEditText = view.findViewById(R.id.import_decimal_separator_edit_text)
        val spendingPositiveCb: CheckBox = view.findViewById(R.id.import_spending_positive_checkbox)

        importFormatEditText = importFormatEt
        exportFormatEditText = exportFormatEt
        exportCsvNoTransfersTextView = exportCsvNoTransfersTv
        startDateEditText = startDateEt
        endDateEditText = endDateEt
        walletsEditText = walletsEt
        importFileEditText = importFileEt
        exportFolderEditText = exportFolderEt
        exportColumnsEditText = exportColumnsEt
        uniqueWalletCheckbox = uniqueWalletCb
        importMappingLayout = importMappingLay
        importWalletEditText = importWalletEt
        dateColumnEditText = dateColumnEt
        amountColumnEditText = amountColumnEt
        descriptionColumnEditText = descriptionColumnEt
        noteColumnEditText = noteColumnEt
        categoryColumnEditText = categoryColumnEt
        dateFormatEditText = dateFormatEt
        decimalSeparatorEditText = decimalSeparatorEt
        spendingPositiveCheckbox = spendingPositiveCb

        mode = getActivityMode()
        importFormatEt.visibility = if (mode == MODE_IMPORT) View.VISIBLE else View.GONE
        exportFormatEt.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        startDateEt.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        endDateEt.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        walletsEt.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        importFileEt.visibility = if (mode == MODE_IMPORT) View.VISIBLE else View.GONE
        exportFolderEt.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        exportColumnsEt.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        uniqueWalletCb.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE

        importFormatEt.setOnClickListener {
            val dataFormats = arrayOf(DataFormat.CSV)
            dataFormatPicker?.showPicker(dataFormats)
        }
        exportFormatEt.setOnClickListener {
            val dataFormats = arrayOf(DataFormat.CSV, DataFormat.XLS, DataFormat.PDF)
            dataFormatPicker?.showPicker(dataFormats)
        }
        startDateEt.setOnClickListener {
            startDateTimePicker?.showDatePicker()
        }
        startDateEt.setOnCancelButtonClickListener {
            startDateTimePicker?.currentDateTime = null
            false
        }
        endDateEt.setOnClickListener {
            endDateTimePicker?.showDatePicker()
        }
        endDateEt.setOnCancelButtonClickListener {
            endDateTimePicker?.currentDateTime = null
            false
        }
        walletsEt.setOnClickListener {
            walletPicker?.showMultiWalletPicker()
        }
        importFileEt.setOnClickListener {
            importFileLauncher.launch(arrayOf("*/*"))
        }
        exportFolderEt.setOnClickListener {
            exportFolderLauncher.launch(null)
        }

        importFormatEt.setTextViewMode(true)
        exportFormatEt.setTextViewMode(true)
        startDateEt.setTextViewMode(true)
        endDateEt.setTextViewMode(true)
        walletsEt.setTextViewMode(true)
        importFileEt.setTextViewMode(true)
        exportFolderEt.setTextViewMode(true)
        exportColumnsEt.setTextViewMode(true)
        importWalletEt.setTextViewMode(true)
        dateColumnEt.setTextViewMode(true)
        amountColumnEt.setTextViewMode(true)
        descriptionColumnEt.setTextViewMode(true)
        noteColumnEt.setTextViewMode(true)
        categoryColumnEt.setTextViewMode(true)
        dateFormatEt.setTextViewMode(true)
        decimalSeparatorEt.setTextViewMode(true)

        importWalletEt.setOnClickListener { importWalletPicker?.showSingleWalletPicker() }
        dateColumnEt.setOnClickListener {
            showColumnPicker(dateColumnEt, false, importMapping.date) { column ->
                importMapping.date = column
            }
        }
        amountColumnEt.setOnClickListener {
            showColumnPicker(amountColumnEt, false, importMapping.amount) { column ->
                importMapping.amount = column
            }
        }
        descriptionColumnEt.setOnClickListener {
            showColumnPicker(descriptionColumnEt, true, importMapping.description) { column ->
                importMapping.description = column
            }
        }
        noteColumnEt.setOnClickListener {
            showColumnPicker(noteColumnEt, true, importMapping.note) { column ->
                importMapping.note = column
            }
        }
        categoryColumnEt.setOnClickListener {
            showColumnPicker(categoryColumnEt, true, importMapping.category) { column ->
                importMapping.category = column
            }
        }
        dateFormatEt.setOnClickListener {
            showChoicePicker(
                dateFormatEt,
                CsvImportMapping.DATE_PATTERNS,
                CsvImportMapping.DATE_PATTERNS.indexOf(importMapping.datePattern)
            ) { which ->
                importMapping.datePattern = CsvImportMapping.DATE_PATTERNS[which]
            }
        }
        decimalSeparatorEt.setOnClickListener {
            showChoicePicker(
                decimalSeparatorEt,
                arrayOf(getString(R.string.csv_import_decimal_dot), getString(R.string.csv_import_decimal_comma)),
                if (importMapping.decimalComma) 1 else 0
            ) { which ->
                importMapping.decimalComma = (which == 1)
            }
        }

        addRequiredValidator(importWalletEt, R.string.error_input_missing_wallet) {
            importWalletPicker?.isSelected == true
        }
        addRequiredValidator(dateColumnEt, R.string.csv_import_error_missing_date_column) {
            importMapping.date != CsvImportMapping.NONE
        }
        addRequiredValidator(amountColumnEt, R.string.csv_import_error_missing_amount_column) {
            importMapping.amount != CsvImportMapping.NONE
        }
        addRequiredValidator(dateFormatEt, R.string.csv_import_error_missing_date_format) {
            importMapping.datePattern != null
        }

        importFormatEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_format)
            override fun isValid(charSequence: CharSequence): Boolean = dataFormatPicker?.isSelected == true
            override fun autoValidate(): Boolean = false
        })
        exportFormatEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_format)
            override fun isValid(charSequence: CharSequence): Boolean = dataFormatPicker?.isSelected == true
            override fun autoValidate(): Boolean = false
        })
        walletsEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_multiple_wallets)
            override fun isValid(charSequence: CharSequence): Boolean = walletPicker?.isSelected == true
            override fun autoValidate(): Boolean = false
        })
        importFileEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_input_file)
            override fun isValid(charSequence: CharSequence): Boolean = importFile != null
            override fun autoValidate(): Boolean = false
        })
        exportFolderEt.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_output_folder)
            override fun isValid(charSequence: CharSequence): Boolean = exportFolderUri != null
            override fun autoValidate(): Boolean = false
        })
        exportColumnsEt.setOnClickListener {
            exportColumnsPicker?.showPicker()
        }

        val fragmentManager = supportFragmentManager
        dataFormatPicker = ImportExportFormatPicker.createPicker(fragmentManager, TAG_DATA_FORMAT_PICKER)
        startDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_START_DATE_TIME_PICKER, null)
        endDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_END_DATE_TIME_PICKER, null)
        walletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, null as Array<Wallet>?)
        exportColumnsPicker = ExportColumnsPicker.createPicker(fragmentManager, TAG_COLUMNS_PICKER)
        importWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_IMPORT_WALLET_PICKER, null as Wallet?)
        progressDialog = fragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG) as? GenericProgressDialog
    }

    private fun getActivityMode(): Int {
        val callerIntent = intent
        return callerIntent?.getIntExtra(MODE, MODE_EXPORT) ?: MODE_EXPORT
    }

    override fun getActivityTitleRes(): Int {
        return when (mode) {
            MODE_EXPORT -> R.string.title_activity_export_data
            MODE_IMPORT -> R.string.title_activity_import_data
            else -> 0
        }
    }

    @MenuRes
    override fun onInflateMenu(): Int {
        return R.menu.menu_import_export
    }

    override fun onMenuCreated(menu: Menu) {
        when (mode) {
            MODE_IMPORT -> {
                menu.findItem(R.id.action_import_data)?.isVisible = true
                menu.findItem(R.id.action_export_data)?.isVisible = false
            }
            MODE_EXPORT -> {
                menu.findItem(R.id.action_import_data)?.isVisible = false
                menu.findItem(R.id.action_export_data)?.isVisible = true
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_data -> {
                val formatValid = importFormatEditText?.validate() == true
                val fileValid = importFileEditText?.validate() == true
                val mappingValid = !isImportMappingShown() || validateImportMapping()
                if (formatValid && fileValid && mappingValid) {
                    ThemedDialog.buildMaterialDialog(this)
                        .setTitle(R.string.title_warning)
                        .setMessage(R.string.message_data_import_without_backup)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            importData()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            R.id.action_export_data -> {
                val formatValid = exportFormatEditText?.validate() == true
                val walletsValid = walletsEditText?.validate() == true
                val folderValid = exportFolderEditText?.validate() == true
                if (formatValid && walletsValid && folderValid) {
                    exportData()
                }
            }
        }
        return false
    }

    fun importData() {
        val picker = dataFormatPicker ?: return
        val stagedFile = importFile ?: return
        val serviceIntent = Intent(this, ImportExportIntentService::class.java).apply {
            putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT)
            putExtra(ImportExportIntentService.FORMAT, picker.currentFormat)
            putExtra(ImportExportIntentService.FILE, stagedFile)
            if (isImportMappingShown()) {
                val header = importHeader
                if (header != null) {
                    importMapping.separator = header.separator
                }
                importMapping.spendingPositive = spendingPositiveCheckbox?.isChecked == true
                val headerCells = header?.cells
                if (headerCells != null) {
                    PreferenceManager.setCsvImportMapping(
                        CsvImportMapping.signature(headerCells),
                        importMapping.encode()
                    )
                }
                val wallet = importWalletPicker?.currentWallet
                if (wallet != null) {
                    importMapping.walletId = wallet.id
                }
                putExtra(ImportExportIntentService.MAPPING, importMapping)
            }
            val token = UUID.randomUUID().toString()
            tokens.add(token)
            putExtra(ImportExportIntentService.TOKEN, token)
        }
        startService(serviceIntent)
    }

    fun exportData() {
        val picker = dataFormatPicker ?: return
        val startPicker = startDateTimePicker ?: return
        val endPicker = endDateTimePicker ?: return
        val walletsPick = walletPicker ?: return
        val columnsPicker = exportColumnsPicker ?: return

        val serviceIntent = Intent(this, ImportExportIntentService::class.java).apply {
            putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_EXPORT)
            putExtra(ImportExportIntentService.FORMAT, picker.currentFormat)
            putExtra(ImportExportIntentService.START_DATE, startPicker.currentDateTime)
            putExtra(ImportExportIntentService.END_DATE, endPicker.currentDateTime)
            putExtra(ImportExportIntentService.WALLETS, walletsPick.currentWallets)
            putExtra(ImportExportIntentService.FOLDER, getExportCacheDir())
            putExtra(ImportExportIntentService.UNIQUE_WALLET, uniqueWalletCheckbox?.isChecked == true)
            putExtra(ImportExportIntentService.OPTIONAL_COLUMNS, columnsPicker.currentServiceColumns)
            val token = UUID.randomUUID().toString()
            tokens.add(token)
            putExtra(ImportExportIntentService.TOKEN, token)
        }
        startService(serviceIntent)
    }

    private fun onImportFileSelected(uri: Uri) {
        importWalletPicker?.onWalletSelected(null)
        try {
            var displayName = queryDisplayName(uri)
            if (TextUtils.isEmpty(displayName)) {
                displayName = "import"
            }
            val cleanName = displayName?.replace('/', '_') ?: "import"
            val staged = File(cacheDir, "import_$cleanName")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(staged).use { output ->
                    copyStream(input, output)
                }
            } ?: throw IOException("unable to open the selected file")

            importFile = staged
            importFileEditText?.setText(displayName)
            try {
                importHeader = CsvImportMapping.readHeader(staged)
            } catch (e: RuntimeException) {
                throw IOException(e.message, e)
            }
            onImportHeaderRead(null)

            if (dataFormatPicker?.isSelected != true) {
                val dot = displayName?.lastIndexOf('.') ?: -1
                if (dot >= 0) {
                    val extension = displayName?.substring(dot)?.lowercase(Locale.ENGLISH)
                    if (extension == ".csv") {
                        dataFormatPicker?.currentFormat = DataFormat.CSV
                    }
                }
            }
        } catch (e: IOException) {
            importFile = null
            importFileEditText?.setText(null)
            importHeader = null
            onImportHeaderRead(null)
            ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_failed)
                .setMessage(getString(R.string.message_data_import_failed, e.message ?: ""))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun onImportHeaderRead(chosen: CsvImportMapping?) {
        val currentHeader = importHeader
        val mapped = currentHeader != null && !currentHeader.nativeHeader
        importMappingLayout?.visibility = if (mapped) View.VISIBLE else View.GONE
        if (!mapped) {
            return
        }
        val targetMapping = chosen ?: getRememberedImportMapping().also {
            spendingPositiveCheckbox?.isChecked = it.spendingPositive
        }
        importMapping = targetMapping
        updateImportMappingFields()
    }

    private fun getRememberedImportMapping(): CsvImportMapping {
        val header = importHeader ?: return CsvImportMapping()
        val signature = CsvImportMapping.signature(header.cells)
        val remembered = CsvImportMapping.decode(PreferenceManager.getCsvImportMapping(signature))
        val columns = header.cells.size
        if (remembered != null && remembered.date < columns && remembered.amount < columns
            && remembered.description < columns && remembered.note < columns
            && remembered.category < columns
        ) {
            return remembered
        }
        return CsvImportMapping()
    }

    private fun isImportMappingShown(): Boolean {
        return importMappingLayout?.visibility == View.VISIBLE
    }

    private fun validateImportMapping(): Boolean {
        return importWalletEditText?.validate() == true &&
            dateColumnEditText?.validate() == true &&
            amountColumnEditText?.validate() == true &&
            dateFormatEditText?.validate() == true
    }

    private fun updateImportMappingFields() {
        dateColumnEditText?.setText(getColumnName(importMapping.date))
        amountColumnEditText?.setText(getColumnName(importMapping.amount))
        descriptionColumnEditText?.setText(getColumnName(importMapping.description))
        noteColumnEditText?.setText(getColumnName(importMapping.note))
        categoryColumnEditText?.setText(getColumnName(importMapping.category))
        dateFormatEditText?.setText(importMapping.datePattern)
        val separatorRes = if (importMapping.decimalComma) {
            R.string.csv_import_decimal_comma
        } else {
            R.string.csv_import_decimal_dot
        }
        decimalSeparatorEditText?.setText(separatorRes)
    }

    private fun getColumnName(column: Int): String? {
        if (column == CsvImportMapping.NONE) {
            return null
        }
        val header = importHeader ?: return null
        if (column < 0 || column >= header.cells.size) {
            return null
        }
        val name = header.cells[column].trim()
        return if (name.isEmpty()) getString(R.string.csv_import_column_unnamed, column + 1) else name
    }

    private fun showColumnPicker(
        field: MaterialEditText,
        optional: Boolean,
        current: Int,
        onPicked: (Int) -> Unit
    ) {
        val header = importHeader ?: return
        val offset = if (optional) 1 else 0
        val items = arrayOfNulls<String>(header.cells.size + offset)
        if (optional) {
            items[0] = getString(R.string.csv_import_column_none)
        }
        for (i in header.cells.indices) {
            items[i + offset] = getColumnName(i)
        }
        @Suppress("UNCHECKED_CAST")
        val nonNullItems = items as Array<String>
        showChoicePicker(field, nonNullItems, current + offset) { which ->
            onPicked(which - offset)
        }
    }

    private fun showChoicePicker(
        field: MaterialEditText,
        items: Array<String>,
        checked: Int,
        onPicked: (Int) -> Unit
    ) {
        ThemedDialog.buildMaterialDialog(this)
            .setTitle(field.hint)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                onPicked(which)
                updateImportMappingFields()
                dialog.dismiss()
            }
            .show()
    }

    private fun addRequiredValidator(field: MaterialEditText, errorRes: Int, isValid: () -> Boolean) {
        field.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(errorRes)
            override fun isValid(charSequence: CharSequence): Boolean = isValid()
            override fun autoValidate(): Boolean = false
        })
    }

    private fun onExportFolderSelected(uri: Uri) {
        exportFolderUri = uri
        val folder = DocumentFile.fromTreeUri(this, uri)
        exportFolderEditText?.setText(folder?.name)
    }

    private fun getExportCacheDir(): File {
        val directory = File(cacheDir, "export")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    @Throws(IOException::class)
    private fun saveExportToSelectedFolder(sourceUri: Uri?, type: String?) {
        val folderUri = exportFolderUri ?: throw IOException("missing export destination")
        if (sourceUri == null) {
            throw IOException("missing export destination")
        }
        val folder = DocumentFile.fromTreeUri(this, folderUri)
            ?: throw IOException("unable to open the destination folder")
        var name = queryDisplayName(sourceUri)
        if (TextUtils.isEmpty(name)) {
            name = sourceUri.lastPathSegment
        }
        val finalName = name ?: "export"
        val target = folder.createFile("application/octet-stream", finalName)
            ?: throw IOException("unable to create the file in the destination folder")
        val input = contentResolver.openInputStream(sourceUri)
        val output = contentResolver.openOutputStream(target.uri)
        if (input == null || output == null) {
            input?.close()
            output?.close()
            throw IOException("unable to copy the export to the destination")
        }
        input.use { inStream ->
            output.use { outStream ->
                copyStream(inStream, outStream)
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                return cursor.getString(index)
            }
        }
        return null
    }

    @Throws(IOException::class)
    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onFormatChanged(tag: String, format: DataFormat?) {
        if (format != null) {
            when (format) {
                DataFormat.CSV -> {
                    importFormatEditText?.setText(R.string.hint_data_format_csv)
                    exportFormatEditText?.setText(R.string.hint_data_format_csv)
                    if (mode == MODE_EXPORT) {
                        exportColumnsEditText?.visibility = View.VISIBLE
                        exportCsvNoTransfersTextView?.visibility = View.VISIBLE
                    }
                }
                DataFormat.XLS -> {
                    importFormatEditText?.setText(R.string.hint_data_format_xls)
                    exportFormatEditText?.setText(R.string.hint_data_format_xls)
                    if (mode == MODE_EXPORT) {
                        exportColumnsEditText?.visibility = View.VISIBLE
                        exportCsvNoTransfersTextView?.visibility = View.GONE
                    }
                }
                DataFormat.PDF -> {
                    importFormatEditText?.setText(R.string.hint_data_format_pdf)
                    exportFormatEditText?.setText(R.string.hint_data_format_pdf)
                    if (mode == MODE_EXPORT) {
                        exportColumnsEditText?.visibility = View.VISIBLE
                        exportCsvNoTransfersTextView?.visibility = View.GONE
                    }
                }
            }
        } else {
            importFormatEditText?.setText(null)
            exportFormatEditText?.setText(null)
            exportColumnsEditText?.visibility = View.GONE
            exportCsvNoTransfersTextView?.visibility = View.GONE
        }
        onFormatOrWalletChanged()
    }

    override fun onDateTimeChanged(tag: String, date: Date?) {
        when (tag) {
            TAG_START_DATE_TIME_PICKER -> {
                startDateEditText?.let { editText ->
                    if (date != null) {
                        DateFormatter.applyDate(editText, date)
                    } else {
                        editText.setText(null)
                    }
                }
            }
            TAG_END_DATE_TIME_PICKER -> {
                endDateEditText?.let { editText ->
                    if (date != null) {
                        DateFormatter.applyDate(editText, date)
                    } else {
                        editText.setText(null)
                    }
                }
            }
        }
    }

    override fun onWalletChanged(tag: String, wallet: Wallet?) {
        importWalletEditText?.setText(wallet?.name)
    }

    override fun onWalletListChanged(tag: String, wallets: Array<Wallet>?) {
        if (wallets != null && wallets.isNotEmpty()) {
            val builder = StringBuilder()
            for (i in wallets.indices) {
                if (i != 0) {
                    builder.append(", ")
                }
                builder.append(wallets[i].name)
            }
            walletsEditText?.setText(builder)
        } else {
            walletsEditText?.setText(null)
        }
        onFormatOrWalletChanged()
    }

    private fun onFormatOrWalletChanged() {
        if (mode == MODE_EXPORT) {
            if (walletPicker?.isSelected == true) {
                val wallets = walletPicker?.currentWallets
                if (wallets != null && wallets.size > 1) {
                    val currentFormat = dataFormatPicker?.currentFormat
                    if (currentFormat != null) {
                        when (currentFormat) {
                            DataFormat.CSV -> uniqueWalletCheckbox?.visibility = View.GONE
                            DataFormat.XLS, DataFormat.PDF -> uniqueWalletCheckbox?.visibility = View.VISIBLE
                        }
                        return
                    }
                }
            }
        }
        uniqueWalletCheckbox?.visibility = View.GONE
    }

    override fun onExportColumnsChanged(tag: String, columns: Array<String>?) {
        if (columns != null && columns.isNotEmpty()) {
            val builder = StringBuilder()
            for (i in columns.indices) {
                if (i != 0) {
                    builder.append(", ")
                }
                builder.append(columns[i])
            }
            exportColumnsEditText?.setText(builder)
        } else {
            exportColumnsEditText?.setText(null)
        }
    }

    companion object {
        const val MODE = "ImportExportActivity::Argument::Mode"

        const val MODE_EXPORT = 0
        const val MODE_IMPORT = 1

        private const val TAG_DATA_FORMAT_PICKER = "ImportExportActivity::Tag::DataFormatPicker"
        private const val TAG_START_DATE_TIME_PICKER = "ImportExportActivity::Tag::StartDateTimePicker"
        private const val TAG_END_DATE_TIME_PICKER = "ImportExportActivity::Tag::EndDateTimePicker"
        private const val TAG_WALLET_PICKER = "ImportExportActivity::Tag::WalletPicker"
        private const val TAG_IMPORT_WALLET_PICKER = "ImportExportActivity::Tag::ImportWalletPicker"
        private const val TAG_COLUMNS_PICKER = "ImportExportActivity::Tag::ColumnsPicker"
        private const val TAG_PROGRESS_DIALOG = "ImportExportActivity::tag::GenericProgressDialog"

        private const val SS_IMPORT_FILE = "ImportExportActivity::SavedState::ImportFile"
        private const val SS_EXPORT_FOLDER_URI = "ImportExportActivity::SavedState::ExportFolderUri"
        private const val SS_IMPORT_MAPPING = "ImportExportActivity::SavedState::ImportMapping"
        const val SS_TOKEN = "ImportExportActivity::SavedState::Token"
    }
}
