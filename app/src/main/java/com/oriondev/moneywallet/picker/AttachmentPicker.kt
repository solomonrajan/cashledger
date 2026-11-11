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

package com.oriondev.moneywallet.picker

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.service.AttachmentHandlerIntentService
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.util.Queue

/**
 * Created by andrea on 27/03/18.
 */
class AttachmentPicker : Fragment() {

    private var mEditMode = false
    private var mController: Controller? = null

    private val mOldAttachments: ArrayList<Attachment> = ArrayList()
    private val mNewAttachments: ArrayList<Attachment> = ArrayList()
    private val mDeletedAttachments: ArrayList<Attachment> = ArrayList()

    private val mQueuedUris: Queue<Uri> = ArrayDeque()

    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val type = intent.getIntExtra(AttachmentHandlerIntentService.ACTION, 0)
            if (type == AttachmentHandlerIntentService.ACTION_CREATE) {
                @Suppress("DEPRECATION")
                val attachment: Attachment? =
                    intent.getParcelableExtra(AttachmentHandlerIntentService.ATTACHMENT)
                when (action) {
                    LocalAction.ACTION_ATTACHMENT_OP_STARTED ->
                        updateAttachmentStatus(attachment, Attachment.Status.PENDING, null)
                    LocalAction.ACTION_ATTACHMENT_OP_FINISHED ->
                        updateAttachmentStatus(attachment, Attachment.Status.READY, null)
                    LocalAction.ACTION_ATTACHMENT_OP_FAILED -> {
                        val error = intent.getStringExtra(AttachmentHandlerIntentService.ERROR)
                        updateAttachmentStatus(attachment, Attachment.Status.PENDING, error)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mOldAttachments.clear()
        mNewAttachments.clear()
        mDeletedAttachments.clear()
        if (savedInstanceState != null) {
            mEditMode = savedInstanceState.getBoolean(SS_EDIT_MODE)
            mOldAttachments.loadFromBundle(savedInstanceState, SS_OLD_ATTACHMENT_LIST)
            mNewAttachments.loadFromBundle(savedInstanceState, SS_NEW_ATTACHMENT_LIST)
            mDeletedAttachments.loadFromBundle(savedInstanceState, SS_DELETED_ATTACHMENT_LIST)
        } else {
            val arguments = arguments
            mEditMode = arguments != null && arguments.getBoolean(ARG_EDIT_MODE)
            if (mEditMode && arguments != null) {
                mOldAttachments.loadFromBundle(arguments, ARG_OLD_ATTACHMENT_LIST)
            }
        }
    }

    private fun MutableList<Attachment>.loadFromBundle(bundle: Bundle, key: String) {
        @Suppress("DEPRECATION")
        val items: ArrayList<Attachment>? = bundle.getParcelableArrayList(key)
        if (items != null) {
            addAll(items)
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (mQueuedUris.isNotEmpty()) {
            // Drain any URIs queued before the activity became available.
            while (mQueuedUris.isNotEmpty()) {
                val queuedUri = mQueuedUris.poll()
                if (queuedUri != null) {
                    onFileSelected(queuedUri)
                }
            }
        } else {
            fireCallbackSafely()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mController = findController(Controller::class.java, context)
        val intentFilter = IntentFilter()
        intentFilter.addAction(LocalAction.ACTION_ATTACHMENT_OP_STARTED)
        intentFilter.addAction(LocalAction.ACTION_ATTACHMENT_OP_FINISHED)
        intentFilter.addAction(LocalAction.ACTION_ATTACHMENT_OP_FAILED)
        LocalBroadcastManager.getInstance(context).registerReceiver(mBroadcastReceiver, intentFilter)
    }

    fun areAllAttachmentsReady(): Boolean {
        return mNewAttachments.none { it.status == Attachment.Status.PENDING }
    }

    fun remove(attachment: Attachment) {
        if (mEditMode && mOldAttachments.contains(attachment)) {
            // Flag existing attachments as deleted so canceling the edit keeps them intact.
            mOldAttachments.remove(attachment)
            mDeletedAttachments.add(attachment)
        } else {
            // Permanently delete newly added attachments.
            mNewAttachments.remove(attachment)
            deleteAttachmentAsync(attachment)
        }
        fireCallbackSafely()
    }

    fun showPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = ALL_FILES
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CODE_FILE_PICKER)
    }

    fun cleanUp(rollback: Boolean) {
        val targets = if (rollback) mNewAttachments else mDeletedAttachments
        for (attachment in targets) {
            deleteAttachmentAsync(attachment)
        }
    }

    val currentAttachments: List<Attachment>
        get() {
            val attachments = ArrayList<Attachment>(mOldAttachments.size + mNewAttachments.size)
            attachments.addAll(mOldAttachments)
            attachments.addAll(mNewAttachments)
            return attachments
        }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_FILE_PICKER) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val clipData = data.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        if (uri != null) {
                            onFileSelected(uri)
                        }
                    }
                    return
                }
                val uri = data.data
                if (uri != null) {
                    onFileSelected(uri)
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun addFileFromUri(uri: Uri) {
        mQueuedUris.add(uri)
    }

    private fun onFileSelected(uri: Uri) {
        val activity = activity ?: return
        var attachment: Attachment? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val contentResolver = activity.contentResolver
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && sizeIndex >= 0) {
                        attachment = Attachment(
                            0L,
                            Attachment.generateFileUID(),
                            cursor.getString(nameIndex) ?: "",
                            contentResolver.getType(uri) ?: DEFAULT_MIME_TYPE,
                            cursor.getLong(sizeIndex),
                            Attachment.Status.PENDING
                        )
                    }
                }
            }
        } else {
            // Some pickers return a file:// URI directly, which cannot be queried via ContentResolver.
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            val status = activity.checkCallingOrSelfPermission(permission)
            val path = uri.path
            if (status == PackageManager.PERMISSION_GRANTED && path != null) {
                val file = File(path)
                if (file.exists()) {
                    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                    var contentType = DEFAULT_MIME_TYPE
                    if (!extension.isNullOrEmpty()) {
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
                        if (mimeType != null) {
                            contentType = mimeType
                        }
                    }
                    attachment = Attachment(
                        0L,
                        Attachment.generateFileUID(),
                        file.name,
                        contentType,
                        file.length(),
                        Attachment.Status.PENDING
                    )
                }
            }
        }
        val createdAttachment = attachment
        if (createdAttachment != null) {
            mNewAttachments.add(createdAttachment)
            fireCallbackSafely()
            createAttachmentAsync(uri, createdAttachment)
        }
    }

    private fun createAttachmentAsync(uri: Uri, attachment: Attachment) {
        val activity = activity ?: return
        val intent = Intent(activity, AttachmentHandlerIntentService::class.java)
        intent.data = uri
        intent.putExtra(AttachmentHandlerIntentService.ATTACHMENT, attachment)
        intent.putExtra(AttachmentHandlerIntentService.ACTION, AttachmentHandlerIntentService.ACTION_CREATE)
        activity.startService(intent)
    }

    private fun deleteAttachmentAsync(attachment: Attachment) {
        val activity = activity ?: return
        val intent = Intent(activity, AttachmentHandlerIntentService::class.java)
        intent.putExtra(AttachmentHandlerIntentService.ATTACHMENT, attachment)
        intent.putExtra(AttachmentHandlerIntentService.ACTION, AttachmentHandlerIntentService.ACTION_DELETE)
        activity.startService(intent)
    }

    private fun fireCallbackSafely() {
        mController?.onAttachmentListChanged(currentAttachments)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_EDIT_MODE, mEditMode)
        outState.putParcelableArrayList(SS_OLD_ATTACHMENT_LIST, mOldAttachments)
        outState.putParcelableArrayList(SS_NEW_ATTACHMENT_LIST, mNewAttachments)
        outState.putParcelableArrayList(SS_DELETED_ATTACHMENT_LIST, mDeletedAttachments)
    }

    override fun onDetach() {
        super.onDetach()
        val activity = activity
        if (activity != null) {
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(mBroadcastReceiver)
        }
        mController = null
    }

    private fun updateAttachmentStatus(
        attachment: Attachment?,
        status: Attachment.Status,
        error: String?
    ) {
        if (attachment == null) {
            return
        }
        for (attach in mNewAttachments) {
            if (attach == attachment) {
                attach.id = attachment.id
                attach.setStatus(status, error)
                fireCallbackSafely()
                break
            }
        }
    }

    interface Controller {
        fun onAttachmentListChanged(attachments: List<Attachment>)
    }

    companion object {

        private const val SS_EDIT_MODE = "AttachmentPicker::SavedState::EditMode"
        private const val SS_OLD_ATTACHMENT_LIST = "AttachmentPicker::SavedState::OldAttachmentList"
        private const val SS_NEW_ATTACHMENT_LIST = "AttachmentPicker::SavedState::NewAttachmentList"
        private const val SS_DELETED_ATTACHMENT_LIST = "AttachmentPicker::SavedState::DeletedAttachmentList"

        private const val ARG_EDIT_MODE = "AttachmentPicker::Arguments::EditMode"
        private const val ARG_OLD_ATTACHMENT_LIST = "AttachmentPicker::Arguments::OldAttachmentList"

        private const val ALL_FILES = "*/*"
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
        private const val REQUEST_CODE_FILE_PICKER = 36347

        @JvmStatic
        fun createPicker(
            fragmentManager: FragmentManager,
            tag: String?,
            attachments: ArrayList<Attachment>?
        ): AttachmentPicker {
            return fragmentManager.findOrCreatePicker(tag) {
                val arguments = Bundle()
                if (attachments != null) {
                    arguments.putBoolean(ARG_EDIT_MODE, true)
                    arguments.putParcelableArrayList(ARG_OLD_ATTACHMENT_LIST, attachments)
                } else {
                    arguments.putBoolean(ARG_EDIT_MODE, false)
                }
                val picker = AttachmentPicker()
                picker.arguments = arguments
                picker
            }
        }
    }
}
