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

package com.oriondev.moneywallet.service

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.DataFormat
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter
import com.oriondev.moneywallet.storage.database.data.AbstractDataImporter
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataExporter
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataImporter
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping
import com.oriondev.moneywallet.storage.database.data.pdf.PDFDataExporter
import com.oriondev.moneywallet.storage.database.data.xls.XLSDataExporter
import com.oriondev.moneywallet.utils.DateUtils
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.math.min

/**
 * Created by andrea on 20/12/18.
 */
@Suppress("DEPRECATION")
class ImportExportIntentService : IntentService("ImportExportIntentService") {

    companion object {
        const val MODE: String = "ImportExportIntentService::Arguments::Mode"
        const val FORMAT: String = "ImportExportIntentService::Arguments::Format"
        const val START_DATE: String = "ImportExportIntentService::Arguments::StartDate"
        const val END_DATE: String = "ImportExportIntentService::Arguments::EndDate"
        const val WALLETS: String = "ImportExportIntentService::Arguments::Wallets"
        const val FOLDER: String = "ImportExportIntentService::Arguments::Folder"
        const val FILE: String = "ImportExportIntentService::Arguments::File"
        const val UNIQUE_WALLET: String = "ImportExportIntentService::Arguments::UniqueWallet"
        const val OPTIONAL_COLUMNS: String = "ImportExportIntentService::Arguments::OptionalColumns"
        const val MAPPING: String = "ImportExportIntentService::Arguments::Mapping"
        const val TOKEN: String = "ImportExportIntentService::Arguments::Token"

        const val RESULT_FILE_URI: String = "ImportExportIntentService::Results::FileUri"
        const val RESULT_FILE_TYPE: String = "ImportExportIntentService::Results::FileType"
        const val EXCEPTION: String = "ImportExportIntentService::Results::Exception"
        const val ROUNDED_AMOUNTS: String = "ImportExportIntentService::Results::RoundedAmounts"
        const val ALREADY_SAVED_ROWS: String = "ImportExportIntentService::Results::AlreadySavedRows"

        const val MODE_EXPORT: Int = 0
        const val MODE_IMPORT: Int = 1

        /**
         * Export the transactions into a new file inside the folder and return the closed exporter,
         * which holds the output file and its type. A null wallet array exports every wallet.
         * Runs on the calling thread.
         */
        @JvmStatic
        @Throws(IOException::class)
        internal fun exportTransactions(
            context: Context,
            dataFormat: DataFormat,
            folder: File,
            startDate: Date?,
            endDate: Date?,
            wallets: Array<Wallet>?,
            uniqueWallet: Boolean,
            optionalColumns: Array<String>?
        ): AbstractDataExporter {
            // initialize the correct data exporter
            val dataExporter = getDataExporter(context, dataFormat, folder)
            val contentResolver = context.contentResolver
            val uri = DataContentProvider.CONTENT_TRANSACTIONS
            // initialize the selection builder with common variables
            val selectionBuilder = StringBuilder()
            val selectionArguments = ArrayList<String>()
            // append rule to limit to the end date or to the current date
            selectionBuilder.append("DATE (${Contract.Transaction.DATE}) <= DATE(?)")
            selectionArguments.add(DateUtils.getSQLDateString(getFixedEndDate(endDate)))
            // if provided, apply a rule to the start date
            if (startDate != null) {
                selectionBuilder.append(" AND DATE (${Contract.Transaction.DATE}) >= DATE(?)")
                selectionArguments.add(DateUtils.getSQLDateString(startDate))
            }
            if (dataFormat == DataFormat.CSV) {
                selectionBuilder.append(" AND ${CSVDataExporter.ROWS_SELECTION}")
                selectionArguments.addAll(CSVDataExporter.ROWS_SELECTION_ARGS)
            }
            val sortOrder = "${Contract.Transaction.DATE} DESC"
            // check if we should create a unique wallet or if we can export each wallet
            // in a separate way
            val multiWallet = wallets != null && wallets.size > 1 &&
                dataExporter.isMultiWalletSupported() && !uniqueWallet
            val columns = dataExporter.getColumns(!multiWallet, optionalColumns)
            // before starting with the export logic, check if the exporter should
            // store the people names into his internal cache to speedup the procedure
            if (dataExporter.shouldLoadPeople()) {
                val cursor = contentResolver.query(DataContentProvider.CONTENT_PEOPLE, null, null, null, null)
                cursor?.use {
                    dataExporter.cachePeople(it)
                }
            }
            // handle the export logic differently
            if (multiWallet && wallets != null) {
                // execute a query for each wallet: we should clone the original builder
                // to avoid mistakes during each successive query
                for (wallet in wallets) {
                    val selection = "$selectionBuilder AND ${Contract.Transaction.WALLET_ID} = ?"
                    val walletArgs = ArrayList(selectionArguments)
                    walletArgs.add(wallet.id.toString())
                    val cursor = contentResolver.query(uri, null, selection, walletArgs.toTypedArray(), sortOrder)
                    cursor?.use {
                        dataExporter.exportData(it, columns, wallet)
                    }
                }
            } else {
                // execute only a large query: we can modify the original builder here
                if (wallets != null) {
                    selectionBuilder.append(" AND (")
                    for (i in wallets.indices) {
                        if (i != 0) {
                            selectionBuilder.append(" OR ")
                        }
                        selectionBuilder.append("${Contract.Transaction.WALLET_ID} = ?")
                        selectionArguments.add(wallets[i].id.toString())
                    }
                    selectionBuilder.append(")")
                }
                val cursor = contentResolver.query(
                    uri,
                    null,
                    selectionBuilder.toString(),
                    selectionArguments.toTypedArray(),
                    sortOrder
                )
                cursor?.use {
                    if (wallets != null) {
                        dataExporter.exportData(it, columns, *wallets)
                    } else {
                        dataExporter.exportData(it, columns)
                    }
                }
            }
            // close the exporter to flush and close all the open streams
            dataExporter.close()
            return dataExporter
        }

        @Throws(IOException::class)
        private fun getDataExporter(context: Context, dataFormat: DataFormat, folder: File): AbstractDataExporter {
            return when (dataFormat) {
                DataFormat.CSV -> CSVDataExporter(context, folder)
                DataFormat.XLS -> XLSDataExporter(context, folder)
                DataFormat.PDF -> PDFDataExporter(context, folder)
            }
        }

        private fun getFixedEndDate(endDate: Date?): Date {
            val now = Date()
            if (endDate != null) {
                val minMillis = min(now.time, endDate.time)
                return Date(minMillis)
            }
            return now
        }
    }

    private var mReporter: TaskReporter? = null
    private var mToken: String? = null

    private fun getReporter(): TaskReporter {
        val current = mReporter
        if (current != null) {
            return current
        }
        val created = TaskReporter(this)
        mReporter = created
        return created
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            mReporter = TaskReporter(this)
            mToken = intent.getStringExtra(TOKEN)
            when (intent.getIntExtra(MODE, MODE_EXPORT)) {
                MODE_EXPORT -> handleExport(intent)
                MODE_IMPORT -> handleImport(intent)
            }
        }
    }

    private fun handleImport(intent: Intent) {
        notifyTaskStarted(LocalAction.ACTION_IMPORT_SERVICE_STARTED)
        try {
            // extract parameters from the intent
            val dataFormat = intent.getSerializableExtra(FORMAT) as? DataFormat
                ?: throw IllegalArgumentException("parameter is null [FORMAT]")
            val file = intent.getSerializableExtra(FILE) as? File
            if (file == null || !file.exists()) {
                throw IllegalArgumentException("parameter is null or not a file [FILE]")
            }
            val mapping = intent.getSerializableExtra(MAPPING) as? CsvImportMapping
            // initialize the correct data importer
            val dataImporter = getDataImporter(dataFormat, file, mapping)
            val roundedAmounts: Int
            val alreadySavedRows: Int
            try {
                dataImporter.importData()
                roundedAmounts = dataImporter.getRoundedAmounts()
                alreadySavedRows = dataImporter.alreadySavedRows
            } finally {
                dataImporter.close()
            }
            notifyTaskFinished(LocalAction.ACTION_IMPORT_SERVICE_FINISHED, roundedAmounts, alreadySavedRows)
        } catch (e: Exception) {
            notifyTaskFailed(LocalAction.ACTION_IMPORT_SERVICE_FAILED, e)
        }
    }

    private fun handleExport(intent: Intent) {
        notifyTaskStarted(LocalAction.ACTION_EXPORT_SERVICE_STARTED)
        try {
            // extract parameters from the intent
            val dataFormat = intent.getSerializableExtra(FORMAT) as? DataFormat
                ?: throw IllegalArgumentException("parameter is null [FORMAT]")
            val startDate = intent.getSerializableExtra(START_DATE) as? Date
            val endDate = intent.getSerializableExtra(END_DATE) as? Date
            val wallets = getWalletList(intent, WALLETS)
            if (wallets == null || wallets.isEmpty()) {
                throw IllegalArgumentException("parameter is null or empty [WALLETS]")
            }
            val folder = intent.getSerializableExtra(FOLDER) as? File
            if (folder == null || !folder.isDirectory) {
                throw IllegalArgumentException("parameter is null or not a directory [FOLDER]")
            }
            val uniqueWallet = intent.getBooleanExtra(UNIQUE_WALLET, false)
            val optionalColumns = intent.getStringArrayExtra(OPTIONAL_COLUMNS)
            val dataExporter = exportTransactions(
                this,
                dataFormat,
                folder,
                startDate,
                endDate,
                wallets,
                uniqueWallet,
                optionalColumns
            )
            // if no exception has been thrown so far, we can ask the exporter
            // for the output file: we can pass the uri of this file inside the intent
            // Uri resultUri = Uri.fromFile(dataExporter.getOutputFile());
            val authority = "$packageName.storage.file"
            val outputFile = dataExporter.getOutputFile()
            val resultUri = FileProvider.getUriForFile(this, authority, outputFile)
            val resultType = dataExporter.getResultType()
            // send a successful intent to inform the receivers that the operation succeed
            notifyTaskFinished(LocalAction.ACTION_EXPORT_SERVICE_FINISHED, resultUri, resultType)
        } catch (e: Exception) {
            notifyTaskFailed(LocalAction.ACTION_EXPORT_SERVICE_FAILED, e)
        }
    }

    private fun getWalletList(intent: Intent, key: String): Array<Wallet>? {
        val parcelableArray = intent.getParcelableArrayExtra(key) ?: return null
        val wallets = ArrayList<Wallet>(parcelableArray.size)
        for (item in parcelableArray) {
            val wallet = item as? Wallet
                ?: throw IllegalArgumentException("Invalid element in [$key]")
            wallets.add(wallet)
        }
        return wallets.toTypedArray()
    }

    private fun notifyTaskStarted(action: String) {
        val extras = Bundle()
        extras.putString(TOKEN, mToken)
        getReporter().broadcast(action, extras)
    }

    private fun notifyTaskFinished(action: String, roundedAmounts: Int, alreadySavedRows: Int) {
        val extras = Bundle()
        extras.putInt(ROUNDED_AMOUNTS, roundedAmounts)
        extras.putInt(ALREADY_SAVED_ROWS, alreadySavedRows)
        extras.putString(TOKEN, mToken)
        getReporter().broadcast(action, extras)
    }

    private fun notifyTaskFinished(action: String, resultUri: Uri, resultType: String) {
        val extras = Bundle()
        extras.putParcelable(RESULT_FILE_URI, resultUri)
        extras.putString(RESULT_FILE_TYPE, resultType)
        extras.putString(TOKEN, mToken)
        getReporter().broadcast(action, extras)
    }

    private fun notifyTaskFailed(action: String, exception: Exception) {
        val extras = Bundle()
        extras.putSerializable(EXCEPTION, exception)
        extras.putString(TOKEN, mToken)
        getReporter().broadcast(action, extras)
    }

    @Throws(IOException::class)
    private fun getDataImporter(
        dataFormat: DataFormat,
        file: File,
        mapping: CsvImportMapping?
    ): AbstractDataImporter {
        return when (dataFormat) {
            DataFormat.CSV -> CSVDataImporter(this, file, mapping)
            else -> throw RuntimeException("DataFormat not supported")
        }
    }
}
