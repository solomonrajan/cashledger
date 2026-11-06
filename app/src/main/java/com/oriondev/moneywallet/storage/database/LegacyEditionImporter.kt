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

package com.oriondev.moneywallet.storage.database

import android.content.ContentResolver
import android.content.Context
import com.oriondev.moneywallet.storage.database.legacy.LegacyDatabaseImporter
import com.oriondev.moneywallet.storage.database.legacy.LegacyUserPreferences
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException

/**
 * Created by andrea on 10/08/18.
 */
class LegacyEditionImporter @Throws(ImportException::class) constructor(context: Context) {

    companion object {
        const val DATABASE_NAME: String = "MoneyWallet"
        private const val LEGACY_IMAGE_FOLDER: String = "images"
        private const val ATTACHMENT_FOLDER: String = "attachments"
    }

    private val mContext: Context = context
    private val mDatabaseImporter: LegacyDatabaseImporter

    init {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        mDatabaseImporter = LegacyDatabaseImporter(databaseFile)
    }

    /**
     * The rows go in inside one transaction, so a failure that reaches this method leaves the
     * ledger as it was instead of half merged. There is no copy of it to fall back on here, since
     * this one merges into the database in place and never moves it aside.
     *
     * What that does not cover, because it is worth knowing before trusting the sentence above.
     * A row the current schema will not take throws out of the insert, checked on an emulator and
     * not reasoned about, and every insert in LegacyDatabaseImporter sits in its own catch that
     * drops it. So that row is skipped, the upgrade carries on and reports success, and only a
     * failure that gets past those catches reaches the rollback. Moving the attachment files is a
     * second step that runs after this one has committed, so its failures cannot be undone either;
     * the attachment rows are in here with everything else.
     *
     * The legacy file is closed and deleted after the transaction and not inside it. Deleting it
     * is the one step here that cannot be undone, and a commit that failed with the delete already
     * done would take the data the upgrade was reading from with it.
     */
    @Throws(ImportException::class)
    fun importDatabase() {
        val contentResolver = mContext.contentResolver
        try {
            DataContentProvider.runInOneTransaction(mContext) {
                importRows(contentResolver)
                null
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw ImportException(e.message)
        }
        // close the database file in order to delete the file on disk
        mDatabaseImporter.close()
        mContext.deleteDatabase(DATABASE_NAME)
    }

    @Throws(ImportException::class)
    private fun importRows(contentResolver: ContentResolver) {
        mDatabaseImporter.importWallets(contentResolver)
        mDatabaseImporter.importCategories(contentResolver)
        mDatabaseImporter.importEvents(contentResolver)
        mDatabaseImporter.importPlaces(contentResolver)
        mDatabaseImporter.importPeople(contentResolver)
        mDatabaseImporter.importEventPeople(contentResolver)
        mDatabaseImporter.importDebts(contentResolver)
        mDatabaseImporter.importDebtPeople(contentResolver)
        mDatabaseImporter.importBudgets(contentResolver)
        mDatabaseImporter.importBudgetWallets(contentResolver)
        mDatabaseImporter.importBudgetCategories(contentResolver)
        mDatabaseImporter.importCategoryRules(contentResolver)
        mDatabaseImporter.importSavings(contentResolver)
        mDatabaseImporter.importRecurrentTransactions(contentResolver)
        mDatabaseImporter.importRecurrentTransfers(contentResolver)
        mDatabaseImporter.importTransactions(contentResolver)
        mDatabaseImporter.importTransactionPeople(contentResolver)
        mDatabaseImporter.importTransactionModels(contentResolver)
        mDatabaseImporter.importTransfers(contentResolver)
        mDatabaseImporter.importTransferPeople(contentResolver)
        mDatabaseImporter.importTransferModels(contentResolver)
        mDatabaseImporter.importAttachments(contentResolver)
        mDatabaseImporter.importTransactionAttachments(contentResolver)
        mDatabaseImporter.importTransferAttachments(contentResolver)
    }

    @Throws(ImportException::class)
    fun importAttachments() {
        val baseFolder = mContext.getExternalFilesDir(null)
        val imageFolder = File(baseFolder, LEGACY_IMAGE_FOLDER)
        val attachmentFolder = File(baseFolder, ATTACHMENT_FOLDER)
        if (imageFolder.exists()) {
            val children = imageFolder.listFiles()
            if (children != null && children.isNotEmpty()) {
                if (!attachmentFolder.exists()) {
                    try {
                        FileUtils.forceMkdir(attachmentFolder)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                val contentResolver = mContext.contentResolver
                for (child in children) {
                    val uniqueId = mDatabaseImporter.getAttachmentId(child.name)
                    if (uniqueId != null) {
                        val attachment = File(attachmentFolder, uniqueId)
                        try {
                            FileUtils.moveFile(child, attachment)
                        } catch (e: IOException) {
                            throw ImportException(e.message)
                        }
                        mDatabaseImporter.importAttachment(contentResolver, uniqueId, attachment.length())
                    }
                }
            }
            FileUtils.deleteQuietly(imageFolder)
        }
    }

    @Throws(ImportException::class)
    fun importPreferences() {
        val legacyUserPreferences = LegacyUserPreferences(mContext)
        val dateFormat = legacyUserPreferences.dateFormat
        val colorIncomes = legacyUserPreferences.colorIn
        val colorExpenses = legacyUserPreferences.colorOut
        val groupType = legacyUserPreferences.groupType
        val firstDayOfMonth = legacyUserPreferences.firstDayOfMonth
        val firstDayOfWeek = legacyUserPreferences.firstDayOfWeek
        val reminderEnabled = legacyUserPreferences.isReminderEnabled
        val reminderHour = legacyUserPreferences.reminderHour
        val currencyEnabled = legacyUserPreferences.isCurrencySymbolEnabled
        val groupDigitsEnabled = legacyUserPreferences.isGroupDigitsEnabled
        val roundDecimalsEnabled = legacyUserPreferences.isRoundDecimalsEnabled

        legacyUserPreferences.destroy()

        PreferenceManager.setDateFormat(dateFormat)
        PreferenceManager.setCurrentIncomeColor(colorIncomes)
        PreferenceManager.setCurrentExpenseColor(colorExpenses)
        PreferenceManager.setCurrentGroupType(groupType)
        PreferenceManager.setCurrentFirstDayOfMonth(firstDayOfMonth)
        PreferenceManager.setCurrentFirstDayOfWeek(firstDayOfWeek)
        if (reminderEnabled) {
            PreferenceManager.setCurrentDailyReminder(mContext, reminderHour)
        } else {
            PreferenceManager.setCurrentDailyReminder(mContext, PreferenceManager.DAILY_REMINDER_DISABLED)
        }
        PreferenceManager.setCurrencyEnabled(currencyEnabled)
        PreferenceManager.setGroupDigitsEnabled(groupDigitsEnabled)
        PreferenceManager.setRoundDecimalsEnabled(roundDecimalsEnabled)
        PreferenceManager.setIsFirstStartDone(true)
    }
}
