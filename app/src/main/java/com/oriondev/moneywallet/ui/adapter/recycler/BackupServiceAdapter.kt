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
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.BackupService
import com.oriondev.moneywallet.storage.database.backup.BackupManager

/**
 * Created by andre on 21/03/2018.
 */
class BackupServiceAdapter(
    private val mController: Controller?
) : RecyclerView.Adapter<BackupServiceAdapter.ViewHolder>() {

    private val mBackupServices: List<BackupService> = BackupManager.getBackupServices()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.adapter_backup_service_item, parent, false)
        return ViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val backupService = mBackupServices[position]
        holder.mAvatarImageView.setImageResource(backupService.iconRes)
        holder.mPrimaryTextView.setText(backupService.nameRes)
    }

    override fun getItemCount(): Int = mBackupServices.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        internal val mAvatarImageView: ImageView = itemView.findViewById(R.id.avatar_image_view)
        internal val mPrimaryTextView: TextView = itemView.findViewById(R.id.primary_text_view)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val controller = mController ?: return
            @Suppress("DEPRECATION")
            val position = adapterPosition
            if (position in mBackupServices.indices) {
                controller.onBackupServiceClick(mBackupServices[position])
            }
        }
    }

    interface Controller {

        fun onBackupServiceClick(service: BackupService)
    }
}
