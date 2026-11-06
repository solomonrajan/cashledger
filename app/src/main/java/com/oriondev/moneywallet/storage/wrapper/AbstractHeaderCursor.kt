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

package com.oriondev.moneywallet.storage.wrapper

import android.database.AbstractCursor
import android.database.ContentObserver
import android.database.Cursor
import android.database.DataSetObserver
import android.util.SparseArray
import com.oriondev.moneywallet.model.Pair

/**
 * Created by andrea on 03/03/18.
 */
abstract class AbstractHeaderCursor<H>(
    private val mCursor: Cursor
) : AbstractCursor() {

    private val mHeaders: SparseArray<H> = SparseArray()
    private val mIndices: MutableList<Pair<Int, Int>> = ArrayList()
    private val mHeaderColumnCount: Int
    private val mColumnNames: Array<String>

    private var mIsHeader: Boolean = false
    private var mPosition: Int = 0

    init {
        val headerColumnNames = getHeaderColumnNames()
        mHeaderColumnCount = headerColumnNames.size
        mColumnNames = generateColumnNames(headerColumnNames, mCursor)
    }

    private fun generateColumnNames(headerColumnNames: Array<String>, cursor: Cursor): Array<String> {
        val cursorColumnNames = cursor.columnNames ?: emptyArray()
        val columnNames = Array(headerColumnNames.size + cursorColumnNames.size) { "" }
        for (i in columnNames.indices) {
            if (i < headerColumnNames.size) {
                columnNames[i] = headerColumnNames[i]
            } else {
                columnNames[i] = cursorColumnNames[i - headerColumnNames.size]
            }
        }
        return columnNames
    }

    protected abstract fun generateHeaders(cursor: Cursor)

    protected fun getHeader(): H? {
        return mHeaders.get(mPosition)
    }

    protected fun isHeader(): Boolean {
        return mIsHeader
    }

    /** Whether that position holds a header, answered from the index list without moving any cursor. */
    fun isHeaderAt(position: Int): Boolean {
        return position in 0 until mIndices.size && mIndices[position].l == TYPE_HEADER
    }

    protected fun addHeader(header: H) {
        val id = mHeaders.size()
        mHeaders.put(id, header)
        mIndices.add(Pair(TYPE_HEADER, id))
    }

    protected fun addItem(position: Int) {
        mIndices.add(Pair(TYPE_ITEM, position))
    }

    /**
     * Obtain the size of the wrapped cursor.
     * It must consider the size of the SQL cursor and the size of all the headers.
     * @return the correct size of this wrapper.
     */
    override fun getCount(): Int {
        return mIndices.size
    }

    override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
        if (newPosition < mIndices.size) {
            val item = mIndices[newPosition]
            mIsHeader = item.l == TYPE_HEADER
            mPosition = item.r
            if (!mIsHeader) {
                mCursor.moveToPosition(mPosition)
            }
            return true
        }
        return false
    }

    override fun getColumnNames(): Array<String> {
        return mColumnNames
    }

    protected abstract fun getHeaderColumnNames(): Array<String>

    override fun getString(i: Int): String? {
        if (i < mHeaderColumnCount) {
            return getHeaderString(i)
        }
        return mCursor.getString(i - mHeaderColumnCount)
    }

    protected abstract fun getHeaderString(index: Int): String?

    override fun getShort(i: Int): Short {
        if (i < mHeaderColumnCount) {
            return getHeaderShort(i)
        }
        return mCursor.getShort(i - mHeaderColumnCount)
    }

    protected abstract fun getHeaderShort(index: Int): Short

    override fun getInt(i: Int): Int {
        if (i < mHeaderColumnCount) {
            return getHeaderInt(i)
        }
        return mCursor.getInt(i - mHeaderColumnCount)
    }

    protected abstract fun getHeaderInt(index: Int): Int

    override fun getLong(i: Int): Long {
        if (i < mHeaderColumnCount) {
            return getHeaderLong(i)
        }
        return mCursor.getLong(i - mHeaderColumnCount)
    }

    protected abstract fun getHeaderLong(index: Int): Long

    override fun getFloat(i: Int): Float {
        if (i < mHeaderColumnCount) {
            return getHeaderFloat(i)
        }
        return mCursor.getFloat(i - mHeaderColumnCount)
    }

    protected abstract fun getHeaderFloat(index: Int): Float

    override fun getDouble(i: Int): Double {
        if (i < mHeaderColumnCount) {
            return getHeaderDouble(i)
        }
        return mCursor.getDouble(i - mHeaderColumnCount)
    }

    protected abstract fun getHeaderDouble(index: Int): Double

    override fun isNull(i: Int): Boolean {
        if (i < mHeaderColumnCount) {
            return isHeaderNull(i)
        }
        return mCursor.isNull(i - mHeaderColumnCount)
    }

    protected abstract fun isHeaderNull(index: Int): Boolean

    @Suppress("DEPRECATION")
    override fun deactivate() {
        mCursor.deactivate()
        super.deactivate()
    }

    override fun close() {
        mCursor.close()
        super.close()
    }

    override fun registerContentObserver(observer: ContentObserver?) {
        mCursor.registerContentObserver(observer)
    }

    override fun unregisterContentObserver(observer: ContentObserver?) {
        mCursor.unregisterContentObserver(observer)
    }

    override fun registerDataSetObserver(observer: DataSetObserver?) {
        mCursor.registerDataSetObserver(observer)
    }

    override fun unregisterDataSetObserver(observer: DataSetObserver?) {
        mCursor.unregisterDataSetObserver(observer)
    }

    @Suppress("DEPRECATION")
    override fun requery(): Boolean {
        return mCursor.requery()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}
