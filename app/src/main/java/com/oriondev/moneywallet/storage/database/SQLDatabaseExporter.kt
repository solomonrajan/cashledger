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

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.database.Cursor
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

/**
 * Created by andrea on 28/10/18.
 */
@SuppressLint("Range")
object SQLDatabaseExporter {

    @JvmStatic
    fun getCurrency(cursor: Cursor): Currency {
        val obj = Currency()
        obj.mIso = cursor.getString(cursor.getColumnIndex(Schema.Currency.ISO))
        obj.mName = cursor.getString(cursor.getColumnIndex(Schema.Currency.NAME))
        obj.mSymbol = cursor.getString(cursor.getColumnIndex(Schema.Currency.SYMBOL))
        obj.mDecimals = cursor.getInt(cursor.getColumnIndex(Schema.Currency.DECIMALS))
        obj.mFavourite = cursor.getInt(cursor.getColumnIndex(Schema.Currency.FAVOURITE)) == 1
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Currency.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Currency.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Currency.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getWallet(cursor: Cursor): Wallet {
        val obj = Wallet()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Wallet.ID))
        obj.mName = cursor.getString(cursor.getColumnIndex(Schema.Wallet.NAME))
        obj.mIcon = cursor.getString(cursor.getColumnIndex(Schema.Wallet.ICON))
        obj.mCurrency = cursor.getString(cursor.getColumnIndex(Schema.Wallet.CURRENCY))
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.Wallet.NOTE))
        obj.mStartMoney = cursor.getLong(cursor.getColumnIndex(Schema.Wallet.START_MONEY))
        obj.mCountInTotal = cursor.getInt(cursor.getColumnIndex(Schema.Wallet.COUNT_IN_TOTAL)) == 1
        obj.mArchived = cursor.getInt(cursor.getColumnIndex(Schema.Wallet.ARCHIVED)) == 1
        obj.mIndex = cursor.getInt(cursor.getColumnIndex(Schema.Wallet.INDEX))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Wallet.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Wallet.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Wallet.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Wallet.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getCategory(cursor: Cursor): Category {
        val obj = Category()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Category.ID))
        obj.mName = cursor.getString(cursor.getColumnIndex(Schema.Category.NAME))
        obj.mIcon = cursor.getString(cursor.getColumnIndex(Schema.Category.ICON))
        obj.mType = cursor.getInt(cursor.getColumnIndex(Schema.Category.TYPE))
        val parentIndex = cursor.getColumnIndex(Schema.Category.PARENT)
        obj.mParent = if (cursor.isNull(parentIndex)) null else cursor.getLong(parentIndex)
        obj.mShowReport = cursor.getInt(cursor.getColumnIndex(Schema.Category.SHOW_REPORT)) == 1
        obj.mIndex = cursor.getInt(cursor.getColumnIndex(Schema.Category.INDEX))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Category.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Category.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Category.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Category.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getEvent(cursor: Cursor): Event {
        val obj = Event()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Event.ID))
        obj.mName = cursor.getString(cursor.getColumnIndex(Schema.Event.NAME))
        obj.mIcon = cursor.getString(cursor.getColumnIndex(Schema.Event.ICON))
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.Event.NOTE))
        obj.mStartDate = cursor.getString(cursor.getColumnIndex(Schema.Event.START_DATE))
        obj.mEndDate = cursor.getString(cursor.getColumnIndex(Schema.Event.END_DATE))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Event.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Event.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Event.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Event.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getCategoryRule(cursor: Cursor): CategoryRule {
        val obj = CategoryRule()
        obj.mId = cursor.getLong(cursor.getColumnIndexOrThrow(Schema.CategoryRule.ID))
        obj.mPattern = cursor.getString(cursor.getColumnIndexOrThrow(Schema.CategoryRule.PATTERN))
        obj.mCategory = cursor.getLong(cursor.getColumnIndexOrThrow(Schema.CategoryRule.CATEGORY))
        obj.mIndex = cursor.getInt(cursor.getColumnIndexOrThrow(Schema.CategoryRule.INDEX))
        obj.mUUID = cursor.getString(cursor.getColumnIndexOrThrow(Schema.CategoryRule.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndexOrThrow(Schema.CategoryRule.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(Schema.CategoryRule.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getPlace(cursor: Cursor): Place {
        val obj = Place()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Place.ID))
        obj.mName = cursor.getString(cursor.getColumnIndex(Schema.Place.NAME))
        obj.mIcon = cursor.getString(cursor.getColumnIndex(Schema.Place.ICON))
        obj.mAddress = cursor.getString(cursor.getColumnIndex(Schema.Place.ADDRESS))
        val latIndex = cursor.getColumnIndex(Schema.Place.LATITUDE)
        val lonIndex = cursor.getColumnIndex(Schema.Place.LONGITUDE)
        if (!cursor.isNull(latIndex) && !cursor.isNull(lonIndex)) {
            obj.mLatitude = cursor.getDouble(latIndex)
            obj.mLongitude = cursor.getDouble(lonIndex)
        } else {
            obj.mLatitude = null
            obj.mLongitude = null
        }
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Place.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Place.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Place.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Place.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getPerson(cursor: Cursor): Person {
        val obj = Person()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Person.ID))
        obj.mName = cursor.getString(cursor.getColumnIndex(Schema.Person.NAME))
        obj.mIcon = cursor.getString(cursor.getColumnIndex(Schema.Person.ICON))
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.Person.NOTE))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Person.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Person.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Person.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Person.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getEventPerson(cursor: Cursor): EventPerson {
        val obj = EventPerson()
        val eventIndex = cursor.getColumnIndex(Schema.EventPeople.EVENT)
        val personIndex = cursor.getColumnIndex(Schema.EventPeople.PERSON)
        obj.mEvent = if (cursor.isNull(eventIndex)) null else cursor.getLong(eventIndex)
        obj.mPerson = if (cursor.isNull(personIndex)) null else cursor.getLong(personIndex)
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.EventPeople.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.EventPeople.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.EventPeople.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getDebt(cursor: Cursor): Debt {
        val obj = Debt()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Debt.ID))
        obj.mType = cursor.getInt(cursor.getColumnIndex(Schema.Debt.TYPE))
        obj.mIcon = cursor.getString(cursor.getColumnIndex(Schema.Debt.ICON))
        obj.mDescription = cursor.getString(cursor.getColumnIndex(Schema.Debt.DESCRIPTION))
        obj.mDate = cursor.getString(cursor.getColumnIndex(Schema.Debt.DATE))
        obj.mExpirationDate = cursor.getString(cursor.getColumnIndex(Schema.Debt.EXPIRATION_DATE))
        val walletIndex = cursor.getColumnIndex(Schema.Debt.WALLET)
        obj.mWallet = if (cursor.isNull(walletIndex)) null else cursor.getLong(walletIndex)
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.Debt.NOTE))
        val placeIndex = cursor.getColumnIndex(Schema.Debt.PLACE)
        obj.mPlace = if (cursor.isNull(placeIndex)) null else cursor.getLong(placeIndex)
        obj.mMoney = cursor.getLong(cursor.getColumnIndex(Schema.Debt.MONEY))
        obj.mArchived = cursor.getInt(cursor.getColumnIndex(Schema.Debt.ARCHIVED)) == 1
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Debt.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Debt.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Debt.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Debt.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getDebtPerson(cursor: Cursor): DebtPerson {
        val obj = DebtPerson()
        val debtIndex = cursor.getColumnIndex(Schema.DebtPeople.DEBT)
        val personIndex = cursor.getColumnIndex(Schema.DebtPeople.PERSON)
        obj.mDebt = if (cursor.isNull(debtIndex)) null else cursor.getLong(debtIndex)
        obj.mPerson = if (cursor.isNull(personIndex)) null else cursor.getLong(personIndex)
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.DebtPeople.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.DebtPeople.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.DebtPeople.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getBudget(cursor: Cursor): Budget {
        val obj = Budget()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Budget.ID))
        obj.mType = cursor.getInt(cursor.getColumnIndex(Schema.Budget.TYPE))
        val catIndex = cursor.getColumnIndex(Schema.Budget.CATEGORY)
        obj.mCategory = if (cursor.isNull(catIndex)) null else cursor.getLong(catIndex)
        obj.mStartDate = cursor.getString(cursor.getColumnIndex(Schema.Budget.START_DATE))
        obj.mEndDate = cursor.getString(cursor.getColumnIndex(Schema.Budget.END_DATE))
        obj.mMoney = cursor.getLong(cursor.getColumnIndex(Schema.Budget.MONEY))
        obj.mCurrency = cursor.getString(cursor.getColumnIndex(Schema.Budget.CURRENCY))
        obj.mRule = cursor.getString(cursor.getColumnIndex(Schema.Budget.RULE))
        obj.mRuleStart = cursor.getString(cursor.getColumnIndex(Schema.Budget.RULE_START))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Budget.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Budget.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Budget.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Budget.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getBudgetCategory(cursor: Cursor): BudgetCategory {
        val obj = BudgetCategory()
        val budgetIndex = cursor.getColumnIndex(Schema.BudgetCategory.BUDGET)
        val catIndex = cursor.getColumnIndex(Schema.BudgetCategory.CATEGORY)
        obj.mBudget = if (cursor.isNull(budgetIndex)) null else cursor.getLong(budgetIndex)
        obj.mCategory = if (cursor.isNull(catIndex)) null else cursor.getLong(catIndex)
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.BudgetCategory.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.BudgetCategory.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.BudgetCategory.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getBudgetWallet(cursor: Cursor): BudgetWallet {
        val obj = BudgetWallet()
        val budgetIndex = cursor.getColumnIndex(Schema.BudgetWallet.BUDGET)
        val walletIndex = cursor.getColumnIndex(Schema.BudgetWallet.WALLET)
        obj.mBudget = if (cursor.isNull(budgetIndex)) null else cursor.getLong(budgetIndex)
        obj.mWallet = if (cursor.isNull(walletIndex)) null else cursor.getLong(walletIndex)
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.BudgetWallet.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.BudgetWallet.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.BudgetWallet.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getSaving(cursor: Cursor): Saving {
        val obj = Saving()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Saving.ID))
        obj.mDescription = cursor.getString(cursor.getColumnIndex(Schema.Saving.DESCRIPTION))
        obj.mIcon = cursor.getString(cursor.getColumnIndex(Schema.Saving.ICON))
        obj.mStartMoney = cursor.getLong(cursor.getColumnIndex(Schema.Saving.START_MONEY))
        obj.mEndMoney = cursor.getLong(cursor.getColumnIndex(Schema.Saving.END_MONEY))
        val walletIndex = cursor.getColumnIndex(Schema.Saving.WALLET)
        obj.mWallet = if (cursor.isNull(walletIndex)) null else cursor.getLong(walletIndex)
        obj.mEndDate = cursor.getString(cursor.getColumnIndex(Schema.Saving.END_DATE))
        obj.mComplete = cursor.getInt(cursor.getColumnIndex(Schema.Saving.COMPLETE)) == 1
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.Saving.NOTE))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Saving.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Saving.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Saving.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Saving.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getRecurrentTransaction(cursor: Cursor): RecurrentTransaction {
        val obj = RecurrentTransaction()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransaction.ID))
        obj.mMoney = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransaction.MONEY))
        obj.mDescription = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.DESCRIPTION))
        val catIndex = cursor.getColumnIndex(Schema.RecurrentTransaction.CATEGORY)
        obj.mCategory = if (cursor.isNull(catIndex)) null else cursor.getLong(catIndex)
        obj.mDirection = cursor.getInt(cursor.getColumnIndex(Schema.RecurrentTransaction.DIRECTION))
        val walletIndex = cursor.getColumnIndex(Schema.RecurrentTransaction.WALLET)
        obj.mWallet = if (cursor.isNull(walletIndex)) null else cursor.getLong(walletIndex)
        val placeIndex = cursor.getColumnIndex(Schema.RecurrentTransaction.PLACE)
        obj.mPlace = if (cursor.isNull(placeIndex)) null else cursor.getLong(placeIndex)
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.NOTE))
        val eventIndex = cursor.getColumnIndex(Schema.RecurrentTransaction.EVENT)
        obj.mEvent = if (cursor.isNull(eventIndex)) null else cursor.getLong(eventIndex)
        obj.mConfirmed = cursor.getInt(cursor.getColumnIndex(Schema.RecurrentTransaction.CONFIRMED)) == 1
        obj.mCountInTotal = cursor.getInt(cursor.getColumnIndex(Schema.RecurrentTransaction.COUNT_IN_TOTAL)) == 1
        obj.mStartDate = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.START_DATE))
        obj.mLastOccurrence = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.LAST_OCCURRENCE))
        obj.mNextOccurrence = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.NEXT_OCCURRENCE))
        obj.mRule = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.RULE))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransaction.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransaction.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.RecurrentTransaction.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getRecurrentTransfer(cursor: Cursor): RecurrentTransfer {
        val obj = RecurrentTransfer()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.ID))
        obj.mDescription = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.DESCRIPTION))
        val fromWalletIndex = cursor.getColumnIndex(Schema.RecurrentTransfer.WALLET_FROM)
        val toWalletIndex = cursor.getColumnIndex(Schema.RecurrentTransfer.WALLET_TO)
        obj.mFromWallet = if (cursor.isNull(fromWalletIndex)) null else cursor.getLong(fromWalletIndex)
        obj.mToWallet = if (cursor.isNull(toWalletIndex)) null else cursor.getLong(toWalletIndex)
        obj.mFromMoney = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.MONEY_FROM))
        obj.mToMoney = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.MONEY_TO))
        obj.mTaxMoney = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.MONEY_TAX))
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.NOTE))
        val placeIndex = cursor.getColumnIndex(Schema.RecurrentTransfer.PLACE)
        obj.mPlace = if (cursor.isNull(placeIndex)) null else cursor.getLong(placeIndex)
        val eventIndex = cursor.getColumnIndex(Schema.RecurrentTransfer.EVENT)
        obj.mEvent = if (cursor.isNull(eventIndex)) null else cursor.getLong(eventIndex)
        obj.mConfirmed = cursor.getInt(cursor.getColumnIndex(Schema.RecurrentTransfer.CONFIRMED)) == 1
        obj.mCountInTotal = cursor.getInt(cursor.getColumnIndex(Schema.RecurrentTransfer.COUNT_IN_TOTAL)) == 1
        obj.mStartDate = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.START_DATE))
        obj.mLastOccurrence = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.LAST_OCCURRENCE))
        obj.mNextOccurrence = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.NEXT_OCCURRENCE))
        obj.mRule = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.RULE))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.RecurrentTransfer.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.RecurrentTransfer.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.RecurrentTransfer.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getTransaction(cursor: Cursor): Transaction {
        val obj = Transaction()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Transaction.ID))
        obj.mMoney = cursor.getLong(cursor.getColumnIndex(Schema.Transaction.MONEY))
        obj.mDate = cursor.getString(cursor.getColumnIndex(Schema.Transaction.DATE))
        obj.mDescription = cursor.getString(cursor.getColumnIndex(Schema.Transaction.DESCRIPTION))
        val catIndex = cursor.getColumnIndex(Schema.Transaction.CATEGORY)
        obj.mCategory = if (cursor.isNull(catIndex)) null else cursor.getLong(catIndex)
        obj.mDirection = cursor.getInt(cursor.getColumnIndex(Schema.Transaction.DIRECTION))
        obj.mType = cursor.getInt(cursor.getColumnIndex(Schema.Transaction.TYPE))
        val walletIndex = cursor.getColumnIndex(Schema.Transaction.WALLET)
        obj.mWallet = if (cursor.isNull(walletIndex)) null else cursor.getLong(walletIndex)
        val placeIndex = cursor.getColumnIndex(Schema.Transaction.PLACE)
        obj.mPlace = if (cursor.isNull(placeIndex)) null else cursor.getLong(placeIndex)
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.Transaction.NOTE))
        val savingIndex = cursor.getColumnIndex(Schema.Transaction.SAVING)
        obj.mSaving = if (cursor.isNull(savingIndex)) null else cursor.getLong(savingIndex)
        val debtIndex = cursor.getColumnIndex(Schema.Transaction.DEBT)
        obj.mDebt = if (cursor.isNull(debtIndex)) null else cursor.getLong(debtIndex)
        val eventIndex = cursor.getColumnIndex(Schema.Transaction.EVENT)
        obj.mEvent = if (cursor.isNull(eventIndex)) null else cursor.getLong(eventIndex)
        val recurrenceIndex = cursor.getColumnIndex(Schema.Transaction.RECURRENCE)
        obj.mRecurrence = if (cursor.isNull(recurrenceIndex)) null else cursor.getLong(recurrenceIndex)
        obj.mConfirmed = cursor.getInt(cursor.getColumnIndex(Schema.Transaction.CONFIRMED)) == 1
        obj.mCountInTotal = cursor.getInt(cursor.getColumnIndex(Schema.Transaction.COUNT_IN_TOTAL)) == 1
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Transaction.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Transaction.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Transaction.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Transaction.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getTransactionPerson(cursor: Cursor): TransactionPerson {
        val obj = TransactionPerson()
        val transIndex = cursor.getColumnIndex(Schema.TransactionPeople.TRANSACTION)
        val personIndex = cursor.getColumnIndex(Schema.TransactionPeople.PERSON)
        obj.mTransaction = if (cursor.isNull(transIndex)) null else cursor.getLong(transIndex)
        obj.mPerson = if (cursor.isNull(personIndex)) null else cursor.getLong(personIndex)
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.TransactionPeople.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.TransactionPeople.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.TransactionPeople.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getTransactionModel(cursor: Cursor): TransactionModel {
        val obj = TransactionModel()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.TransactionModel.ID))
        obj.mMoney = cursor.getLong(cursor.getColumnIndex(Schema.TransactionModel.MONEY))
        obj.mDescription = cursor.getString(cursor.getColumnIndex(Schema.TransactionModel.DESCRIPTION))
        val catIndex = cursor.getColumnIndex(Schema.TransactionModel.CATEGORY)
        obj.mCategory = if (cursor.isNull(catIndex)) null else cursor.getLong(catIndex)
        obj.mDirection = cursor.getInt(cursor.getColumnIndex(Schema.TransactionModel.DIRECTION))
        val walletIndex = cursor.getColumnIndex(Schema.TransactionModel.WALLET)
        obj.mWallet = if (cursor.isNull(walletIndex)) null else cursor.getLong(walletIndex)
        val placeIndex = cursor.getColumnIndex(Schema.TransactionModel.PLACE)
        obj.mPlace = if (cursor.isNull(placeIndex)) null else cursor.getLong(placeIndex)
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.TransactionModel.NOTE))
        val eventIndex = cursor.getColumnIndex(Schema.TransactionModel.EVENT)
        obj.mEvent = if (cursor.isNull(eventIndex)) null else cursor.getLong(eventIndex)
        obj.mConfirmed = cursor.getInt(cursor.getColumnIndex(Schema.TransactionModel.CONFIRMED)) == 1
        obj.mCountInTotal = cursor.getInt(cursor.getColumnIndex(Schema.TransactionModel.COUNT_IN_TOTAL)) == 1
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.TransactionModel.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.TransactionModel.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.TransactionModel.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.TransactionModel.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getTransfer(cursor: Cursor): Transfer {
        val obj = Transfer()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Transfer.ID))
        obj.mDescription = cursor.getString(cursor.getColumnIndex(Schema.Transfer.DESCRIPTION))
        obj.mDate = cursor.getString(cursor.getColumnIndex(Schema.Transfer.DATE))
        val fromIndex = cursor.getColumnIndex(Schema.Transfer.TRANSACTION_FROM)
        val toIndex = cursor.getColumnIndex(Schema.Transfer.TRANSACTION_TO)
        val taxIndex = cursor.getColumnIndex(Schema.Transfer.TRANSACTION_TAX)
        obj.mTransactionFrom = if (cursor.isNull(fromIndex)) null else cursor.getLong(fromIndex)
        obj.mTransactionTo = if (cursor.isNull(toIndex)) null else cursor.getLong(toIndex)
        obj.mTransactionTax = if (cursor.isNull(taxIndex)) null else cursor.getLong(taxIndex)
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.Transfer.NOTE))
        val placeIndex = cursor.getColumnIndex(Schema.Transfer.PLACE)
        obj.mPlace = if (cursor.isNull(placeIndex)) null else cursor.getLong(placeIndex)
        val eventIndex = cursor.getColumnIndex(Schema.Transfer.EVENT)
        obj.mEvent = if (cursor.isNull(eventIndex)) null else cursor.getLong(eventIndex)
        val recurrenceIndex = cursor.getColumnIndex(Schema.Transfer.RECURRENCE)
        obj.mRecurrence = if (cursor.isNull(recurrenceIndex)) null else cursor.getLong(recurrenceIndex)
        obj.mConfirmed = cursor.getInt(cursor.getColumnIndex(Schema.Transfer.CONFIRMED)) == 1
        obj.mCountInTotal = cursor.getInt(cursor.getColumnIndex(Schema.Transfer.COUNT_IN_TOTAL)) == 1
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Transfer.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Transfer.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Transfer.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Transfer.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getTransferPerson(cursor: Cursor): TransferPerson {
        val obj = TransferPerson()
        val transIndex = cursor.getColumnIndex(Schema.TransferPeople.TRANSFER)
        val personIndex = cursor.getColumnIndex(Schema.TransferPeople.PERSON)
        obj.mTransfer = if (cursor.isNull(transIndex)) null else cursor.getLong(transIndex)
        obj.mPerson = if (cursor.isNull(personIndex)) null else cursor.getLong(personIndex)
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.TransferPeople.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.TransferPeople.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.TransferPeople.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getTransferModel(cursor: Cursor): TransferModel {
        val obj = TransferModel()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.ID))
        obj.mDescription = cursor.getString(cursor.getColumnIndex(Schema.TransferModel.DESCRIPTION))
        val fromWalletIndex = cursor.getColumnIndex(Schema.TransferModel.WALLET_FROM)
        val toWalletIndex = cursor.getColumnIndex(Schema.TransferModel.WALLET_TO)
        obj.mFromWallet = if (cursor.isNull(fromWalletIndex)) null else cursor.getLong(fromWalletIndex)
        obj.mToWallet = if (cursor.isNull(toWalletIndex)) null else cursor.getLong(toWalletIndex)
        obj.mFromMoney = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.MONEY_FROM))
        obj.mToMoney = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.MONEY_TO))
        obj.mTaxMoney = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.MONEY_TAX))
        obj.mNote = cursor.getString(cursor.getColumnIndex(Schema.TransferModel.NOTE))
        val placeIndex = cursor.getColumnIndex(Schema.TransferModel.PLACE)
        obj.mPlace = if (cursor.isNull(placeIndex)) null else cursor.getLong(placeIndex)
        val eventIndex = cursor.getColumnIndex(Schema.TransferModel.EVENT)
        obj.mEvent = if (cursor.isNull(eventIndex)) null else cursor.getLong(eventIndex)
        obj.mConfirmed = cursor.getInt(cursor.getColumnIndex(Schema.TransferModel.CONFIRMED)) == 1
        obj.mCountInTotal = cursor.getInt(cursor.getColumnIndex(Schema.TransferModel.COUNT_IN_TOTAL)) == 1
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.TransferModel.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.TransferModel.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.TransferModel.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.TransferModel.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getAttachment(cursor: Cursor): Attachment {
        val obj = Attachment()
        obj.mId = cursor.getLong(cursor.getColumnIndex(Schema.Attachment.ID))
        obj.mFile = cursor.getString(cursor.getColumnIndex(Schema.Attachment.FILE))
        obj.mName = cursor.getString(cursor.getColumnIndex(Schema.Attachment.NAME))
        obj.mType = cursor.getString(cursor.getColumnIndex(Schema.Attachment.TYPE))
        obj.mSize = cursor.getLong(cursor.getColumnIndex(Schema.Attachment.SIZE))
        obj.mTag = cursor.getString(cursor.getColumnIndex(Schema.Attachment.TAG))
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.Attachment.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.Attachment.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.Attachment.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getTransactionAttachment(cursor: Cursor): TransactionAttachment {
        val obj = TransactionAttachment()
        val transIndex = cursor.getColumnIndex(Schema.TransactionAttachment.TRANSACTION)
        val attachIndex = cursor.getColumnIndex(Schema.TransactionAttachment.ATTACHMENT)
        obj.mTransaction = if (cursor.isNull(transIndex)) null else cursor.getLong(transIndex)
        obj.mAttachment = if (cursor.isNull(attachIndex)) null else cursor.getLong(attachIndex)
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.TransactionAttachment.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.TransactionAttachment.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.TransactionAttachment.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getTransferAttachment(cursor: Cursor): TransferAttachment {
        val obj = TransferAttachment()
        val transIndex = cursor.getColumnIndex(Schema.TransferAttachment.TRANSFER)
        val attachIndex = cursor.getColumnIndex(Schema.TransferAttachment.ATTACHMENT)
        obj.mTransfer = if (cursor.isNull(transIndex)) null else cursor.getLong(transIndex)
        obj.mAttachment = if (cursor.isNull(attachIndex)) null else cursor.getLong(attachIndex)
        obj.mUUID = cursor.getString(cursor.getColumnIndex(Schema.TransferAttachment.UUID))
        obj.mLastEdit = cursor.getLong(cursor.getColumnIndex(Schema.TransferAttachment.LAST_EDIT))
        obj.mDeleted = cursor.getInt(cursor.getColumnIndex(Schema.TransferAttachment.DELETED)) == 1
        return obj
    }

    @JvmStatic
    fun getAllCurrencies(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_CURRENCIES
        val selection = "${Schema.Currency.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllWallets(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_WALLETS
        val selection = "${Schema.Wallet.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllCategories(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_CATEGORIES
        val selection = "${Schema.Category.DELETED} = 0"
        val sortOrder = "${Schema.Category.PARENT} ASC"
        return contentResolver.query(uri, null, selection, null, sortOrder)
    }

    @JvmStatic
    fun getAllEvents(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_EVENTS
        val selection = "${Schema.Event.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllCategoryRules(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_CATEGORY_RULES
        val selection = "${Schema.CategoryRule.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllPlaces(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_PLACES
        val selection = "${Schema.Place.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllPeople(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_PEOPLE
        val selection = "${Schema.Person.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllEventPeople(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_EVENT_PEOPLE
        val selection = "${Schema.EventPeople.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllDebt(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_DEBT
        val selection = "${Schema.Debt.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllDebtPeople(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_DEBT_PEOPLE
        val selection = "${Schema.DebtPeople.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllBudget(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_BUDGET
        val selection = "${Schema.Budget.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllBudgetWallets(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_BUDGET_WALLET
        val selection = "${Schema.BudgetWallet.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllBudgetCategories(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_BUDGET_CATEGORY
        val selection = "${Schema.BudgetCategory.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllSavings(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_SAVING
        val selection = "${Schema.Saving.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllRecurrentTransactions(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_RECURRENT_TRANSACTION
        val selection = "${Schema.RecurrentTransaction.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllRecurrentTransfers(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_RECURRENT_TRANSFER
        val selection = "${Schema.RecurrentTransfer.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllTransactions(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_TRANSACTION
        val selection = "${Schema.Transaction.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllTransactionPeople(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_TRANSACTION_PEOPLE
        val selection = "${Schema.TransactionPeople.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllTransactionModels(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_TRANSACTION_MODEL
        val selection = "${Schema.TransactionModel.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllTransfers(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_TRANSFER
        val selection = "${Schema.Transfer.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllTransferPeople(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_TRANSFER_PEOPLE
        val selection = "${Schema.TransferPeople.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllTransferModels(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_TRANSFER_MODEL
        val selection = "${Schema.TransferModel.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllAttachments(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_ATTACHMENT
        val selection = "${Schema.Attachment.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllTransactionAttachments(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_TRANSACTION_ATTACHMENT
        val selection = "${Schema.TransactionAttachment.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }

    @JvmStatic
    fun getAllTransferAttachments(contentResolver: ContentResolver): Cursor? {
        val uri = SyncContentProvider.CONTENT_TRANSFER_ATTACHMENT
        val selection = "${Schema.TransferAttachment.DELETED} = 0"
        return contentResolver.query(uri, null, selection, null, null)
    }
}
