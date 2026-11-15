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

package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.ui.activity.NewEditEventActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import java.util.Date

/**
 * Created by andrea on 03/04/18.
 */
class EventItemFragment : SecondaryPanelFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var mProgressLayout: View? = null
    private var mMainLayout: View? = null

    private var mAvatarImageView: ImageView? = null
    private var mNameTextView: TextView? = null
    private var mStartDateTextView: TextView? = null
    private var mEndDateTextView: TextView? = null
    private var mNoteTextView: TextView? = null

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_show_icon_name_item, parent, true)
        mAvatarImageView = view.findViewById(R.id.avatar_image_view)
        mNameTextView = view.findViewById(R.id.name_text_view)
    }

    override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_show_event_item, parent, true)
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel)
        mMainLayout = view.findViewById(R.id.secondary_panel_layout)
        mStartDateTextView = view.findViewById(R.id.start_date_text_view)
        mEndDateTextView = view.findViewById(R.id.end_date_text_view)
        mNoteTextView = view.findViewById(R.id.note_text_view)
    }

    override fun getTitle(): String {
        return getString(R.string.title_fragment_item_event)
    }

    override fun onInflateMenu(): Int {
        return R.menu.menu_list_edit_delete_item
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_show_transaction_list) {
            val intent = Intent(activity, TransactionListActivity::class.java).apply {
                putExtra(TransactionListActivity.EVENT_ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_edit_item) {
            val intent = Intent(activity, NewEditEventActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM)
                putExtra(NewEditItemActivity.ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_delete_item) {
            activity?.let { showDeleteDialog(it) }
        }
        return false
    }

    private fun showDeleteDialog(context: Context) {
        ThemedDialog.buildMaterialDialog(context)
            .setTitle(R.string.title_warning)
            .setMessage(R.string.message_delete_event)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val act = activity
                if (act != null) {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, getItemId())
                    val contentResolver: ContentResolver = act.contentResolver
                    contentResolver.delete(uri, null, null)
                    navigateBackSafely()
                    showItemId(0L)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onShowItemId(itemId: Long) {
        setLoadingScreen(true)
        LoaderManager.getInstance(this).restartLoader(EVENT_LOADER_ID, null, this)
    }

    private fun setLoadingScreen(loading: Boolean) {
        if (loading) {
            mAvatarImageView?.setImageDrawable(null)
            mNameTextView?.text = null
            mProgressLayout?.visibility = View.VISIBLE
            mMainLayout?.visibility = View.GONE
        } else {
            mProgressLayout?.visibility = View.GONE
            mMainLayout?.visibility = View.VISIBLE
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        val act = activity ?: return null
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, getItemId())
        val projection = arrayOf(
            Contract.Event.NAME,
            Contract.Event.ICON,
            Contract.Event.START_DATE,
            Contract.Event.END_DATE,
            Contract.Event.NOTE
        )
        return CursorLoader(act, uri, projection, null, null, null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (cursor != null && cursor.moveToFirst()) {
            val icon: Icon? = IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Event.ICON)))
            mAvatarImageView?.let { IconLoader.loadInto(icon, it) }
            mNameTextView?.text = cursor.getString(cursor.getColumnIndex(Contract.Event.NAME))
            val startDate: Date? = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Event.START_DATE)))
            val endDate: Date? = DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Event.END_DATE)))
            startDate?.let { d -> mStartDateTextView?.let { tv -> DateFormatter.applyDate(tv, d) } }
            endDate?.let { d -> mEndDateTextView?.let { tv -> DateFormatter.applyDate(tv, d) } }
            val note = cursor.getString(cursor.getColumnIndex(Contract.Event.NOTE))
            if (!TextUtils.isEmpty(note)) {
                mNoteTextView?.text = note
                mNoteTextView?.visibility = View.VISIBLE
            } else {
                mNoteTextView?.visibility = View.GONE
            }
        } else {
            showItemId(0L)
        }
        setLoadingScreen(false)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    companion object {
        private const val EVENT_LOADER_ID = 53322
    }
}
