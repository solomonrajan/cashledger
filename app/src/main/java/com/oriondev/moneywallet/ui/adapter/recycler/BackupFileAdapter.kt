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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.utils.Utils
import java.util.Collections

/**
 * Created by andre on 21/03/2018.
 */
class BackupFileAdapter(
    private val mController: Controller?
) : RecyclerView.Adapter<BackupFileAdapter.ViewHolder>(), Comparator<IFile> {

    private val mFileList: MutableList<IFile> = ArrayList()
    private var mCanNavigateBack: Boolean = false

    fun setFileList(fileList: List<IFile>?, canNavigateBack: Boolean) {
        mFileList.clear()
        if (fileList != null) {
            mFileList.addAll(fileList)
            Collections.sort(mFileList, this)
        }
        mCanNavigateBack = canNavigateBack
        notifyDataSetChanged()
    }

    fun addFileToList(file: IFile) {
        mFileList.add(file)
        Collections.sort(mFileList, this)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_backup_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (mCanNavigateBack && position == 0) {
            holder.mAvatarImageView.visibility = View.INVISIBLE
            holder.mPrimaryTextView.text = " ... "
            holder.mSecondaryTextView.setText(R.string.hint_navigate_up)
        } else {
            holder.mAvatarImageView.visibility = View.VISIBLE
            val file = mFileList[position - if (mCanNavigateBack) 1 else 0]
            holder.mPrimaryTextView.text = file.getName()
            if (file.isDirectory()) {
                holder.mAvatarImageView.setImageResource(R.drawable.ic_folder_open_black_24dp)
                holder.mSecondaryTextView.setText(R.string.hint_directory)
            } else {
                val context = holder.mAvatarImageView.context
                val extension = file.getExtension()
                Glide.with(context)
                    .load(Utils.getFileIcon(extension))
                    .into(holder.mAvatarImageView)
                val size = file.getSize()
                if (size >= 0L) {
                    holder.mSecondaryTextView.text = Utils.readableFileSize(size)
                } else {
                    holder.mSecondaryTextView.setText(R.string.hint_size_unknown)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return mFileList.size + if (mCanNavigateBack) 1 else 0
    }

    override fun compare(file1: IFile, file2: IFile): Int {
        val name1 = file1.getName().orEmpty()
        val name2 = file2.getName().orEmpty()
        return if (file1.isDirectory()) {
            if (file2.isDirectory()) name1.compareTo(name2) else -1
        } else {
            if (file2.isDirectory()) {
                1
            } else {
                // Reverse alphabetical sort so that more recent backup files appear at the top
                name2.compareTo(name1)
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        internal val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)
        internal val mSecondaryTextView: TextView = itemView.findViewById(R.id.secondary_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            @Suppress("DEPRECATION")
            val position = adapterPosition
            if (position == RecyclerView.NO_POSITION) {
                return
            }
            if (mCanNavigateBack) {
                if (position == 0) {
                    controller.navigateBack()
                } else if (position - 1 in mFileList.indices) {
                    controller.onFileClick(mFileList[position - 1])
                }
            } else if (position in mFileList.indices) {
                controller.onFileClick(mFileList[position])
            }
        }
    }

    interface Controller {

        fun navigateBack()

        fun onFileClick(file: IFile)
    }
}
