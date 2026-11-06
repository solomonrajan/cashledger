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

import android.content.ContentResolver
import com.oriondev.moneywallet.storage.database.DatabaseImporter
import com.oriondev.moneywallet.storage.database.ImportException
import com.oriondev.moneywallet.storage.database.SQLDatabaseImporter
import java.io.IOException
import java.io.InputStream
import org.json.JSONException

/**
 * This class is an implementation of DatabaseImporter and it is able to read and parse a stream
 * of JSON data. The JSON data must be well formatted and ordered because it is parsed on the
 * fly to avoid huge memory allocations when the backup file is very large.
 */
class JSONDatabaseImporter @Throws(ImportException::class) constructor(
    inputStream: InputStream
) : DatabaseImporter {

    private val reader: JSONDataStreamReader
    private val factory: JSONDataInputFactory
    private var version: Int = 0

    init {
        try {
            reader = JSONDataStreamReader(inputStream)
            factory = JSONDataInputFactory()
        } catch (e: IOException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importHeader() {
        try {
            if (JSONDatabase.Header.OBJECT == reader.readName()) {
                val `object` = reader.readObject()
                version = `object`.getInt(JSONDatabase.Header.VERSION_CODE)
                if (version > JSONDatabase.MAX_SUPPORTED_VERSION) {
                    throw ImportException(
                        "This backup belongs to a newer version of the application and cannot be imported. Please update the application to restore it."
                    )
                } else if (version < JSONDatabase.MIN_SUPPORTED_VERSION) {
                    throw ImportException(
                        "This backup is too old and no more supported by this version of the application. It cannot be restored."
                    )
                }
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importCurrencies(contentResolver: ContentResolver) {
        // currencies are stored starting from backup version >= 2
        if (version >= 2) {
            try {
                if (JSONDatabase.Currency.ARRAY == reader.readName()) {
                    reader.beginArray()
                    while (reader.hasArrayAnotherObject()) {
                        val `object` = reader.readObject()
                        val currency = factory.getCurrency(`object`)
                        SQLDatabaseImporter.insert(contentResolver, currency)
                    }
                    reader.endArray()
                } else {
                    throw ImportException("Wrong array name (expected = 'currencies')")
                }
            } catch (e: IOException) {
                throw ImportException(e.message)
            } catch (e: JSONException) {
                throw ImportException(e.message)
            }
        }
    }

    @Throws(ImportException::class)
    override fun importWallets(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Wallet.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val wallet = factory.getWallet(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, wallet)
                    factory.cacheWallet(wallet.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'wallets')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importCategories(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Category.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val category = factory.getCategory(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, category)
                    factory.cacheCategory(category.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'categories')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importEvents(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Event.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val event = factory.getEvent(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, event)
                    factory.cacheEvent(event.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'events')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importPlaces(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Place.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val place = factory.getPlace(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, place)
                    factory.cachePlace(place.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'places')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importPeople(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Person.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val person = factory.getPerson(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, person)
                    factory.cachePerson(person.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'people')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importEventPeople(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.EventPeople.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val eventPerson = factory.getEventPerson(`object`)
                    SQLDatabaseImporter.insert(contentResolver, eventPerson)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'event_people')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importDebts(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Debt.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val debt = factory.getDebt(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, debt)
                    factory.cacheDebt(debt.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'debts')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importDebtPeople(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.DebtPeople.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val debtPerson = factory.getDebtPerson(`object`)
                    SQLDatabaseImporter.insert(contentResolver, debtPerson)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'debt_people')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importBudgets(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Budget.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val budget = factory.getBudget(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, budget)
                    factory.cacheBudget(budget.mUUID, id)
                    if (version < 3) {
                        SQLDatabaseImporter.insertBudgetCategory(contentResolver, id, budget.mCategory)
                    }
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'budgets')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importBudgetWallets(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.BudgetWallet.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val budgetWallet = factory.getBudgetWallet(`object`)
                    SQLDatabaseImporter.insert(contentResolver, budgetWallet)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'budget_wallets')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importBudgetCategories(contentResolver: ContentResolver) {
        // budget categories are stored starting from backup version >= 3
        if (version >= 3) {
            try {
                if (JSONDatabase.BudgetCategory.ARRAY == reader.readName()) {
                    reader.beginArray()
                    while (reader.hasArrayAnotherObject()) {
                        val `object` = reader.readObject()
                        val budgetCategory = factory.getBudgetCategory(`object`)
                        SQLDatabaseImporter.insert(contentResolver, budgetCategory)
                    }
                    reader.endArray()
                } else {
                    throw ImportException("Wrong array name (expected = 'budget_categories')")
                }
            } catch (e: IOException) {
                throw ImportException(e.message)
            } catch (e: JSONException) {
                throw ImportException(e.message)
            }
        }
    }

    @Throws(ImportException::class)
    override fun importCategoryRules(contentResolver: ContentResolver) {
        // category rules are stored starting from backup version >= 4
        if (version >= 4) {
            try {
                if (JSONDatabase.CategoryRule.ARRAY == reader.readName()) {
                    reader.beginArray()
                    while (reader.hasArrayAnotherObject()) {
                        val `object` = reader.readObject()
                        val categoryRule = factory.getCategoryRule(`object`)
                        SQLDatabaseImporter.insert(contentResolver, categoryRule)
                    }
                    reader.endArray()
                } else {
                    throw ImportException("Wrong array name (expected = 'category_rules')")
                }
            } catch (e: IOException) {
                throw ImportException(e.message)
            } catch (e: JSONException) {
                throw ImportException(e.message)
            }
        }
    }

    @Throws(ImportException::class)
    override fun importSavings(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Saving.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val saving = factory.getSaving(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, saving)
                    factory.cacheSaving(saving.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'savings')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importRecurrentTransactions(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.RecurrentTransaction.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transaction = factory.getRecurrentTransaction(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, transaction)
                    factory.cacheRecurrentTransaction(transaction.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'recurrent_transactions')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importRecurrentTransfers(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.RecurrentTransfer.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transfer = factory.getRecurrentTransfer(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, transfer)
                    factory.cacheRecurrentTransfer(transfer.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'recurrent_transfers')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importTransactions(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Transaction.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transaction = factory.getTransaction(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, transaction)
                    factory.cacheTransaction(transaction.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'transactions')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importTransactionPeople(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.TransactionPeople.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transactionPerson = factory.getTransactionPerson(`object`)
                    SQLDatabaseImporter.insert(contentResolver, transactionPerson)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'transaction_people')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importTransactionModels(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.TransactionModel.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transactionModel = factory.getTransactionModel(`object`)
                    SQLDatabaseImporter.insert(contentResolver, transactionModel)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'transaction_models')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importTransfers(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Transfer.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transfer = factory.getTransfer(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, transfer)
                    factory.cacheTransfer(transfer.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'transfers')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importTransferPeople(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.TransferPeople.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transferPerson = factory.getTransferPerson(`object`)
                    SQLDatabaseImporter.insert(contentResolver, transferPerson)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'transfer_people')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importTransferModels(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.TransferModel.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transferModel = factory.getTransferModel(`object`)
                    SQLDatabaseImporter.insert(contentResolver, transferModel)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'transfer_models')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importAttachments(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.Attachment.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val attachment = factory.getAttachment(`object`)
                    val id = SQLDatabaseImporter.insert(contentResolver, attachment)
                    factory.cacheAttachment(attachment.mUUID, id)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'attachments')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importTransactionAttachments(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.TransactionAttachment.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transactionAttachment = factory.getTransactionAttachment(`object`)
                    SQLDatabaseImporter.insert(contentResolver, transactionAttachment)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'transaction_attachments')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun importTransferAttachments(contentResolver: ContentResolver) {
        try {
            if (JSONDatabase.TransferAttachment.ARRAY == reader.readName()) {
                reader.beginArray()
                while (reader.hasArrayAnotherObject()) {
                    val `object` = reader.readObject()
                    val transferAttachment = factory.getTransferAttachment(`object`)
                    SQLDatabaseImporter.insert(contentResolver, transferAttachment)
                }
                reader.endArray()
            } else {
                throw ImportException("Wrong array name (expected = 'transfer_attachments')")
            }
        } catch (e: IOException) {
            throw ImportException(e.message)
        } catch (e: JSONException) {
            throw ImportException(e.message)
        }
    }

    @Throws(ImportException::class)
    override fun close() {
        try {
            reader.close()
        } catch (e: IOException) {
            throw ImportException(e.message)
        }
    }
}
