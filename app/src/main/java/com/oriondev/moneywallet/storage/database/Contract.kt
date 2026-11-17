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

import android.text.TextUtils
import com.oriondev.moneywallet.model.Identifiable
import java.util.ArrayList
import java.util.Locale

/**
 * This class contains all the static strings that are part of the results returned by the internal
 * content provider. It will virtually modify the structure of the database and return the items
 * in a different format. The application must use the virtual tables defined in this class when a
 * component interact with the [DataContentProvider].
 */
object Contract {

    object Currency {
        const val NAME: String = Schema.Currency.NAME
        const val ISO: String = Schema.Currency.ISO
        const val SYMBOL: String = Schema.Currency.SYMBOL
        const val DECIMALS: String = Schema.Currency.DECIMALS
        const val FAVOURITE: String = Schema.Currency.FAVOURITE
        const val FIX_MONEY_DECIMALS: String = "action_fix_money_decimals"

        /**
         * Asks an insert to bring back a row that is present and not served instead of being
         * refused by it. Only the default set sets this. See SQLDatabase.insertCurrency.
         */
        const val REVIVE_IF_DELETED: String = "action_revive_if_deleted"
    }

    object Wallet {
        const val ID: String = Schema.Wallet.ID
        const val NAME: String = Schema.Wallet.NAME
        const val ICON: String = Schema.Wallet.ICON
        const val CURRENCY: String = Schema.Wallet.CURRENCY
        const val NOTE: String = Schema.Wallet.NOTE
        const val COUNT_IN_TOTAL: String = Schema.Wallet.COUNT_IN_TOTAL
        const val START_MONEY: String = Schema.Wallet.START_MONEY
        const val TOTAL_MONEY: String = "wallet_" + Schema.Alias.TOTAL_MONEY
        const val ARCHIVED: String = Schema.Wallet.ARCHIVED
        const val TAG: String = Schema.Wallet.TAG
        const val INDEX: String = Schema.Wallet.INDEX
    }

    object Transaction {
        const val ID: String = Schema.Transaction.ID
        const val MONEY: String = Schema.Transaction.MONEY
        const val DATE: String = Schema.Transaction.DATE
        const val DESCRIPTION: String = Schema.Transaction.DESCRIPTION
        const val CATEGORY_ID: String = Schema.Transaction.CATEGORY
        const val CATEGORY_NAME: String = "transaction_" + Schema.Category.NAME
        const val CATEGORY_ICON: String = "transaction_" + Schema.Category.ICON
        const val CATEGORY_PARENT_ID: String = "transaction_" + Schema.Category.PARENT
        const val CATEGORY_TYPE: String = "transaction_" + Schema.Category.TYPE
        const val CATEGORY_TAG: String = "transaction_" + Schema.Category.TAG
        const val CATEGORY_SHOW_REPORT: String = "transaction_" + Schema.Category.SHOW_REPORT
        const val CATEGORY_PARENT_SHOW_REPORT: String = "transaction_" + Category.PARENT_SHOW_REPORT

        /**
         * A transaction belongs in the reports only when its own category is included and, for a
         * transaction filed on a subcategory, the parent is included too. Both report screens
         * filter on this, so a category hidden on one of them cannot still be counted on the other.
         */
        const val REPORT_FILTER: String =
            "(" + CATEGORY_SHOW_REPORT + " = 1 AND IFNULL(" + CATEGORY_PARENT_SHOW_REPORT + ", 1) = 1)"
        const val DIRECTION: String = Schema.Transaction.DIRECTION
        const val TYPE: String = Schema.Transaction.TYPE
        const val WALLET_ID: String = Schema.Transaction.WALLET
        const val WALLET_NAME: String = "transaction_" + Schema.Wallet.NAME
        const val WALLET_ICON: String = "transaction_" + Schema.Wallet.ICON
        const val WALLET_CURRENCY: String = "transaction_" + Schema.Wallet.CURRENCY
        const val WALLET_COUNT_IN_TOTAL: String = "transaction_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_ARCHIVED: String = "transaction_" + Schema.Wallet.ARCHIVED
        const val WALLET_TAG: String = "transaction_" + Schema.Wallet.TAG
        const val PLACE_ID: String = Schema.Transaction.PLACE
        const val PLACE_NAME: String = "transaction_" + Schema.Place.NAME
        const val PLACE_ICON: String = "transaction_" + Schema.Place.ICON
        const val PLACE_ADDRESS: String = "transaction_" + Schema.Place.ADDRESS
        const val PLACE_LATITUDE: String = "transaction_" + Schema.Place.LATITUDE
        const val PLACE_LONGITUDE: String = "transaction_" + Schema.Place.LONGITUDE
        const val PLACE_TAG: String = "transaction_" + Schema.Place.TAG
        const val NOTE: String = Schema.Transaction.NOTE
        const val EVENT_ID: String = Schema.Transaction.EVENT
        const val EVENT_NAME: String = "transaction_" + Schema.Event.NAME
        const val EVENT_ICON: String = "transaction_" + Schema.Event.ICON
        const val EVENT_NOTE: String = "transaction_" + Schema.Event.NOTE
        const val EVENT_START_DATE: String = "transaction_" + Schema.Event.START_DATE
        const val EVENT_END_DATE: String = "transaction_" + Schema.Event.END_DATE
        const val EVENT_TAG: String = "transaction_" + Schema.Event.TAG
        const val SAVING_ID: String = Schema.Transaction.SAVING
        const val DEBT_ID: String = Schema.Transaction.DEBT
        const val RECURRENCE_ID: String = Schema.Transaction.RECURRENCE
        const val CONFIRMED: String = Schema.Transaction.CONFIRMED
        const val COUNT_IN_TOTAL: String = Schema.Transaction.COUNT_IN_TOTAL
        const val PEOPLE_IDS: String = "transaction_person_ids"
        const val ATTACHMENT_IDS: String = "transaction_attachment_ids"
        const val TAG: String = Schema.Transaction.TAG
    }

    object Transfer {
        const val ID: String = Schema.Transfer.ID
        const val DESCRIPTION: String = Schema.Transfer.DESCRIPTION
        const val DATE: String = Schema.Transfer.DATE
        const val TRANSACTION_FROM_ID: String = Schema.Transfer.TRANSACTION_FROM
        const val TRANSACTION_FROM_WALLET_ID: String = Schema.Transfer.TRANSACTION_FROM + "_wallet_id"
        const val TRANSACTION_FROM_WALLET_NAME: String = Schema.Transfer.TRANSACTION_FROM + "_wallet_name"
        const val TRANSACTION_FROM_WALLET_ICON: String = Schema.Transfer.TRANSACTION_FROM + "_wallet_icon"
        const val TRANSACTION_FROM_WALLET_CURRENCY: String = Schema.Transfer.TRANSACTION_FROM + "_wallet_currency"
        const val TRANSACTION_FROM_WALLET_COUNT_IN_TOTAL: String =
            Schema.Transfer.TRANSACTION_FROM + "_wallet_count_in_total"
        const val TRANSACTION_FROM_WALLET_ARCHIVED: String =
            Schema.Transfer.TRANSACTION_FROM + "_wallet_archived"
        const val TRANSACTION_FROM_WALLET_TAG: String = Schema.Transfer.TRANSACTION_FROM + "_wallet_tag"
        const val TRANSACTION_FROM_MONEY: String = Schema.Transfer.TRANSACTION_FROM + "_money"
        const val TRANSACTION_FROM_TAG: String = Schema.Transfer.TRANSACTION_FROM + "_tax"
        const val TRANSACTION_TO_ID: String = Schema.Transfer.TRANSACTION_TO
        const val TRANSACTION_TO_WALLET_ID: String = Schema.Transfer.TRANSACTION_TO + "_wallet_id"
        const val TRANSACTION_TO_WALLET_NAME: String = Schema.Transfer.TRANSACTION_TO + "_wallet_name"
        const val TRANSACTION_TO_WALLET_ICON: String = Schema.Transfer.TRANSACTION_TO + "_wallet_icon"
        const val TRANSACTION_TO_WALLET_CURRENCY: String = Schema.Transfer.TRANSACTION_TO + "_wallet_currency"
        const val TRANSACTION_TO_WALLET_COUNT_IN_TOTAL: String =
            Schema.Transfer.TRANSACTION_TO + "_wallet_count_in_total"
        const val TRANSACTION_TO_WALLET_ARCHIVED: String =
            Schema.Transfer.TRANSACTION_TO + "_wallet_archived"
        const val TRANSACTION_TO_WALLET_TAG: String = Schema.Transfer.TRANSACTION_TO + "_wallet_tag"
        const val TRANSACTION_TO_MONEY: String = Schema.Transfer.TRANSACTION_TO + "_money"
        const val TRANSACTION_TO_TAG: String = Schema.Transfer.TRANSACTION_TO + "_tax"
        const val TRANSACTION_TAX_ID: String = Schema.Transfer.TRANSACTION_TAX
        const val TRANSACTION_TAX_WALLET_ID: String = Schema.Transfer.TRANSACTION_TAX + "_wallet_id"
        const val TRANSACTION_TAX_WALLET_NAME: String = Schema.Transfer.TRANSACTION_TAX + "_wallet_name"
        const val TRANSACTION_TAX_WALLET_CURRENCY: String =
            Schema.Transfer.TRANSACTION_TAX + "_wallet_currency"
        const val TRANSACTION_TAX_WALLET_COUNT_IN_TOTAL: String =
            Schema.Transfer.TRANSACTION_TAX + "_wallet_count_in_total"
        const val TRANSACTION_TAX_WALLET_ARCHIVED: String =
            Schema.Transfer.TRANSACTION_TAX + "_wallet_archived"
        const val TRANSACTION_TAX_WALLET_TAG: String = Schema.Transfer.TRANSACTION_TAX + "_wallet_tag"
        const val TRANSACTION_TAX_MONEY: String = Schema.Transfer.TRANSACTION_TAX + "_money"
        const val TRANSACTION_TAX_TAG: String = Schema.Transfer.TRANSACTION_TAX + "_tax"
        const val NOTE: String = Schema.Transfer.NOTE
        const val PLACE_ID: String = Schema.Transfer.PLACE
        const val PLACE_NAME: String = "transfer_" + Schema.Place.NAME
        const val PLACE_ICON: String = "transfer_" + Schema.Place.ICON
        const val PLACE_ADDRESS: String = "transfer_" + Schema.Place.ADDRESS
        const val PLACE_LATITUDE: String = "transfer_" + Schema.Place.LATITUDE
        const val PLACE_LONGITUDE: String = "transfer_" + Schema.Place.LONGITUDE
        const val PLACE_TAG: String = "transfer_" + Schema.Place.TAG
        const val EVENT_ID: String = Schema.Transfer.EVENT
        const val EVENT_NAME: String = "transfer_" + Schema.Event.NAME
        const val EVENT_ICON: String = "transfer_" + Schema.Event.ICON
        const val EVENT_NOTE: String = "transfer_" + Schema.Event.NOTE
        const val EVENT_START_DATE: String = "transfer_" + Schema.Event.START_DATE
        const val EVENT_END_DATE: String = "transfer_" + Schema.Event.END_DATE
        const val EVENT_TAG: String = "transfer_" + Schema.Event.TAG
        const val RECURRENCE_ID: String = Schema.Transfer.RECURRENCE
        const val CONFIRMED: String = Schema.Transfer.CONFIRMED
        const val COUNT_IN_TOTAL: String = Schema.Transfer.COUNT_IN_TOTAL
        const val PEOPLE_IDS: String = "transfer_person_ids"
        const val ATTACHMENT_IDS: String = "transfer_attachment_ids"
        const val TAG: String = Schema.Transfer.TAG
    }

    object Category {
        const val ID: String = Schema.Category.ID
        const val NAME: String = Schema.Category.NAME
        const val ICON: String = Schema.Category.ICON
        const val TYPE: String = Schema.Category.TYPE
        const val PARENT: String = Schema.Category.PARENT
        const val PARENT_NAME: String = "category_parent_name"
        const val PARENT_ICON: String = "category_parent_icon"
        const val PARENT_TYPE: String = "category_parent_type"
        const val PARENT_SHOW_REPORT: String = "category_parent_show_report"
        const val PARENT_TAG: String = "category_parent_tag"
        const val SHOW_REPORT: String = Schema.Category.SHOW_REPORT
        const val INDEX: String = Schema.Category.INDEX
        const val GROUP_ID: String = Schema.Alias.CATEGORY_GROUP_ID
        const val GROUP_NAME: String = Schema.Alias.CATEGORY_GROUP_NAME
        const val GROUP_INDEX: String = Schema.Alias.CATEGORY_GROUP_INDEX
        const val TAG: String = Schema.Category.TAG
    }

    object Debt {
        const val ID: String = Schema.Debt.ID
        const val TYPE: String = Schema.Debt.TYPE
        const val ICON: String = Schema.Debt.ICON
        const val DESCRIPTION: String = Schema.Debt.DESCRIPTION
        const val DATE: String = Schema.Debt.DATE
        const val EXPIRATION_DATE: String = Schema.Debt.EXPIRATION_DATE
        const val WALLET_ID: String = Schema.Debt.WALLET
        const val WALLET_NAME: String = "debt_" + Schema.Wallet.NAME
        const val WALLET_ICON: String = "debt_" + Schema.Wallet.ICON
        const val WALLET_CURRENCY: String = "debt_" + Schema.Wallet.CURRENCY
        const val WALLET_COUNT_IN_TOTAL: String = "debt_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_ARCHIVED: String = "debt_" + Schema.Wallet.ARCHIVED
        const val WALLET_TAG: String = "debt_" + Schema.Wallet.TAG
        const val NOTE: String = Schema.Debt.NOTE
        const val PLACE_ID: String = Schema.Debt.PLACE
        const val PLACE_NAME: String = "debt_" + Schema.Place.NAME
        const val PLACE_ICON: String = "debt_" + Schema.Place.ICON
        const val PLACE_ADDRESS: String = "debt_" + Schema.Place.ADDRESS
        const val PLACE_LATITUDE: String = "debt_" + Schema.Place.LATITUDE
        const val PLACE_LONGITUDE: String = "debt_" + Schema.Place.LONGITUDE
        const val PLACE_TAG: String = "debt_" + Schema.Place.TAG
        const val MONEY: String = Schema.Debt.MONEY
        const val ARCHIVED: String = Schema.Debt.ARCHIVED
        const val PROGRESS: String = "debt_" + Schema.Alias.PROGRESS
        const val PEOPLE_IDS: String = "debt_person_ids"
        const val TAG: String = Schema.Debt.TAG
        const val INSERT_MASTER_TRANSACTION: String = "action_insert_master_transaction"
    }

    object Budget {
        const val ID: String = Schema.Budget.ID
        const val TYPE: String = Schema.Budget.TYPE

        /**
         * The first of [CATEGORY_IDS], kept so a release that predates that list still
         * shows the budget running on one category instead of on none. Nothing matches
         * transactions on it any more, that is the join table behind [CATEGORY_IDS].
         * A write and an upgrade both leave it naming a category the join table also holds; a
         * release old enough to write it and not the table can put it out of step, and the next
         * upgrade puts it back.
         */
        const val CATEGORY_ID: String = Schema.Budget.CATEGORY

        /**
         * Every category the budget covers, in the same angle bracketed form
         * [WALLET_IDS] uses, written on an insert or an update and read back on a query.
         * A query hands them over lowest id first, and so does the editor when it reads the
         * categories of the budget it is opening, which is what keeps [CATEGORY_ID] on
         * the same category through an edit and through a period rolled forward.
         */
        const val CATEGORY_IDS: String = "budget_category_ids"
        const val CATEGORY_NAME: String = "budget_" + Schema.Category.NAME
        const val CATEGORY_ICON: String = "budget_" + Schema.Category.ICON
        const val CATEGORY_TYPE: String = "budget_" + Schema.Category.TYPE
        const val CATEGORY_SHOW_REPORT: String = "budget_" + Schema.Category.SHOW_REPORT
        const val CATEGORY_TAG: String = "budget_" + Schema.Category.TAG
        const val START_DATE: String = Schema.Budget.START_DATE
        const val END_DATE: String = Schema.Budget.END_DATE
        const val MONEY: String = Schema.Budget.MONEY

        // on an insert and on an update through DataContentProvider the value sent is ignored and
        // the currency is taken from the budget's wallets
        const val CURRENCY: String = Schema.Budget.CURRENCY
        const val RULE: String = Schema.Budget.RULE
        const val RULE_START: String = Schema.Budget.RULE_START

        // insert only, never a column: the id of the period this one was rolled forward from
        const val ROLLED_FROM_ID: String = "budget_rolled_from_id"
        const val PROGRESS: String = "budget_" + Schema.Alias.PROGRESS
        const val WALLET_IDS: String = "budget_wallet_ids"
        const val HAS_WALLET_IN_TOTAL: String = "budget_has_wallet_in_total"
        const val TAG: String = Schema.Budget.TAG
    }

    /**
     * Selection over the raw budgets table naming the budgets a roll forward can open a period
     * for: repeating, not deleted, and left with wallets that still exist and still agree on one
     * currency. Each of the last two stops a roll on its own, for its own reason. A budget with
     * no wallet left is absent from the rows this class describes, which come from a join over
     * those wallets, so the roll never sees it. A budget whose wallets disagree is handed out,
     * but refuses every write, because both the insert and the update check that before writing
     * anything. Waking the roll for either leaves it with nothing to do and the same reason to
     * wake again, and the two go round for as long as the budget stays in that state.
     */
    const val ROLLABLE_BUDGET_SELECTION: String =
        Schema.Budget.RULE + " IS NOT NULL AND " + Schema.Budget.DELETED + " = 0 AND (SELECT COUNT(DISTINCT w." +
            Schema.Wallet.CURRENCY + ") FROM " + Schema.BudgetWallet.TABLE + " AS bw JOIN " + Schema.Wallet.TABLE +
            " AS w ON bw." + Schema.BudgetWallet.WALLET + " = w." + Schema.Wallet.ID + " WHERE bw." +
            Schema.BudgetWallet.BUDGET + " = " + Schema.Budget.TABLE + "." + Schema.Budget.ID + " AND bw." +
            Schema.BudgetWallet.DELETED + " = 0 AND w." + Schema.Wallet.DELETED + " = 0) = 1"

    /**
     * Selection over the raw budgets table naming the budgets of one chain that come after a given
     * one. Takes three arguments: the id of the budget asking, the uuid of the budget its chain
     * started from escaped for LIKE and followed by ":%", and the whole uuid of the budget asking.
     *
     * Every period a roll opens is named after the budget its chain started from, so the chain is
     * what the uuid argument matches; the day a schedule is anchored to is not an identity, since
     * two budgets set up on the same afternoon are anchored to the same day.
     *
     * A chain is ordered by those names and not by the dates its rows carry. It holds the budget
     * it started from, whose uuid carries no appended day, and one row per period after that,
     * named after the chain then a colon then the day that period begins on. Schema declares the
     * column TEXT with no collation of its own, so SQLite compares it byte for byte, and both
     * halves of the order follow: the name of the budget the chain started from is a prefix of
     * every other name in the chain, so it sorts before all of them, and the rest share that
     * prefix and differ only in a date written the ISO way, so comparing those as text compares
     * them as dates. That is the order the roll opened them in. A uuid is never rewritten once
     * minted, so nothing an owner does can reorder a chain, while the dates on a row are freely
     * editable: ordering a chain by its start dates let an old period be edited forward past the
     * live one, which then reported the live period as history and refused every save it was
     * offered.
     *
     * The chain argument is only ever matched with LIKE, and never for equality, because equality
     * could not return a row. [budgetChainOf] hands back either the whole uuid it
     * was given or the front of it, so the chain is always a prefix of the uuid of the budget
     * asking and therefore never above it, while a row matched by equality would have to be above
     * it to clear the last test. Nothing an owner has, restored or otherwise, satisfies both.
     *
     * A row flagged deleted is counted too, because it still holds the name of the period it
     * covered and a chain restarted from behind it would ask the roll for a name already spoken
     * for. Deleting a budget in the app does not produce such a row: DataContentProvider turns the
     * deleted flag off at startup, so a delete there takes the row away. This is for rows that
     * arrive already flagged.
     *
     * The caller escapes the second argument because a uuid is only plain hexadecimal while it is
     * minted here. One restored from a backup is whatever that file held, and an underscore in
     * it would stand for any character and match a chain it has nothing to do with.
     */
    const val LATER_PERIOD_OF_CHAIN_SELECTION: String =
        Schema.Budget.ID + " <> ? AND " + Schema.Budget.UUID + " LIKE ? ESCAPE '\\' AND " +
            Schema.Budget.UUID + " > ?"

    /**
     * The raw uuid column of the budgets table. It is not part of the rows this class otherwise
     * describes; a caller reading it has to go to the table itself.
     */
    const val BUDGET_UUID: String = Schema.Budget.UUID

    /** A day as a roll writes it into the name of the period it opens. */
    private const val APPENDED_DAY: String = "\\d{4}-\\d{2}-\\d{2}"
    private val APPENDED_DAY_REGEX: Regex = Regex(APPENDED_DAY)

    /**
     * The uuid of the budget the given budget's chain started from. A roll names each period it
     * opens after the chain, a colon, and the day that period begins on, so this takes such a day
     * back off again and hands back whatever the chain started from. A budget that is itself the
     * start of its chain is its own answer.
     *
     * The day is taken off the END and only when it really is a day. Both matter for a uuid that
     * did not come from this app: the ones minted here are hexadecimal and hold no colon of their
     * own, while one restored from a backup file is whatever that file held. Cutting at the first
     * colon instead put a chain root that contained a colon after its own periods, since it then
     * shared their prefix and carried something other than a date where they carry one, and every
     * period of that chain read as history because a later member appeared to exist. The budget
     * could then never be saved while it repeated.
     *
     * This stops such a chain being built that way, and does not put one right that already was.
     * A chain whose periods were opened while the first colon was the cut is named for the part
     * before that colon, its root still sorts after all of them, and it stays unsavable while it
     * repeats. Turning repeat off does not give it back either: the budget then reads as a period
     * its chain has moved past, which is what hides the box that would turn it on again. That was
     * so before this method changed too.
     *
     * One reading stays ambiguous and cannot be recovered: a chain root whose own uuid ends in a
     * colon and eight digits split by dashes is indistinguishable from a period, and is read as
     * one. Nothing this app writes looks like that.
     *
     * @param budgetUUID uuid of a budget in the chain.
     * @return the uuid of the budget the chain started from.
     */
    @JvmStatic
    fun budgetChainOf(budgetUUID: String): String {
        val separator = budgetUUID.lastIndexOf(':')
        return if (separator >= 0 && APPENDED_DAY_REGEX.matches(budgetUUID.substring(separator + 1))) {
            budgetUUID.substring(0, separator)
        } else {
            budgetUUID
        }
    }

    /**
     * The lowest a saving's balance reaches from a given moment onwards, over the rows it
     * already carries. A withdrawal dated at that moment can take at most this, because taking
     * more puts the saving under zero on some date at or after it.
     *
     * A saving's own sum cannot answer that. It is a total over the whole set of rows and holds
     * no date order, so a withdrawal dated before the deposit that funds it clears it and the
     * saving sits under zero in between.
     *
     * dates and signedMoney describe the same rows in the same positions, ordered by date
     * ascending. A deposit is positive and a withdrawal negative. A deposit that is not
     * confirmed belongs in neither array; a withdrawal belongs in them whether it is confirmed
     * or not. The caller orders on the raw date column and this compares those
     * same strings, so the two orders are one order, and a date this app writes is a fixed width
     * yyyy-MM-dd HH:mm:ss whose byte order is its time order.
     *
     * The answer can be negative, which says the saving is already under zero on some date from
     * here on and has nothing at all to give. A caller deciding what a withdrawal may take holds
     * it at zero itself.
     *
     * @param startMoney the saving's start money.
     * @param dates the date of each row, ascending.
     * @param signedMoney what each row moves the saving by, in the same positions.
     * @param from the moment to count from, as a date string of the same kind.
     * @return the lowest balance the saving reaches at or after from.
     */
    @JvmStatic
    fun lowestSavingBalanceFrom(
        startMoney: Long,
        dates: Array<String>,
        signedMoney: LongArray,
        from: String
    ): Long {
        var balance = startMoney
        var row = 0
        while (row < dates.size && dates[row].compareTo(from) <= 0) {
            balance += signedMoney[row]
            row++
        }
        var lowest = balance
        while (row < dates.size) {
            balance += signedMoney[row]
            // Only at the end of a run of rows sharing a date. The balance part way through
            // such a run is not one the saving ever holds, and the order inside the run is
            // whatever the query gives back, so comparing there both invents a low point and
            // makes the answer depend on that order.
            if ((row + 1 == dates.size || dates[row + 1] != dates[row]) && balance < lowest) {
                lowest = balance
            }
            row++
        }
        return lowest
    }

    object Saving {
        const val ID: String = Schema.Saving.ID
        const val DESCRIPTION: String = Schema.Saving.DESCRIPTION
        const val ICON: String = Schema.Saving.ICON
        const val START_MONEY: String = Schema.Saving.START_MONEY
        const val END_MONEY: String = Schema.Saving.END_MONEY
        const val WALLET_ID: String = Schema.Saving.WALLET
        const val WALLET_NAME: String = "saving_" + Schema.Wallet.NAME
        const val WALLET_ICON: String = "saving_" + Schema.Wallet.ICON
        const val WALLET_CURRENCY: String = "saving_" + Schema.Wallet.CURRENCY
        const val WALLET_COUNT_IN_TOTAL: String = "saving_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_ARCHIVED: String = "saving_" + Schema.Wallet.ARCHIVED
        const val WALLET_TAG: String = "saving_" + Schema.Wallet.TAG
        const val END_DATE: String = Schema.Saving.END_DATE
        const val COMPLETE: String = Schema.Saving.COMPLETE
        const val NOTE: String = Schema.Saving.NOTE
        const val PROGRESS: String = "saving_" + Schema.Alias.PROGRESS
        const val TAG: String = Schema.Saving.TAG
    }

    object Event {
        const val ID: String = Schema.Event.ID
        const val NAME: String = Schema.Event.NAME
        const val ICON: String = Schema.Event.ICON
        const val NOTE: String = Schema.Event.NOTE
        const val START_DATE: String = Schema.Event.START_DATE
        const val END_DATE: String = Schema.Event.END_DATE
        const val PROGRESS: String = "event_" + Schema.Alias.PROGRESS
        const val TAG: String = Schema.Event.TAG
    }

    object RecurrentTransaction {
        const val ID: String = Schema.RecurrentTransaction.ID
        const val MONEY: String = Schema.RecurrentTransaction.MONEY
        const val DESCRIPTION: String = Schema.RecurrentTransaction.DESCRIPTION
        const val CATEGORY_ID: String = Schema.RecurrentTransaction.CATEGORY
        const val CATEGORY_NAME: String = "model_transaction_" + Schema.Category.NAME
        const val CATEGORY_ICON: String = "model_transaction_" + Schema.Category.ICON
        const val CATEGORY_TYPE: String = "model_transaction_" + Schema.Category.TYPE
        const val CATEGORY_SHOW_REPORT: String = "model_transaction_" + Schema.Category.SHOW_REPORT
        const val CATEGORY_TAG: String = "model_transaction_" + Schema.Category.TAG
        const val DIRECTION: String = Schema.RecurrentTransaction.DIRECTION
        const val WALLET_ID: String = Schema.RecurrentTransaction.WALLET
        const val WALLET_NAME: String = "model_transaction_" + Schema.Wallet.NAME
        const val WALLET_ICON: String = "model_transaction_" + Schema.Wallet.ICON
        const val WALLET_CURRENCY: String = "model_transaction_" + Schema.Wallet.CURRENCY
        const val WALLET_COUNT_IN_TOTAL: String = "model_transaction_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_ARCHIVED: String = "model_transaction_" + Schema.Wallet.ARCHIVED
        const val WALLET_TAG: String = "model_transaction_" + Schema.Wallet.TAG
        const val PLACE_ID: String = Schema.RecurrentTransaction.PLACE
        const val PLACE_NAME: String = "model_transaction_" + Schema.Place.NAME
        const val PLACE_ICON: String = "model_transaction_" + Schema.Place.ICON
        const val PLACE_ADDRESS: String = "model_transaction_" + Schema.Place.ADDRESS
        const val PLACE_LATITUDE: String = "model_transaction_" + Schema.Place.LATITUDE
        const val PLACE_LONGITUDE: String = "model_transaction_" + Schema.Place.LONGITUDE
        const val PLACE_TAG: String = "model_transaction_" + Schema.Place.TAG
        const val NOTE: String = Schema.RecurrentTransaction.NOTE
        const val EVENT_ID: String = Schema.RecurrentTransaction.EVENT
        const val EVENT_NAME: String = "model_transaction_" + Schema.Event.NAME
        const val EVENT_ICON: String = "model_transaction_" + Schema.Event.ICON
        const val EVENT_NOTE: String = "model_transaction_" + Schema.Event.NOTE
        const val EVENT_START_DATE: String = "model_transaction_" + Schema.Event.START_DATE
        const val EVENT_END_DATE: String = "model_transaction_" + Schema.Event.END_DATE
        const val EVENT_TAG: String = "model_transaction_" + Schema.Event.TAG
        const val CONFIRMED: String = Schema.RecurrentTransaction.CONFIRMED
        const val COUNT_IN_TOTAL: String = Schema.RecurrentTransaction.COUNT_IN_TOTAL
        const val START_DATE: String = Schema.RecurrentTransaction.START_DATE
        const val LAST_OCCURRENCE: String = Schema.RecurrentTransaction.LAST_OCCURRENCE
        const val NEXT_OCCURRENCE: String = Schema.RecurrentTransaction.NEXT_OCCURRENCE
        const val RULE: String = Schema.RecurrentTransaction.RULE
        const val TAG: String = Schema.RecurrentTransaction.TAG
    }

    object RecurrentTransfer {
        const val ID: String = Schema.RecurrentTransfer.ID
        const val DESCRIPTION: String = Schema.RecurrentTransfer.DESCRIPTION
        const val WALLET_FROM_ID: String = Schema.RecurrentTransfer.WALLET_FROM
        const val WALLET_FROM_NAME: String = "model_transaction_from_" + Schema.Wallet.NAME
        const val WALLET_FROM_ICON: String = "model_transaction_from_" + Schema.Wallet.ICON
        const val WALLET_FROM_CURRENCY: String = "model_transaction_from_" + Schema.Wallet.CURRENCY
        const val WALLET_FROM_COUNT_IN_TOTAL: String =
            "model_transaction_from_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_FROM_ARCHIVED: String = "model_transaction_from_" + Schema.Wallet.ARCHIVED
        const val WALLET_FROM_TAG: String = "model_transaction_from_" + Schema.Wallet.TAG
        const val WALLET_TO_ID: String = Schema.RecurrentTransfer.WALLET_TO
        const val WALLET_TO_NAME: String = "model_transaction_to_" + Schema.Wallet.NAME
        const val WALLET_TO_ICON: String = "model_transaction_to_" + Schema.Wallet.ICON
        const val WALLET_TO_CURRENCY: String = "model_transaction_to_" + Schema.Wallet.CURRENCY
        const val WALLET_TO_COUNT_IN_TOTAL: String =
            "model_transaction_to_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_TO_ARCHIVED: String = "model_transaction_to_" + Schema.Wallet.ARCHIVED
        const val WALLET_TO_TAG: String = "model_transaction_to_" + Schema.Wallet.TAG
        const val MONEY_FROM: String = Schema.RecurrentTransfer.MONEY_FROM
        const val MONEY_TO: String = Schema.RecurrentTransfer.MONEY_TO
        const val MONEY_TAX: String = Schema.RecurrentTransfer.MONEY_TAX
        const val NOTE: String = Schema.RecurrentTransfer.NOTE
        const val EVENT_ID: String = Schema.RecurrentTransfer.EVENT
        const val EVENT_NAME: String = "model_transfer_" + Schema.Event.NAME
        const val EVENT_ICON: String = "model_transfer_" + Schema.Event.ICON
        const val EVENT_NOTE: String = "model_transfer_" + Schema.Event.NOTE
        const val EVENT_START_DATE: String = "model_transfer_" + Schema.Event.START_DATE
        const val EVENT_END_DATE: String = "model_transfer_" + Schema.Event.END_DATE
        const val EVENT_TAG: String = "model_transfer_" + Schema.Event.TAG
        const val PLACE_ID: String = Schema.RecurrentTransfer.PLACE
        const val PLACE_NAME: String = "model_transfer_" + Schema.Place.NAME
        const val PLACE_ICON: String = "model_transfer_" + Schema.Place.ICON
        const val PLACE_ADDRESS: String = "model_transfer_" + Schema.Place.ADDRESS
        const val PLACE_LATITUDE: String = "model_transfer_" + Schema.Place.LATITUDE
        const val PLACE_LONGITUDE: String = "model_transfer_" + Schema.Place.LONGITUDE
        const val PLACE_TAG: String = "model_transfer_" + Schema.Place.TAG
        const val CONFIRMED: String = Schema.RecurrentTransfer.CONFIRMED
        const val COUNT_IN_TOTAL: String = Schema.RecurrentTransfer.COUNT_IN_TOTAL
        const val START_DATE: String = Schema.RecurrentTransfer.START_DATE
        const val LAST_OCCURRENCE: String = Schema.RecurrentTransfer.LAST_OCCURRENCE
        const val NEXT_OCCURRENCE: String = Schema.RecurrentTransfer.NEXT_OCCURRENCE
        const val RULE: String = Schema.RecurrentTransfer.RULE
        const val TAG: String = Schema.RecurrentTransfer.TAG
    }

    object TransactionModel {
        const val ID: String = Schema.TransactionModel.ID
        const val MONEY: String = Schema.TransactionModel.MONEY
        const val DESCRIPTION: String = Schema.TransactionModel.DESCRIPTION
        const val CATEGORY_ID: String = Schema.TransactionModel.CATEGORY
        const val CATEGORY_NAME: String = "model_transaction_" + Schema.Category.NAME
        const val CATEGORY_ICON: String = "model_transaction_" + Schema.Category.ICON
        const val CATEGORY_TYPE: String = "model_transaction_" + Schema.Category.TYPE
        const val CATEGORY_SHOW_REPORT: String = "model_transaction_" + Schema.Category.SHOW_REPORT
        const val CATEGORY_TAG: String = "model_transaction_" + Schema.Category.TAG
        const val DIRECTION: String = Schema.TransactionModel.DIRECTION
        const val WALLET_ID: String = Schema.TransactionModel.WALLET
        const val WALLET_NAME: String = "model_transaction_" + Schema.Wallet.NAME
        const val WALLET_ICON: String = "model_transaction_" + Schema.Wallet.ICON
        const val WALLET_CURRENCY: String = "model_transaction_" + Schema.Wallet.CURRENCY
        const val WALLET_COUNT_IN_TOTAL: String = "model_transaction_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_ARCHIVED: String = "model_transaction_" + Schema.Wallet.ARCHIVED
        const val WALLET_TAG: String = "model_transaction_" + Schema.Wallet.TAG
        const val PLACE_ID: String = Schema.TransactionModel.PLACE
        const val PLACE_NAME: String = "model_transaction_" + Schema.Place.NAME
        const val PLACE_ICON: String = "model_transaction_" + Schema.Place.ICON
        const val PLACE_ADDRESS: String = "model_transaction_" + Schema.Place.ADDRESS
        const val PLACE_LATITUDE: String = "model_transaction_" + Schema.Place.LATITUDE
        const val PLACE_LONGITUDE: String = "model_transaction_" + Schema.Place.LONGITUDE
        const val PLACE_TAG: String = "model_transaction_" + Schema.Place.TAG
        const val NOTE: String = Schema.TransactionModel.NOTE
        const val EVENT_ID: String = Schema.TransactionModel.EVENT
        const val EVENT_NAME: String = "model_transaction_" + Schema.Event.NAME
        const val EVENT_ICON: String = "model_transaction_" + Schema.Event.ICON
        const val EVENT_NOTE: String = "model_transaction_" + Schema.Event.NOTE
        const val EVENT_START_DATE: String = "model_transaction_" + Schema.Event.START_DATE
        const val EVENT_END_DATE: String = "model_transaction_" + Schema.Event.END_DATE
        const val EVENT_TAG: String = "model_transaction_" + Schema.Event.TAG
        const val CONFIRMED: String = Schema.TransactionModel.CONFIRMED
        const val COUNT_IN_TOTAL: String = Schema.TransactionModel.COUNT_IN_TOTAL
        const val TAG: String = Schema.TransactionModel.TAG
    }

    object TransferModel {
        const val ID: String = Schema.TransferModel.ID
        const val DESCRIPTION: String = Schema.TransferModel.DESCRIPTION
        const val WALLET_FROM_ID: String = Schema.TransferModel.WALLET_FROM
        const val WALLET_FROM_NAME: String = "model_transaction_from_" + Schema.Wallet.NAME
        const val WALLET_FROM_ICON: String = "model_transaction_from_" + Schema.Wallet.ICON
        const val WALLET_FROM_CURRENCY: String = "model_transaction_from_" + Schema.Wallet.CURRENCY
        const val WALLET_FROM_COUNT_IN_TOTAL: String =
            "model_transaction_from_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_FROM_ARCHIVED: String = "model_transaction_from_" + Schema.Wallet.ARCHIVED
        const val WALLET_FROM_TAG: String = "model_transaction_from_" + Schema.Wallet.TAG
        const val WALLET_TO_ID: String = Schema.TransferModel.WALLET_TO
        const val WALLET_TO_NAME: String = "model_transaction_to_" + Schema.Wallet.NAME
        const val WALLET_TO_ICON: String = "model_transaction_to_" + Schema.Wallet.ICON
        const val WALLET_TO_CURRENCY: String = "model_transaction_to_" + Schema.Wallet.CURRENCY
        const val WALLET_TO_COUNT_IN_TOTAL: String =
            "model_transaction_to_" + Schema.Wallet.COUNT_IN_TOTAL
        const val WALLET_TO_ARCHIVED: String = "model_transaction_to_" + Schema.Wallet.ARCHIVED
        const val WALLET_TO_TAG: String = "model_transaction_to_" + Schema.Wallet.TAG
        const val MONEY_FROM: String = Schema.TransferModel.MONEY_FROM
        const val MONEY_TO: String = Schema.TransferModel.MONEY_TO
        const val MONEY_TAX: String = Schema.TransferModel.MONEY_TAX
        const val NOTE: String = Schema.TransferModel.NOTE
        const val EVENT_ID: String = Schema.TransferModel.EVENT
        const val EVENT_NAME: String = "model_transfer_" + Schema.Event.NAME
        const val EVENT_ICON: String = "model_transfer_" + Schema.Event.ICON
        const val EVENT_NOTE: String = "model_transfer_" + Schema.Event.NOTE
        const val EVENT_START_DATE: String = "model_transfer_" + Schema.Event.START_DATE
        const val EVENT_END_DATE: String = "model_transfer_" + Schema.Event.END_DATE
        const val EVENT_TAG: String = "model_transfer_" + Schema.Event.TAG
        const val PLACE_ID: String = Schema.TransferModel.PLACE
        const val PLACE_NAME: String = "model_transfer_" + Schema.Place.NAME
        const val PLACE_ICON: String = "model_transfer_" + Schema.Place.ICON
        const val PLACE_ADDRESS: String = "model_transfer_" + Schema.Place.ADDRESS
        const val PLACE_LATITUDE: String = "model_transfer_" + Schema.Place.LATITUDE
        const val PLACE_LONGITUDE: String = "model_transfer_" + Schema.Place.LONGITUDE
        const val PLACE_TAG: String = "model_transfer_" + Schema.Place.TAG
        const val CONFIRMED: String = Schema.TransferModel.CONFIRMED
        const val COUNT_IN_TOTAL: String = Schema.TransferModel.COUNT_IN_TOTAL
        const val TAG: String = Schema.TransferModel.TAG
    }

    object Place {
        const val ID: String = Schema.Place.ID
        const val NAME: String = Schema.Place.NAME
        const val ICON: String = Schema.Place.ICON
        const val ADDRESS: String = Schema.Place.ADDRESS
        const val LATITUDE: String = Schema.Place.LATITUDE
        const val LONGITUDE: String = Schema.Place.LONGITUDE
        const val TAG: String = Schema.Place.TAG
    }

    object CategoryRule {
        const val ID: String = Schema.CategoryRule.ID
        const val PATTERN: String = Schema.CategoryRule.PATTERN
        const val CATEGORY_ID: String = Schema.CategoryRule.CATEGORY

        /** Read only, joined from the category the rule names. Writes ignore it. */
        const val CATEGORY_NAME: String = "rule_category_name"

        /**
         * The priority the rules are matched in, lowest first. An insert that names no index of
         * its own is appended after the rules already stored.
         */
        const val INDEX: String = Schema.CategoryRule.INDEX
    }

    object Person {
        const val ID: String = Schema.Person.ID
        const val NAME: String = Schema.Person.NAME
        const val ICON: String = Schema.Person.ICON
        const val NOTE: String = Schema.Person.NOTE
        const val TAG: String = Schema.Person.TAG
    }

    object Attachment {
        const val ID: String = Schema.Attachment.ID
        const val FILE: String = Schema.Attachment.FILE
        const val NAME: String = Schema.Attachment.NAME
        const val TYPE: String = Schema.Attachment.TYPE
        const val SIZE: String = Schema.Attachment.SIZE
        const val TAG: String = Schema.Attachment.TAG
    }

    enum class CategoryType(val value: Int) {
        INCOME(Schema.CategoryType.INCOME),
        EXPENSE(Schema.CategoryType.EXPENSE),
        SYSTEM(Schema.CategoryType.SYSTEM);

        companion object {
            @JvmStatic
            fun fromValue(value: Int): CategoryType? {
                return when (value) {
                    Schema.CategoryType.INCOME -> INCOME
                    Schema.CategoryType.EXPENSE -> EXPENSE
                    Schema.CategoryType.SYSTEM -> SYSTEM
                    else -> null
                }
            }
        }
    }

    object CategoryTag {
        const val TRANSFER: String = Schema.CategoryTag.TRANSFER
        const val TRANSFER_TAX: String = Schema.CategoryTag.TRANSFER_TAX
        const val DEBT: String = Schema.CategoryTag.DEBT
        const val CREDIT: String = Schema.CategoryTag.CREDIT
        const val PAID_DEBT: String = Schema.CategoryTag.PAID_DEBT
        const val PAID_CREDIT: String = Schema.CategoryTag.PAID_CREDIT
        const val TAX: String = Schema.CategoryTag.TAX
        const val SAVING_DEPOSIT: String = Schema.CategoryTag.SAVING_DEPOSIT
        const val SAVING_WITHDRAW: String = Schema.CategoryTag.SAVING_WITHDRAW
    }

    enum class DebtType(val value: Int) {
        DEBT(0),
        CREDIT(1);

        companion object {
            @JvmStatic
            fun fromValue(value: Int): DebtType {
                return when (value) {
                    0 -> DEBT
                    1 -> CREDIT
                    else -> throw IllegalArgumentException("Invalid debt type")
                }
            }
        }
    }

    enum class BudgetType(val value: Int) {
        EXPENSES(Schema.BudgetType.EXPENSES),
        INCOMES(Schema.BudgetType.INCOMES),
        CATEGORY(Schema.BudgetType.CATEGORY);

        companion object {
            @JvmStatic
            fun fromValue(value: Int): BudgetType? {
                return when (value) {
                    Schema.BudgetType.EXPENSES -> EXPENSES
                    Schema.BudgetType.INCOMES -> INCOMES
                    Schema.BudgetType.CATEGORY -> CATEGORY
                    else -> null
                }
            }
        }
    }

    object Direction {
        const val INCOME: Int = Schema.Direction.INCOME
        const val EXPENSE: Int = Schema.Direction.EXPENSE
    }

    object TransactionType {
        const val STANDARD: Int = 0
        const val TRANSFER: Int = 1
        const val DEBT: Int = 2
        const val SAVING: Int = 3
        const val MODEL: Int = 4
    }

    object ErrorCode {
        const val CURRENCY_IN_USE: Int = 4535
        const val WALLET_USED_IN_TRANSFER: Int = 4536
        const val CATEGORY_HAS_CHILDREN: Int = 4537
        const val CATEGORY_IN_USE: Int = 4538
        const val CATEGORY_HIERARCHY_NOT_SUPPORTED: Int = 4539
        const val CATEGORY_NOT_CONSISTENT: Int = 4540
        const val WALLETS_NOT_FOUND: Int = 4541
        const val WALLETS_NOT_CONSISTENT: Int = 4542
        const val SYSTEM_CATEGORY_NOT_MODIFIABLE: Int = 4543
        const val TRANSACTION_USED_IN_TRANSFER: Int = 4544
        const val INVALID_RECURRENCE_RULE: Int = 4545
    }

    @JvmStatic
    fun parseObjectIds(encodedIds: String?): List<Long>? {
        if (!TextUtils.isEmpty(encodedIds)) {
            val safeEncoded = encodedIds ?: return null
            val objectIds = ArrayList<Long>()
            val parts = safeEncoded.split(",")
            for (part in parts) {
                if (part.startsWith("<") && part.endsWith(">")) {
                    val encodedId = part.substring(1, part.length - 1)
                    try {
                        objectIds.add(encodedId.toLong())
                    } catch (ignore: NumberFormatException) {
                        // do nothing
                    }
                }
            }
            return objectIds
        }
        return null
    }

    @JvmStatic
    fun <T : Identifiable> getObjectIds(objects: Array<T>?): String? {
        if (objects != null && objects.isNotEmpty()) {
            val builder = StringBuilder()
            for (i in objects.indices) {
                if (i != 0) {
                    builder.append(",")
                }
                builder.append(String.format(Locale.ENGLISH, "<%d>", objects[i].id))
            }
            return builder.toString()
        }
        return null
    }

    @JvmStatic
    fun <T : Identifiable> getObjectIds(objects: List<T>?): String? {
        if (objects != null && objects.isNotEmpty()) {
            val builder = StringBuilder()
            for (i in objects.indices) {
                if (i != 0) {
                    builder.append(",")
                }
                builder.append(String.format(Locale.ENGLISH, "<%d>", objects[i].id))
            }
            return builder.toString()
        }
        return null
    }
}
