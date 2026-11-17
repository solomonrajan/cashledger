/*
 * Copyright (c) 2026 solomonrajan.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.storage.database.json

import android.database.Cursor
import com.oriondev.moneywallet.storage.database.DatabaseExporter
import com.oriondev.moneywallet.storage.database.ExportException
import com.oriondev.moneywallet.storage.database.SQLDatabaseExporter
import java.io.IOException
import java.io.OutputStream
import org.json.JSONException
import org.json.JSONObject

class JSONDatabaseExporter @Throws(ExportException::class) constructor(
    outputStream: OutputStream
) : DatabaseExporter {

    private val writer: JSONDataStreamWriter
    private val factory: JSONDataOutputFactory

    init {
        try {
            writer = JSONDataStreamWriter(outputStream)
            factory = JSONDataOutputFactory()
        } catch (e: IOException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportHeader() {
        try {
            writer.writeName(JSONDatabase.Header.OBJECT)
            val `object` = JSONObject()
            `object`.put(JSONDatabase.Header.VERSION_CODE, JSONDatabase.VERSION)
            writer.writeJSONObject(`object`)
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportCurrencies(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Currency.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val currency = SQLDatabaseExporter.getCurrency(cursor)
                    if (currency != null) {
                        val `object` = factory.getObject(currency)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportWallets(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Wallet.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val wallet = SQLDatabaseExporter.getWallet(cursor)
                    if (wallet != null) {
                        val `object` = factory.getObject(wallet)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportCategories(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Category.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val category = SQLDatabaseExporter.getCategory(cursor)
                    if (category != null) {
                        val `object` = factory.getObject(category)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportEvents(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Event.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val event = SQLDatabaseExporter.getEvent(cursor)
                    if (event != null) {
                        val `object` = factory.getObject(event)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportPlaces(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Place.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val place = SQLDatabaseExporter.getPlace(cursor)
                    if (place != null) {
                        val `object` = factory.getObject(place)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportPeople(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Person.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val person = SQLDatabaseExporter.getPerson(cursor)
                    if (person != null) {
                        val `object` = factory.getObject(person)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportEventPeople(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.EventPeople.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val eventPerson = SQLDatabaseExporter.getEventPerson(cursor)
                    if (eventPerson != null) {
                        val `object` = factory.getObject(eventPerson)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportDebts(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Debt.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val debt = SQLDatabaseExporter.getDebt(cursor)
                    if (debt != null) {
                        val `object` = factory.getObject(debt)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportDebtPeople(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.DebtPeople.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val debtPerson = SQLDatabaseExporter.getDebtPerson(cursor)
                    if (debtPerson != null) {
                        val `object` = factory.getObject(debtPerson)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportBudgets(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Budget.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val budget = SQLDatabaseExporter.getBudget(cursor)
                    if (budget != null) {
                        val `object` = factory.getObject(budget)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportBudgetWallets(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.BudgetWallet.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val budgetWallet = SQLDatabaseExporter.getBudgetWallet(cursor)
                    if (budgetWallet != null) {
                        val `object` = factory.getObject(budgetWallet)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportBudgetCategories(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.BudgetCategory.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val budgetCategory = SQLDatabaseExporter.getBudgetCategory(cursor)
                    if (budgetCategory != null) {
                        val `object` = factory.getObject(budgetCategory)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportCategoryRules(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.CategoryRule.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val categoryRule = SQLDatabaseExporter.getCategoryRule(cursor)
                    if (categoryRule != null) {
                        val `object` = factory.getObject(categoryRule)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportSavings(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Saving.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val saving = SQLDatabaseExporter.getSaving(cursor)
                    if (saving != null) {
                        val `object` = factory.getObject(saving)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportRecurrentTransactions(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.RecurrentTransaction.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val recurrentTransaction = SQLDatabaseExporter.getRecurrentTransaction(cursor)
                    if (recurrentTransaction != null) {
                        val `object` = factory.getObject(recurrentTransaction)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportRecurrentTransfers(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.RecurrentTransfer.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val recurrentTransfer = SQLDatabaseExporter.getRecurrentTransfer(cursor)
                    if (recurrentTransfer != null) {
                        val `object` = factory.getObject(recurrentTransfer)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportTransactions(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Transaction.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val transaction = SQLDatabaseExporter.getTransaction(cursor)
                    if (transaction != null) {
                        val `object` = factory.getObject(transaction)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportTransactionPeople(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.TransactionPeople.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val transactionPerson = SQLDatabaseExporter.getTransactionPerson(cursor)
                    if (transactionPerson != null) {
                        val `object` = factory.getObject(transactionPerson)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportTransactionModels(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.TransactionModel.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val transactionModel = SQLDatabaseExporter.getTransactionModel(cursor)
                    if (transactionModel != null) {
                        val `object` = factory.getObject(transactionModel)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportTransfers(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Transfer.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val transfer = SQLDatabaseExporter.getTransfer(cursor)
                    if (transfer != null) {
                        val `object` = factory.getObject(transfer)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportTransferPeople(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.TransferPeople.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val transferPerson = SQLDatabaseExporter.getTransferPerson(cursor)
                    if (transferPerson != null) {
                        val `object` = factory.getObject(transferPerson)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportTransferModels(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.TransferModel.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val transferModel = SQLDatabaseExporter.getTransferModel(cursor)
                    if (transferModel != null) {
                        val `object` = factory.getObject(transferModel)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportAttachments(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.Attachment.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val attachment = SQLDatabaseExporter.getAttachment(cursor)
                    if (attachment != null) {
                        val `object` = factory.getObject(attachment)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportTransactionAttachments(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.TransactionAttachment.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val transactionAttachment = SQLDatabaseExporter.getTransactionAttachment(cursor)
                    if (transactionAttachment != null) {
                        val `object` = factory.getObject(transactionAttachment)
                        writer.writeJSONObject(`object`)
                    }
                }
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun exportTransferAttachments(cursor: Cursor?) {
        try {
            writer.writeName(JSONDatabase.TransferAttachment.ARRAY)
            writer.beginArray()
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    val transferAttachment = SQLDatabaseExporter.getTransferAttachment(cursor)
                    if (transferAttachment != null) {
                        val `object` = factory.getObject(transferAttachment)
                        writer.writeJSONObject(`object`)
                    }
                }
                cursor.close()
            }
            writer.endArray()
        } catch (e: IOException) {
            throw ExportException(e.message)
        } catch (e: JSONException) {
            throw ExportException(e.message)
        }
    }

    @Throws(ExportException::class)
    override fun close() {
        try {
            writer.close()
        } catch (e: IOException) {
            throw ExportException(e.message)
        }
    }
}

