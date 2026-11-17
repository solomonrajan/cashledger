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

package com.oriondev.moneywallet.api

import android.content.Context
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.disk.DiskBackendService
import com.oriondev.moneywallet.api.disk.DiskBackendServiceAPI
import com.oriondev.moneywallet.api.saf.SAFBackendService
import com.oriondev.moneywallet.api.saf.SAFBackendServiceAPI
import com.oriondev.moneywallet.api.webdav.WebDAVBackendService
import com.oriondev.moneywallet.api.webdav.WebDAVBackendServiceAPI
import com.oriondev.moneywallet.model.BackupService
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.model.LocalFile
import com.oriondev.moneywallet.model.SAFFile
import com.oriondev.moneywallet.model.WebDAVFile
import java.util.ArrayList

/**
 * Created by andrea on 21/11/18.
 */
object BackendServiceFactory {

    const val SERVICE_ID_EXTERNAL_MEMORY: String = "external_memory"
    const val SERVICE_ID_SAF: String = "storage_access_framework"
    const val SERVICE_ID_WEBDAV: String = "webdav"

    private val DESCRIPTORS: List<BackendDescriptor> = buildDescriptors()

    private fun buildDescriptors(): List<BackendDescriptor> {
        val descriptors = ArrayList<BackendDescriptor>()
        descriptors.add(object : BackendDescriptor(SERVICE_ID_EXTERNAL_MEMORY, R.drawable.ic_sd_24dp) {
            override fun getDefaultFolder(): IFile? {
                return DiskBackendServiceAPI.getDefaultFolder()
            }

            override fun createDelegate(listener: AbstractBackendServiceDelegate.BackendServiceStatusListener?): AbstractBackendServiceDelegate {
                return DiskBackendService(listener)
            }

            @Throws(BackendException::class)
            override fun createServiceApi(context: Context): IBackendServiceAPI {
                return DiskBackendServiceAPI()
            }

            override fun createFile(encoded: String?): IFile {
                return LocalFile(encoded)
            }
        })
        descriptors.add(object : BackendDescriptor(SERVICE_ID_SAF, R.drawable.ic_storage_black_24dp) {
            override fun createDelegate(listener: AbstractBackendServiceDelegate.BackendServiceStatusListener?): AbstractBackendServiceDelegate {
                return SAFBackendService(listener)
            }

            @Throws(BackendException::class)
            override fun createServiceApi(context: Context): IBackendServiceAPI {
                return SAFBackendServiceAPI(context)
            }

            override fun createFile(encoded: String?): IFile? {
                return SAFFile.decode(encoded)
            }
        })
        descriptors.add(object : BackendDescriptor(SERVICE_ID_WEBDAV, R.drawable.ic_webdav_24dp) {
            override fun createDelegate(listener: AbstractBackendServiceDelegate.BackendServiceStatusListener?): AbstractBackendServiceDelegate {
                return WebDAVBackendService(listener)
            }

            @Throws(BackendException::class)
            override fun createServiceApi(context: Context): IBackendServiceAPI {
                return WebDAVBackendServiceAPI(context)
            }

            override fun createFile(encoded: String?): IFile? {
                return WebDAVFile.decode(encoded)
            }
        })
        return descriptors
    }

    private fun findDescriptor(backendId: String?): BackendDescriptor? {
        if (backendId == null) return null
        for (descriptor in DESCRIPTORS) {
            if (descriptor.id == backendId) {
                return descriptor
            }
        }
        return null
    }

    @JvmStatic
    fun getServiceById(
        backendId: String?,
        listener: AbstractBackendServiceDelegate.BackendServiceStatusListener?
    ): AbstractBackendServiceDelegate? {
        val descriptor = findDescriptor(backendId)
        return descriptor?.createDelegate(listener)
    }

    @JvmStatic
    @Throws(BackendException::class)
    fun getServiceAPIById(context: Context, backendId: String?): IBackendServiceAPI {
        val descriptor = findDescriptor(backendId) ?: throw BackendException("Invalid backend")
        return descriptor.createServiceApi(context)
    }

    @JvmStatic
    fun getBackupServices(): List<BackupService> {
        val services = ArrayList<BackupService>()
        for (descriptor in DESCRIPTORS) {
            if (descriptor.isAvailable()) {
                services.add(BackupService(descriptor.id, descriptor.iconRes, descriptor.getNameRes()))
            }
        }
        return services
    }

    @JvmStatic
    fun getFile(backendId: String?, encoded: String?): IFile? {
        val descriptor = findDescriptor(backendId) ?: return null
        return if (encoded != null) descriptor.createFile(encoded) else descriptor.getDefaultFolder()
    }
}
