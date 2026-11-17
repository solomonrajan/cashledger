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

package com.oriondev.moneywallet.storage.database.legacy

/**
 * Created by andrea on 02/11/18.
 */
internal object LegacyDatabaseSchema {

    const val DATABASE_NAME: String = "MoneyWallet"

    const val INDEX_HIDDEN_CATEGORY_TRANSFER: Int = -10
    const val INDEX_HIDDEN_CATEGORY_TRANSFER_TAX: Int = -11
    const val INDEX_HIDDEN_CATEGORY_DEBT: Int = -12
    const val INDEX_HIDDEN_CATEGORY_DEBT_PAID: Int = -13
    const val INDEX_HIDDEN_CATEGORY_CREDIT: Int = -14
    const val INDEX_HIDDEN_CATEGORY_CREDIT_PAID: Int = -15
    const val INDEX_HIDDEN_CATEGORY_OPERATION_TAX: Int = -16
    const val INDEX_HIDDEN_CATEGORY_SAVING_IN: Int = -17
    const val INDEX_HIDDEN_CATEGORY_SAVING_OUT: Int = -18

    const val HIDDEN_CATEGORY_TRANSFER_KEY: String = "system_transfer"
    const val HIDDEN_CATEGORY_TRANSFER_TAX_KEY: String = "system_transfer_tax"
    const val HIDDEN_CATEGORY_DEBT_KEY: String = "system_debt"
    const val HIDDEN_CATEGORY_DEBT_PAID_KEY: String = "system_debt_paid"
    const val HIDDEN_CATEGORY_CREDIT_KEY: String = "system_credit"
    const val HIDDEN_CATEGORY_CREDIT_PAID_KEY: String = "system_credit_paid"
    const val HIDDEN_CATEGORY_OPERATION_TAX_KEY: String = "system_operation_tax"
    const val HIDDEN_CATEGORY_SAVING_IN_KEY: String = "system_saving_in"
    const val HIDDEN_CATEGORY_SAVING_OUT_KEY: String = "system_saving_out"

    const val TRANSFER_HIDDEN_CATEGORY_ICON: String = "icon_transfer"
    const val TRANSFER_TAX_HIDDEN_CATEGORY_ICON: String = "icon_transfer_tax"
    const val HIDDEN_CATEGORY_DEBT: String = "icon_debt"
    const val HIDDEN_CATEGORY_DEBT_PAID: String = "icon_debt_paid"
    const val HIDDEN_CATEGORY_CREDIT: String = "icon_credit"
    const val HIDDEN_CATEGORY_CREDIT_PAID: String = "icon_credit_paid"
    const val HIDDEN_CATEGORY_OPERATION_TAX: String = "icon_operation_tax"
    const val HIDDEN_CATEGORY_SAVING_IN: String = "icon_saving_in"
    const val HIDDEN_CATEGORY_SAVING_OUT: String = "icon_saving_out"

    const val TYPE_DEBT: Int = 0
    const val TYPE_CREDIT: Int = 1

    const val TYPE_BUDGET_CATEGORY: Int = 0
    const val TYPE_BUDGET_CASH_FLOW: Int = 1
    const val BUDGET_INFLOW: Int = 1
    const val BUDGET_OUTFLOW: Int = 0

    object Wallet {
        const val TABLE: String = "wallet_table"
        const val ID: String = "wallet_id"
        const val NAME: String = "wallet_name"
        const val ICON: String = "wallet_icon"
        const val CURRENCY_ISO: String = "wallet_currency_iso"
        const val IN_TOTAL: String = "wallet_in_total"
        const val INITIAL: String = "wallet_initial"
        const val UUID: String = "wallet_UUID"
        const val DELETED: String = "wallet_deleted"
        const val LAST_EDIT: String = "wallet_last_edit"
    }

    object Category {
        const val TABLE: String = "category_table"
        const val ID: String = "category_id"
        const val NAME: String = "category_name"
        const val ICON: String = "category_icon"
        const val IS_IN: String = "category_is_in"
        const val IS_HIDDEN: String = "category_is_hidden"
        const val PARENT: String = "category_sub_id"
        const val REPORT: String = "category_report"
        const val INDEX: String = "category_index"
        const val UUID: String = "category_UUID"
        const val DELETED: String = "category_deleted"
        const val LAST_EDIT: String = "category_last_edit"
    }

    object Transaction {
        const val TABLE: String = "transaction_table"
        const val ID: String = "transaction_id"
        const val DESCRIPTION: String = "transaction_description"
        const val DATE: String = "transaction_date"
        const val PLACE: String = "transaction_place"
        const val LATITUDE: String = "transaction_latitude"
        const val LONGITUDE: String = "transaction_longitude"
        const val NOTE: String = "transaction_note"
        const val WALLET: String = "transaction_wallet_id"
        const val CATEGORY: String = "transaction_category"
        const val TRANSFER: String = "transaction_transfer_id"
        const val IS_IN: String = "transaction_is_in"
        const val IMPORT: String = "transaction_import"
        const val RECURRENCE: String = "transaction_recurrent"
        const val DEBT: String = "transaction_debt"
        const val TAX_ON: String = "transaction_tax_on"
        const val SAVING: String = "transaction_saving"
        const val EVENT: String = "transaction_event"
        const val UUID: String = "transaction_UUID"
        const val DELETED: String = "transaction_deleted"
        const val LAST_EDIT: String = "transaction_last_edit"
    }

    object Budget {
        const val TABLE: String = "budget_table"
        const val ID: String = "budget_id"
        const val TYPE: String = "budget_type"
        const val CATEGORY_OR_FLOW: String = "budget_category_or_flow"
        const val WALLETS: String = "budget_wallets"
        const val DATE_FROM: String = "budget_date_from"
        const val DATE_TO: String = "budget_date_to"
        const val MAX: String = "budget_max"
        const val NOTIFY: String = "budget_notify"
        const val NOTIFY_PERCENTAGE: String = "budget_notify_percentage"
        const val UUID: String = "budget_UUID"
        const val DELETED: String = "budget_deleted"
        const val LAST_EDIT: String = "budget_last_edit"
    }

    object Recurrence {
        const val TABLE: String = "recurring_table"
        const val ID: String = "recurring_id"
        const val DESCRIPTION: String = "recurring_description"
        const val PLACE: String = "recurring_place"
        const val LATITUDE: String = "recurring_latitude"
        const val LONGITUDE: String = "recurring_longitude"
        const val NOTE: String = "recurring_note"
        const val WALLET: String = "recurring_wallet_id"
        const val CATEGORY: String = "recurring_category"
        const val IS_IN: String = "recurring_is_in"
        const val IMPORT: String = "recurring_import"
        const val ENCODED_INFO: String = "recurring_encoded_info"
        const val UUID: String = "recurring_UUID"
        const val DELETED: String = "recurring_deleted"
        const val LAST_EDIT: String = "recurring_last_edit"
    }

    object Debt {
        const val TABLE: String = "debt_table"
        const val ID: String = "debt_id"
        const val TYPE: String = "debt_type"
        const val DESCRIPTION: String = "debt_description"
        const val DATE: String = "debt_date"
        const val EXPIRATION_DATE: String = "debt_expiration_date"
        const val PLACE: String = "debt_place"
        const val NOTE: String = "debt_note"
        const val WALLET: String = "debt_wallet_id"
        const val IMPORT: String = "debt_import"
        const val UUID: String = "debt_UUID"
        const val DELETED: String = "debt_deleted"
        const val LAST_EDIT: String = "debt_last_edit"
    }

    object Saving {
        const val TABLE: String = "saving_table"
        const val ID: String = "saving_id"
        const val ICON: String = "saving_icon"
        const val DESCRIPTION: String = "saving_description"
        const val INITIAL: String = "saving_initial_cash"
        const val TARGET: String = "saving_target_cash"
        const val WALLET: String = "saving_wallet"
        const val END_DATE: String = "saving_end_date"
        const val COMPLETE: String = "saving_complete"
        const val UUID: String = "saving_UUID"
        const val DELETED: String = "saving_deleted"
        const val LAST_EDIT: String = "saving_last_edit"
    }

    object Event {
        const val TABLE: String = "event_table"
        const val ID: String = "event_id"
        const val NAME: String = "event_name"
        const val ICON: String = "event_icon"
        const val NOTE: String = "event_note"
        const val DATE_FROM: String = "event_date_from"
        const val DATE_TO: String = "event_date_to"
        const val UUID: String = "event_UUID"
        const val DELETED: String = "event_deleted"
        const val LAST_EDIT: String = "event_last_edit"
    }
}
