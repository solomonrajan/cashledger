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
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditPersonActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.ui.activity.TransactionListActivity
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Created by andrea on 03/04/18.
 */
class PersonItemFragment : SecondaryPanelFragment(), LoaderManager.LoaderCallbacks<Cursor> {

    private var mProgressLayout: View? = null
    private var mMainLayout: View? = null

    private var mAvatarImageView: ImageView? = null
    private var mNameTextView: TextView? = null
    private var mNoteTextView: TextView? = null

    override fun onCreateHeaderView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_header_show_icon_name_item, parent, true)
        mAvatarImageView = view.findViewById(R.id.avatar_image_view)
        mNameTextView = view.findViewById(R.id.name_text_view)
    }

    override fun onCreateBodyView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        val view = inflater.inflate(R.layout.layout_panel_show_person_item, parent, true)
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel)
        mMainLayout = view.findViewById(R.id.secondary_panel_layout)
        mNoteTextView = view.findViewById(R.id.note_text_view)
    }

    override fun getTitle(): String {
        return getString(R.string.title_fragment_item_person)
    }

    override fun onInflateMenu(): Int {
        return R.menu.menu_person_item
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        val itemId = item.itemId
        if (itemId == R.id.action_new_transaction_for_person) {
            val intent = Intent(activity, NewEditTransactionActivity::class.java).apply {
                putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM)
                putExtra(NewEditTransactionActivity.PERSON_ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_show_transaction_list) {
            val intent = Intent(activity, TransactionListActivity::class.java).apply {
                putExtra(TransactionListActivity.PERSON_ID, getItemId())
            }
            startActivity(intent)
        } else if (itemId == R.id.action_edit_item) {
            val intent = Intent(activity, NewEditPersonActivity::class.java).apply {
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
            .setMessage(R.string.message_delete_person)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val act = activity
                if (act != null) {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, getItemId())
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
        LoaderManager.getInstance(this).restartLoader(PERSON_LOADER_ID, null, this)
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
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, getItemId())
        val projection = arrayOf(
            Contract.Person.NAME,
            Contract.Person.ICON,
            Contract.Person.NOTE
        )
        return CursorLoader(act, uri, projection, null, null, null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        if (cursor != null && cursor.moveToFirst()) {
            val icon: Icon? = IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.ICON)))
            mAvatarImageView?.let { IconLoader.loadInto(icon, it) }
            mNameTextView?.text = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NAME))
            val note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NOTE))
            mNoteTextView?.text = note
            mNoteTextView?.visibility = if (TextUtils.isEmpty(note)) View.GONE else View.VISIBLE
        } else {
            showItemId(0L)
        }
        setLoadingScreen(false)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        // nothing to release
    }

    companion object {
        private const val PERSON_LOADER_ID = 53323
    }
}
