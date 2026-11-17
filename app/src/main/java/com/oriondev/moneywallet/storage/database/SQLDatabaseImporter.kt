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
import android.content.ContentUris
import android.content.ContentValues
import com.oriondev.moneywallet.storage.database.model.Attachment
import com.oriondev.moneywallet.storage.database.model.Budget
import com.oriondev.moneywallet.storage.database.model.BudgetCategory
import com.oriondev.moneywallet.storage.database.model.BudgetWallet
import com.oriondev.moneywallet.storage.database.model.Category
import com.oriondev.moneywallet.storage.database.model.CategoryRule
import com.oriondev.moneywallet.storage.database.model.Currency
import com.oriondev.moneywallet.storage.database.model.Debt
import com.oriondev.moneywallet.storage.database.model.DebtPerson
import com.oriondev.moneywallet.storage.database.model.Event
import com.oriondev.moneywallet.storage.database.model.EventPerson
import com.oriondev.moneywallet.storage.database.model.Person
import com.oriondev.moneywallet.storage.database.model.Place
import com.oriondev.moneywallet.storage.database.model.RecurrentTransaction
import com.oriondev.moneywallet.storage.database.model.RecurrentTransfer
import com.oriondev.moneywallet.storage.database.model.Saving
import com.oriondev.moneywallet.storage.database.model.Transaction
import com.oriondev.moneywallet.storage.database.model.TransactionAttachment
import com.oriondev.moneywallet.storage.database.model.TransactionModel
import com.oriondev.moneywallet.storage.database.model.TransactionPerson
import com.oriondev.moneywallet.storage.database.model.Transfer
import com.oriondev.moneywallet.storage.database.model.TransferAttachment
import com.oriondev.moneywallet.storage.database.model.TransferModel
import com.oriondev.moneywallet.storage.database.model.TransferPerson
import com.oriondev.moneywallet.storage.database.model.Wallet
import java.util.UUID

/**
 * Created by andrea on 27/10/18.
 */
object SQLDatabaseImporter {

    const val DATABASE_NAME: String = "database"

    /**
     * The file a restore builds its import in, beside the live database so that putting it in
     * place is a rename within one folder and not a copy across volumes.
     */
    const val STAGING_DATABASE_NAME: String = "database.staging"

    @JvmStatic
    fun insert(contentResolver: ContentResolver, currency: Currency): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Currency.ISO, currency.mIso)
            put(Schema.Currency.NAME, currency.mName)
            put(Schema.Currency.SYMBOL, currency.mSymbol)
            put(Schema.Currency.DECIMALS, currency.mDecimals)
            put(Schema.Currency.FAVOURITE, currency.mFavourite)
            put(Schema.Currency.UUID, currency.mUUID)
            put(Schema.Currency.LAST_EDIT, currency.mLastEdit)
            put(Schema.Currency.DELETED, currency.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_CURRENCIES, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, wallet: Wallet): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Wallet.NAME, wallet.mName)
            put(Schema.Wallet.ICON, wallet.mIcon)
            put(Schema.Wallet.CURRENCY, wallet.mCurrency)
            put(Schema.Wallet.START_MONEY, wallet.mStartMoney)
            put(Schema.Wallet.COUNT_IN_TOTAL, wallet.mCountInTotal)
            put(Schema.Wallet.ARCHIVED, wallet.mArchived)
            put(Schema.Wallet.NOTE, wallet.mNote)
            put(Schema.Wallet.INDEX, wallet.mIndex)
            put(Schema.Wallet.TAG, wallet.mTag)
            put(Schema.Wallet.UUID, wallet.mUUID)
            put(Schema.Wallet.LAST_EDIT, wallet.mLastEdit)
            put(Schema.Wallet.DELETED, wallet.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_WALLETS, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, category: Category): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Category.NAME, category.mName)
            put(Schema.Category.ICON, category.mIcon)
            put(Schema.Category.TYPE, category.mType)
            put(Schema.Category.PARENT, category.mParent)
            put(Schema.Category.SHOW_REPORT, category.mShowReport)
            put(Schema.Category.INDEX, category.mIndex)
            put(Schema.Category.TAG, category.mTag)
            put(Schema.Category.UUID, category.mUUID)
            put(Schema.Category.LAST_EDIT, category.mLastEdit)
            put(Schema.Category.DELETED, category.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_CATEGORIES, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, event: Event): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Event.NAME, event.mName)
            put(Schema.Event.ICON, event.mIcon)
            put(Schema.Event.NOTE, event.mNote)
            put(Schema.Event.START_DATE, event.mStartDate)
            put(Schema.Event.END_DATE, event.mEndDate)
            put(Schema.Event.TAG, event.mTag)
            put(Schema.Event.UUID, event.mUUID)
            put(Schema.Event.LAST_EDIT, event.mLastEdit)
            put(Schema.Event.DELETED, event.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_EVENTS, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, categoryRule: CategoryRule): Long {
        val contentValues = ContentValues().apply {
            put(Schema.CategoryRule.PATTERN, categoryRule.mPattern)
            put(Schema.CategoryRule.CATEGORY, categoryRule.mCategory)
            put(Schema.CategoryRule.INDEX, categoryRule.mIndex)
            put(Schema.CategoryRule.UUID, categoryRule.mUUID)
            put(Schema.CategoryRule.LAST_EDIT, categoryRule.mLastEdit)
            put(Schema.CategoryRule.DELETED, categoryRule.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_CATEGORY_RULES, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, place: Place): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Place.NAME, place.mName)
            put(Schema.Place.ICON, place.mIcon)
            put(Schema.Place.ADDRESS, place.mAddress)
            put(Schema.Place.LATITUDE, place.mLatitude)
            put(Schema.Place.LONGITUDE, place.mLongitude)
            put(Schema.Place.TAG, place.mTag)
            put(Schema.Place.UUID, place.mUUID)
            put(Schema.Place.LAST_EDIT, place.mLastEdit)
            put(Schema.Place.DELETED, place.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_PLACES, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, person: Person): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Person.NAME, person.mName)
            put(Schema.Person.ICON, person.mIcon)
            put(Schema.Person.NOTE, person.mNote)
            put(Schema.Person.TAG, person.mTag)
            put(Schema.Person.UUID, person.mUUID)
            put(Schema.Person.LAST_EDIT, person.mLastEdit)
            put(Schema.Person.DELETED, person.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_PEOPLE, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, eventPerson: EventPerson): Long {
        val contentValues = ContentValues().apply {
            put(Schema.EventPeople.EVENT, eventPerson.mEvent)
            put(Schema.EventPeople.PERSON, eventPerson.mPerson)
            put(Schema.EventPeople.UUID, eventPerson.mUUID)
            put(Schema.EventPeople.LAST_EDIT, eventPerson.mLastEdit)
            put(Schema.EventPeople.DELETED, eventPerson.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_EVENT_PEOPLE, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, debt: Debt): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Debt.TYPE, debt.mType)
            put(Schema.Debt.ICON, debt.mIcon)
            put(Schema.Debt.DESCRIPTION, debt.mDescription)
            put(Schema.Debt.DATE, debt.mDate)
            put(Schema.Debt.EXPIRATION_DATE, debt.mExpirationDate)
            put(Schema.Debt.WALLET, debt.mWallet)
            put(Schema.Debt.NOTE, debt.mNote)
            put(Schema.Debt.PLACE, debt.mPlace)
            put(Schema.Debt.MONEY, debt.mMoney)
            put(Schema.Debt.ARCHIVED, debt.mArchived)
            put(Schema.Debt.TAG, debt.mTag)
            put(Schema.Debt.UUID, debt.mUUID)
            put(Schema.Debt.LAST_EDIT, debt.mLastEdit)
            put(Schema.Debt.DELETED, debt.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_DEBT, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, debtPerson: DebtPerson): Long {
        val contentValues = ContentValues().apply {
            put(Schema.DebtPeople.DEBT, debtPerson.mDebt)
            put(Schema.DebtPeople.PERSON, debtPerson.mPerson)
            put(Schema.DebtPeople.UUID, debtPerson.mUUID)
            put(Schema.DebtPeople.LAST_EDIT, debtPerson.mLastEdit)
            put(Schema.DebtPeople.DELETED, debtPerson.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_DEBT_PEOPLE, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, budget: Budget): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Budget.TYPE, budget.mType)
            put(Schema.Budget.CATEGORY, budget.mCategory)
            put(Schema.Budget.START_DATE, budget.mStartDate)
            put(Schema.Budget.END_DATE, budget.mEndDate)
            put(Schema.Budget.MONEY, budget.mMoney)
            put(Schema.Budget.CURRENCY, budget.mCurrency)
            put(Schema.Budget.RULE, budget.mRule)
            put(Schema.Budget.RULE_START, budget.mRuleStart)
            put(Schema.Budget.TAG, budget.mTag)
            put(Schema.Budget.UUID, budget.mUUID)
            put(Schema.Budget.LAST_EDIT, budget.mLastEdit)
            put(Schema.Budget.DELETED, budget.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_BUDGET, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    /**
     * Puts the single category a budget carries into the join table. A backup written before the
     * budget_categories array existed, and the legacy database, both name one category on the
     * budget row itself, and the queries match on the join table alone, so a restore that skipped
     * this would leave every category budget matching nothing at all.
     *
     * @param contentResolver resolver the restore is writing through.
     * @param budgetId id the budget was given by this restore.
     * @param categoryId category it names, ignored when null.
     */
    @JvmStatic
    fun insertBudgetCategory(contentResolver: ContentResolver, budgetId: Long, categoryId: Long?) {
        if (categoryId == null) {
            return
        }
        val budgetCategory = BudgetCategory().apply {
            mBudget = budgetId
            mCategory = categoryId
            mUUID = UUID.randomUUID().toString()
            mLastEdit = System.currentTimeMillis()
            mDeleted = false
        }
        insert(contentResolver, budgetCategory)
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, budgetCategory: BudgetCategory): Long {
        val contentValues = ContentValues().apply {
            put(Schema.BudgetCategory.BUDGET, budgetCategory.mBudget)
            put(Schema.BudgetCategory.CATEGORY, budgetCategory.mCategory)
            put(Schema.BudgetCategory.UUID, budgetCategory.mUUID)
            put(Schema.BudgetCategory.LAST_EDIT, budgetCategory.mLastEdit)
            put(Schema.BudgetCategory.DELETED, budgetCategory.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_BUDGET_CATEGORY, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, budgetWallet: BudgetWallet): Long {
        val contentValues = ContentValues().apply {
            put(Schema.BudgetWallet.BUDGET, budgetWallet.mBudget)
            put(Schema.BudgetWallet.WALLET, budgetWallet.mWallet)
            put(Schema.BudgetWallet.UUID, budgetWallet.mUUID)
            put(Schema.BudgetWallet.LAST_EDIT, budgetWallet.mLastEdit)
            put(Schema.BudgetWallet.DELETED, budgetWallet.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_BUDGET_WALLET, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, saving: Saving): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Saving.DESCRIPTION, saving.mDescription)
            put(Schema.Saving.ICON, saving.mIcon)
            put(Schema.Saving.START_MONEY, saving.mStartMoney)
            put(Schema.Saving.END_MONEY, saving.mEndMoney)
            put(Schema.Saving.WALLET, saving.mWallet)
            put(Schema.Saving.END_DATE, saving.mEndDate)
            put(Schema.Saving.COMPLETE, saving.mComplete)
            put(Schema.Saving.NOTE, saving.mNote)
            put(Schema.Saving.TAG, saving.mTag)
            put(Schema.Saving.UUID, saving.mUUID)
            put(Schema.Saving.LAST_EDIT, saving.mLastEdit)
            put(Schema.Saving.DELETED, saving.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_SAVING, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, recurrentTransaction: RecurrentTransaction): Long {
        val contentValues = ContentValues().apply {
            put(Schema.RecurrentTransaction.MONEY, recurrentTransaction.mMoney)
            put(Schema.RecurrentTransaction.DESCRIPTION, recurrentTransaction.mDescription)
            put(Schema.RecurrentTransaction.CATEGORY, recurrentTransaction.mCategory)
            put(Schema.RecurrentTransaction.DIRECTION, recurrentTransaction.mDirection)
            put(Schema.RecurrentTransaction.WALLET, recurrentTransaction.mWallet)
            put(Schema.RecurrentTransaction.PLACE, recurrentTransaction.mPlace)
            put(Schema.RecurrentTransaction.NOTE, recurrentTransaction.mNote)
            put(Schema.RecurrentTransaction.EVENT, recurrentTransaction.mEvent)
            put(Schema.RecurrentTransaction.CONFIRMED, recurrentTransaction.mConfirmed)
            put(Schema.RecurrentTransaction.COUNT_IN_TOTAL, recurrentTransaction.mCountInTotal)
            put(Schema.RecurrentTransaction.START_DATE, recurrentTransaction.mStartDate)
            put(Schema.RecurrentTransaction.LAST_OCCURRENCE, recurrentTransaction.mLastOccurrence)
            put(Schema.RecurrentTransaction.NEXT_OCCURRENCE, recurrentTransaction.mNextOccurrence)
            put(Schema.RecurrentTransaction.RULE, recurrentTransaction.mRule)
            put(Schema.RecurrentTransaction.TAG, recurrentTransaction.mTag)
            put(Schema.RecurrentTransaction.UUID, recurrentTransaction.mUUID)
            put(Schema.RecurrentTransaction.LAST_EDIT, recurrentTransaction.mLastEdit)
            put(Schema.RecurrentTransaction.DELETED, recurrentTransaction.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_RECURRENT_TRANSACTION, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, recurrentTransfer: RecurrentTransfer): Long {
        val contentValues = ContentValues().apply {
            put(Schema.RecurrentTransfer.DESCRIPTION, recurrentTransfer.mDescription)
            put(Schema.RecurrentTransfer.WALLET_FROM, recurrentTransfer.mFromWallet)
            put(Schema.RecurrentTransfer.WALLET_TO, recurrentTransfer.mToWallet)
            put(Schema.RecurrentTransfer.MONEY_FROM, recurrentTransfer.mFromMoney)
            put(Schema.RecurrentTransfer.MONEY_TO, recurrentTransfer.mToMoney)
            put(Schema.RecurrentTransfer.MONEY_TAX, recurrentTransfer.mTaxMoney)
            put(Schema.RecurrentTransfer.NOTE, recurrentTransfer.mNote)
            put(Schema.RecurrentTransfer.EVENT, recurrentTransfer.mEvent)
            put(Schema.RecurrentTransfer.PLACE, recurrentTransfer.mPlace)
            put(Schema.RecurrentTransfer.CONFIRMED, recurrentTransfer.mConfirmed)
            put(Schema.RecurrentTransfer.COUNT_IN_TOTAL, recurrentTransfer.mCountInTotal)
            put(Schema.RecurrentTransfer.START_DATE, recurrentTransfer.mStartDate)
            put(Schema.RecurrentTransfer.LAST_OCCURRENCE, recurrentTransfer.mLastOccurrence)
            put(Schema.RecurrentTransfer.NEXT_OCCURRENCE, recurrentTransfer.mNextOccurrence)
            put(Schema.RecurrentTransfer.RULE, recurrentTransfer.mRule)
            put(Schema.RecurrentTransfer.TAG, recurrentTransfer.mTag)
            put(Schema.RecurrentTransfer.UUID, recurrentTransfer.mUUID)
            put(Schema.RecurrentTransfer.LAST_EDIT, recurrentTransfer.mLastEdit)
            put(Schema.RecurrentTransfer.DELETED, recurrentTransfer.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_RECURRENT_TRANSFER, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, transaction: Transaction): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Transaction.MONEY, transaction.mMoney)
            put(Schema.Transaction.DATE, transaction.mDate)
            put(Schema.Transaction.DESCRIPTION, transaction.mDescription)
            put(Schema.Transaction.CATEGORY, transaction.mCategory)
            put(Schema.Transaction.DIRECTION, transaction.mDirection)
            put(Schema.Transaction.TYPE, transaction.mType)
            put(Schema.Transaction.WALLET, transaction.mWallet)
            put(Schema.Transaction.PLACE, transaction.mPlace)
            put(Schema.Transaction.NOTE, transaction.mNote)
            put(Schema.Transaction.SAVING, transaction.mSaving)
            put(Schema.Transaction.DEBT, transaction.mDebt)
            put(Schema.Transaction.EVENT, transaction.mEvent)
            put(Schema.Transaction.RECURRENCE, transaction.mRecurrence)
            put(Schema.Transaction.CONFIRMED, transaction.mConfirmed)
            put(Schema.Transaction.COUNT_IN_TOTAL, transaction.mCountInTotal)
            put(Schema.Transaction.TAG, transaction.mTag)
            put(Schema.Transaction.UUID, transaction.mUUID)
            put(Schema.Transaction.LAST_EDIT, transaction.mLastEdit)
            put(Schema.Transaction.DELETED, transaction.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_TRANSACTION, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, transactionPeople: TransactionPerson): Long {
        val contentValues = ContentValues().apply {
            put(Schema.TransactionPeople.TRANSACTION, transactionPeople.mTransaction)
            put(Schema.TransactionPeople.PERSON, transactionPeople.mPerson)
            put(Schema.TransactionPeople.UUID, transactionPeople.mUUID)
            put(Schema.TransactionPeople.LAST_EDIT, transactionPeople.mLastEdit)
            put(Schema.TransactionPeople.DELETED, transactionPeople.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_TRANSACTION_PEOPLE, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, transactionModel: TransactionModel): Long {
        val contentValues = ContentValues().apply {
            put(Schema.TransactionModel.MONEY, transactionModel.mMoney)
            put(Schema.TransactionModel.DESCRIPTION, transactionModel.mDescription)
            put(Schema.TransactionModel.CATEGORY, transactionModel.mCategory)
            put(Schema.TransactionModel.DIRECTION, transactionModel.mDirection)
            put(Schema.TransactionModel.WALLET, transactionModel.mWallet)
            put(Schema.TransactionModel.PLACE, transactionModel.mPlace)
            put(Schema.TransactionModel.NOTE, transactionModel.mNote)
            put(Schema.TransactionModel.EVENT, transactionModel.mEvent)
            put(Schema.TransactionModel.CONFIRMED, transactionModel.mConfirmed)
            put(Schema.TransactionModel.COUNT_IN_TOTAL, transactionModel.mCountInTotal)
            put(Schema.TransactionModel.TAG, transactionModel.mTag)
            put(Schema.TransactionModel.UUID, transactionModel.mUUID)
            put(Schema.TransactionModel.LAST_EDIT, transactionModel.mLastEdit)
            put(Schema.TransactionModel.DELETED, transactionModel.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_TRANSACTION_MODEL, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, transfers: Transfer): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Transfer.DESCRIPTION, transfers.mDescription)
            put(Schema.Transfer.DATE, transfers.mDate)
            put(Schema.Transfer.TRANSACTION_FROM, transfers.mTransactionFrom)
            put(Schema.Transfer.TRANSACTION_TO, transfers.mTransactionTo)
            put(Schema.Transfer.TRANSACTION_TAX, transfers.mTransactionTax)
            put(Schema.Transfer.NOTE, transfers.mNote)
            put(Schema.Transfer.PLACE, transfers.mPlace)
            put(Schema.Transfer.EVENT, transfers.mEvent)
            put(Schema.Transfer.RECURRENCE, transfers.mRecurrence)
            put(Schema.Transfer.CONFIRMED, transfers.mConfirmed)
            put(Schema.Transfer.COUNT_IN_TOTAL, transfers.mCountInTotal)
            put(Schema.Transfer.TAG, transfers.mTag)
            put(Schema.Transfer.UUID, transfers.mUUID)
            put(Schema.Transfer.LAST_EDIT, transfers.mLastEdit)
            put(Schema.Transfer.DELETED, transfers.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_TRANSFER, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, transferPerson: TransferPerson): Long {
        val contentValues = ContentValues().apply {
            put(Schema.TransferPeople.TRANSFER, transferPerson.mTransfer)
            put(Schema.TransferPeople.PERSON, transferPerson.mPerson)
            put(Schema.TransferPeople.UUID, transferPerson.mUUID)
            put(Schema.TransferPeople.LAST_EDIT, transferPerson.mLastEdit)
            put(Schema.TransferPeople.DELETED, transferPerson.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_TRANSFER_PEOPLE, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, transferModel: TransferModel): Long {
        val contentValues = ContentValues().apply {
            put(Schema.TransferModel.DESCRIPTION, transferModel.mDescription)
            put(Schema.TransferModel.WALLET_FROM, transferModel.mFromWallet)
            put(Schema.TransferModel.WALLET_TO, transferModel.mToWallet)
            put(Schema.TransferModel.MONEY_FROM, transferModel.mFromMoney)
            put(Schema.TransferModel.MONEY_TO, transferModel.mToMoney)
            put(Schema.TransferModel.MONEY_TAX, transferModel.mTaxMoney)
            put(Schema.TransferModel.NOTE, transferModel.mNote)
            put(Schema.TransferModel.EVENT, transferModel.mEvent)
            put(Schema.TransferModel.PLACE, transferModel.mPlace)
            put(Schema.TransferModel.CONFIRMED, transferModel.mConfirmed)
            put(Schema.TransferModel.COUNT_IN_TOTAL, transferModel.mCountInTotal)
            put(Schema.TransferModel.TAG, transferModel.mTag)
            put(Schema.TransferModel.UUID, transferModel.mUUID)
            put(Schema.TransferModel.LAST_EDIT, transferModel.mLastEdit)
            put(Schema.TransferModel.DELETED, transferModel.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_TRANSFER_MODEL, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, attachment: Attachment): Long {
        val contentValues = ContentValues().apply {
            put(Schema.Attachment.FILE, attachment.mFile)
            put(Schema.Attachment.NAME, attachment.mName)
            put(Schema.Attachment.TYPE, attachment.mType)
            put(Schema.Attachment.SIZE, attachment.mSize)
            put(Schema.Attachment.TAG, attachment.mTag)
            put(Schema.Attachment.UUID, attachment.mUUID)
            put(Schema.Attachment.LAST_EDIT, attachment.mLastEdit)
            put(Schema.Attachment.DELETED, attachment.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_ATTACHMENT, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, transactionAttachment: TransactionAttachment): Long {
        val contentValues = ContentValues().apply {
            put(Schema.TransactionAttachment.TRANSACTION, transactionAttachment.mTransaction)
            put(Schema.TransactionAttachment.ATTACHMENT, transactionAttachment.mAttachment)
            put(Schema.TransactionAttachment.UUID, transactionAttachment.mUUID)
            put(Schema.TransactionAttachment.LAST_EDIT, transactionAttachment.mLastEdit)
            put(Schema.TransactionAttachment.DELETED, transactionAttachment.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_TRANSACTION_ATTACHMENT, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }

    @JvmStatic
    fun insert(contentResolver: ContentResolver, transferAttachment: TransferAttachment): Long {
        val contentValues = ContentValues().apply {
            put(Schema.TransferAttachment.TRANSFER, transferAttachment.mTransfer)
            put(Schema.TransferAttachment.ATTACHMENT, transferAttachment.mAttachment)
            put(Schema.TransferAttachment.UUID, transferAttachment.mUUID)
            put(Schema.TransferAttachment.LAST_EDIT, transferAttachment.mLastEdit)
            put(Schema.TransferAttachment.DELETED, transferAttachment.mDeleted)
        }
        val uri = contentResolver.insert(SyncContentProvider.CONTENT_TRANSFER_ATTACHMENT, contentValues)
        return uri?.let { ContentUris.parseId(it) } ?: -1L
    }
}

