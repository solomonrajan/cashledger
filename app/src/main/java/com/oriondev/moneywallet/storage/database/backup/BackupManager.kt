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

package com.oriondev.moneywallet.storage.database.backup

import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.model.BackupService

/**
 * Created by andre on 21/03/2018.
 */
object BackupManager {

    const val BACKUP_EXTENSION_LEGACY: String = ".mwb"
    const val BACKUP_EXTENSION_STANDARD: String = ".mwbx"
    const val BACKUP_EXTENSION_PROTECTED: String = ".mwbs"

    object FileStructure {
        const val ENCODING: String = "UTF-8"
        const val FILE_DATABASE: String = "database.json"
        const val FOLDER_DATABASES: String = "databases/"
        const val FOLDER_ATTACHMENTS: String = "attachments/"
    }

    @JvmStatic
    fun getBackupServices(): List<BackupService> {
        return BackendServiceFactory.getBackupServices()
    }

    @JvmStatic
    fun getExtension(encrypted: Boolean): String {
        return if (encrypted) BACKUP_EXTENSION_PROTECTED else BACKUP_EXTENSION_STANDARD
    }
}
