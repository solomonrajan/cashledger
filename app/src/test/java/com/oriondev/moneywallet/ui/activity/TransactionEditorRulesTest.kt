package com.oriondev.moneywallet.ui.activity

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.ui.activity.TransactionEditorRules.SavingRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections

/**
 * The transaction editor's rules, driven directly.
 */
class TransactionEditorRulesTest {

    companion object {
        private const val DEPOSIT = Contract.Direction.EXPENSE
        private const val WITHDRAWAL = Contract.Direction.INCOME

        private fun rules(type: Int, debtPayment: Boolean): TransactionEditorRules {
            val rules = TransactionEditorRules()
            rules.type = type
            rules.isDebtPayment = debtPayment
            return rules
        }

        private fun read(value: String): String {
            return StringBuilder(value).toString()
        }

        private fun ledger(): MutableList<SavingRow> {
            return ArrayList(
                Arrays.asList(
                    SavingRow(3L, "2026-09-01 10:00:00", 90000L, DEPOSIT, true),
                    SavingRow(1L, "2026-09-10 10:00:00", 15000L, WITHDRAWAL, true),
                    SavingRow(2L, "2026-09-20 10:00:00", 30000L, DEPOSIT, true)
                )
            )
        }

        private fun roundTrip(rules: TransactionEditorRules): TransactionEditorRules {
            val bytes = ByteArrayOutputStream()
            val out = ObjectOutputStream(bytes)
            out.writeObject(rules)
            out.close()
            val `in` = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray()))
            return `in`.readObject() as TransactionEditorRules
        }
    }

    // ---- what the editor hides -------------------------------------------------------------

    @Test
    fun anOrdinaryTransactionOffersBothFields() {
        val rules = rules(TransactionEditorRules.TYPE_STANDARD, false)
        assertFalse(rules.hidesCategoryField())
        assertFalse(rules.hidesWalletField())
    }

    @Test
    fun aSavingTransactionHidesBothFields() {
        val rules = rules(TransactionEditorRules.TYPE_SAVING, false)
        assertTrue(rules.hidesCategoryField())
        assertTrue(
            "a saving's progress is summed with no currency in it, so the wallet a saving transaction sits in must not be changeable from the editor",
            rules.hidesWalletField()
        )
    }

    @Test
    fun aSavingTransactionHidesItsWalletFieldWhateverTheDebtFlagSays() {
        assertTrue(
            "the debt payment flag must not reach the saving case, or a saving row carrying a paid category gets its wallet field back",
            rules(TransactionEditorRules.TYPE_SAVING, true).hidesWalletField()
        )
    }

    @Test
    fun aTransactionFromAModelOffersBothFields() {
        val rules = rules(TransactionEditorRules.TYPE_MODEL, false)
        assertFalse(
            "a row entered from a model is an ordinary transaction and has to be filed under a category the user picks",
            rules.hidesCategoryField()
        )
        assertFalse(rules.hidesWalletField())
    }

    @Test
    fun aTransferOffersBothFields() {
        val rules = rules(TransactionEditorRules.TYPE_TRANSFER, false)
        assertFalse(rules.hidesCategoryField())
        assertFalse(rules.hidesWalletField())
    }

    @Test
    fun anOrdinaryTransactionKeepsItsWalletFieldEvenWithTheFlagSet() {
        assertFalse(
            "only a debt row reads the flag at all",
            rules(TransactionEditorRules.TYPE_STANDARD, true).hidesWalletField()
        )
    }

    @Test
    fun aDebtPaymentHidesBothFields() {
        val rules = rules(TransactionEditorRules.TYPE_DEBT, true)
        assertTrue(rules.hidesCategoryField())
        assertTrue(
            "a debt's progress is summed with no currency in it either",
            rules.hidesWalletField()
        )
    }

    @Test
    fun aDebtsMasterTransactionKeepsItsWalletField() {
        val rules = rules(TransactionEditorRules.TYPE_DEBT, false)
        assertTrue(
            "a master transaction is filed under the debt's own category, so the field stays hidden",
            rules.hidesCategoryField()
        )
        assertFalse(
            "editing a master transaction's wallet moves the debt itself through syncDebtOfMasterTransaction, so hiding the field there would take that away",
            rules.hidesWalletField()
        )
    }

    @Test
    fun theDebtPaymentFlagStartsFalse() {
        assertFalse(
            "a debt row the flag was never worked out for keeps its wallet field",
            TransactionEditorRules().isDebtPayment
        )
    }

    // ---- what makes a row a payment --------------------------------------------------------

    @Test
    fun onlyThePaidCategoriesMakeARowAPayment() {
        assertTrue(TransactionEditorRules.isDebtPaymentTag(read(Contract.CategoryTag.PAID_DEBT)))
        assertTrue(TransactionEditorRules.isDebtPaymentTag(read(Contract.CategoryTag.PAID_CREDIT)))
        assertFalse(
            "the debt's own category is a master transaction, not a payment",
            TransactionEditorRules.isDebtPaymentTag(Contract.CategoryTag.DEBT)
        )
        assertFalse(TransactionEditorRules.isDebtPaymentTag(Contract.CategoryTag.CREDIT))
        assertFalse(
            "a tag read off a name column, or a fifth constructor argument dropped, leaves this null",
            TransactionEditorRules.isDebtPaymentTag(null)
        )
        assertFalse(TransactionEditorRules.isDebtPaymentTag("Groceries"))
    }

    // ---- which category a new row is filed under -------------------------------------------

    @Test
    fun theDebtActionNamesTheKindOfDebt() {
        assertEquals(
            Contract.DebtType.DEBT,
            TransactionEditorRules.debtTypeFor(TransactionEditorRules.DEBT_PAY)
        )
        assertEquals(
            Contract.DebtType.CREDIT,
            TransactionEditorRules.debtTypeFor(TransactionEditorRules.DEBT_RECEIVE)
        )
        assertNull(
            "a launch naming no action names no kind",
            TransactionEditorRules.debtTypeFor(0)
        )
    }

    @Test
    fun settlingInFullNamesTheSameKindAsItsPlainAction() {
        assertEquals(
            Contract.DebtType.DEBT,
            TransactionEditorRules.debtTypeFor(TransactionEditorRules.DEBT_PAY_IN_FULL)
        )
        assertEquals(
            Contract.DebtType.CREDIT,
            TransactionEditorRules.debtTypeFor(TransactionEditorRules.DEBT_RECEIVE_IN_FULL)
        )
    }

    @Test
    fun onlyTheInFullActionsSettleInFull() {
        assertTrue(TransactionEditorRules.settlesInFull(TransactionEditorRules.DEBT_PAY_IN_FULL))
        assertTrue(TransactionEditorRules.settlesInFull(TransactionEditorRules.DEBT_RECEIVE_IN_FULL))
        assertFalse(
            "pay keeps opening on nothing",
            TransactionEditorRules.settlesInFull(TransactionEditorRules.DEBT_PAY)
        )
        assertFalse(TransactionEditorRules.settlesInFull(TransactionEditorRules.DEBT_RECEIVE))
        assertFalse(TransactionEditorRules.settlesInFull(0))
    }

    // ---- what settling a debt in full opens on ---------------------------------------------

    @Test
    fun settlingInFullOpensOnWhatIsLeftToSettle() {
        assertEquals(7500L, TransactionEditorRules.settleDebtPrefill(10000L, 2500L))
    }

    @Test
    fun settlingInFullReadsADebtsNegativeProgressAsPaid() {
        assertEquals(
            "a debt's payments are expenses and sum negative, and reading the sign as it comes would offer more than the debt itself",
            7500L, TransactionEditorRules.settleDebtPrefill(10000L, -2500L)
        )
    }

    @Test
    fun settlingInFullADebtPaidPastItsTargetOffersNothing() {
        assertEquals(0L, TransactionEditorRules.settleDebtPrefill(10000L, -12000L))
        assertEquals(0L, TransactionEditorRules.settleDebtPrefill(10000L, 10000L))
    }

    @Test
    fun eachKindOfDebtIsFiledUnderItsOwnPaidCategory() {
        assertEquals(
            Contract.CategoryTag.PAID_DEBT,
            TransactionEditorRules.debtCategoryTag(Contract.DebtType.DEBT)
        )
        assertEquals(
            Contract.CategoryTag.PAID_CREDIT,
            TransactionEditorRules.debtCategoryTag(Contract.DebtType.CREDIT)
        )
        assertNull(
            "no kind, no category, and the editor runs no query",
            TransactionEditorRules.debtCategoryTag(null)
        )
    }

    @Test
    fun aDepositAndAWithdrawAreFiledUnderTheirOwnCategories() {
        assertEquals(
            Contract.CategoryTag.SAVING_DEPOSIT,
            TransactionEditorRules.savingCategoryTag(TransactionEditorRules.SAVING_DEPOSIT)
        )
        assertEquals(
            Contract.CategoryTag.SAVING_WITHDRAW,
            TransactionEditorRules.savingCategoryTag(TransactionEditorRules.SAVING_WITHDRAW)
        )
    }

    @Test
    fun withdrawEverythingIsFiledUnderTheWithdrawCategory() {
        assertEquals(
            "withdraw everything is a withdrawal. The activity used to reach this by falling through one case into the other",
            Contract.CategoryTag.SAVING_WITHDRAW,
            TransactionEditorRules.savingCategoryTag(TransactionEditorRules.SAVING_WITHDRAW_EVERYTHING)
        )
    }

    @Test
    fun anActionThatNamesNothingIsFiledNowhere() {
        assertNull(
            "a crafted intent carrying no saving action used to bind null into the category query and crash the editor as it opened",
            TransactionEditorRules.savingCategoryTag(0)
        )
    }

    @Test
    fun onlyWithdrawEverythingCompletesTheSaving() {
        assertTrue(TransactionEditorRules.completesTheSaving(TransactionEditorRules.SAVING_WITHDRAW_EVERYTHING))
        assertFalse(TransactionEditorRules.completesTheSaving(TransactionEditorRules.SAVING_WITHDRAW))
        assertFalse(TransactionEditorRules.completesTheSaving(TransactionEditorRules.SAVING_DEPOSIT))
        assertFalse(TransactionEditorRules.completesTheSaving(0))
    }

    // ---- the prefill and the ceiling -------------------------------------------------------

    @Test
    fun theWithdrawEverythingPrefillIsWhatTheSavingCanGive() {
        assertEquals(76000L, TransactionEditorRules.withdrawEverythingPrefill(76000L))
    }

    @Test
    fun theWithdrawEverythingPrefillIsNeverNegative() {
        assertEquals(
            "a saving already carrying more withdrawals than it holds gives a negative figure",
            0L, TransactionEditorRules.withdrawEverythingPrefill(-4240L)
        )
    }

    @Test
    fun aSavingWhoseRowsDoNotComeBackPrefillsNothing() {
        assertEquals(0L, TransactionEditorRules.withdrawEverythingPrefill(null))
    }

    @Test
    fun theCeilingIsHeldAtNothing() {
        assertEquals(
            "a saving already under zero on some date from here on would otherwise refuse every save of every row it carries, an amount of nothing included",
            0L, TransactionEditorRules.withdrawLimit(-1L)
        )
        assertEquals(76000L, TransactionEditorRules.withdrawLimit(76000L))
    }

    @Test
    fun anAmountOfNothingIsNeverRefused() {
        assertTrue(
            "or a stored withdrawal of nothing could never have its own note or date corrected, and only deletion would be open",
            TransactionEditorRules.isWithinLimit(0L, 0L)
        )
    }

    @Test
    fun theCeilingRefusesOnlyWhatIsOverIt() {
        assertTrue(TransactionEditorRules.isWithinLimit(75999L, 76000L))
        assertTrue(
            "the ceiling itself is allowed",
            TransactionEditorRules.isWithinLimit(76000L, 76000L)
        )
        assertFalse(TransactionEditorRules.isWithinLimit(76001L, 76000L))
    }

    // ---- when the ceiling applies at all ---------------------------------------------------

    @Test
    fun theCeilingAppliesInTheSavingsOwnCurrency() {
        assertTrue(TransactionEditorRules.ceilingApplies(read("EUR"), read("EUR")))
    }

    @Test
    fun theCeilingStepsAsideForAWalletInAnotherCurrency() {
        assertFalse(
            "the two amounts are not comparable as they stand and this screen converts nowhere else",
            TransactionEditorRules.ceilingApplies("EUR", "USD")
        )
    }

    @Test
    fun theCeilingStepsAsideWhenEitherCurrencyIsUnresolvable() {
        assertFalse(
            "a saving whose own currency the app cannot resolve, which a restored backup can produce",
            TransactionEditorRules.ceilingApplies(null, "EUR")
        )
        assertFalse(TransactionEditorRules.ceilingApplies("EUR", null))
        assertFalse(TransactionEditorRules.ceilingApplies(null, null))
    }

    // ---- the stored row that is kept or lowered --------------------------------------------

    @Test
    fun aStoredWithdrawalKeptExactlyAsItWasIsNeverRefused() {
        assertTrue(
            TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                500L, "2026-09-01 10:00:00", 500L, "2026-09-01 10:00:00"
            )
        )
    }

    @Test
    fun aStoredWithdrawalLoweredIsNeverRefused() {
        assertTrue(
            "two withdrawals that already have a saving under zero would otherwise freeze each other, and deleting one would be the only way out",
            TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                400L, "2026-09-01 10:00:00", 500L, "2026-09-01 10:00:00"
            )
        )
    }

    @Test
    fun aStoredWithdrawalRaisedIsHeldToTheCeiling() {
        assertFalse(
            TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                600L, "2026-09-01 10:00:00", 500L, "2026-09-01 10:00:00"
            )
        )
    }

    @Test
    fun aStoredWithdrawalMovedEarlierIsHeldToTheCeiling() {
        assertFalse(
            "moving a stored row earlier is a new drain on every date it moves across",
            TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                500L, "2026-08-01 10:00:00", 500L, "2026-09-01 10:00:00"
            )
        )
    }

    @Test
    fun aStoredWithdrawalMovedLaterIsNeverRefused() {
        assertTrue(
            TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                500L, "2026-10-01 10:00:00", 500L, "2026-09-01 10:00:00"
            )
        )
    }

    // ---- how the walk reads one row --------------------------------------------------------

    @Test
    fun aWithdrawalIsTheIncomeHalfOfThePair() {
        assertTrue(
            "it pays money into the wallet. Reading the other constant here compiles and turns every row into its opposite",
            TransactionEditorRules.isWithdrawal(Contract.Direction.INCOME)
        )
        assertFalse(TransactionEditorRules.isWithdrawal(Contract.Direction.EXPENSE))
    }

    @Test
    fun aWithdrawalTakesTheSavingDownAndADepositPutsMoneyIn() {
        assertEquals(
            -500L,
            TransactionEditorRules.signedAmount(SavingRow(1L, "2026-09-01 10:00:00", 500L, WITHDRAWAL, true))
        )
        assertEquals(
            500L,
            TransactionEditorRules.signedAmount(SavingRow(1L, "2026-09-01 10:00:00", 500L, DEPOSIT, true))
        )
    }

    @Test
    fun anUnconfirmedDepositIsLeftOut() {
        assertFalse(
            "landing takes being confirmed, and money that never arrives must not pay for a withdrawal that does",
            TransactionEditorRules.countsTowardBalance(
                SavingRow(1L, "2026-09-01 10:00:00", 500L, DEPOSIT, false)
            )
        )
    }

    @Test
    fun anUnconfirmedWithdrawalIsCounted() {
        assertTrue(
            "leaving it out hands out a ceiling it can then take the saving under",
            TransactionEditorRules.countsTowardBalance(
                SavingRow(1L, "2026-09-01 10:00:00", 500L, WITHDRAWAL, false)
            )
        )
    }

    @Test
    fun aConfirmedDepositIsCounted() {
        assertTrue(
            TransactionEditorRules.countsTowardBalance(
                SavingRow(1L, "2026-09-01 10:00:00", 500L, DEPOSIT, true)
            )
        )
    }

    // ---- the walk ---------------------------------------------------------------------------

    @Test
    fun theWalkCountsEveryRowFromTheDateOnScreen() {
        assertEquals(
            "start 10000 plus 90000 in, minus 15000 out, is 85000 before the deposit on the 20th lifts it",
            85000L,
            TransactionEditorRules.lowestSavingBalanceFrom(10000L, ledger(), -1L, "2026-09-01 10:00:00")
        )
    }

    @Test
    fun theWalkCountsFromTheDateItIsGivenAndNotFromToday() {
        assertEquals(
            "the same rows from a date after all of them answer what the saving ends up holding, not the 85000 it dips to on the way.",
            115000L,
            TransactionEditorRules.lowestSavingBalanceFrom(10000L, ledger(), -1L, "2026-09-25 10:00:00")
        )
    }

    @Test
    fun theWalkLeavesOutTheRowBeingEdited() {
        assertEquals(
            "the row being edited must not be held against its own drain, or it could never be raised",
            100000L,
            TransactionEditorRules.lowestSavingBalanceFrom(10000L, ledger(), 1L, "2026-09-01 10:00:00")
        )
    }

    @Test
    fun theWalkOrdersTheRowsItself() {
        val shuffled = ledger()
        Collections.reverse(shuffled)
        assertEquals(
            "the answer must not depend on the order the caller read the rows in.",
            85000L,
            TransactionEditorRules.lowestSavingBalanceFrom(10000L, shuffled, -1L, "2026-09-01 10:00:00")
        )
    }

    @Test
    fun theWalkLeavesOutAnUnconfirmedDepositThatWouldPayForAWithdrawal() {
        val rows = ArrayList(
            Arrays.asList(
                SavingRow(1L, "2026-09-01 10:00:00", 300000L, DEPOSIT, false),
                SavingRow(2L, "2026-10-01 10:00:00", 300000L, WITHDRAWAL, true)
            )
        )
        assertEquals(
            "an unconfirmed deposit funding a withdrawal dated next month is the reported defect: the goal went to 3,000.00 negative when that row landed",
            -300000L,
            TransactionEditorRules.lowestSavingBalanceFrom(0L, rows, -1L, "2026-09-01 10:00:00")
        )
    }

    @Test
    fun theWalkOverNoRowsAnswersTheStartMoney() {
        assertEquals(
            10000L,
            TransactionEditorRules.lowestSavingBalanceFrom(10000L, ArrayList<SavingRow>(), -1L, "2026-09-01 10:00:00")
        )
    }

    // ---- the state the editor carries across a recreate ------------------------------------

    @Test
    fun theRulesCarryTheirStateAcrossASerializeRoundTrip() {
        val before = TransactionEditorRules()
        before.type = TransactionEditorRules.TYPE_DEBT
        before.debtId = 7L
        before.savingId = 88L
        before.isSavingCompleted = true
        before.isDebtPayment = true
        val after = roundTrip(before)
        assertEquals(TransactionEditorRules.TYPE_DEBT, after.type)
        assertEquals(java.lang.Long.valueOf(7L), after.debtId)
        assertEquals(
            "asserting null here passes whether or not the field survives, which is how a lost saving id would read",
            java.lang.Long.valueOf(88L), after.savingId
        )
        assertTrue(after.isSavingCompleted)
        assertTrue(
            "losing this gives a payment its wallet field back on the next rotation",
            after.isDebtPayment
        )
    }
}
