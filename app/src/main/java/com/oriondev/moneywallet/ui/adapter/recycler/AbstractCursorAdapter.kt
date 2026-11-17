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

package com.oriondev.moneywallet.ui.adapter.recycler

import android.database.Cursor
import android.database.DataSetObserver
import androidx.recyclerview.widget.RecyclerView

/**
 * Created by andrea on 26/01/18.
 */
abstract class AbstractCursorAdapter<VH : RecyclerView.ViewHolder>(
    cursor: Cursor?,
    private val mIdColumn: String
) : RecyclerView.Adapter<VH>() {

    private var mCursor: Cursor? = cursor
    private var mDataValid: Boolean = cursor != null
    private var mRowIdColumn: Int = cursor?.getColumnIndex(mIdColumn) ?: -1
    private val mDataSetObserver: DataSetObserver = NotifyingDataSetObserver()
    private var mColumnIndicesLoaded: Boolean = false

    init {
        mCursor?.registerDataSetObserver(mDataSetObserver)
    }

    private fun prepareNewCursor(cursor: Cursor) {
        onLoadColumnIndices(cursor)
    }

    protected fun ensureColumnIndicesLoaded(cursor: Cursor) {
        if (!mColumnIndicesLoaded) {
            mColumnIndicesLoaded = true
            prepareNewCursor(cursor)
        }
    }

    protected abstract fun onLoadColumnIndices(cursor: Cursor)

    fun isDataValid(): Boolean = mDataValid

    fun getCursor(): Cursor? = mCursor

    override fun getItemCount(): Int {
        val cursor = mCursor
        return if (mDataValid && cursor != null) cursor.count else 0
    }

    override fun getItemId(position: Int): Long {
        val cursor = mCursor
        if (mDataValid && cursor != null && cursor.moveToPosition(position)) {
            return cursor.getLong(mRowIdColumn)
        }
        return 0L
    }

    protected fun getSafeCursor(position: Int): Cursor? {
        val cursor = mCursor
        if (mDataValid && cursor != null) {
            ensureColumnIndicesLoaded(cursor)
            if (cursor.moveToPosition(position)) {
                return cursor
            }
        }
        return null
    }

    override fun setHasStableIds(hasStableIds: Boolean) {
        super.setHasStableIds(true)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cursor = mCursor
        if (!mDataValid || cursor == null) {
            throw IllegalStateException("this should only be called when cursor data is valid")
        }
        ensureColumnIndicesLoaded(cursor)
        if (!cursor.moveToPosition(position)) {
            throw IllegalStateException("couldn't move cursor to position: $position")
        }
        onBindViewHolder(holder, cursor)
    }

    abstract fun onBindViewHolder(holder: VH, cursor: Cursor)

    fun changeCursor(cursor: Cursor?) {
        val old = swapCursor(cursor)
        old?.close()
    }

    open fun swapCursor(newCursor: Cursor?): Cursor? {
        if (newCursor == mCursor) {
            return null
        }
        val oldCursor = mCursor
        oldCursor?.unregisterDataSetObserver(mDataSetObserver)
        mCursor = newCursor
        if (newCursor != null) {
            newCursor.registerDataSetObserver(mDataSetObserver)
            mRowIdColumn = newCursor.getColumnIndexOrThrow(mIdColumn)
            mDataValid = true
            mColumnIndicesLoaded = true
            prepareNewCursor(newCursor)
            notifyDataSetChanged()
        } else {
            mRowIdColumn = -1
            mDataValid = false
            mColumnIndicesLoaded = false
            notifyDataSetChanged()
        }
        return oldCursor
    }

    private inner class NotifyingDataSetObserver : DataSetObserver() {

        override fun onChanged() {
            super.onChanged()
            mDataValid = true
            notifyDataSetChanged()
        }

        override fun onInvalidated() {
            super.onInvalidated()
            mDataValid = false
            notifyDataSetChanged()
        }
    }
}
