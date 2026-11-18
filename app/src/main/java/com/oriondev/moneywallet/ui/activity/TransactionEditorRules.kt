package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.storage.database.Contract
import java.io.Serializable
import kotlin.math.abs

/**
 * What the transaction editor decides, apart from the screen it decides it on. Plain values in
 * and plain values out, so each rule can be driven from a JVM test instead of being pinned by
 * matching the text of the activity.
 *
 * The editor keeps the loading. Every branch of it chooses which row to read and reading takes a
 * content resolver, so what lives here is only what is decided once the values are in hand.
 *
 * It carries the six pieces of state the editor holds across a recreate. They travel as one
 * object under one bundle key, so the keys cannot collide with each other.
 */
class TransactionEditorRules : Serializable {

    var type: Int = TYPE_STANDARD
    var debtId: Long? = null
    var savingId: Long? = null
    var isSavingCompleted: Boolean = false
    var isDebtPayment: Boolean = false
    var isRecurrent: Boolean = false

    /**
     * Only a transaction standing on its own is split. A transfer leg, a debt row and a saving
     * row each carry their role in a category of their own, and a row started from a model is
     * stored as a model row. One a recurrence wrote is left whole like the recurrence itself.
     */
    fun offersSplit(): Boolean {
        return type == TYPE_STANDARD && debtId == null && savingId == null && !isRecurrent
    }

    /**
     * A debt row and a saving row are both filed under a category the editor picks for them, so
     * neither offers the field.
     */
    fun hidesCategoryField(): Boolean {
        return type == TYPE_DEBT || type == TYPE_SAVING
    }

    /**
     * A saving's progress and a debt's progress are each summed over the transactions filed
     * against them with no currency anywhere in that sum, so a row moved onto a wallet held in
     * another currency is added at face value and 500 euros count as 500 dollars.
     *
     * A debt's master transaction is not one of those rows. It carries the same TYPE_DEBT as a
     * payment and is told apart only by its category, and editing its wallet moves the debt
     * itself through syncDebtOfMasterTransaction, so it keeps the field.
     */
    fun hidesWalletField(): Boolean {
        return type == TYPE_SAVING || (type == TYPE_DEBT && isDebtPayment)
    }

    /** One row filed against a saving, as the walk above needs it. */
    class SavingRow(
        @JvmField val id: Long,
        @JvmField val date: String,
        @JvmField val money: Long,
        @JvmField val direction: Int,
        @JvmField val confirmed: Boolean
    )

    companion object {
        private const val serialVersionUID = 1L

        const val TYPE_STANDARD: Int = 0
        const val TYPE_TRANSFER: Int = 1
        const val TYPE_DEBT: Int = 2
        const val TYPE_SAVING: Int = 3
        const val TYPE_MODEL: Int = 4

        const val DEBT_PAY: Int = 1
        const val DEBT_RECEIVE: Int = 2
        const val DEBT_PAY_IN_FULL: Int = 3
        const val DEBT_RECEIVE_IN_FULL: Int = 4

        const val SAVING_DEPOSIT: Int = 1
        const val SAVING_WITHDRAW: Int = 2
        const val SAVING_WITHDRAW_EVERYTHING: Int = 3

        /** Whether a category this row is filed under makes it a payment and not a debt's own row. */
        @JvmStatic
        fun isDebtPaymentTag(categoryTag: String?): Boolean {
            return Contract.CategoryTag.PAID_DEBT == categoryTag ||
                Contract.CategoryTag.PAID_CREDIT == categoryTag
        }

        /**
         * The kind of debt an action names, for a launch that carries no debt row of its own. Answers
         * null for anything else, which is what a crafted intent naming no action gives.
         */
        @JvmStatic
        fun debtTypeFor(debtAction: Int): Contract.DebtType? {
            return when (debtAction) {
                DEBT_PAY, DEBT_PAY_IN_FULL -> Contract.DebtType.DEBT
                DEBT_RECEIVE, DEBT_RECEIVE_IN_FULL -> Contract.DebtType.CREDIT
                else -> null
            }
        }

        /** Only the in full actions open on an amount; pay and receive open on nothing. */
        @JvmStatic
        fun settlesInFull(debtAction: Int): Boolean {
            return debtAction == DEBT_PAY_IN_FULL || debtAction == DEBT_RECEIVE_IN_FULL
        }

        /**
         * What settling in full opens on, which is the figure the debt card shows as left to settle.
         * A debt's payments sum negative, hence the abs, and one paid past its target offers nothing.
         */
        @JvmStatic
        fun settleDebtPrefill(target: Long, progress: Long): Long {
            return (target - abs(progress)).coerceAtLeast(0L)
        }

        /** The category a new debt row is filed under, or null when the kind is not known. */
        @JvmStatic
        fun debtCategoryTag(debtType: Contract.DebtType?): String? {
            return when (debtType) {
                Contract.DebtType.DEBT -> Contract.CategoryTag.PAID_DEBT
                Contract.DebtType.CREDIT -> Contract.CategoryTag.PAID_CREDIT
                null -> null
            }
        }

        /**
         * The category a new saving row is filed under. Withdraw everything is a withdrawal, so the
         * two answer the same tag; the activity used to reach that by falling through one case into
         * the other, where a break left the tag null and the query below crashed the editor as it
         * opened.
         */
        @JvmStatic
        fun savingCategoryTag(savingAction: Int): String? {
            return when (savingAction) {
                SAVING_DEPOSIT -> Contract.CategoryTag.SAVING_DEPOSIT
                SAVING_WITHDRAW, SAVING_WITHDRAW_EVERYTHING -> Contract.CategoryTag.SAVING_WITHDRAW
                else -> null
            }
        }

        /** Only withdraw everything empties the saving, so only it marks the saving complete. */
        @JvmStatic
        fun completesTheSaving(savingAction: Int): Boolean {
            return savingAction == SAVING_WITHDRAW_EVERYTHING
        }

        /**
         * The most a withdrawal may take. Held at nothing, since a saving already under zero on some
         * date from here on gives a negative figure and no amount at all would clear it.
         */
        @JvmStatic
        fun withdrawLimit(lowestBalance: Long): Long {
            return lowestBalance.coerceAtLeast(0L)
        }

        /**
         * What withdraw everything opens on, which is the same figure the check applies when the save
         * is pressed, since the row it writes is dated now. A saving whose rows do not come back
         * offers nothing.
         */
        @JvmStatic
        fun withdrawEverythingPrefill(lowestBalance: Long?): Long {
            return if (lowestBalance != null) withdrawLimit(lowestBalance) else 0L
        }

        /** Whether an amount clears the ceiling. */
        @JvmStatic
        fun isWithinLimit(money: Long, limit: Long): Boolean {
            return money <= limit
        }

        /**
         * The ceiling is in the saving's own currency. A row sitting in a wallet held in another one
         * is not comparable with it as it stands and this screen converts nowhere else, so the check
         * steps aside instead of comparing amounts that do not mean the same thing. A saving that
         * gives back no row at all leaves its currency null and stops here too.
         */
        @JvmStatic
        fun ceilingApplies(savingCurrencyIso: String?, walletCurrencyIso: String?): Boolean {
            return savingCurrencyIso != null && walletCurrencyIso != null && savingCurrencyIso == walletCurrencyIso
        }

        /**
         * Whether the row being edited is being kept or lowered on a date it does not move back from.
         * Such a save takes nothing the saving is not already giving, so it is never refused.
         */
        @JvmStatic
        fun isStoredWithdrawalKeptOrLowered(
            money: Long,
            date: String,
            storedMoney: Long,
            storedDate: String
        ): Boolean {
            return money <= storedMoney && date.compareTo(storedDate) >= 0
        }

        /** A withdrawal is the income half of the pair, since it pays money into the wallet. */
        @JvmStatic
        fun isWithdrawal(direction: Int): Boolean {
            return direction == Contract.Direction.INCOME
        }

        /**
         * A deposit that is not confirmed is left out, because landing takes being confirmed and
         * money that never arrives must not pay for a withdrawal that does. A withdrawal is counted
         * whether it is confirmed or not, for the mirror reason, that leaving it out hands out a
         * ceiling it can then take the saving under.
         */
        @JvmStatic
        fun countsTowardBalance(row: SavingRow): Boolean {
            return isWithdrawal(row.direction) || row.confirmed
        }

        /** A withdrawal takes the saving down and a deposit puts money in. */
        @JvmStatic
        fun signedAmount(row: SavingRow): Long {
            return if (isWithdrawal(row.direction)) -row.money else row.money
        }

        /**
         * The lowest a saving's balance reaches from a moment onwards, over the rows it already
         * carries.
         */
        @JvmStatic
        fun lowestSavingBalanceFrom(
            startMoney: Long,
            rows: List<SavingRow>,
            excludedId: Long,
            from: String
        ): Long {
            val counted = ArrayList<SavingRow>()
            for (row in rows) {
                if (row.id != excludedId && countsTowardBalance(row)) {
                    counted.add(row)
                }
            }
            counted.sortWith { left, right -> left.date.compareTo(right.date) }
            val dates = Array(counted.size) { counted[it].date }
            val signedMoney = LongArray(counted.size) { signedAmount(counted[it]) }
            return Contract.lowestSavingBalanceFrom(startMoney, dates, signedMoney, from)
        }
    }
}
