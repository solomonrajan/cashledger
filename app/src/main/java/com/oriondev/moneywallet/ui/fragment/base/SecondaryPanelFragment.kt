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

package com.oriondev.moneywallet.ui.fragment.base

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.ui.activity.base.ThemedActivity
import com.oriondev.moneywallet.utils.Utils
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * Created by andrea on 01/04/18.
 */
abstract class SecondaryPanelFragment : Fragment(), Toolbar.OnMenuItemClickListener {

    companion object {
        private const val SS_ITEM_ID = "SecondaryPanelFragment::SavedState::ItemId"

        private val sDeleteExecutor: Executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "SecondaryPanelDelete")
        }
        private val sMainHandler = Handler(Looper.getMainLooper())
    }

    private var mEmptyLayout: ViewGroup? = null
    private var mMainLayout: ViewGroup? = null
    private var mToolbar: Toolbar? = null

    private var mCurrentId: Long = 0L

    private var mDeleting = false
    private var mIsCreated = false
    private var mCachedId: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_secondary_panel_item, container, false)
        mEmptyLayout = view.findViewById(R.id.empty_screen_secondary_panel_layout)
        mMainLayout = Utils.findViewGroupByIds(
            view,
            R.id.main_screen_secondary_panel_scroll_view,
            R.id.main_screen_secondary_panel_linear_layout
        )
        val headerLayout: ViewGroup? = view.findViewById(R.id.header_secondary_panel_layout)
        val toolbar: Toolbar? = view.findViewById(R.id.secondary_toolbar)
        mToolbar = toolbar
        val bodyLayout: ViewGroup? = view.findViewById(R.id.body_secondary_panel_layout)
        headerLayout?.let { onCreateHeaderView(inflater, it, savedInstanceState) }
        bodyLayout?.let { onCreateBodyView(inflater, it, savedInstanceState) }
        val currentActivity = activity
        if (currentActivity is ThemedActivity) {
            currentActivity.followScrollForStatusBarIcons(
                view.findViewById(R.id.main_screen_secondary_panel_scroll_view),
                headerLayout
            )
        }
        if (toolbar != null) {
            val parent = parentFragment
            if (parent is MultiPanelController && !parent.isExtendedLayout()) {
                toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp)
                toolbar.setNavigationOnClickListener {
                    navigateBackSafely()
                }
            }
            toolbar.title = getTitle()
            val menuRes = onInflateMenu()
            if (menuRes > 0) {
                toolbar.inflateMenu(menuRes)
                toolbar.setOnMenuItemClickListener { item -> mDeleting || onMenuItemClick(item) }
            }
        }
        mIsCreated = true
        return view
    }

    protected abstract fun onCreateHeaderView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    )

    protected abstract fun onCreateBodyView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (mCachedId > 0) {
            showItemId(mCachedId)
            mCachedId = 0L
        } else {
            if (savedInstanceState != null) {
                showItemId(savedInstanceState.getLong(SS_ITEM_ID, 0L))
            } else {
                showItemId(0L)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(SS_ITEM_ID, mCurrentId)
    }

    /**
     * Deletes a row through the content provider on one worker thread that every panel shares.
     * The toolbar menu ignores taps until the delete comes back, whichever item the panel shows
     * in the meantime, so the row cannot be edited, archived or deleted again and no second
     * delete can be queued from the panel. The result is dropped when the fragment has no view
     * left. A delete that went through is dropped when the panel has since been pointed at
     * another item, since the same fragment is reused for every item the list selects, and a
     * refused delete is reported whichever item the panel shows. With no error callback the
     * failure is rethrown on the main thread.
     */
    protected open fun deleteItemInBackground(uri: Uri, onError: Consumer<SQLiteDataException>?) {
        val context = context ?: return
        val resolver = context.applicationContext.contentResolver
        val itemId = getItemId()
        mDeleting = true
        sDeleteExecutor.execute {
            var failure: SQLiteDataException? = null
            try {
                resolver.delete(uri, null, null)
            } catch (e: SQLiteDataException) {
                failure = e
            }
            val error = failure
            sMainHandler.post {
                if (view == null) {
                    return@post
                }
                mDeleting = false
                if (error != null) {
                    if (onError != null) {
                        onError.accept(error)
                    } else {
                        throw error
                    }
                    return@post
                }
                if (getItemId() != itemId) {
                    return@post
                }
                navigateBackSafely()
                showItemId(0L)
            }
        }
    }

    open fun navigateBackSafely() {
        val parent = parentFragment
        if (parent is MultiPanelController) {
            parent.closeSecondaryPanel()
        }
    }

    open fun showItemId(itemId: Long) {
        if (mIsCreated) {
            mCurrentId = itemId
            val emptyLayout = mEmptyLayout
            val mainLayout = mMainLayout
            if (mCurrentId > 0L) {
                emptyLayout?.visibility = View.GONE
                mainLayout?.visibility = View.VISIBLE
                onShowItemId(mCurrentId)
            } else {
                emptyLayout?.visibility = View.VISIBLE
                mainLayout?.visibility = View.GONE
                // a panel with nothing to show must not stay full screen on the one panel layout
                navigateBackSafely()
            }
        } else {
            mCachedId = itemId
        }
    }

    protected abstract fun getTitle(): String?

    @MenuRes
    protected abstract fun onInflateMenu(): Int

    override fun onMenuItemClick(item: MenuItem): Boolean = false

    protected open fun setMenuItemVisibility(id: Int, visible: Boolean) {
        val menu = mToolbar?.menu
        menu?.findItem(id)?.isVisible = visible
    }

    protected open fun getToolbar(): Toolbar? = mToolbar

    open fun getItemId(): Long = mCurrentId

    protected abstract fun onShowItemId(itemId: Long)
}
