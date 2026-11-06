package com.oriondev.moneywallet.storage.database.data

/**
 * Created by andrea on 21/12/18.
 */

/**
 * Contract column keys shared by every data exporter. The value of each key is the string that
 * ends up in a CSV header cell, so the literals must not change. Only the CSV exporter uses
 * [COLUMN_CURRENCY]; the other formats simply never add it to their column list.
 */
object Constants {
    const val COLUMN_WALLET = "wallet"
    const val COLUMN_CURRENCY = "currency"
    const val COLUMN_CATEGORY = "category"
    const val COLUMN_DATETIME = "datetime"
    const val COLUMN_MONEY = "money"
    const val COLUMN_DESCRIPTION = "description"
    const val COLUMN_EVENT = "event"
    const val COLUMN_PEOPLE = "people"
    const val COLUMN_PLACE = "place"
    const val COLUMN_NOTE = "note"
}
