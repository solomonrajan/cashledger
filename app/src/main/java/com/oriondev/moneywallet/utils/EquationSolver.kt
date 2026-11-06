/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.utils

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.model.MoneyScale
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.max

/**
 * Created by andrea on 31/03/18.
 */
class EquationSolver(savedInstanceState: Bundle?, private val mController: Controller?) {

    @JvmField
    @VisibleForTesting
    internal var mFirstNumber: String?

    @JvmField
    @VisibleForTesting
    internal var mSecondNumber: String?

    @JvmField
    @VisibleForTesting
    internal var mOperation: Operation?

    @JvmField
    @VisibleForTesting
    internal var mCurrency: CurrencyUnit?

    /**
     * True while mFirstNumber holds an answer an operation produced rather than a number the
     * keypad typed. getResult rounds an answer to the currency scale and leaves a typed number
     * to toMinorUnits, which truncates whatever will not fit.
     */
    @JvmField
    @VisibleForTesting
    internal var mComputed: Boolean

    init {
        if (savedInstanceState != null) {
            mFirstNumber = savedInstanceState.getString(SS_FIRST_NUMBER, "0")
            mSecondNumber = savedInstanceState.getString(SS_SECOND_NUMBER, null)
            @Suppress("DEPRECATION")
            mOperation = savedInstanceState.getSerializable(SS_OPERATION_NUMBER) as? Operation
            @Suppress("DEPRECATION")
            mCurrency = savedInstanceState.getParcelable(SS_CURRENCY) as? CurrencyUnit
            mComputed = savedInstanceState.getBoolean(SS_COMPUTED, false)
        } else {
            mFirstNumber = "0"
            mSecondNumber = null
            mOperation = null
            mCurrency = null
            mComputed = false
        }
        updateDisplaySafely()
    }

    fun setValue(currency: CurrencyUnit?, money: Long) {
        // Scale taken through CurrencyManager for the reason getResult gives.
        val decimals = CurrencyManager.getDecimals(currency)
        mFirstNumber = if (money != 0L && decimals > 0) {
            MoneyScale.toHumanAmount(money, decimals).toPlainString()
        } else {
            money.toString()
        }
        mSecondNumber = null
        mOperation = null
        mCurrency = currency
        mComputed = false
        updateDisplaySafely()
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SS_FIRST_NUMBER, mFirstNumber)
        outState.putString(SS_SECOND_NUMBER, mSecondNumber)
        outState.putSerializable(SS_OPERATION_NUMBER, mOperation)
        // The constructor reads this key back, and the activity only calls setValue when there is
        // no saved state, so leaving the currency out made getResult return the typed number
        // rather than its minor units after a rotation: 12.50 came back as 12, not 1250.
        outState.putParcelable(SS_CURRENCY, mCurrency)
        // Same trap as the currency above: a key the bundle does not carry comes back as the
        // default, so an answer still on the display after a rotation would be truncated rather
        // than rounded when it reaches the ledger.
        outState.putBoolean(SS_COMPUTED, mComputed)
    }

    fun clear() {
        mFirstNumber = "0"
        mSecondNumber = null
        mOperation = null
        mComputed = false
        updateDisplaySafely()
    }

    fun appendOperation(operation: Operation) {
        if (mOperation == null || execute(false)) {
            mOperation = operation
            updateDisplaySafely()
        }
    }

    /**
     * The three editing keys act at a cursor, an index into the string updateDisplaySafely
     * renders, and hand back where the cursor sits once the edit is in. An index up to the length
     * of the first number addresses the first number; anything past that addresses the second one,
     * with the three character operator span pointing at its start. An index outside the display
     * means the end of the display.
     */
    private fun locate(cursor: Int): Target {
        val first = firstLength()
        if (mOperation == null) {
            return Target(true, if (cursor < 0 || cursor > first) first else cursor)
        }
        val second = mSecondNumber?.length ?: 0
        val end = first + 3 + second
        val at = if (cursor < 0 || cursor > end) end else cursor
        return if (at <= first) {
            Target(true, at)
        } else {
            Target(false, max(at - first - 3, 0))
        }
    }

    private fun firstLength(): Int {
        return mFirstNumber?.length ?: 0
    }

    private fun cursorOf(target: Target, offset: Int): Int {
        return if (target.mFirst) offset else firstLength() + 3 + offset
    }

    private fun store(target: Target, number: String) {
        if (target.mFirst) {
            clearComputedIfValueChanged(number)
            mFirstNumber = number
        } else {
            mSecondNumber = number
        }
    }

    fun insertPoint(cursor: Int): Int {
        val target = locate(cursor)
        var number = if (target.mFirst) mFirstNumber else mSecondNumber
        var offset = target.mOffset
        if (number == null || number.isEmpty() || number == "0") {
            number = "0."
            offset = 2
        } else if (!number.contains(".")) {
            val sign = if (number.startsWith("-")) 1 else 0
            if (offset <= sign) {
                // A point typed in front of the digits opens a fraction instead of splitting one.
                number = number.substring(0, sign) + "0." + number.substring(sign)
                offset = sign + 2
            } else {
                number = number.substring(0, offset) + "." + number.substring(offset)
                offset++
            }
        }
        store(target, number)
        updateDisplaySafely()
        return cursorOf(target, offset)
    }

    fun insertNumber(digit: String, cursor: Int): Int {
        val target = locate(cursor)
        var number = if (target.mFirst) mFirstNumber else mSecondNumber
        var offset = target.mOffset
        if (number == null || number.isEmpty() || number == "0") {
            number = if (digit == "000") "0" else digit
            offset = number.length
        } else {
            if (number.startsWith("-") && offset == 0) {
                // A digit typed in front of a sign belongs after it.
                offset = 1
            }
            number = number.substring(0, offset) + digit + number.substring(offset)
            offset += digit.length
        }
        store(target, number)
        updateDisplaySafely()
        return cursorOf(target, offset)
    }

    fun backspace(cursor: Int): Int {
        val target = locate(cursor)
        var number = if (target.mFirst) mFirstNumber else mSecondNumber
        var offset = target.mOffset
        var atOperator = false
        if (offset == 0) {
            if (!target.mFirst && (number == null || number.isEmpty())) {
                // Nothing left of the second number to delete, so the operator goes instead.
                mOperation = null
                mSecondNumber = null
                atOperator = true
            } else if (!target.mFirst) {
                // Nothing to delete here, so the cursor steps back over the operator instead.
                atOperator = true
            }
        } else {
            val num = number ?: ""
            number = if (offset <= num.length) {
                num.substring(0, offset - 1) + num.substring(offset)
            } else {
                num
            }
            offset--
            if (target.mFirst && number.isEmpty()) {
                number = "0"
                offset = 1
            }
            store(target, number)
        }
        updateDisplaySafely()
        return if (atOperator) firstLength() else cursorOf(target, offset)
    }

    fun isPendingOperation(): Boolean {
        return mOperation != null
    }

    fun execute(fireCallback: Boolean): Boolean {
        val first = parseNumber(mFirstNumber)
        val second = parseNumber(mSecondNumber)
        val op = mOperation ?: return false
        try {
            val result: BigDecimal = when (op) {
                Operation.ADDITION -> first.add(second)
                Operation.SUBTRACTION -> first.subtract(second)
                Operation.MULTIPLICATION -> first.multiply(second)
                Operation.DIVISION -> {
                    // divide(divisor, roundingMode) returns a value whose scale is the dividend's,
                    // so a 10 typed with no decimals rounded 10 / 4 to 2. Divide at 16 significant
                    // digits instead. Narrowing to the currency scale here would narrow every step
                    // of a chain rather than the answer: with a zero decimal currency, 10.0 / 4 * 4
                    // would give 8. getResult narrows the finished equation once.
                    first.divide(second, MathContext.DECIMAL64)
                }
            }
            // String.valueOf renders a small enough result in scientific notation, the product
            // 0.001 * 0.0001 as 1E-7, and the next keypress appends to whatever string lands here.
            var formattedResult = result.toPlainString()
            if (formattedResult.endsWith(".0")) {
                formattedResult = formattedResult.substring(0, formattedResult.length - 2)
            }
            mFirstNumber = formattedResult
            mSecondNumber = null
            mOperation = null
            // An operation that hands the amount back unchanged has computed nothing the ledger
            // can see, and the display still reads the number that was there before it. The equals
            // key runs the equation whether or not a second number was entered, and appendOperation
            // runs it again when a second operator is pressed, so both reach here against the zero
            // parseNumber returns for a second number nobody typed. Adding that zero and
            // multiplying by one land in the same place: leave the flag as it was found, so a typed
            // amount stays typed and an answer stays an answer.
            if (result.compareTo(first) != 0) {
                mComputed = true
            }
            if (fireCallback) {
                updateDisplaySafely()
            }
            return true
        } catch (ignore: ArithmeticException) {
            ignore.printStackTrace()
        }
        return false
    }

    fun getResult(): Long {
        if (isPendingOperation() && !execute(false)) {
            // Error occurred during calculation
            return 0L
        }
        var parsedNumber = parseNumber(mFirstNumber)
        // A currency this installation cannot resolve is worth two decimals here, which is what
        // CurrencyManager.getDecimals answers for a null one and what MoneyFormatter divides the
        // same row by when it cannot resolve it either. This used to return the typed number as
        // if it were already minor units, so a wallet naming a missing currency stored a typed
        // 12.50 as 12, which MoneyFormatter then renders as 0.12.
        val decimals = CurrencyManager.getDecimals(mCurrency)
        if (mComputed) {
            // An answer is rounded to the currency scale, so 20.00 / 3 stores 667. A typed
            // number falls through to toMinorUnits, which truncates what will not fit: 64.9 on a
            // zero decimal currency stays 64, as the entry tests pin.
            parsedNumber = parsedNumber.setScale(decimals, RoundingMode.HALF_EVEN)
        }
        return MoneyScale.toMinorUnits(parsedNumber, decimals)
    }

    /**
     * Clears the computed flag when the candidate first number differs in value from the one on
     * the display. Compared as numbers, not as strings: appending a zero to an answer, or
     * backspacing one off it, leaves the display reading the same amount, and a keypress that
     * changes nothing does not retype what is there.
     */
    private fun clearComputedIfValueChanged(number: String) {
        if (parseNumber(number).compareTo(parseNumber(mFirstNumber)) != 0) {
            mComputed = false
        }
    }

    private fun parseNumber(number: String?): BigDecimal {
        var safe = if (number.isNullOrEmpty()) "0" else number
        if (safe.endsWith(".")) {
            safe = safe.substring(0, safe.length - 1)
        }
        return try {
            BigDecimal(safe)
        } catch (ignore: NumberFormatException) {
            BigDecimal.valueOf(0)
        }
    }

    private class Target(val mFirst: Boolean, val mOffset: Int)

    enum class Operation {
        ADDITION,
        SUBTRACTION,
        MULTIPLICATION,
        DIVISION
    }

    private fun updateDisplaySafely() {
        if (mController != null) {
            val builder = StringBuilder()
            builder.append(mFirstNumber)
            val op = mOperation
            if (op != null) {
                when (op) {
                    Operation.ADDITION -> builder.append(" + ")
                    Operation.SUBTRACTION -> builder.append(" − ")
                    Operation.MULTIPLICATION -> builder.append(" × ")
                    Operation.DIVISION -> builder.append(" ÷ ")
                }
                if (mSecondNumber != null) {
                    builder.append(mSecondNumber)
                }
            }
            mController.onUpdateDisplay(builder.toString())
        }
    }

    fun interface Controller {
        fun onUpdateDisplay(text: String)
    }

    companion object {
        private const val SS_FIRST_NUMBER = "EquationSolver::SavedState::FirstNumber"
        private const val SS_SECOND_NUMBER = "EquationSolver::SavedState::SecondNumber"
        private const val SS_OPERATION_NUMBER = "EquationSolver::SavedState::Operation"
        private const val SS_CURRENCY = "EquationSolver::SavedState::Currency"
        private const val SS_COMPUTED = "EquationSolver::SavedState::Computed"
    }
}

