package com.oriondev.moneywallet.ui.activity.base

import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

abstract class SinglePanelSimpleListActivity :
    SinglePanelActivity(),
    LoaderManager.LoaderCallbacks<Cursor>,
    SwipeRefreshLayout.OnRefreshListener {

    private var advancedRecyclerView: AdvancedRecyclerView? = null
    private var abstractCursorAdapter: AbstractCursorAdapter<*>? = null

    override fun onCreatePanelView(
        inflater: LayoutInflater,
        parent: ViewGroup,
        savedInstanceState: Bundle?
    ) {
        val view = inflater.inflate(R.layout.layout_activity_single_panel_body_list, parent, true)
        val recyclerView = view.findViewById<AdvancedRecyclerView>(R.id.advanced_recycler_view)
        advancedRecyclerView = recyclerView
        onPrepareRecyclerView(recyclerView)
        val adapter = onCreateAdapter()
        abstractCursorAdapter = adapter
        recyclerView.setAdapter(adapter)
        recyclerView.setOnRefreshListener(this)
        recyclerView.setState(AdvancedRecyclerView.State.LOADING)
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        super.onViewCreated(savedInstanceState)
        recreateLoader()
    }

    protected open fun recreateLoader() {
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, null, this)
    }

    protected abstract fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView)

    protected abstract fun onCreateAdapter(): AbstractCursorAdapter<*>

    protected open fun getAdvancedRecyclerView(): AdvancedRecyclerView? {
        return advancedRecyclerView
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        abstractCursorAdapter?.changeCursor(cursor)
        if (cursor != null && cursor.count > 0) {
            advancedRecyclerView?.setState(AdvancedRecyclerView.State.READY)
        } else {
            advancedRecyclerView?.setState(AdvancedRecyclerView.State.EMPTY)
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        abstractCursorAdapter?.changeCursor(null)
    }

    override fun onRefresh() {
        recreateLoader()
        advancedRecyclerView?.setState(AdvancedRecyclerView.State.REFRESHING)
    }

    companion object {
        private const val DEFAULT_LOADER_ID = 23
    }
}
