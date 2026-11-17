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

package com.oriondev.moneywallet.api.disk

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * From Android 13 the platform denies WRITE_EXTERNAL_STORAGE to an app targeting 33 or higher,
 * so the backend has to report itself enabled without it.
 */
@RunWith(RobolectricTestRunner::class)
class DiskBackendServiceTest {

    private val service = DiskBackendService(null)

    @Test
    @Config(sdk = [33, 36])
    fun theBackendIsEnabledFromAndroid13WithoutTheStoragePermission() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertTrue(service.isServiceEnabled(application))
    }

    @Test
    @Config(sdk = [32])
    fun theBackendStillNeedsTheStoragePermissionBelowAndroid13() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertFalse(service.isServiceEnabled(application))
        shadowOf(application).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertTrue(service.isServiceEnabled(application))
    }
}
