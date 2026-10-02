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

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
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
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MenuRes
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.FragmentManager
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
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.UUID

class ImportExportActivity : SinglePanelActivity(), 
    ImportExportFormatPicker.Controller, 
    DateTimePicker.Controller, 
    WalletPicker.MultiWalletController, 
    WalletPicker.SingleWalletController, 
    ExportColumnsPicker.Controller {

    private lateinit var importFormatEditText: MaterialEditText
    private lateinit var exportFormatEditText: MaterialEditText
    private lateinit var exportCsvNoTransfersTextView: View
    private lateinit var startDateEditText: MaterialEditText
    private lateinit var endDateEditText: MaterialEditText
    private lateinit var walletsEditText: MaterialEditText
    private lateinit var importFileEditText: MaterialEditText
    private lateinit var exportFolderEditText: MaterialEditText
    private lateinit var exportColumnsEditText: MaterialEditText
    private lateinit var uniqueWalletCheckbox: CheckBox
    private lateinit var importMappingLayout: View
    private lateinit var importWalletEditText: MaterialEditText
    private lateinit var dateColumnEditText: MaterialEditText
    private lateinit var amountColumnEditText: MaterialEditText
    private lateinit var descriptionColumnEditText: MaterialEditText
    private lateinit var noteColumnEditText: MaterialEditText
    private lateinit var categoryColumnEditText: MaterialEditText
    private lateinit var dateFormatEditText: MaterialEditText
    private lateinit var decimalSeparatorEditText: MaterialEditText
    private lateinit var spendingPositiveCheckbox: CheckBox

    private lateinit var dataFormatPicker: ImportExportFormatPicker
    private lateinit var startDateTimePicker: DateTimePicker
    private lateinit var endDateTimePicker: DateTimePicker
    private lateinit var walletPicker: WalletPicker
    private lateinit var exportColumnsPicker: ExportColumnsPicker
    private lateinit var importWalletPicker: WalletPicker

    private lateinit var importFileLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var exportFolderLauncher: ActivityResultLauncher<Uri?>
    
    private var importFile: File? = null
    private var exportFolderUri: Uri? = null

    private var importHeader: CsvImportMapping.Header? = null
    private var importMapping = CsvImportMapping()

    private var mode = MODE_EXPORT
    private var tokens = ArrayList<String>()

    private var progressDialog: GenericProgressDialog? = null
    private lateinit var localBroadcastManager: LocalBroadcastManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        importFileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                onImportFileSelected(uri)
            }
        }
        
        exportFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                onExportFolderSelected(uri)
            }
        }
        
        if (savedInstanceState != null) {
            val savedTokens = savedInstanceState.getStringArrayList(SS_TOKEN)
            if (savedTokens != null) {
                tokens = savedTokens
            }
            val importPath = savedInstanceState.getString(SS_IMPORT_FILE)
            if (importPath != null) {
                importFile = File(importPath)
                importFileEditText.setText(importFile?.name)
                try {
                    importFile?.let {
                        importHeader = CsvImportMapping.readHeader(it)
                    }
                    onImportHeaderRead(savedInstanceState.getSerializable(SS_IMPORT_MAPPING) as? CsvImportMapping)
                } catch (e: Exception) {
                    importFile = null
                    importFileEditText.setText(null)
                }
            }
            val exportUriStr = savedInstanceState.getString(SS_EXPORT_FOLDER_URI)
            if (exportUriStr != null) {
                exportFolderUri = Uri.parse(exportUriStr)
                val folder = DocumentFile.fromTreeUri(this, exportFolderUri!!)
                exportFolderEditText.setText(folder?.name)
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
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        localBroadcastManager.registerReceiver(localBroadcastReceiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()
        localBroadcastManager.unregisterReceiver(localBroadcastReceiver)
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
        
        importFormatEditText = view.findViewById(R.id.import_format_edit_text)
        exportFormatEditText = view.findViewById(R.id.export_format_edit_text)
        exportCsvNoTransfersTextView = view.findViewById(R.id.export_csv_no_transfers_text_view)
        startDateEditText = view.findViewById(R.id.start_date_edit_text)
        endDateEditText = view.findViewById(R.id.end_date_edit_text)
        walletsEditText = view.findViewById(R.id.wallets_edit_text)
        importFileEditText = view.findViewById(R.id.import_file_edit_text)
        exportFolderEditText = view.findViewById(R.id.export_folder_edit_text)
        exportColumnsEditText = view.findViewById(R.id.export_optional_columns_edit_text)
        uniqueWalletCheckbox = view.findViewById(R.id.export_unique_wallet_checkbox)
        importMappingLayout = view.findViewById(R.id.import_mapping_layout)
        importWalletEditText = view.findViewById(R.id.import_wallet_edit_text)
        dateColumnEditText = view.findViewById(R.id.import_date_column_edit_text)
        amountColumnEditText = view.findViewById(R.id.import_amount_column_edit_text)
        descriptionColumnEditText = view.findViewById(R.id.import_description_column_edit_text)
        noteColumnEditText = view.findViewById(R.id.import_note_column_edit_text)
        categoryColumnEditText = view.findViewById(R.id.import_category_column_edit_text)
        dateFormatEditText = view.findViewById(R.id.import_date_format_edit_text)
        decimalSeparatorEditText = view.findViewById(R.id.import_decimal_separator_edit_text)
        spendingPositiveCheckbox = view.findViewById(R.id.import_spending_positive_checkbox)

        mode = activityMode
        importFormatEditText.visibility = if (mode == MODE_IMPORT) View.VISIBLE else View.GONE
        exportFormatEditText.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        startDateEditText.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        endDateEditText.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        walletsEditText.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        importFileEditText.visibility = if (mode == MODE_IMPORT) View.VISIBLE else View.GONE
        exportFolderEditText.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        exportColumnsEditText.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE
        uniqueWalletCheckbox.visibility = if (mode == MODE_EXPORT) View.VISIBLE else View.GONE

        importFormatEditText.setOnClickListener { dataFormatPicker.showPicker(arrayOf(DataFormat.CSV)) }
        exportFormatEditText.setOnClickListener { dataFormatPicker.showPicker(arrayOf(DataFormat.CSV, DataFormat.XLS, DataFormat.PDF)) }
        startDateEditText.setOnClickListener { startDateTimePicker.showDatePicker() }
        startDateEditText.setOnCancelButtonClickListener {
            startDateTimePicker.setCurrentDateTime(null)
            false
        }
        endDateEditText.setOnClickListener { endDateTimePicker.showDatePicker() }
        endDateEditText.setOnCancelButtonClickListener {
            endDateTimePicker.setCurrentDateTime(null)
            false
        }
        walletsEditText.setOnClickListener { walletPicker.showMultiWalletPicker() }
        importFileEditText.setOnClickListener { importFileLauncher.launch(arrayOf("*/*")) }
        exportFolderEditText.setOnClickListener { exportFolderLauncher.launch(null) }

        importFormatEditText.setTextViewMode(true)
        exportFormatEditText.setTextViewMode(true)
        startDateEditText.setTextViewMode(true)
        endDateEditText.setTextViewMode(true)
        walletsEditText.setTextViewMode(true)
        importFileEditText.setTextViewMode(true)
        exportFolderEditText.setTextViewMode(true)
        exportColumnsEditText.setTextViewMode(true)
        importWalletEditText.setTextViewMode(true)
        dateColumnEditText.setTextViewMode(true)
        amountColumnEditText.setTextViewMode(true)
        descriptionColumnEditText.setTextViewMode(true)
        noteColumnEditText.setTextViewMode(true)
        categoryColumnEditText.setTextViewMode(true)
        dateFormatEditText.setTextViewMode(true)
        decimalSeparatorEditText.setTextViewMode(true)

        importWalletEditText.setOnClickListener { importWalletPicker.showSingleWalletPicker() }
        dateColumnEditText.setOnClickListener { showColumnPicker(dateColumnEditText, false, importMapping.date) { importMapping.date = it } }
        amountColumnEditText.setOnClickListener { showColumnPicker(amountColumnEditText, false, importMapping.amount) { importMapping.amount = it } }
        descriptionColumnEditText.setOnClickListener { showColumnPicker(descriptionColumnEditText, true, importMapping.description) { importMapping.description = it } }
        noteColumnEditText.setOnClickListener { showColumnPicker(noteColumnEditText, true, importMapping.note) { importMapping.note = it } }
        categoryColumnEditText.setOnClickListener { showColumnPicker(categoryColumnEditText, true, importMapping.category) { importMapping.category = it } }
        
        dateFormatEditText.setOnClickListener {
            showChoicePicker(dateFormatEditText, CsvImportMapping.DATE_PATTERNS, CsvImportMapping.DATE_PATTERNS.indexOf(importMapping.datePattern)) {
                importMapping.datePattern = CsvImportMapping.DATE_PATTERNS[it]
            }
        }
        
        decimalSeparatorEditText.setOnClickListener {
            showChoicePicker(
                decimalSeparatorEditText,
                arrayOf(getString(R.string.csv_import_decimal_dot), getString(R.string.csv_import_decimal_comma)),
                if (importMapping.decimalComma) 1 else 0
            ) { importMapping.decimalComma = (it == 1) }
        }

        addRequiredValidator(importWalletEditText, R.string.error_input_missing_wallet) { importWalletPicker.isSelected }
        addRequiredValidator(dateColumnEditText, R.string.csv_import_error_missing_date_column) { importMapping.date != CsvImportMapping.NONE }
        addRequiredValidator(amountColumnEditText, R.string.csv_import_error_missing_amount_column) { importMapping.amount != CsvImportMapping.NONE }
        addRequiredValidator(dateFormatEditText, R.string.csv_import_error_missing_date_format) { importMapping.datePattern != null }

        importFormatEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_format)
            override fun isValid(charSequence: CharSequence): Boolean = dataFormatPicker.isSelected
            override fun autoValidate(): Boolean = false
        })

        exportFormatEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_format)
            override fun isValid(charSequence: CharSequence): Boolean = dataFormatPicker.isSelected
            override fun autoValidate(): Boolean = false
        })

        walletsEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_multiple_wallets)
            override fun isValid(charSequence: CharSequence): Boolean = walletPicker.isSelected
            override fun autoValidate(): Boolean = false
        })

        importFileEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_input_file)
            override fun isValid(charSequence: CharSequence): Boolean = importFile != null
            override fun autoValidate(): Boolean = false
        })

        exportFolderEditText.addValidator(object : Validator {
            override fun getErrorMessage(): String = getString(R.string.error_input_missing_output_folder)
            override fun isValid(charSequence: CharSequence): Boolean = exportFolderUri != null
            override fun autoValidate(): Boolean = false
        })

        exportColumnsEditText.setOnClickListener { exportColumnsPicker.showPicker() }

        val fragmentManager = supportFragmentManager
        dataFormatPicker = ImportExportFormatPicker.createPicker(fragmentManager, TAG_DATA_FORMAT_PICKER)
        startDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_START_DATE_TIME_PICKER, null)
        endDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_END_DATE_TIME_PICKER, null)
        walletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, null as Array<Wallet>?)
        exportColumnsPicker = ExportColumnsPicker.createPicker(fragmentManager, TAG_COLUMNS_PICKER)
        importWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_IMPORT_WALLET_PICKER, null as Wallet?)
        progressDialog = fragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG) as? GenericProgressDialog
    }

    private val activityMode: Int
        get() = intent?.getIntExtra(MODE, MODE_EXPORT) ?: MODE_EXPORT

    override fun getActivityTitleRes(): Int = when (mode) {
        MODE_EXPORT -> R.string.title_activity_export_data
        MODE_IMPORT -> R.string.title_activity_import_data
        else -> 0
    }

    @MenuRes
    override fun onInflateMenu(): Int = R.menu.menu_import_export

    override fun onMenuCreated(menu: Menu) {
        when (mode) {
            MODE_IMPORT -> {
                menu.findItem(R.id.action_import_data).isVisible = true
                menu.findItem(R.id.action_export_data).isVisible = false
            }
            MODE_EXPORT -> {
                menu.findItem(R.id.action_import_data).isVisible = false
                menu.findItem(R.id.action_export_data).isVisible = true
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_data -> {
                if (importFormatEditText.validate() && importFileEditText.validate() && (!isImportMappingShown() || validateImportMapping())) {
                    ThemedDialog.buildMaterialDialog(this)
                        .setTitle(R.string.title_warning)
                        .setMessage(R.string.message_data_import_without_backup)
                        .setPositiveButton(android.R.string.ok) { _, _ -> importData() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
            R.id.action_export_data -> {
                if (exportFormatEditText.validate() && walletsEditText.validate() && exportFolderEditText.validate()) {
                    exportData()
                }
            }
        }
        return false
    }

    private fun importData() {
        val intent = Intent(this, ImportExportIntentService::class.java).apply {
            putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT)
            putExtra(ImportExportIntentService.FORMAT, dataFormatPicker.currentFormat)
            putExtra(ImportExportIntentService.FILE, importFile)
        }
        
        if (isImportMappingShown()) {
            importMapping.separator = importHeader?.separator ?: ','
            importMapping.spendingPositive = spendingPositiveCheckbox.isChecked
            importHeader?.cells?.let { cells ->
                PreferenceManager.setCsvImportMapping(CsvImportMapping.signature(cells), importMapping.encode())
            }
            importMapping.walletId = importWalletPicker.currentWallet?.id ?: 0L
            intent.putExtra(ImportExportIntentService.MAPPING, importMapping)
        }
        
        val token = UUID.randomUUID().toString()
        tokens.add(token)
        intent.putExtra(ImportExportIntentService.TOKEN, token)
        startService(intent)
    }

    private fun exportData() {
        val intent = Intent(this, ImportExportIntentService::class.java).apply {
            putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_EXPORT)
            putExtra(ImportExportIntentService.FORMAT, dataFormatPicker.currentFormat)
            putExtra(ImportExportIntentService.START_DATE, startDateTimePicker.currentDateTime)
            putExtra(ImportExportIntentService.END_DATE, endDateTimePicker.currentDateTime)
            putExtra(ImportExportIntentService.WALLETS, walletPicker.currentWallets)
            putExtra(ImportExportIntentService.FOLDER, getExportCacheDir())
            putExtra(ImportExportIntentService.UNIQUE_WALLET, uniqueWalletCheckbox.isChecked)
            putExtra(ImportExportIntentService.OPTIONAL_COLUMNS, exportColumnsPicker.currentServiceColumns)
        }
        
        val token = UUID.randomUUID().toString()
        tokens.add(token)
        intent.putExtra(ImportExportIntentService.TOKEN, token)
        startService(intent)
    }

    private fun onImportFileSelected(uri: Uri) {
        importWalletPicker.onWalletSelected(null)
        try {
            var displayName = queryDisplayName(uri)
            if (TextUtils.isEmpty(displayName)) {
                displayName = "import"
            }
            
            val staged = File(cacheDir, "import_" + displayName!!.replace('/', '_'))
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(staged).use { output ->
                    copyStream(input, output)
                }
            } ?: throw IOException("unable to open the selected file")
            
            importFile = staged
            importFileEditText.setText(displayName)
            
            try {
                importHeader = CsvImportMapping.readHeader(staged)
            } catch (e: RuntimeException) {
                throw IOException(e.message, e)
            }
            
            onImportHeaderRead(null)
            
            if (!dataFormatPicker.isSelected) {
                val dot = displayName.lastIndexOf('.')
                if (dot >= 0) {
                    val extension = displayName.substring(dot).toLowerCase(Locale.ENGLISH)
                    if (extension == ".csv") {
                        dataFormatPicker.currentFormat = DataFormat.CSV
                    }
                }
            }
        } catch (e: IOException) {
            importFile = null
            importFileEditText.setText(null)
            importHeader = null
            onImportHeaderRead(null)
            
            ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_failed)
                .setMessage(getString(R.string.message_data_import_failed, e.message))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun onImportHeaderRead(chosen: CsvImportMapping?) {
        val mapped = importHeader != null && !importHeader!!.nativeHeader
        importMappingLayout.visibility = if (mapped) View.VISIBLE else View.GONE
        if (!mapped) return
        
        val effectiveChosen = chosen ?: getRememberedImportMapping().also {
            spendingPositiveCheckbox.isChecked = it.spendingPositive
        }
        
        importMapping = effectiveChosen
        updateImportMappingFields()
    }

    private fun getRememberedImportMapping(): CsvImportMapping {
        val signature = importHeader?.cells?.let { CsvImportMapping.signature(it) } ?: ""
        val remembered = CsvImportMapping.decode(PreferenceManager.getCsvImportMapping(signature))
        val columns = importHeader?.cells?.size ?: 0
        
        if (remembered != null && remembered.date < columns && remembered.amount < columns &&
            remembered.description < columns && remembered.note < columns &&
            remembered.category < columns) {
            return remembered
        }
        return CsvImportMapping()
    }

    private fun isImportMappingShown(): Boolean = importMappingLayout.visibility == View.VISIBLE

    private fun validateImportMapping(): Boolean = 
        importWalletEditText.validate() && dateColumnEditText.validate() && 
        amountColumnEditText.validate() && dateFormatEditText.validate()

    private fun updateImportMappingFields() {
        dateColumnEditText.setText(getColumnName(importMapping.date))
        amountColumnEditText.setText(getColumnName(importMapping.amount))
        descriptionColumnEditText.setText(getColumnName(importMapping.description))
        noteColumnEditText.setText(getColumnName(importMapping.note))
        categoryColumnEditText.setText(getColumnName(importMapping.category))
        dateFormatEditText.setText(importMapping.datePattern)
        decimalSeparatorEditText.setText(if (importMapping.decimalComma) R.string.csv_import_decimal_comma else R.string.csv_import_decimal_dot)
    }

    private fun getColumnName(column: Int): String? {
        if (column == CsvImportMapping.NONE || importHeader == null) return null
        val name = importHeader!!.cells.getOrNull(column)?.trim() ?: return null
        return if (name.isEmpty()) getString(R.string.csv_import_column_unnamed, column + 1) else name
    }

    private fun showColumnPicker(field: MaterialEditText, optional: Boolean, current: Int, onPicked: (Int) -> Unit) {
        val cells = importHeader?.cells ?: emptyArray()
        val offset = if (optional) 1 else 0
        val items = Array(cells.size + offset) { "" }
        if (optional) items[0] = getString(R.string.csv_import_column_none)
        
        for (i in cells.indices) {
            items[i + offset] = getColumnName(i) ?: ""
        }
        
        showChoicePicker(field, items, current + offset) { onPicked(it - offset) }
    }

    private fun showChoicePicker(field: MaterialEditText, items: Array<String>, checked: Int, onPicked: (Int) -> Unit) {
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
        exportFolderEditText.setText(folder?.name)
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
        if (exportFolderUri == null || sourceUri == null) {
            throw IOException("missing export destination")
        }
        val folder = DocumentFile.fromTreeUri(this, exportFolderUri!!) ?: throw IOException("unable to open the destination folder")
        
        var name = queryDisplayName(sourceUri)
        if (TextUtils.isEmpty(name)) {
            name = sourceUri.lastPathSegment
        }
        
        val target = folder.createFile("application/octet-stream", name!!) ?: throw IOException("unable to create the file in the destination folder")
        
        contentResolver.openInputStream(sourceUri)?.use { input ->
            contentResolver.openOutputStream(target.uri)?.use { output ->
                copyStream(input, output)
            } ?: throw IOException("unable to copy the export to the destination")
        } ?: throw IOException("unable to copy the export to the destination")
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
        return null
    }

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onFormatChanged(tag: String?, format: DataFormat?) {
        if (format != null) {
            when (format) {
                DataFormat.CSV -> {
                    importFormatEditText.setText(R.string.hint_data_format_csv)
                    exportFormatEditText.setText(R.string.hint_data_format_csv)
                    if (mode == MODE_EXPORT) {
                        exportColumnsEditText.visibility = View.VISIBLE
                        exportCsvNoTransfersTextView.visibility = View.VISIBLE
                    }
                }
                DataFormat.XLS -> {
                    importFormatEditText.setText(R.string.hint_data_format_xls)
                    exportFormatEditText.setText(R.string.hint_data_format_xls)
                    if (mode == MODE_EXPORT) {
                        exportColumnsEditText.visibility = View.VISIBLE
                        exportCsvNoTransfersTextView.visibility = View.GONE
                    }
                }
                DataFormat.PDF -> {
                    importFormatEditText.setText(R.string.hint_data_format_pdf)
                    exportFormatEditText.setText(R.string.hint_data_format_pdf)
                    if (mode == MODE_EXPORT) {
                        exportColumnsEditText.visibility = View.VISIBLE
                        exportCsvNoTransfersTextView.visibility = View.GONE
                    }
                }
            }
        } else {
            importFormatEditText.setText(null)
            exportFormatEditText.setText(null)
            exportColumnsEditText.visibility = View.GONE
            exportCsvNoTransfersTextView.visibility = View.GONE
        }
        onFormatOrWalletChanged()
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        when (tag) {
            TAG_START_DATE_TIME_PICKER -> {
                if (date != null) {
                    DateFormatter.applyDate(startDateEditText, date)
                } else {
                    startDateEditText.setText(null)
                }
            }
            TAG_END_DATE_TIME_PICKER -> {
                if (date != null) {
                    DateFormatter.applyDate(endDateEditText, date)
                } else {
                    endDateEditText.setText(null)
                }
            }
        }
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        importWalletEditText.setText(wallet?.name)
    }

    override fun onWalletListChanged(tag: String?, wallets: Array<out Wallet>?) {
        if (!wallets.isNullOrEmpty()) {
            walletsEditText.setText(wallets.joinToString(", ") { it.name })
        } else {
            walletsEditText.setText(null)
        }
        onFormatOrWalletChanged()
    }

    private fun onFormatOrWalletChanged() {
        if (mode == MODE_EXPORT) {
            if (walletPicker.isSelected) {
                val wallets = walletPicker.currentWallets
                if (wallets != null && wallets.size > 1) {
                    val dataFormat = dataFormatPicker.currentFormat
                    if (dataFormat != null) {
                        when (dataFormat) {
                            DataFormat.CSV -> uniqueWalletCheckbox.visibility = View.GONE
                            DataFormat.XLS, DataFormat.PDF -> uniqueWalletCheckbox.visibility = View.VISIBLE
                        }
                        return
                    }
                }
            }
        }
        uniqueWalletCheckbox.visibility = View.GONE
    }

    override fun onExportColumnsChanged(tag: String?, columns: Array<out String>?) {
        if (!columns.isNullOrEmpty()) {
            exportColumnsEditText.setText(columns.joinToString(", "))
        } else {
            exportColumnsEditText.setText(null)
        }
    }

    private val localBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!tokens.contains(intent.getStringExtra(ImportExportIntentService.TOKEN))) return
            
            when (intent.action) {
                LocalAction.ACTION_IMPORT_SERVICE_STARTED -> {
                    if (progressDialog == null) {
                        progressDialog = GenericProgressDialog.newInstance(R.string.title_data_importing, R.string.message_data_import_running, true)
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
                    val exception = intent.getSerializableExtra(ImportExportIntentService.EXCEPTION) as Exception
                    
                    ThemedDialog.buildMaterialDialog(this@ImportExportActivity)
                        .setTitle(R.string.title_failed)
                        .setMessage(getString(R.string.message_data_import_failed, exception.message))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                LocalAction.ACTION_EXPORT_SERVICE_STARTED -> {
                    if (progressDialog == null) {
                        progressDialog = GenericProgressDialog.newInstance(R.string.title_data_exporting, R.string.message_data_export_running, true)
                    }
                    progressDialog?.show(supportFragmentManager, TAG_PROGRESS_DIALOG)
                }
                LocalAction.ACTION_EXPORT_SERVICE_FINISHED -> {
                    progressDialog?.dismissAllowingStateLoss()
                    progressDialog = null
                    
                    val resultUri = intent.getParcelableExtra<Uri>(ImportExportIntentService.RESULT_FILE_URI)
                    val resultType = intent.getStringExtra(ImportExportIntentService.RESULT_FILE_TYPE)
                    
                    try {
                        saveExportToSelectedFolder(resultUri, resultType)
                    } catch (e: IOException) {
                        ThemedDialog.buildMaterialDialog(this@ImportExportActivity)
                            .setTitle(R.string.title_failed)
                            .setMessage(getString(R.string.message_data_export_failed, e.message))
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
                                } catch (ignore: ActivityNotFoundException) {}
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                LocalAction.ACTION_EXPORT_SERVICE_FAILED -> {
                    progressDialog?.dismissAllowingStateLoss()
                    progressDialog = null
                    val exception = intent.getSerializableExtra(ImportExportIntentService.EXCEPTION) as Exception
                    
                    ThemedDialog.buildMaterialDialog(this@ImportExportActivity)
                        .setTitle(R.string.title_failed)
                        .setMessage(getString(R.string.message_data_export_failed, exception.message))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
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

        @Throws(IOException::class)
        private fun copyStream(input: InputStream, output: OutputStream) {
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            output.flush()
        }
    }
}
