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

package com.oriondev.moneywallet.storage.database

import android.content.ContentResolver
import android.database.ContentObservable
import android.database.ContentObserver
import android.database.Cursor
import android.database.CursorWrapper
import android.net.Uri
import java.lang.ref.WeakReference
import java.util.LinkedHashSet

/**
 * This class works as wrapper on top of [Cursor].
 * This allow to register multiple notification uri instead of a single one.
 *
 * Nothing registers more than one on it now. What it is still here for is the replay in
 * registerContentObserver, which hands a newly registered observer the changes this cursor was
 * already told about. AbstractCursor drops those, and a loader registers after the query it is
 * loading has returned, so that window is a real one.
 *
 * Credits to: https://gist.github.com/chalup/4201307da02b9cfe4f40
 */
open class MultiUriCursorWrapper(cursor: Cursor?) : CursorWrapper(cursor) {

    @JvmField
    protected var mClosed: Boolean = false

    @JvmField
    protected var mContentResolver: ContentResolver? = null

    private val mNotifyUris = LinkedHashSet<Uri>()

    private val mSelfObserverLock = Any()
    private var mSelfObserver: ContentObserver? = null
    private var mSelfObserverRegistered = false

    private val mChangedByUris = LinkedHashSet<Uri?>()

    private val mContentObservable = ContentObservable()

    override fun deactivate() {
        onDeactivateOrClose()
        super.deactivate()
    }

    protected open fun onDeactivateOrClose() {
        mSelfObserver?.let { observer ->
            mContentResolver?.unregisterContentObserver(observer)
            mSelfObserverRegistered = false
        }
    }

    override fun requery(): Boolean {
        val observer = mSelfObserver
        val resolver = mContentResolver
        if (observer != null && !mSelfObserverRegistered && resolver != null) {
            for (notifyUri in mNotifyUris) {
                resolver.registerContentObserver(notifyUri, true, observer)
            }
            mSelfObserverRegistered = true
        }

        val success = super.requery()
        if (success) {
            mChangedByUris.clear()
        }
        return success
    }

    override fun isClosed(): Boolean {
        return super.isClosed() && mClosed
    }

    override fun close() {
        super.close()
        mClosed = true
        mContentObservable.unregisterAll()
        onDeactivateOrClose()
    }

    /**
     * Under the same lock onChange takes, because this walks the set that method adds to and it
     * adds from whichever binder thread the notification arrived on. Without the lock a loader
     * registering here can walk the set while it is being written, and a change that lands
     * between onChange's dispatch and its add reaches neither the dispatch nor this replay.
     */
    override fun registerContentObserver(observer: ContentObserver) {
        synchronized(mSelfObserverLock) {
            mContentObservable.registerObserver(observer)
            for (changedByUri in mChangedByUris) {
                observer.dispatchChange(false, changedByUri)
            }
        }
    }

    override fun unregisterContentObserver(observer: ContentObserver) {
        // cursor will unregister all observers when it close
        if (!mClosed) {
            mContentObservable.unregisterObserver(observer)
        }
    }

    private fun onChange(selfChange: Boolean, uri: Uri?) {
        synchronized(mSelfObserverLock) {
            mContentObservable.dispatchChange(selfChange, uri)

            if (!selfChange) {
                mChangedByUris.add(uri)
            }
        }
    }

    override fun setNotificationUri(contentResolver: ContentResolver, notifyUri: Uri) {
        synchronized(mSelfObserverLock) {
            mNotifyUris.add(notifyUri)
            mContentResolver = contentResolver
            val observer = mSelfObserver ?: SelfContentObserver(this).also { mSelfObserver = it }
            contentResolver.registerContentObserver(notifyUri, true, observer)
            mSelfObserverRegistered = true
        }
    }

    override fun getNotificationUri(): Uri? {
        synchronized(mSelfObserverLock) {
            return firstNotificationUri
        }
    }

    private val firstNotificationUri: Uri?
        get() = mNotifyUris.firstOrNull()

    @Suppress("Deprecation")
    protected fun finalize() {
        try {
            val observer = mSelfObserver
            if (observer != null && mSelfObserverRegistered) {
                mContentResolver?.unregisterContentObserver(observer)
            }

            if (!mClosed) close()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // ignored
        }
    }

    private class SelfContentObserver(cursor: MultiUriCursorWrapper) : ContentObserver(null) {

        private val mCursor = WeakReference(cursor)

        override fun deliverSelfNotifications(): Boolean {
            return false
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            val cursor = mCursor.get()
            cursor?.onChange(false, uri)
        }

        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }
    }
}
