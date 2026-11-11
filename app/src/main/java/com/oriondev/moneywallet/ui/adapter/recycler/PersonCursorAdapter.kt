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
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andrea on 05/03/18.
 */
class PersonCursorAdapter(
    private val mActionListener: ActionListener?
) : AbstractCursorAdapter<PersonCursorAdapter.ViewHolder>(null, Contract.Person.ID) {

    private var mIndexId: Int = -1
    private var mIndexName: Int = -1
    private var mIndexIcon: Int = -1

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Person.ID)
        mIndexName = cursor.getColumnIndex(Contract.Person.NAME)
        mIndexIcon = cursor.getColumnIndex(Contract.Person.ICON)
    }

    override fun onBindViewHolder(holder: ViewHolder, cursor: Cursor) {
        val icon = IconLoader.parse(cursor.getString(mIndexIcon))
        IconLoader.loadInto(icon, holder.mAvatarImageView)
        holder.mNameTextView.text = cursor.getString(mIndexName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_person_item, parent, false)
        if (parent is RecyclerView) {
            val parentDelegate = parent.compatAccessibilityDelegate
            if (parentDelegate != null) {
                ViewCompat.setAccessibilityDelegate(
                    itemView,
                    PersonItemDelegate(parentDelegate.itemDelegate)
                )
            } else {
                // Only reachable if something cleared the RecyclerView's own delegate. The row
                // still works, it just loses the named long press, so leave a trace rather than
                // dropping the label in silence.
                Log.w("PersonCursorAdapter", "no RecyclerView accessibility delegate, long press stays unlabelled")
            }
        }
        return ViewHolder(itemView)
    }

    /**
     * A person row needs two things announced that cannot both come from one delegate. Its
     * position in the list is supplied only by the RecyclerView's own item delegate, and the long
     * press is otherwise announced with no destination. Setting any delegate on a row makes
     * RecyclerView skip attaching its own, so this forwards to that one rather than replacing it.
     */
    private class PersonItemDelegate(
        private val mItemDelegate: AccessibilityDelegateCompat
    ) : AccessibilityDelegateCompat() {

        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            mItemDelegate.onInitializeAccessibilityNodeInfo(host, info)
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
                    host.context.getString(R.string.action_open_person_details)
                )
            )
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            // Nothing this forward reaches is implemented on the recyclerview this project
            // resolves, so it changes no behaviour today. It is here so that wrapping the item
            // delegate stays a complete wrap rather than a partial one.
            return mItemDelegate.performAccessibilityAction(host, action, args)
        }
    }

    inner class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView),
        View.OnClickListener,
        View.OnLongClickListener {

        val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        val mNameTextView: TextView = itemView.findViewById(R.id.primary_text_view)

        init {
            itemView.setOnClickListener(this)
            // Named for screen readers by PersonItemDelegate above, which is set in
            // onCreateViewHolder because it needs the RecyclerView to forward to.
            itemView.setOnLongClickListener(this)
        }

        override fun onClick(v: View) {
            val listener = mActionListener ?: return
            val cursor = getSafeCursor(adapterPosition) ?: return
            listener.onPersonClick(cursor.getLong(mIndexId))
        }

        override fun onLongClick(v: View): Boolean {
            val listener = mActionListener ?: return false
            val cursor = getSafeCursor(adapterPosition)
            if (cursor != null) {
                listener.onPersonLongClick(cursor.getLong(mIndexId))
            }
            // Consumed whenever there is a listener to consume it. Declining it would let the
            // release that follows run performClick instead, so a long press that lost its row
            // would open the transaction list rather than the panel the user was reaching for.
            return true
        }
    }

    interface ActionListener {
        fun onPersonClick(id: Long)

        fun onPersonLongClick(id: Long)
    }
}
