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

/**
 * This class contains all the static strings that defines the raw SQL database structure.
 * The access is granted only to the current package because for a better management of the
 * internal items, the content provider will serve the cursor with a different structure.
 * For a more detailed explanation please look at [Contract].
 */
internal object Schema {

    /**
     * This class defines the common columns for all SQL tables.
     * Those columns are necessary for future updates in case of creation of a SyncAdapter.
     */
    open class BaseTable {
        companion object {
            const val UUID: String = "uuid"
            const val LAST_EDIT: String = "last_edit"
            const val DELETED: String = "deleted"
        }
    }

    object Alias {
        const val TOTAL_MONEY: String = "total_money"
        const val PROGRESS: String = "progress"
        const val CATEGORY_GROUP_ID: String = "category_group_id"
        const val CATEGORY_GROUP_NAME: String = "category_group_name"
        const val CATEGORY_GROUP_INDEX: String = "category_group_index"
    }

    object Currency : BaseTable() {
        const val TABLE: String = "currencies"
        const val NAME: String = "currency_name"
        const val ISO: String = "currency_iso"
        const val SYMBOL: String = "currency_symbol"
        const val DECIMALS: String = "currency_decimals"
        const val FAVOURITE: String = "currency_favourite"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Wallet : BaseTable() {
        const val TABLE: String = "wallets"
        const val ID: String = "wallet_id"
        const val NAME: String = "wallet_name"
        const val ICON: String = "wallet_icon"
        const val CURRENCY: String = "wallet_currency"
        const val NOTE: String = "wallet_note"
        const val START_MONEY: String = "wallet_start_money"
        const val COUNT_IN_TOTAL: String = "wallet_count_in_total"
        const val ARCHIVED: String = "wallet_archived"
        const val TAG: String = "wallet_tag"
        const val INDEX: String = "wallet_index"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Category : BaseTable() {
        const val TABLE: String = "categories"
        const val ID: String = "category_id"
        const val NAME: String = "category_name"
        const val ICON: String = "category_icon"
        const val TYPE: String = "category_type"
        const val PARENT: String = "category_parent"
        const val TAG: String = "category_tag"
        const val SHOW_REPORT: String = "category_show_report"
        const val INDEX: String = "category_index"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object CategoryRule : BaseTable() {
        const val TABLE: String = "category_rules"
        const val ID: String = "rule_id"
        const val PATTERN: String = "rule_pattern"
        const val CATEGORY: String = "rule_category"
        const val INDEX: String = "rule_index"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Event : BaseTable() {
        const val TABLE: String = "events"
        const val ID: String = "event_id"
        const val NAME: String = "event_name"
        const val ICON: String = "event_icon"
        const val NOTE: String = "event_note"
        const val START_DATE: String = "event_start_date"
        const val END_DATE: String = "event_end_date"
        const val TAG: String = "event_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Place : BaseTable() {
        const val TABLE: String = "places"
        const val ID: String = "place_id"
        const val NAME: String = "place_name"
        const val ICON: String = "place_icon"
        const val ADDRESS: String = "place_address"
        const val LATITUDE: String = "place_latitude"
        const val LONGITUDE: String = "place_longitude"
        const val TAG: String = "place_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Person : BaseTable() {
        const val TABLE: String = "people"
        const val ID: String = "person_id"
        const val NAME: String = "person_name"
        const val ICON: String = "person_icon"
        const val NOTE: String = "person_note"
        const val TAG: String = "person_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object EventPeople : BaseTable() {
        const val TABLE: String = "event_people"
        const val EVENT: String = "_event"
        const val PERSON: String = "_person"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Debt : BaseTable() {
        const val TABLE: String = "debts"
        const val ID: String = "debt_id"
        const val TYPE: String = "debt_type"
        const val ICON: String = "debt_icon"
        const val DESCRIPTION: String = "debt_description"
        const val DATE: String = "debt_date"
        const val EXPIRATION_DATE: String = "debt_expiration_date"
        const val WALLET: String = "debt_wallet"
        const val NOTE: String = "debt_note"
        const val PLACE: String = "debt_place"
        const val MONEY: String = "debt_money"
        const val ARCHIVED: String = "debt_archived"
        const val TAG: String = "debt_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object DebtPeople : BaseTable() {
        const val TABLE: String = "debt_people"
        const val DEBT: String = "_debt"
        const val PERSON: String = "_person"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Budget : BaseTable() {
        const val TABLE: String = "budgets"
        const val ID: String = "budget_id"
        const val TYPE: String = "budget_type"
        const val CATEGORY: String = "budget_category"
        const val START_DATE: String = "budget_start_date"
        const val END_DATE: String = "budget_end_date"
        const val MONEY: String = "budget_money"
        const val CURRENCY: String = "budget_currency"
        const val RULE: String = "budget_rule"
        const val RULE_START: String = "budget_rule_start"
        const val TAG: String = "budget_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object BudgetWallet : BaseTable() {
        const val TABLE: String = "budget_wallets"
        const val BUDGET: String = "_budget"
        const val WALLET: String = "_wallet"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object BudgetCategory : BaseTable() {
        const val TABLE: String = "budget_categories"
        const val BUDGET: String = "_budget"
        const val CATEGORY: String = "_category"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Saving : BaseTable() {
        const val TABLE: String = "savings"
        const val ID: String = "saving_id"
        const val DESCRIPTION: String = "saving_description"
        const val ICON: String = "saving_icon"
        const val START_MONEY: String = "saving_start_money"
        const val END_MONEY: String = "saving_end_money"
        const val WALLET: String = "saving_wallet"
        const val END_DATE: String = "saving_end_date"
        const val COMPLETE: String = "saving_complete"
        const val NOTE: String = "saving_note"
        const val TAG: String = "saving_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Transaction : BaseTable() {
        const val TABLE: String = "transactions"
        const val ID: String = "transaction_id"
        const val MONEY: String = "transaction_money"
        const val DATE: String = "transaction_date"
        const val DESCRIPTION: String = "transaction_description"
        const val CATEGORY: String = "transaction_category"
        const val DIRECTION: String = "transaction_direction"
        const val TYPE: String = "transaction_type"
        const val WALLET: String = "transaction_wallet"
        const val PLACE: String = "transaction_place"
        const val NOTE: String = "transaction_note"
        const val SAVING: String = "transaction_saving"
        const val DEBT: String = "transaction_debt"
        const val EVENT: String = "transaction_event"
        const val RECURRENCE: String = "transaction_recurrence"
        const val CONFIRMED: String = "transaction_confirmed"
        const val COUNT_IN_TOTAL: String = "transaction_count_in_total"
        const val TAG: String = "transaction_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransactionPeople : BaseTable() {
        const val TABLE: String = "transaction_people"
        const val TRANSACTION: String = "_transaction"
        const val PERSON: String = "_person"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Transfer : BaseTable() {
        const val TABLE: String = "transfers"
        const val ID: String = "transfer_id"
        const val DESCRIPTION: String = "transfer_description"
        const val DATE: String = "transfer_date"
        const val TRANSACTION_FROM: String = "transfer_transaction_from"
        const val TRANSACTION_TO: String = "transfer_transaction_to"
        const val TRANSACTION_TAX: String = "transfer_transaction_tax"
        const val NOTE: String = "transfer_note"
        const val PLACE: String = "transfer_place"
        const val EVENT: String = "transfer_event"
        const val RECURRENCE: String = "transaction_recurrence"
        const val CONFIRMED: String = "transfer_confirmed"
        const val COUNT_IN_TOTAL: String = "transfer_count_in_total"
        const val TAG: String = "transfer_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransferPeople : BaseTable() {
        const val TABLE: String = "transfer_people"
        const val TRANSFER: String = "_transfer"
        const val PERSON: String = "_person"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransactionModel : BaseTable() {
        const val TABLE: String = "transaction_models"
        const val ID: String = "model_id"
        const val MONEY: String = "model_transaction_money"
        const val DESCRIPTION: String = "model_transaction_description"
        const val CATEGORY: String = "model_transaction_category"
        const val DIRECTION: String = "model_transaction_direction"
        const val WALLET: String = "model_transaction_wallet"
        const val PLACE: String = "model_transaction_place"
        const val NOTE: String = "model_transaction_note"
        const val EVENT: String = "model_transaction_event"
        const val CONFIRMED: String = "model_transaction_confirmed"
        const val COUNT_IN_TOTAL: String = "model_transaction_count_in_total"
        const val TAG: String = "model_transaction_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransferModel : BaseTable() {
        const val TABLE: String = "transfer_models"
        const val ID: String = "model_id"
        const val DESCRIPTION: String = "model_transfer_description"
        const val WALLET_FROM: String = "model_transfer_from_wallet"
        const val WALLET_TO: String = "model_transfer_to_wallet"
        const val MONEY_FROM: String = "model_transfer_from_money"
        const val MONEY_TO: String = "model_transfer_to_money"
        const val MONEY_TAX: String = "model_transfer_tax_money"
        const val NOTE: String = "model_transfer_note"
        const val EVENT: String = "model_transfer_event"
        const val PLACE: String = "model_transfer_place"
        const val CONFIRMED: String = "model_transfer_confirmed"
        const val COUNT_IN_TOTAL: String = "model_transfer_count_in_total"
        const val TAG: String = "model_transfer_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object RecurrentTransaction : BaseTable() {
        const val TABLE: String = "recurrent_transactions"
        const val ID: String = "recurrent_transaction_id"
        const val MONEY: String = "recurrent_transaction_money"
        const val DESCRIPTION: String = "recurrent_transaction_description"
        const val CATEGORY: String = "recurrent_transaction_category"
        const val DIRECTION: String = "recurrent_transaction_direction"
        const val WALLET: String = "recurrent_transaction_wallet"
        const val PLACE: String = "recurrent_transaction_place"
        const val NOTE: String = "recurrent_transaction_note"
        const val EVENT: String = "recurrent_transaction_event"
        const val CONFIRMED: String = "recurrent_transaction_confirmed"
        const val COUNT_IN_TOTAL: String = "recurrent_transaction_count_in_total"
        const val START_DATE: String = "recurrent_transaction_start_date"
        const val LAST_OCCURRENCE: String = "recurrent_transaction_last_occurrence"
        const val NEXT_OCCURRENCE: String = "recurrent_transaction_next_occurrence"
        const val RULE: String = "recurrent_transaction_rule"
        const val TAG: String = "recurrent_transaction_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object RecurrentTransfer : BaseTable() {
        const val TABLE: String = "recurrent_transfers"
        const val ID: String = "recurrent_transfer_id"
        const val DESCRIPTION: String = "recurrent_transfer_description"
        const val WALLET_FROM: String = "recurrent_transfer_from_wallet"
        const val WALLET_TO: String = "recurrent_transfer_to_wallet"
        const val MONEY_FROM: String = "recurrent_transfer_from_money"
        const val MONEY_TO: String = "recurrent_transfer_to_money"
        const val MONEY_TAX: String = "recurrent_transfer_tax_money"
        const val NOTE: String = "recurrent_transfer_note"
        const val EVENT: String = "recurrent_transfer_event"
        const val PLACE: String = "recurrent_transfer_place"
        const val CONFIRMED: String = "recurrent_transfer_confirmed"
        const val COUNT_IN_TOTAL: String = "recurrent_transfer_count_in_total"
        const val START_DATE: String = "recurrent_transfer_start_date"
        const val LAST_OCCURRENCE: String = "recurrent_transfer_last_occurrence"
        const val NEXT_OCCURRENCE: String = "recurrent_transfer_next_occurrence"
        const val RULE: String = "recurrent_transfer_rule"
        const val TAG: String = "recurrent_transfer_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Attachment : BaseTable() {
        const val TABLE: String = "attachments"
        const val ID: String = "attachment_id"
        const val FILE: String = "attachment_file"
        const val NAME: String = "attachment_name"
        const val TYPE: String = "attachment_type"
        const val SIZE: String = "attachment_size"
        const val TAG: String = "attachment_tag"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransactionAttachment : BaseTable() {
        const val TABLE: String = "transaction_attachment"
        const val TRANSACTION: String = "_transaction"
        const val ATTACHMENT: String = "_attachment"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransferAttachment : BaseTable() {
        const val TABLE: String = "transfer_attachment"
        const val TRANSFER: String = "_transfer"
        const val ATTACHMENT: String = "_attachment"
        const val UUID: String = "uuid"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object CategoryType {
        const val INCOME: Int = 0
        const val EXPENSE: Int = 1
        const val SYSTEM: Int = 2
    }

    object CategoryTag {
        const val TRANSFER: String = "system::transfer"
        const val TRANSFER_TAX: String = "system::transfer_tax"
        const val DEBT: String = "system::debt"
        const val CREDIT: String = "system::credit"
        const val PAID_DEBT: String = "system::paid_debt"
        const val PAID_CREDIT: String = "system::paid_credit"
        const val TAX: String = "system::tax"
        const val SAVING_DEPOSIT: String = "system::deposit"
        const val SAVING_WITHDRAW: String = "system::withdraw"
    }

    object BudgetType {
        const val EXPENSES: Int = 0
        const val INCOMES: Int = 1
        const val CATEGORY: Int = 2
    }

    object Direction {
        const val INCOME: Int = 1
        const val EXPENSE: Int = 0
    }

    const val CREATE_TABLE_CURRENCY: String = "CREATE TABLE " + Currency.TABLE + " (" +
        Currency.ISO + " TEXT PRIMARY KEY, " +
        Currency.NAME + " TEXT NOT NULL, " +
        Currency.SYMBOL + " TEXT, " +
        Currency.DECIMALS + " INTEGER NOT NULL, " +
        Currency.FAVOURITE + " INTEGER NOT NULL DEFAULT 0, " +
        Currency.UUID + " TEXT NOT NULL UNIQUE, " +
        Currency.LAST_EDIT + " INTEGER NOT NULL, " +
        Currency.DELETED + " INTEGER NOT NULL DEFAULT 0" +
        ")"

    const val CREATE_TABLE_WALLET: String = "CREATE TABLE " + Wallet.TABLE + " (" +
        Wallet.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Wallet.NAME + " TEXT NOT NULL, " +
        Wallet.ICON + " TEXT, " +
        Wallet.CURRENCY + " TEXT NOT NULL, " +
        Wallet.START_MONEY + " INTEGER NOT NULL DEFAULT 0, " +
        Wallet.COUNT_IN_TOTAL + " INTEGER NOT NULL DEFAULT 1, " +
        Wallet.NOTE + " TEXT, " +
        Wallet.ARCHIVED + " INTEGER NOT NULL DEFAULT 0, " +
        Wallet.INDEX + " INTEGER NOT NULL DEFAULT 0, " +
        Wallet.TAG + " TEXT, " +
        Wallet.UUID + " TEXT NOT NULL UNIQUE, " +
        Wallet.LAST_EDIT + " INTEGER NOT NULL, " +
        Wallet.DELETED + " INTEGER NOT NULL DEFAULT 0" +
        ")"

    const val CREATE_TABLE_CATEGORY: String = "CREATE TABLE " + Category.TABLE + " (" +
        Category.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Category.NAME + " TEXT NOT NULL, " +
        Category.ICON + " TEXT NOT NULL, " +
        Category.TYPE + " INTEGER NOT NULL, " +
        Category.PARENT + " INTEGER, " +
        Category.SHOW_REPORT + " INTEGER NOT NULL DEFAULT 1, " +
        Category.INDEX + " INTEGER NOT NULL DEFAULT 0, " +
        Category.TAG + " TEXT, " +
        Category.UUID + " TEXT NOT NULL UNIQUE, " +
        Category.LAST_EDIT + " INTEGER NOT NULL, " +
        Category.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + Category.PARENT + ") REFERENCES " + Category.TABLE +
        "(" + Category.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    /**
     * The rules that name a category from what a transaction description holds. IF NOT EXISTS
     * because the upgrade that adds the table runs again after an older release is installed
     * over this database and then upgraded, which leaves the table in place and stamps the
     * version back.
     *
     * The category carries ON DELETE CASCADE, so deleting a category takes the rules that name
     * it with it. A rule holds no ledger figure, and nothing else reads it, so keeping one that
     * points at a category that is gone would only block the deletion.
     */
    const val CREATE_TABLE_CATEGORY_RULE: String = "CREATE TABLE IF NOT EXISTS " +
        CategoryRule.TABLE + " (" +
        CategoryRule.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        CategoryRule.PATTERN + " TEXT NOT NULL, " +
        CategoryRule.CATEGORY + " INTEGER NOT NULL, " +
        CategoryRule.INDEX + " INTEGER NOT NULL, " +
        CategoryRule.UUID + " TEXT NOT NULL UNIQUE, " +
        CategoryRule.LAST_EDIT + " INTEGER NOT NULL, " +
        CategoryRule.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + CategoryRule.CATEGORY + ") REFERENCES " + Category.TABLE +
        "(" + Category.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_EVENT: String = "CREATE TABLE " + Event.TABLE + " (" +
        Event.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Event.NAME + " TEXT NOT NULL, " +
        Event.ICON + " TEXT NOT NULL, " +
        Event.NOTE + " TEXT, " +
        Event.START_DATE + " DATETIME NOT NULL, " +
        Event.END_DATE + " DATETIME NOT NULL, " +
        Event.TAG + " TEXT, " +
        Event.UUID + " TEXT NOT NULL UNIQUE, " +
        Event.LAST_EDIT + " INTEGER NOT NULL, " +
        Event.DELETED + " INTEGER NOT NULL DEFAULT 0 " +
        ")"

    const val CREATE_TABLE_PLACE: String = "CREATE TABLE " + Place.TABLE + " (" +
        Place.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Place.NAME + " TEXT NOT NULL, " +
        Place.ICON + " TEXT NOT NULL, " +
        Place.ADDRESS + " TEXT, " +
        Place.LATITUDE + " REAL, " +
        Place.LONGITUDE + " REAL, " +
        Place.TAG + " TEXT, " +
        Place.UUID + " TEXT NOT NULL UNIQUE, " +
        Place.LAST_EDIT + " INTEGER NOT NULL, " +
        Place.DELETED + " INTEGER NOT NULL DEFAULT 0 " +
        ")"

    const val CREATE_TABLE_PERSON: String = "CREATE TABLE " + Person.TABLE + " (" +
        Person.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Person.NAME + " TEXT NOT NULL, " +
        Person.ICON + " TEXT NOT NULL, " +
        Person.NOTE + " TEXT, " +
        Person.TAG + " TEXT, " +
        Person.UUID + " TEXT NOT NULL UNIQUE, " +
        Person.LAST_EDIT + " INTEGER NOT NULL, " +
        Person.DELETED + " INTEGER NOT NULL DEFAULT 0 " +
        ")"

    const val CREATE_TABLE_EVENT_PEOPLE: String = "CREATE TABLE " + EventPeople.TABLE + " (" +
        EventPeople.EVENT + " INTEGER NOT NULL, " +
        EventPeople.PERSON + " INTEGER NOT NULL, " +
        EventPeople.UUID + " TEXT NOT NULL UNIQUE, " +
        EventPeople.LAST_EDIT + " INTEGER NOT NULL, " +
        EventPeople.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY (" + EventPeople.EVENT + ", " + EventPeople.PERSON + ")," +
        "FOREIGN KEY (" + EventPeople.EVENT + ") REFERENCES " + Event.TABLE +
        "(" + Event.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + EventPeople.PERSON + ") REFERENCES " + Person.TABLE +
        "(" + Person.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_DEBT: String = "CREATE TABLE " + Debt.TABLE + " (" +
        Debt.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Debt.TYPE + " INTEGER NOT NULL, " +
        Debt.ICON + " TEXT NOT NULL, " +
        Debt.DESCRIPTION + " TEXT NOT NULL, " +
        Debt.DATE + " DATETIME NOT NULL, " +
        Debt.EXPIRATION_DATE + " DATETIME, " +
        Debt.WALLET + " INTEGER NOT NULL, " +
        Debt.NOTE + " TEXT, " +
        Debt.PLACE + " INTEGER, " +
        Debt.MONEY + " INTEGER NOT NULL, " +
        Debt.ARCHIVED + " INTEGER NOT NULL DEFAULT 0, " +
        Debt.TAG + " TEXT, " +
        Debt.UUID + " TEXT NOT NULL UNIQUE, " +
        Debt.LAST_EDIT + " INTEGER NOT NULL, " +
        Debt.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + Debt.WALLET + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + Debt.PLACE + ") REFERENCES " + Place.TABLE +
        "(" + Place.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL " +
        ")"

    const val CREATE_TABLE_DEBT_PEOPLE: String = "CREATE TABLE " + DebtPeople.TABLE + " (" +
        DebtPeople.DEBT + " INTEGER NOT NULL, " +
        DebtPeople.PERSON + " INTEGER NOT NULL, " +
        DebtPeople.UUID + " TEXT NOT NULL UNIQUE, " +
        DebtPeople.LAST_EDIT + " INTEGER NOT NULL, " +
        DebtPeople.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY (" + DebtPeople.DEBT + ", " + DebtPeople.PERSON + ")," +
        "FOREIGN KEY (" + DebtPeople.DEBT + ") REFERENCES " + Debt.TABLE +
        "(" + Debt.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + DebtPeople.PERSON + ") REFERENCES " + Person.TABLE +
        "(" + Person.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_BUDGET: String = "CREATE TABLE " + Budget.TABLE + " (" +
        Budget.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Budget.TYPE + " INTEGER NOT NULL, " +
        Budget.CATEGORY + " INTEGER, " +
        Budget.START_DATE + " DATETIME NOT NULL, " +
        Budget.END_DATE + " DATETIME NOT NULL, " +
        Budget.MONEY + " INTEGER NOT NULL, " +
        Budget.CURRENCY + " TEXT NOT NULL, " +
        Budget.RULE + " TEXT, " +
        Budget.RULE_START + " DATETIME, " +
        Budget.TAG + " TEXT, " +
        Budget.UUID + " TEXT NOT NULL UNIQUE, " +
        Budget.LAST_EDIT + " INTEGER NOT NULL, " +
        Budget.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + Budget.CATEGORY + ") REFERENCES " + Category.TABLE +
        "(" + Category.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_BUDGET_WALLET: String = "CREATE TABLE " + BudgetWallet.TABLE + " (" +
        BudgetWallet.BUDGET + " INTEGER NOT NULL, " +
        BudgetWallet.WALLET + " INTEGER NOT NULL, " +
        BudgetWallet.UUID + " TEXT NOT NULL UNIQUE, " +
        BudgetWallet.LAST_EDIT + " INTEGER NOT NULL, " +
        BudgetWallet.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY (" + BudgetWallet.BUDGET + ", " + BudgetWallet.WALLET + ")," +
        "FOREIGN KEY (" + BudgetWallet.BUDGET + ") REFERENCES " + Budget.TABLE +
        "(" + Budget.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + BudgetWallet.WALLET + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_BUDGET_CATEGORY: String = "CREATE TABLE IF NOT EXISTS " +
        BudgetCategory.TABLE + " (" +
        BudgetCategory.BUDGET + " INTEGER NOT NULL, " +
        BudgetCategory.CATEGORY + " INTEGER NOT NULL, " +
        BudgetCategory.UUID + " TEXT NOT NULL UNIQUE, " +
        BudgetCategory.LAST_EDIT + " INTEGER NOT NULL, " +
        BudgetCategory.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY (" + BudgetCategory.BUDGET + ", " + BudgetCategory.CATEGORY + ")," +
        "FOREIGN KEY (" + BudgetCategory.BUDGET + ") REFERENCES " + Budget.TABLE +
        "(" + Budget.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + BudgetCategory.CATEGORY + ") REFERENCES " + Category.TABLE +
        "(" + Category.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_SAVING: String = "CREATE TABLE " + Saving.TABLE + " (" +
        Saving.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Saving.DESCRIPTION + " TEXT, " +
        Saving.ICON + " TEXT NOT NULL, " +
        Saving.START_MONEY + " INTEGER NOT NULL, " +
        Saving.END_MONEY + " INTEGER NOT NULL, " +
        Saving.WALLET + " INTEGER NOT NULL, " +
        Saving.END_DATE + " DATETIME, " +
        Saving.COMPLETE + " INTEGER NOT NULL DEFAULT 0, " +
        Saving.NOTE + " TEXT, " +
        Saving.TAG + " TEXT, " +
        Saving.UUID + " TEXT NOT NULL UNIQUE, " +
        Saving.LAST_EDIT + " INTEGER NOT NULL, " +
        Saving.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + Saving.WALLET + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_TRANSACTION: String = "CREATE TABLE " + Transaction.TABLE + " (" +
        Transaction.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Transaction.MONEY + " INTEGER NOT NULL, " +
        Transaction.DATE + " DATETIME NOT NULL, " +
        Transaction.DESCRIPTION + " TEXT, " +
        Transaction.CATEGORY + " INTEGER NOT NULL, " +
        Transaction.DIRECTION + " INTEGER NOT NULL, " +
        Transaction.TYPE + " INTEGER NOT NULL, " +
        Transaction.WALLET + " INTEGER NOT NULL, " +
        Transaction.PLACE + " INTEGER, " +
        Transaction.NOTE + " TEXT, " +
        Transaction.SAVING + " INTEGER, " +
        Transaction.DEBT + " INTEGER, " +
        Transaction.EVENT + " INTEGER, " +
        Transaction.RECURRENCE + " INTEGER, " +
        Transaction.CONFIRMED + " INTEGER NOT NULL DEFAULT 1, " +
        Transaction.COUNT_IN_TOTAL + " INTEGER NOT NULL DEFAULT 1, " +
        Transaction.TAG + " TEXT, " +
        Transaction.UUID + " TEXT NOT NULL UNIQUE, " +
        Transaction.LAST_EDIT + " INTEGER NOT NULL, " +
        Transaction.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + Transaction.CATEGORY + ") REFERENCES " + Category.TABLE +
        "(" + Category.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + Transaction.WALLET + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + Transaction.PLACE + ") REFERENCES " + Place.TABLE +
        "(" + Place.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + Transaction.SAVING + ") REFERENCES " + Saving.TABLE +
        "(" + Saving.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + Transaction.DEBT + ") REFERENCES " + Debt.TABLE +
        "(" + Debt.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + Transaction.EVENT + ") REFERENCES " + Event.TABLE +
        "(" + Event.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + Transaction.RECURRENCE + ") REFERENCES " + RecurrentTransaction.TABLE +
        "(" + RecurrentTransaction.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL " +
        ")"

    const val CREATE_TABLE_TRANSACTION_PEOPLE: String = "CREATE TABLE " + TransactionPeople.TABLE + " (" +
        TransactionPeople.TRANSACTION + " INTEGER NOT NULL, " +
        TransactionPeople.PERSON + " INTEGER NOT NULL, " +
        TransactionPeople.UUID + " TEXT NOT NULL UNIQUE, " +
        TransactionPeople.LAST_EDIT + " INTEGER NOT NULL, " +
        TransactionPeople.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY (" + TransactionPeople.TRANSACTION + ", " + TransactionPeople.PERSON + ")," +
        "FOREIGN KEY (" + TransactionPeople.TRANSACTION + ") REFERENCES " + Transaction.TABLE +
        "(" + Transaction.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + TransactionPeople.PERSON + ") REFERENCES " + Person.TABLE +
        "(" + Person.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_TRANSFER: String = "CREATE TABLE " + Transfer.TABLE + " (" +
        Transfer.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Transfer.DESCRIPTION + " TEXT, " +
        Transfer.DATE + " DATETIME NOT NULL, " +
        Transfer.TRANSACTION_FROM + " INTEGER NOT NULL, " +
        Transfer.TRANSACTION_TO + " INTEGER NOT NULL, " +
        Transfer.TRANSACTION_TAX + " INTEGER, " +
        Transfer.NOTE + " TEXT, " +
        Transfer.PLACE + " INTEGER, " +
        Transfer.EVENT + " INTEGER, " +
        Transfer.RECURRENCE + " INTEGER, " +
        Transfer.CONFIRMED + " INTEGER NOT NULL DEFAULT 1, " +
        Transfer.COUNT_IN_TOTAL + " INTEGER NOT NULL DEFAULT 1, " +
        Transfer.TAG + " TEXT, " +
        Transfer.UUID + " TEXT NOT NULL UNIQUE, " +
        Transfer.LAST_EDIT + " INTEGER NOT NULL, " +
        Transfer.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + Transfer.TRANSACTION_FROM + ") REFERENCES " + Transaction.TABLE +
        "(" + Transaction.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + Transfer.TRANSACTION_TO + ") REFERENCES " + Transaction.TABLE +
        "(" + Transaction.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + Transfer.TRANSACTION_TAX + ") REFERENCES " + Transaction.TABLE +
        "(" + Transaction.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + Transfer.PLACE + ") REFERENCES " + Place.TABLE +
        "(" + Place.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + Transfer.EVENT + ") REFERENCES " + Event.TABLE +
        "(" + Event.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + Transfer.RECURRENCE + ") REFERENCES " + RecurrentTransfer.TABLE +
        "(" + RecurrentTransfer.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL " +
        ")"

    const val CREATE_TABLE_TRANSFER_PEOPLE: String = "CREATE TABLE " + TransferPeople.TABLE + " (" +
        TransferPeople.TRANSFER + " INTEGER NOT NULL, " +
        TransferPeople.PERSON + " INTEGER NOT NULL, " +
        TransferPeople.UUID + " TEXT NOT NULL UNIQUE, " +
        TransferPeople.LAST_EDIT + " INTEGER NOT NULL, " +
        TransferPeople.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY (" + TransferPeople.TRANSFER + ", " + TransferPeople.PERSON + ")," +
        "FOREIGN KEY (" + TransferPeople.TRANSFER + ") REFERENCES " + Transfer.TABLE +
        "(" + Transfer.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + TransferPeople.PERSON + ") REFERENCES " + Person.TABLE +
        "(" + Person.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_TRANSACTION_MODEL: String = "CREATE TABLE " + TransactionModel.TABLE + " (" +
        TransactionModel.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        TransactionModel.MONEY + " INTEGER NOT NULL, " +
        TransactionModel.DESCRIPTION + " TEXT, " +
        TransactionModel.CATEGORY + " INTEGER NOT NULL, " +
        TransactionModel.DIRECTION + " INTEGER NOT NULL, " +
        TransactionModel.WALLET + " INTEGER NOT NULL, " +
        TransactionModel.PLACE + " INTEGER, " +
        TransactionModel.NOTE + " TEXT, " +
        TransactionModel.EVENT + " INTEGER, " +
        TransactionModel.CONFIRMED + " INTEGER NOT NULL DEFAULT 1, " +
        TransactionModel.COUNT_IN_TOTAL + " INTEGER NOT NULL DEFAULT 1, " +
        TransactionModel.TAG + " TEXT, " +
        TransactionModel.UUID + " TEXT NOT NULL UNIQUE, " +
        TransactionModel.LAST_EDIT + " INTEGER NOT NULL, " +
        TransactionModel.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + TransactionModel.CATEGORY + ") REFERENCES " + Category.TABLE +
        "(" + Category.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + TransactionModel.WALLET + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + TransactionModel.PLACE + ") REFERENCES " + Place.TABLE +
        "(" + Place.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + TransactionModel.EVENT + ") REFERENCES " + Event.TABLE +
        "(" + Event.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL " +
        ")"

    const val CREATE_TABLE_TRANSFER_MODEL: String = "CREATE TABLE " + TransferModel.TABLE + " (" +
        TransferModel.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        TransferModel.DESCRIPTION + " TEXT, " +
        TransferModel.WALLET_FROM + " INTEGER NOT NULL, " +
        TransferModel.WALLET_TO + " INTEGER NOT NULL, " +
        TransferModel.MONEY_FROM + " INTEGER NOT NULL, " +
        TransferModel.MONEY_TO + " INTEGER NOT NULL, " +
        TransferModel.MONEY_TAX + " INTEGER, " +
        TransferModel.NOTE + " TEXT, " +
        TransferModel.EVENT + " INTEGER, " +
        TransferModel.PLACE + " INTEGER, " +
        TransferModel.CONFIRMED + " INTEGER NOT NULL DEFAULT 1, " +
        TransferModel.COUNT_IN_TOTAL + " INTEGER NOT NULL DEFAULT 1, " +
        TransferModel.TAG + " TEXT, " +
        TransferModel.UUID + " TEXT NOT NULL UNIQUE, " +
        TransferModel.LAST_EDIT + " INTEGER NOT NULL, " +
        TransferModel.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + TransferModel.WALLET_FROM + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + TransferModel.WALLET_TO + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + TransferModel.EVENT + ") REFERENCES " + Event.TABLE +
        "(" + Event.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + TransferModel.PLACE + ") REFERENCES " + Place.TABLE +
        "(" + Place.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL " +
        ")"

    const val CREATE_TABLE_RECURRENT_TRANSACTION: String = "CREATE TABLE " + RecurrentTransaction.TABLE + " (" +
        RecurrentTransaction.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        RecurrentTransaction.MONEY + " INTEGER NOT NULL, " +
        RecurrentTransaction.DESCRIPTION + " TEXT, " +
        RecurrentTransaction.CATEGORY + " INTEGER NOT NULL, " +
        RecurrentTransaction.DIRECTION + " INTEGER NOT NULL, " +
        RecurrentTransaction.WALLET + " INTEGER NOT NULL, " +
        RecurrentTransaction.PLACE + " INTEGER, " +
        RecurrentTransaction.NOTE + " TEXT, " +
        RecurrentTransaction.EVENT + " INTEGER, " +
        RecurrentTransaction.CONFIRMED + " INTEGER NOT NULL DEFAULT 1, " +
        RecurrentTransaction.COUNT_IN_TOTAL + " INTEGER NOT NULL DEFAULT 1, " +
        RecurrentTransaction.START_DATE + " DATETIME NOT NULL, " +
        RecurrentTransaction.LAST_OCCURRENCE + " DATETIME NOT NULL, " +
        RecurrentTransaction.NEXT_OCCURRENCE + " DATETIME, " +
        RecurrentTransaction.RULE + " TEXT NOT NULL, " +
        RecurrentTransaction.TAG + " TEXT, " +
        RecurrentTransaction.UUID + " TEXT NOT NULL UNIQUE, " +
        RecurrentTransaction.LAST_EDIT + " INTEGER NOT NULL, " +
        RecurrentTransaction.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + RecurrentTransaction.CATEGORY + ") REFERENCES " + Category.TABLE +
        "(" + Category.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + RecurrentTransaction.WALLET + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + RecurrentTransaction.PLACE + ") REFERENCES " + Place.TABLE +
        "(" + Place.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + RecurrentTransaction.EVENT + ") REFERENCES " + Event.TABLE +
        "(" + Event.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL " +
        ")"

    const val CREATE_TABLE_RECURRENT_TRANSFER: String = "CREATE TABLE " + RecurrentTransfer.TABLE + " (" +
        RecurrentTransfer.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        RecurrentTransfer.DESCRIPTION + " TEXT, " +
        RecurrentTransfer.WALLET_FROM + " INTEGER NOT NULL, " +
        RecurrentTransfer.WALLET_TO + " INTEGER NOT NULL, " +
        RecurrentTransfer.MONEY_FROM + " INTEGER NOT NULL, " +
        RecurrentTransfer.MONEY_TO + " INTEGER NOT NULL, " +
        RecurrentTransfer.MONEY_TAX + " INTEGER, " +
        RecurrentTransfer.NOTE + " TEXT, " +
        RecurrentTransfer.EVENT + " INTEGER, " +
        RecurrentTransfer.PLACE + " INTEGER, " +
        RecurrentTransfer.CONFIRMED + " INTEGER NOT NULL DEFAULT 1, " +
        RecurrentTransfer.COUNT_IN_TOTAL + " INTEGER NOT NULL DEFAULT 1, " +
        RecurrentTransfer.START_DATE + " DATETIME NOT NULL, " +
        RecurrentTransfer.LAST_OCCURRENCE + " DATETIME NOT NULL, " +
        RecurrentTransfer.NEXT_OCCURRENCE + " DATETIME, " +
        RecurrentTransfer.RULE + " TEXT NOT NULL, " +
        RecurrentTransfer.TAG + " TEXT, " +
        RecurrentTransfer.UUID + " TEXT NOT NULL UNIQUE, " +
        RecurrentTransfer.LAST_EDIT + " INTEGER NOT NULL, " +
        RecurrentTransfer.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "FOREIGN KEY (" + RecurrentTransfer.WALLET_FROM + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + RecurrentTransfer.WALLET_TO + ") REFERENCES " + Wallet.TABLE +
        "(" + Wallet.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + RecurrentTransfer.EVENT + ") REFERENCES " + Event.TABLE +
        "(" + Event.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL, " +
        "FOREIGN KEY (" + RecurrentTransfer.PLACE + ") REFERENCES " + Place.TABLE +
        "(" + Place.ID + ") ON UPDATE NO ACTION ON DELETE SET NULL " +
        ")"

    const val CREATE_TABLE_ATTACHMENT: String = "CREATE TABLE " + Attachment.TABLE + " (" +
        Attachment.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        Attachment.FILE + " TEXT NOT NULL, " +
        Attachment.NAME + " TEXT NOT NULL, " +
        Attachment.TYPE + " TEXT, " +
        Attachment.SIZE + " INTEGER NOT NULL, " +
        Attachment.TAG + " TEXT, " +
        Attachment.UUID + " TEXT NOT NULL UNIQUE, " +
        Attachment.LAST_EDIT + " INTEGER NOT NULL, " +
        Attachment.DELETED + " INTEGER NOT NULL DEFAULT 0 " +
        ")"

    const val CREATE_TABLE_TRANSACTION_ATTACHMENT: String = "CREATE TABLE " + TransactionAttachment.TABLE + " (" +
        TransactionAttachment.TRANSACTION + " INTEGER NOT NULL, " +
        TransactionAttachment.ATTACHMENT + " INTEGER NOT NULL, " +
        TransactionAttachment.UUID + " TEXT NOT NULL UNIQUE, " +
        TransactionAttachment.LAST_EDIT + " INTEGER NOT NULL, " +
        TransactionAttachment.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY (" + TransactionAttachment.TRANSACTION + ", " + TransactionAttachment.ATTACHMENT + ")," +
        "FOREIGN KEY (" + TransactionAttachment.TRANSACTION + ") REFERENCES " + Transaction.TABLE +
        "(" + Transaction.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + TransactionAttachment.ATTACHMENT + ") REFERENCES " + Attachment.TABLE +
        "(" + Attachment.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_TABLE_TRANSFER_ATTACHMENT: String = "CREATE TABLE " + TransferAttachment.TABLE + " (" +
        TransferAttachment.TRANSFER + " INTEGER NOT NULL, " +
        TransferAttachment.ATTACHMENT + " INTEGER NOT NULL, " +
        TransferAttachment.UUID + " TEXT NOT NULL UNIQUE, " +
        TransferAttachment.LAST_EDIT + " INTEGER NOT NULL, " +
        TransferAttachment.DELETED + " INTEGER NOT NULL DEFAULT 0, " +
        "PRIMARY KEY (" + TransferAttachment.TRANSFER + ", " + TransferAttachment.ATTACHMENT + ")," +
        "FOREIGN KEY (" + TransferAttachment.TRANSFER + ") REFERENCES " + Transfer.TABLE +
        "(" + Transfer.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE, " +
        "FOREIGN KEY (" + TransactionAttachment.ATTACHMENT + ") REFERENCES " + Attachment.TABLE +
        "(" + Attachment.ID + ") ON UPDATE NO ACTION ON DELETE CASCADE " +
        ")"

    const val CREATE_CATEGORY_INDEX_COLUMN: String = "ALTER TABLE " +
        Category.TABLE + " ADD COLUMN " + Category.INDEX + " INTEGER NOT NULL DEFAULT 0"

    const val CREATE_WALLET_INDEX_COLUMN: String = "ALTER TABLE " +
        Wallet.TABLE + " ADD COLUMN " + Wallet.INDEX + " INTEGER NOT NULL DEFAULT 0"

    const val CREATE_BUDGET_RULE_COLUMN: String = "ALTER TABLE " +
        Budget.TABLE + " ADD COLUMN " + Budget.RULE + " TEXT"

    const val CREATE_BUDGET_RULE_START_COLUMN: String = "ALTER TABLE " +
        Budget.TABLE + " ADD COLUMN " + Budget.RULE_START + " DATETIME"

    /**
     * Fills the join table from the single category a budget used to carry. It covers only the
     * budgets that have no live row yet, which is what makes it safe to run a second time, a
     * budget that already covers categories keeps them instead of gaining whatever the column
     * happens to say now. See the upgrade in SQLDatabase for when that second run happens.
     *
     * REPLACE and not IGNORE because a budget can hold a row for this very category that is
     * flagged deleted, which is what changing a budget away from covering categories and back
     * leaves behind. IGNORE would drop the insert on the composite key and leave the budget with
     * a column and no live category at all.
     */
    const val FILL_BUDGET_CATEGORY_FROM_COLUMN: String = "INSERT OR REPLACE INTO " +
        BudgetCategory.TABLE + " (" + BudgetCategory.BUDGET + ", " + BudgetCategory.CATEGORY +
        ", " + BudgetCategory.UUID + ", " + BudgetCategory.LAST_EDIT + ", " +
        BudgetCategory.DELETED + ") SELECT b." + Budget.ID + ", b." + Budget.CATEGORY +
        ", b." + Budget.UUID + " || '-category-' || b." + Budget.CATEGORY +
        ", strftime('%s','now') * 1000, 0 FROM " + Budget.TABLE + " AS b WHERE b." +
        Budget.CATEGORY + " IS NOT NULL AND b." + Budget.DELETED + " = 0 AND b." + Budget.ID +
        " NOT IN (SELECT " + BudgetCategory.BUDGET + " FROM " + BudgetCategory.TABLE +
        " WHERE " + BudgetCategory.DELETED + " = 0)"

    /**
     * Points the single column at the first of the categories a budget covers, once the join
     * table is filled. It corrects a column, and never gives one to a budget that has none:
     * a budget carrying no category has had its type changed away from covering them, and
     * writing one back would leave a row saying it covers a category it does not.
     *
     * What it corrects is a release that predates the table writing the column while the table
     * kept the real set. Leaving that would put a category no screen shows behind the budget,
     * chosen for its icon and refusing to be deleted, with nothing to say why.
     */
    const val ALIGN_BUDGET_CATEGORY_COLUMN: String = "UPDATE " + Budget.TABLE +
        " SET " + Budget.CATEGORY + " = (SELECT MIN(" + BudgetCategory.CATEGORY + ") FROM " +
        BudgetCategory.TABLE + " WHERE " + BudgetCategory.BUDGET + " = " + Budget.TABLE + "." +
        Budget.ID + " AND " + BudgetCategory.DELETED + " = 0) WHERE " + Budget.CATEGORY +
        " IS NOT NULL AND " + Budget.ID + " IN (SELECT " + BudgetCategory.BUDGET + " FROM " +
        BudgetCategory.TABLE + " WHERE " + BudgetCategory.DELETED + " = 0)"

    /**
     * Flags the join rows of any budget that no longer covers categories at all. A release that
     * predates the table can change a budget's type without seeing them, and rows left live
     * there hold their categories against deletion while no screen shows the budget covering
     * anything. Flagged and not deleted, which is what an ordinary edit does to the same rows.
     * Nothing brings them back on its own, a budget turned back into one that covers categories
     * is saved through an editor that asks the user to pick them again.
     */
    const val CLEAR_BUDGET_CATEGORY_OF_OTHER_TYPES: String = "UPDATE " +
        BudgetCategory.TABLE + " SET " + BudgetCategory.DELETED + " = 1, " +
        BudgetCategory.LAST_EDIT + " = strftime('%s','now') * 1000 WHERE " +
        BudgetCategory.DELETED + " = 0 AND " + BudgetCategory.BUDGET + " IN (SELECT " +
        Budget.ID + " FROM " + Budget.TABLE + " WHERE " + Budget.TYPE + " != " +
        BudgetType.CATEGORY + ")"

    /**
     * Takes the single column off any budget that no longer covers categories, for the same
     * reason its rows are flagged. The column carries ON DELETE CASCADE, so one left behind on a
     * budget of another type both refuses its category deletion and puts that budget behind a
     * cascade it does not claim.
     */
    const val CLEAR_BUDGET_COLUMN_OF_OTHER_TYPES: String = "UPDATE " +
        Budget.TABLE + " SET " + Budget.CATEGORY + " = NULL WHERE " + Budget.CATEGORY +
        " IS NOT NULL AND " + Budget.TYPE + " != " + BudgetType.CATEGORY
}
