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

internal object JSONDatabase {

    const val MIN_SUPPORTED_VERSION: Int = 1
    const val MAX_SUPPORTED_VERSION: Int = 4

    // version 3 adds the budget_categories array, so that a budget covering more than one
    // category keeps every one of them through a backup. A release that predates it refuses a
    // version 3 file by its header instead of failing part way through the arrays, which is what
    // it would do with an array it does not expect in the middle of the stream.
    // version 4 adds the category_rules array, so that the rules naming a category from a
    // description survive a restore and a move to another device. A release that predates it
    // refuses a version 4 file by its header instead of failing part way through the arrays.
    const val VERSION: Int = 4

    object Header {
        const val OBJECT: String = "header"
        const val VERSION_CODE: String = "version_code"
    }

    object Currency {
        const val ARRAY: String = "currencies"
        const val ISO: String = "iso"
        const val NAME: String = "name"
        const val SYMBOL: String = "symbol"
        const val DECIMALS: String = "decimals"
        const val FAVOURITE: String = "favourite"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Wallet {
        const val ARRAY: String = "wallets"
        const val ID: String = "id"
        const val NAME: String = "name"
        const val ICON: String = "icon"
        const val CURRENCY: String = "currency"
        const val START_MONEY: String = "start_money"
        const val COUNT_IN_TOTAL: String = "count_in_total"
        const val ARCHIVED: String = "archived"
        const val NOTE: String = "note"
        const val INDEX: String = "index"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Category {
        const val ARRAY: String = "categories"
        const val ID: String = "id"
        const val NAME: String = "name"
        const val ICON: String = "icon"
        const val TYPE: String = "type"
        const val PARENT: String = "parent"
        const val SHOW_REPORT: String = "show_report"
        const val INDEX: String = "index"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Event {
        const val ARRAY: String = "events"
        const val ID: String = "id"
        const val NAME: String = "name"
        const val ICON: String = "icon"
        const val NOTE: String = "note"
        const val START_DATE: String = "start_date"
        const val END_DATE: String = "end_date"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object CategoryRule {
        const val ARRAY: String = "category_rules"
        const val ID: String = "id"
        const val PATTERN: String = "pattern"
        const val CATEGORY: String = "category"
        const val INDEX: String = "index"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Place {
        const val ARRAY: String = "places"
        const val ID: String = "id"
        const val NAME: String = "name"
        const val ICON: String = "icon"
        const val ADDRESS: String = "address"
        const val LATITUDE: String = "latitude"
        const val LONGITUDE: String = "longitude"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Person {
        const val ARRAY: String = "people"
        const val ID: String = "id"
        const val NAME: String = "name"
        const val ICON: String = "icon"
        const val NOTE: String = "note"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object EventPeople {
        const val ARRAY: String = "event_people"
        const val ID: String = "id"
        const val EVENT: String = "event"
        const val PERSON: String = "person"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Debt {
        const val ARRAY: String = "debts"
        const val ID: String = "id"
        const val TYPE: String = "type"
        const val ICON: String = "icon"
        const val DESCRIPTION: String = "description"
        const val DATE: String = "date"
        const val EXPIRATION_DATE: String = "expiration_date"
        const val WALLET: String = "wallet"
        const val NOTE: String = "note"
        const val PLACE: String = "place"
        const val MONEY: String = "money"
        const val ARCHIVED: String = "archived"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object DebtPeople {
        const val ARRAY: String = "debt_people"
        const val ID: String = "id"
        const val DEBT: String = "debt"
        const val PERSON: String = "person"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Budget {
        const val ARRAY: String = "budgets"
        const val ID: String = "id"
        const val TYPE: String = "type"
        const val CATEGORY: String = "category"
        const val START_DATE: String = "start_date"
        const val END_DATE: String = "end_date"
        const val MONEY: String = "money"
        const val CURRENCY: String = "currency"
        const val RULE: String = "rule"
        const val RULE_START: String = "rule_start"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object BudgetWallet {
        const val ARRAY: String = "budget_wallets"
        const val ID: String = "id"
        const val BUDGET: String = "budget"
        const val WALLET: String = "wallet"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object BudgetCategory {
        const val ARRAY: String = "budget_categories"
        const val ID: String = "id"
        const val BUDGET: String = "budget"
        const val CATEGORY: String = "category"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Saving {
        const val ARRAY: String = "savings"
        const val ID: String = "id"
        const val DESCRIPTION: String = "description"
        const val ICON: String = "icon"
        const val START_MONEY: String = "start_money"
        const val END_MONEY: String = "end_money"
        const val WALLET: String = "wallet"
        const val END_DATE: String = "end_date"
        const val COMPLETE: String = "complete"
        const val NOTE: String = "note"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object RecurrentTransaction {
        const val ARRAY: String = "recurrent_transactions"
        const val ID: String = "id"
        const val MONEY: String = "money"
        const val DESCRIPTION: String = "description"
        const val CATEGORY: String = "category"
        const val DIRECTION: String = "direction"
        const val WALLET: String = "wallet"
        const val PLACE: String = "place"
        const val NOTE: String = "note"
        const val EVENT: String = "event"
        const val CONFIRMED: String = "confirmed"
        const val COUNT_IN_TOTAL: String = "count_in_total"
        const val START_DATE: String = "start_date"
        const val LAST_OCCURRENCE: String = "last_occurrence"
        const val NEXT_OCCURRENCE: String = "next_occurrence"
        const val RULE: String = "rule"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object RecurrentTransfer {
        const val ARRAY: String = "recurrent_transfers"
        const val ID: String = "id"
        const val DESCRIPTION: String = "description"
        const val WALLET_FROM: String = "from_wallet"
        const val WALLET_TO: String = "to_wallet"
        const val MONEY_FROM: String = "from_money"
        const val MONEY_TO: String = "to_money"
        const val MONEY_TAX: String = "tax_money"
        const val NOTE: String = "note"
        const val EVENT: String = "event"
        const val PLACE: String = "place"
        const val CONFIRMED: String = "confirmed"
        const val COUNT_IN_TOTAL: String = "count_in_total"
        const val START_DATE: String = "start_date"
        const val LAST_OCCURRENCE: String = "last_occurrence"
        const val NEXT_OCCURRENCE: String = "next_occurrence"
        const val RULE: String = "rule"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Transaction {
        const val ARRAY: String = "transactions"
        const val ID: String = "id"
        const val MONEY: String = "money"
        const val DATE: String = "date"
        const val DESCRIPTION: String = "description"
        const val CATEGORY: String = "category"
        const val DIRECTION: String = "direction"
        const val TYPE: String = "type"
        const val WALLET: String = "wallet"
        const val PLACE: String = "place"
        const val NOTE: String = "note"
        const val SAVING: String = "saving"
        const val DEBT: String = "debt"
        const val EVENT: String = "event"
        const val RECURRENCE: String = "recurrence"
        const val CONFIRMED: String = "confirmed"
        const val COUNT_IN_TOTAL: String = "count_in_total"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransactionPeople {
        const val ARRAY: String = "transaction_people"
        const val ID: String = "id"
        const val TRANSACTION: String = "transaction"
        const val PERSON: String = "person"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Transfer {
        const val ARRAY: String = "transfers"
        const val ID: String = "id"
        const val DESCRIPTION: String = "description"
        const val DATE: String = "date"
        const val FROM: String = "from"
        const val TO: String = "to"
        const val TAX: String = "tax"
        const val NOTE: String = "note"
        const val PLACE: String = "place"
        const val EVENT: String = "event"
        const val RECURRENCE: String = "recurrence"
        const val CONFIRMED: String = "confirmed"
        const val COUNT_IN_TOTAL: String = "count_in_total"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransferPeople {
        const val ARRAY: String = "transfer_people"
        const val ID: String = "id"
        const val TRANSFER: String = "transfer"
        const val PERSON: String = "person"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransactionModel {
        const val ARRAY: String = "transaction_models"
        const val ID: String = "id"
        const val MONEY: String = "money"
        const val DESCRIPTION: String = "description"
        const val CATEGORY: String = "category"
        const val DIRECTION: String = "direction"
        const val WALLET: String = "wallet"
        const val PLACE: String = "place"
        const val NOTE: String = "note"
        const val EVENT: String = "event"
        const val CONFIRMED: String = "confirmed"
        const val COUNT_IN_TOTAL: String = "count_in_total"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransferModel {
        const val ARRAY: String = "transfer_models"
        const val ID: String = "id"
        const val DESCRIPTION: String = "description"
        const val WALLET_FROM: String = "from_wallet"
        const val WALLET_TO: String = "to_wallet"
        const val MONEY_FROM: String = "from_money"
        const val MONEY_TO: String = "to_money"
        const val MONEY_TAX: String = "tax_money"
        const val NOTE: String = "note"
        const val EVENT: String = "event"
        const val PLACE: String = "place"
        const val CONFIRMED: String = "confirmed"
        const val COUNT_IN_TOTAL: String = "count_in_total"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object Attachment {
        const val ARRAY: String = "attachments"
        const val ID: String = "id"
        const val FILE: String = "file"
        const val NAME: String = "name"
        const val TYPE: String = "type"
        const val SIZE: String = "size"
        const val TAG: String = "tag"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransactionAttachment {
        const val ARRAY: String = "transaction_attachment"
        const val ID: String = "id"
        const val TRANSACTION: String = "transaction"
        const val ATTACHMENT: String = "attachment"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }

    object TransferAttachment {
        const val ARRAY: String = "transfer_attachment"
        const val ID: String = "id"
        const val TRANSFER: String = "transfer"
        const val ATTACHMENT: String = "attachment"
        const val LAST_EDIT: String = "last_edit"
        const val DELETED: String = "deleted"
    }
}
