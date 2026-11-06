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
import org.json.JSONObject

/**
 * This class acts as object factory and mapping between the JSONObjects and
 * the native database objects. It also uses an internal cache to correctly
 * handle the mapping between the UUID that are used as identifiers in the
 * json file and native longs that are used as id inside the database.
 */
internal class JSONDataInputFactory {

    private val cacheWallets = HashMap<String, Long>()
    private val cacheCategories = HashMap<String, Long>()
    private val cacheEvents = HashMap<String, Long>()
    private val cachePlaces = HashMap<String, Long>()
    private val cachePeople = HashMap<String, Long>()
    private val cacheDebts = HashMap<String, Long>()
    private val cacheBudgets = HashMap<String, Long>()
    private val cacheSavings = HashMap<String, Long>()
    private val cacheRecurrentTransactions = HashMap<String, Long>()
    private val cacheRecurrentTransfers = HashMap<String, Long>()
    private val cacheTransactions = HashMap<String, Long>()
    private val cacheTransfers = HashMap<String, Long>()
    private val cacheAttachments = HashMap<String, Long>()

    private fun JSONObject.optNullableString(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name) else null
    }

    fun cacheWallet(uuid: String?, id: Long) {
        if (uuid != null) cacheWallets[uuid] = id
    }

    fun cacheCategory(uuid: String?, id: Long) {
        if (uuid != null) cacheCategories[uuid] = id
    }

    fun cacheEvent(uuid: String?, id: Long) {
        if (uuid != null) cacheEvents[uuid] = id
    }

    fun cachePlace(uuid: String?, id: Long) {
        if (uuid != null) cachePlaces[uuid] = id
    }

    fun cachePerson(uuid: String?, id: Long) {
        if (uuid != null) cachePeople[uuid] = id
    }

    fun cacheDebt(uuid: String?, id: Long) {
        if (uuid != null) cacheDebts[uuid] = id
    }

    fun cacheBudget(uuid: String?, id: Long) {
        if (uuid != null) cacheBudgets[uuid] = id
    }

    fun cacheSaving(uuid: String?, id: Long) {
        if (uuid != null) cacheSavings[uuid] = id
    }

    fun cacheRecurrentTransaction(uuid: String?, id: Long) {
        if (uuid != null) cacheRecurrentTransactions[uuid] = id
    }

    fun cacheRecurrentTransfer(uuid: String?, id: Long) {
        if (uuid != null) cacheRecurrentTransfers[uuid] = id
    }

    fun cacheTransaction(uuid: String?, id: Long) {
        if (uuid != null) cacheTransactions[uuid] = id
    }

    fun cacheTransfer(uuid: String?, id: Long) {
        if (uuid != null) cacheTransfers[uuid] = id
    }

    fun cacheAttachment(uuid: String?, id: Long) {
        if (uuid != null) cacheAttachments[uuid] = id
    }

    fun getCurrency(`object`: JSONObject): Currency {
        val currency = Currency()
        currency.mIso = `object`.optNullableString(JSONDatabase.Currency.ISO)
        currency.mName = `object`.optNullableString(JSONDatabase.Currency.NAME)
        currency.mSymbol = `object`.optNullableString(JSONDatabase.Currency.SYMBOL)
        currency.mDecimals = `object`.optInt(JSONDatabase.Currency.DECIMALS, 2)
        currency.mFavourite = `object`.optBoolean(JSONDatabase.Currency.FAVOURITE, false)
        currency.mUUID = "currency_" + `object`.optNullableString(JSONDatabase.Currency.ISO)
        currency.mLastEdit = `object`.optLong(JSONDatabase.Currency.LAST_EDIT, 0L)
        currency.mDeleted = `object`.optBoolean(JSONDatabase.Currency.DELETED, false)
        return currency
    }

    fun getWallet(`object`: JSONObject): Wallet {
        val wallet = Wallet()
        wallet.mName = `object`.optNullableString(JSONDatabase.Wallet.NAME)
        wallet.mIcon = `object`.optNullableString(JSONDatabase.Wallet.ICON)
        wallet.mCurrency = `object`.optNullableString(JSONDatabase.Wallet.CURRENCY)
        wallet.mStartMoney = `object`.optLong(JSONDatabase.Wallet.START_MONEY, 0L)
        wallet.mCountInTotal = `object`.optBoolean(JSONDatabase.Wallet.COUNT_IN_TOTAL, true)
        wallet.mArchived = `object`.optBoolean(JSONDatabase.Wallet.ARCHIVED, false)
        wallet.mNote = `object`.optNullableString(JSONDatabase.Wallet.NOTE)
        wallet.mTag = `object`.optNullableString(JSONDatabase.Wallet.TAG)
        wallet.mIndex = `object`.optInt(JSONDatabase.Wallet.INDEX, 0)
        wallet.mUUID = `object`.optNullableString(JSONDatabase.Wallet.ID)
        wallet.mLastEdit = `object`.optLong(JSONDatabase.Wallet.LAST_EDIT, 0L)
        wallet.mDeleted = `object`.optBoolean(JSONDatabase.Wallet.DELETED, false)
        return wallet
    }

    fun getCategory(`object`: JSONObject): Category {
        val category = Category()
        category.mName = `object`.optNullableString(JSONDatabase.Category.NAME)
        category.mIcon = `object`.optNullableString(JSONDatabase.Category.ICON)
        category.mType = `object`.optInt(JSONDatabase.Category.TYPE, 0)
        category.mParent = cacheCategories[`object`.optNullableString(JSONDatabase.Category.PARENT)]
        category.mTag = `object`.optNullableString(JSONDatabase.Category.TAG)
        category.mShowReport = `object`.optBoolean(JSONDatabase.Category.SHOW_REPORT, true)
        category.mIndex = `object`.optInt(JSONDatabase.Category.INDEX, 0)
        category.mUUID = `object`.optNullableString(JSONDatabase.Category.ID)
        category.mLastEdit = `object`.optLong(JSONDatabase.Category.LAST_EDIT, 0L)
        category.mDeleted = `object`.optBoolean(JSONDatabase.Category.DELETED, false)
        return category
    }

    fun getEvent(`object`: JSONObject): Event {
        val event = Event()
        event.mName = `object`.optNullableString(JSONDatabase.Event.NAME)
        event.mIcon = `object`.optNullableString(JSONDatabase.Event.ICON)
        event.mNote = `object`.optNullableString(JSONDatabase.Event.NOTE)
        event.mStartDate = `object`.optNullableString(JSONDatabase.Event.START_DATE)
        event.mEndDate = `object`.optNullableString(JSONDatabase.Event.END_DATE)
        event.mTag = `object`.optNullableString(JSONDatabase.Event.TAG)
        event.mUUID = `object`.optNullableString(JSONDatabase.Event.ID)
        event.mLastEdit = `object`.optLong(JSONDatabase.Event.LAST_EDIT, 0L)
        event.mDeleted = `object`.optBoolean(JSONDatabase.Event.DELETED, false)
        return event
    }

    fun getCategoryRule(`object`: JSONObject): CategoryRule {
        val categoryRule = CategoryRule()
        categoryRule.mPattern = `object`.optNullableString(JSONDatabase.CategoryRule.PATTERN)
        categoryRule.mCategory = cacheCategories[`object`.optNullableString(JSONDatabase.CategoryRule.CATEGORY)]
        categoryRule.mIndex = `object`.optInt(JSONDatabase.CategoryRule.INDEX, 0)
        categoryRule.mUUID = `object`.optNullableString(JSONDatabase.CategoryRule.ID)
        categoryRule.mLastEdit = `object`.optLong(JSONDatabase.CategoryRule.LAST_EDIT, 0L)
        categoryRule.mDeleted = `object`.optBoolean(JSONDatabase.CategoryRule.DELETED, false)
        return categoryRule
    }

    fun getPlace(`object`: JSONObject): Place {
        val place = Place()
        place.mName = `object`.optNullableString(JSONDatabase.Place.NAME)
        place.mIcon = `object`.optNullableString(JSONDatabase.Place.ICON)
        place.mAddress = `object`.optNullableString(JSONDatabase.Place.ADDRESS)
        place.mLatitude = if (`object`.has(JSONDatabase.Place.LATITUDE)) `object`.optDouble(JSONDatabase.Place.LATITUDE, 0.0) else null
        place.mLongitude = if (`object`.has(JSONDatabase.Place.LONGITUDE)) `object`.optDouble(JSONDatabase.Place.LONGITUDE, 0.0) else null
        place.mTag = `object`.optNullableString(JSONDatabase.Place.TAG)
        place.mUUID = `object`.optNullableString(JSONDatabase.Place.ID)
        place.mLastEdit = `object`.optLong(JSONDatabase.Place.LAST_EDIT, 0L)
        place.mDeleted = `object`.optBoolean(JSONDatabase.Place.DELETED, false)
        return place
    }

    fun getPerson(`object`: JSONObject): Person {
        val person = Person()
        person.mName = `object`.optNullableString(JSONDatabase.Person.NAME)
        person.mIcon = `object`.optNullableString(JSONDatabase.Person.ICON)
        person.mNote = `object`.optNullableString(JSONDatabase.Person.NOTE)
        person.mTag = `object`.optNullableString(JSONDatabase.Person.TAG)
        person.mUUID = `object`.optNullableString(JSONDatabase.Person.ID)
        person.mLastEdit = `object`.optLong(JSONDatabase.Person.LAST_EDIT, 0L)
        person.mDeleted = `object`.optBoolean(JSONDatabase.Person.DELETED, false)
        return person
    }

    fun getEventPerson(`object`: JSONObject): EventPerson {
        val eventPerson = EventPerson()
        eventPerson.mEvent = cacheEvents[`object`.optNullableString(JSONDatabase.EventPeople.EVENT)]
        eventPerson.mPerson = cachePeople[`object`.optNullableString(JSONDatabase.EventPeople.PERSON)]
        eventPerson.mUUID = `object`.optNullableString(JSONDatabase.EventPeople.ID)
        eventPerson.mLastEdit = `object`.optLong(JSONDatabase.EventPeople.LAST_EDIT, 0L)
        eventPerson.mDeleted = `object`.optBoolean(JSONDatabase.EventPeople.DELETED, false)
        return eventPerson
    }

    fun getDebt(`object`: JSONObject): Debt {
        val debt = Debt()
        debt.mType = `object`.optInt(JSONDatabase.Debt.TYPE, 0)
        debt.mIcon = `object`.optNullableString(JSONDatabase.Debt.ICON)
        debt.mDescription = `object`.optNullableString(JSONDatabase.Debt.DESCRIPTION)
        debt.mDate = `object`.optNullableString(JSONDatabase.Debt.DATE)
        debt.mExpirationDate = `object`.optNullableString(JSONDatabase.Debt.EXPIRATION_DATE)
        debt.mWallet = cacheWallets[`object`.optNullableString(JSONDatabase.Debt.WALLET)]
        debt.mNote = `object`.optNullableString(JSONDatabase.Debt.NOTE)
        debt.mPlace = cachePlaces[`object`.optNullableString(JSONDatabase.Debt.PLACE)]
        debt.mMoney = `object`.optLong(JSONDatabase.Debt.MONEY, 0L)
        debt.mArchived = `object`.optBoolean(JSONDatabase.Debt.ARCHIVED, false)
        debt.mTag = `object`.optNullableString(JSONDatabase.Debt.TAG)
        debt.mUUID = `object`.optNullableString(JSONDatabase.Debt.ID)
        debt.mLastEdit = `object`.optLong(JSONDatabase.Debt.LAST_EDIT, 0L)
        debt.mDeleted = `object`.optBoolean(JSONDatabase.Debt.DELETED, false)
        return debt
    }

    fun getDebtPerson(`object`: JSONObject): DebtPerson {
        val debtPerson = DebtPerson()
        debtPerson.mDebt = cacheDebts[`object`.optNullableString(JSONDatabase.DebtPeople.DEBT)]
        debtPerson.mPerson = cachePeople[`object`.optNullableString(JSONDatabase.DebtPeople.PERSON)]
        debtPerson.mUUID = `object`.optNullableString(JSONDatabase.DebtPeople.ID)
        debtPerson.mLastEdit = `object`.optLong(JSONDatabase.DebtPeople.LAST_EDIT, 0L)
        debtPerson.mDeleted = `object`.optBoolean(JSONDatabase.DebtPeople.DELETED, false)
        return debtPerson
    }

    fun getBudget(`object`: JSONObject): Budget {
        val budget = Budget()
        budget.mType = `object`.optInt(JSONDatabase.Budget.TYPE, 0)
        budget.mCategory = cacheCategories[`object`.optNullableString(JSONDatabase.Budget.CATEGORY)]
        budget.mStartDate = `object`.optNullableString(JSONDatabase.Budget.START_DATE)
        budget.mEndDate = `object`.optNullableString(JSONDatabase.Budget.END_DATE)
        budget.mMoney = `object`.optLong(JSONDatabase.Budget.MONEY, 0L)
        budget.mCurrency = `object`.optNullableString(JSONDatabase.Budget.CURRENCY)
        budget.mRule = `object`.optNullableString(JSONDatabase.Budget.RULE)
        budget.mRuleStart = `object`.optNullableString(JSONDatabase.Budget.RULE_START)
        budget.mTag = `object`.optNullableString(JSONDatabase.Budget.TAG)
        budget.mUUID = `object`.optNullableString(JSONDatabase.Budget.ID)
        budget.mLastEdit = `object`.optLong(JSONDatabase.Budget.LAST_EDIT, 0L)
        budget.mDeleted = `object`.optBoolean(JSONDatabase.Budget.DELETED, false)
        return budget
    }

    fun getBudgetCategory(`object`: JSONObject): BudgetCategory {
        val budgetCategory = BudgetCategory()
        budgetCategory.mBudget = cacheBudgets[`object`.optNullableString(JSONDatabase.BudgetCategory.BUDGET)]
        budgetCategory.mCategory = cacheCategories[`object`.optNullableString(JSONDatabase.BudgetCategory.CATEGORY)]
        budgetCategory.mUUID = `object`.optNullableString(JSONDatabase.BudgetCategory.ID)
        budgetCategory.mLastEdit = `object`.optLong(JSONDatabase.BudgetCategory.LAST_EDIT, 0L)
        budgetCategory.mDeleted = `object`.optBoolean(JSONDatabase.BudgetCategory.DELETED, false)
        return budgetCategory
    }

    fun getBudgetWallet(`object`: JSONObject): BudgetWallet {
        val budgetWallet = BudgetWallet()
        budgetWallet.mBudget = cacheBudgets[`object`.optNullableString(JSONDatabase.BudgetWallet.BUDGET)]
        budgetWallet.mWallet = cacheWallets[`object`.optNullableString(JSONDatabase.BudgetWallet.WALLET)]
        budgetWallet.mUUID = `object`.optNullableString(JSONDatabase.BudgetWallet.ID)
        budgetWallet.mLastEdit = `object`.optLong(JSONDatabase.BudgetWallet.LAST_EDIT, 0L)
        budgetWallet.mDeleted = `object`.optBoolean(JSONDatabase.BudgetWallet.DELETED, false)
        return budgetWallet
    }

    fun getSaving(`object`: JSONObject): Saving {
        val saving = Saving()
        saving.mDescription = `object`.optNullableString(JSONDatabase.Saving.DESCRIPTION)
        saving.mIcon = `object`.optNullableString(JSONDatabase.Saving.ICON)
        saving.mStartMoney = `object`.optLong(JSONDatabase.Saving.START_MONEY, 0L)
        saving.mEndMoney = `object`.optLong(JSONDatabase.Saving.END_MONEY, 0L)
        saving.mWallet = cacheWallets[`object`.optNullableString(JSONDatabase.Saving.WALLET)]
        saving.mEndDate = `object`.optNullableString(JSONDatabase.Saving.END_DATE)
        saving.mComplete = `object`.optBoolean(JSONDatabase.Saving.COMPLETE, false)
        saving.mNote = `object`.optNullableString(JSONDatabase.Saving.NOTE)
        saving.mTag = `object`.optNullableString(JSONDatabase.Saving.TAG)
        saving.mUUID = `object`.optNullableString(JSONDatabase.Saving.ID)
        saving.mLastEdit = `object`.optLong(JSONDatabase.Saving.LAST_EDIT, 0L)
        saving.mDeleted = `object`.optBoolean(JSONDatabase.Saving.DELETED, false)
        return saving
    }

    fun getRecurrentTransaction(`object`: JSONObject): RecurrentTransaction {
        val transaction = RecurrentTransaction()
        transaction.mMoney = `object`.optLong(JSONDatabase.RecurrentTransaction.MONEY, 0L)
        transaction.mDescription = `object`.optNullableString(JSONDatabase.RecurrentTransaction.DESCRIPTION)
        transaction.mCategory = cacheCategories[`object`.optNullableString(JSONDatabase.RecurrentTransaction.CATEGORY)]
        transaction.mDirection = `object`.optInt(JSONDatabase.RecurrentTransaction.DIRECTION, 0)
        transaction.mWallet = cacheWallets[`object`.optNullableString(JSONDatabase.RecurrentTransaction.WALLET)]
        transaction.mPlace = cachePlaces[`object`.optNullableString(JSONDatabase.RecurrentTransaction.PLACE)]
        transaction.mNote = `object`.optNullableString(JSONDatabase.RecurrentTransaction.NOTE)
        transaction.mEvent = cacheEvents[`object`.optNullableString(JSONDatabase.RecurrentTransaction.EVENT)]
        transaction.mConfirmed = `object`.optBoolean(JSONDatabase.RecurrentTransaction.CONFIRMED, true)
        transaction.mCountInTotal = `object`.optBoolean(JSONDatabase.RecurrentTransaction.COUNT_IN_TOTAL, true)
        transaction.mStartDate = `object`.optNullableString(JSONDatabase.RecurrentTransaction.START_DATE)
        transaction.mLastOccurrence = `object`.optNullableString(JSONDatabase.RecurrentTransaction.LAST_OCCURRENCE)
        transaction.mNextOccurrence = `object`.optNullableString(JSONDatabase.RecurrentTransaction.NEXT_OCCURRENCE)
        transaction.mRule = `object`.optNullableString(JSONDatabase.RecurrentTransaction.RULE)
        transaction.mTag = `object`.optNullableString(JSONDatabase.RecurrentTransaction.TAG)
        transaction.mUUID = `object`.optNullableString(JSONDatabase.RecurrentTransaction.ID)
        transaction.mLastEdit = `object`.optLong(JSONDatabase.RecurrentTransaction.LAST_EDIT, 0L)
        transaction.mDeleted = `object`.optBoolean(JSONDatabase.RecurrentTransaction.DELETED, false)
        return transaction
    }

    fun getRecurrentTransfer(`object`: JSONObject): RecurrentTransfer {
        val transfer = RecurrentTransfer()
        transfer.mDescription = `object`.optNullableString(JSONDatabase.RecurrentTransfer.DESCRIPTION)
        transfer.mFromWallet = cacheWallets[`object`.optNullableString(JSONDatabase.RecurrentTransfer.WALLET_FROM)]
        transfer.mToWallet = cacheWallets[`object`.optNullableString(JSONDatabase.RecurrentTransfer.WALLET_TO)]
        transfer.mFromMoney = `object`.optLong(JSONDatabase.RecurrentTransfer.MONEY_FROM, 0L)
        transfer.mToMoney = `object`.optLong(JSONDatabase.RecurrentTransfer.MONEY_TO, 0L)
        transfer.mTaxMoney = `object`.optLong(JSONDatabase.RecurrentTransfer.MONEY_TAX, 0L)
        transfer.mNote = `object`.optNullableString(JSONDatabase.RecurrentTransfer.NOTE)
        transfer.mEvent = cacheEvents[`object`.optNullableString(JSONDatabase.RecurrentTransfer.EVENT)]
        transfer.mPlace = cachePlaces[`object`.optNullableString(JSONDatabase.RecurrentTransfer.PLACE)]
        transfer.mConfirmed = `object`.optBoolean(JSONDatabase.RecurrentTransfer.CONFIRMED, true)
        transfer.mCountInTotal = `object`.optBoolean(JSONDatabase.RecurrentTransfer.COUNT_IN_TOTAL, true)
        transfer.mStartDate = `object`.optNullableString(JSONDatabase.RecurrentTransfer.START_DATE)
        transfer.mLastOccurrence = `object`.optNullableString(JSONDatabase.RecurrentTransfer.LAST_OCCURRENCE)
        transfer.mNextOccurrence = `object`.optNullableString(JSONDatabase.RecurrentTransfer.NEXT_OCCURRENCE)
        transfer.mRule = `object`.optNullableString(JSONDatabase.RecurrentTransfer.RULE)
        transfer.mTag = `object`.optNullableString(JSONDatabase.RecurrentTransfer.TAG)
        transfer.mUUID = `object`.optNullableString(JSONDatabase.RecurrentTransfer.ID)
        transfer.mLastEdit = `object`.optLong(JSONDatabase.RecurrentTransfer.LAST_EDIT, 0L)
        transfer.mDeleted = `object`.optBoolean(JSONDatabase.RecurrentTransfer.DELETED, false)
        return transfer
    }

    fun getTransaction(`object`: JSONObject): Transaction {
        val transaction = Transaction()
        transaction.mMoney = `object`.optLong(JSONDatabase.Transaction.MONEY, 0L)
        transaction.mDate = `object`.optNullableString(JSONDatabase.Transaction.DATE)
        transaction.mDescription = `object`.optNullableString(JSONDatabase.Transaction.DESCRIPTION)
        transaction.mCategory = cacheCategories[`object`.optNullableString(JSONDatabase.Transaction.CATEGORY)]
        transaction.mDirection = `object`.optInt(JSONDatabase.Transaction.DIRECTION, 0)
        transaction.mType = `object`.optInt(JSONDatabase.Transaction.TYPE, 0)
        transaction.mWallet = cacheWallets[`object`.optNullableString(JSONDatabase.Transaction.WALLET)]
        transaction.mPlace = cachePlaces[`object`.optNullableString(JSONDatabase.Transaction.PLACE)]
        transaction.mNote = `object`.optNullableString(JSONDatabase.Transaction.NOTE)
        transaction.mSaving = cacheSavings[`object`.optNullableString(JSONDatabase.Transaction.SAVING)]
        transaction.mDebt = cacheDebts[`object`.optNullableString(JSONDatabase.Transaction.DEBT)]
        transaction.mEvent = cacheEvents[`object`.optNullableString(JSONDatabase.Transaction.EVENT)]
        transaction.mRecurrence = cacheRecurrentTransactions[`object`.optNullableString(JSONDatabase.Transaction.RECURRENCE)]
        transaction.mConfirmed = `object`.optBoolean(JSONDatabase.Transaction.CONFIRMED, true)
        transaction.mCountInTotal = `object`.optBoolean(JSONDatabase.Transaction.COUNT_IN_TOTAL, true)
        transaction.mTag = `object`.optNullableString(JSONDatabase.Transaction.TAG)
        transaction.mUUID = `object`.optNullableString(JSONDatabase.Transaction.ID)
        transaction.mLastEdit = `object`.optLong(JSONDatabase.Transaction.LAST_EDIT, 0L)
        transaction.mDeleted = `object`.optBoolean(JSONDatabase.Transaction.DELETED, false)
        return transaction
    }

    fun getTransactionPerson(`object`: JSONObject): TransactionPerson {
        val transactionPerson = TransactionPerson()
        transactionPerson.mTransaction = cacheTransactions[`object`.optNullableString(JSONDatabase.TransactionPeople.TRANSACTION)]
        transactionPerson.mPerson = cachePeople[`object`.optNullableString(JSONDatabase.TransactionPeople.PERSON)]
        transactionPerson.mUUID = `object`.optNullableString(JSONDatabase.TransactionPeople.ID)
        transactionPerson.mLastEdit = `object`.optLong(JSONDatabase.TransactionPeople.LAST_EDIT, 0L)
        transactionPerson.mDeleted = `object`.optBoolean(JSONDatabase.TransactionPeople.DELETED, false)
        return transactionPerson
    }

    fun getTransactionModel(`object`: JSONObject): TransactionModel {
        val transactionModel = TransactionModel()
        transactionModel.mMoney = `object`.optLong(JSONDatabase.TransactionModel.MONEY, 0L)
        transactionModel.mDescription = `object`.optNullableString(JSONDatabase.TransactionModel.DESCRIPTION)
        transactionModel.mCategory = cacheCategories[`object`.optNullableString(JSONDatabase.TransactionModel.CATEGORY)]
        transactionModel.mDirection = `object`.optInt(JSONDatabase.TransactionModel.DIRECTION, 0)
        transactionModel.mWallet = cacheWallets[`object`.optNullableString(JSONDatabase.TransactionModel.WALLET)]
        transactionModel.mPlace = cachePlaces[`object`.optNullableString(JSONDatabase.TransactionModel.PLACE)]
        transactionModel.mNote = `object`.optNullableString(JSONDatabase.TransactionModel.NOTE)
        transactionModel.mEvent = cacheEvents[`object`.optNullableString(JSONDatabase.TransactionModel.EVENT)]
        transactionModel.mConfirmed = `object`.optBoolean(JSONDatabase.TransactionModel.CONFIRMED, true)
        transactionModel.mCountInTotal = `object`.optBoolean(JSONDatabase.TransactionModel.COUNT_IN_TOTAL, true)
        transactionModel.mTag = `object`.optNullableString(JSONDatabase.TransactionModel.TAG)
        transactionModel.mUUID = `object`.optNullableString(JSONDatabase.TransactionModel.ID)
        transactionModel.mLastEdit = `object`.optLong(JSONDatabase.TransactionModel.LAST_EDIT, 0L)
        transactionModel.mDeleted = `object`.optBoolean(JSONDatabase.TransactionModel.DELETED, false)
        return transactionModel
    }

    fun getTransfer(`object`: JSONObject): Transfer {
        val transfer = Transfer()
        transfer.mDescription = `object`.optNullableString(JSONDatabase.Transfer.DESCRIPTION)
        transfer.mDate = `object`.optNullableString(JSONDatabase.Transfer.DATE)
        transfer.mTransactionFrom = cacheTransactions[`object`.optNullableString(JSONDatabase.Transfer.FROM)]
        transfer.mTransactionTo = cacheTransactions[`object`.optNullableString(JSONDatabase.Transfer.TO)]
        transfer.mTransactionTax = cacheTransactions[`object`.optNullableString(JSONDatabase.Transfer.TAX)]
        transfer.mNote = `object`.optNullableString(JSONDatabase.Transfer.NOTE)
        transfer.mPlace = cachePlaces[`object`.optNullableString(JSONDatabase.Transfer.PLACE)]
        transfer.mEvent = cacheEvents[`object`.optNullableString(JSONDatabase.Transfer.EVENT)]
        transfer.mRecurrence = cacheRecurrentTransfers[`object`.optNullableString(JSONDatabase.Transfer.RECURRENCE)]
        transfer.mConfirmed = `object`.optBoolean(JSONDatabase.Transfer.CONFIRMED, true)
        transfer.mCountInTotal = `object`.optBoolean(JSONDatabase.Transfer.COUNT_IN_TOTAL, true)
        transfer.mTag = `object`.optNullableString(JSONDatabase.Transfer.TAG)
        transfer.mUUID = `object`.optNullableString(JSONDatabase.Transfer.ID)
        transfer.mLastEdit = `object`.optLong(JSONDatabase.Transfer.LAST_EDIT, 0L)
        transfer.mDeleted = `object`.optBoolean(JSONDatabase.Transfer.DELETED, false)
        return transfer
    }

    fun getTransferPerson(`object`: JSONObject): TransferPerson {
        val transferPerson = TransferPerson()
        transferPerson.mTransfer = cacheTransfers[`object`.optNullableString(JSONDatabase.TransferPeople.TRANSFER)]
        transferPerson.mPerson = cachePeople[`object`.optNullableString(JSONDatabase.TransferPeople.PERSON)]
        transferPerson.mUUID = `object`.optNullableString(JSONDatabase.TransferPeople.ID)
        transferPerson.mLastEdit = `object`.optLong(JSONDatabase.TransferPeople.LAST_EDIT, 0L)
        transferPerson.mDeleted = `object`.optBoolean(JSONDatabase.TransferPeople.DELETED, false)
        return transferPerson
    }

    fun getTransferModel(`object`: JSONObject): TransferModel {
        val transferModel = TransferModel()
        transferModel.mDescription = `object`.optNullableString(JSONDatabase.TransferModel.DESCRIPTION)
        transferModel.mFromWallet = cacheWallets[`object`.optNullableString(JSONDatabase.TransferModel.WALLET_FROM)]
        transferModel.mToWallet = cacheWallets[`object`.optNullableString(JSONDatabase.TransferModel.WALLET_TO)]
        transferModel.mFromMoney = `object`.optLong(JSONDatabase.TransferModel.MONEY_FROM, 0L)
        transferModel.mToMoney = `object`.optLong(JSONDatabase.TransferModel.MONEY_TO, 0L)
        transferModel.mTaxMoney = `object`.optLong(JSONDatabase.TransferModel.MONEY_TAX, 0L)
        transferModel.mNote = `object`.optNullableString(JSONDatabase.TransferModel.NOTE)
        transferModel.mEvent = cacheEvents[`object`.optNullableString(JSONDatabase.TransferModel.EVENT)]
        transferModel.mPlace = cachePlaces[`object`.optNullableString(JSONDatabase.TransferModel.PLACE)]
        transferModel.mConfirmed = `object`.optBoolean(JSONDatabase.TransferModel.CONFIRMED, true)
        transferModel.mCountInTotal = `object`.optBoolean(JSONDatabase.TransferModel.COUNT_IN_TOTAL, true)
        transferModel.mTag = `object`.optNullableString(JSONDatabase.TransferModel.TAG)
        transferModel.mUUID = `object`.optNullableString(JSONDatabase.TransferModel.ID)
        transferModel.mLastEdit = `object`.optLong(JSONDatabase.TransferModel.LAST_EDIT, 0L)
        transferModel.mDeleted = `object`.optBoolean(JSONDatabase.TransferModel.DELETED, false)
        return transferModel
    }

    fun getAttachment(`object`: JSONObject): Attachment {
        val attachment = Attachment()
        attachment.mFile = `object`.optNullableString(JSONDatabase.Attachment.FILE)
        attachment.mName = `object`.optNullableString(JSONDatabase.Attachment.NAME)
        attachment.mType = `object`.optNullableString(JSONDatabase.Attachment.TYPE)
        attachment.mSize = `object`.optLong(JSONDatabase.Attachment.SIZE, 0L)
        attachment.mTag = `object`.optNullableString(JSONDatabase.Attachment.TAG)
        attachment.mUUID = `object`.optNullableString(JSONDatabase.Attachment.ID)
        attachment.mLastEdit = `object`.optLong(JSONDatabase.Attachment.LAST_EDIT, 0L)
        attachment.mDeleted = `object`.optBoolean(JSONDatabase.Attachment.DELETED, false)
        return attachment
    }

    fun getTransactionAttachment(`object`: JSONObject): TransactionAttachment {
        val transactionAttachment = TransactionAttachment()
        transactionAttachment.mTransaction = cacheTransactions[`object`.optNullableString(JSONDatabase.TransactionAttachment.TRANSACTION)]
        transactionAttachment.mAttachment = cacheAttachments[`object`.optNullableString(JSONDatabase.TransactionAttachment.ATTACHMENT)]
        transactionAttachment.mUUID = `object`.optNullableString(JSONDatabase.TransactionAttachment.ID)
        transactionAttachment.mLastEdit = `object`.optLong(JSONDatabase.TransactionAttachment.LAST_EDIT, 0L)
        transactionAttachment.mDeleted = `object`.optBoolean(JSONDatabase.TransactionAttachment.DELETED, false)
        return transactionAttachment
    }

    fun getTransferAttachment(`object`: JSONObject): TransferAttachment {
        val transferAttachment = TransferAttachment()
        transferAttachment.mTransfer = cacheTransfers[`object`.optNullableString(JSONDatabase.TransferAttachment.TRANSFER)]
        transferAttachment.mAttachment = cacheAttachments[`object`.optNullableString(JSONDatabase.TransferAttachment.ATTACHMENT)]
        transferAttachment.mUUID = `object`.optNullableString(JSONDatabase.TransferAttachment.ID)
        transferAttachment.mLastEdit = `object`.optLong(JSONDatabase.TransferAttachment.LAST_EDIT, 0L)
        transferAttachment.mDeleted = `object`.optBoolean(JSONDatabase.TransferAttachment.DELETED, false)
        return transferAttachment
    }
}
