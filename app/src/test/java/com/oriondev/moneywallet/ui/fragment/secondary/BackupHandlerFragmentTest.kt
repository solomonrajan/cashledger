package com.oriondev.moneywallet.ui.fragment.secondary

import android.Manifest
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every test drives the backup screen inside a resumed activity, which is where a backend used
 * to register its launcher and where AndroidX refuses a registration.
 */
@RunWith(RobolectricTestRunner::class)
class BackupHandlerFragmentTest {

    @Before
    fun setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    @Config(sdk = [32])
    fun theExternalMemoryCoverButtonAsksForStoragePermission() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                clickTheCoverButton(activity, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY)
                val request = shadowOf(activity).lastRequestedPermission
                assertNotNull(request)
                assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, request?.requestedPermissions?.get(0))
            }
        }
    }

    @Test
    @Config(sdk = [33, 35])
    fun theExternalMemoryScreenOpensFromAndroid13WithoutTheStoragePermission() {
        assertCoverVisibility(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, View.GONE, View.VISIBLE)
    }

    @Test
    @Config(sdk = [32])
    fun theExternalMemoryScreenStaysCoveredWithoutTheStoragePermissionBelowAndroid13() {
        assertCoverVisibility(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, View.VISIBLE, View.GONE)
    }

    @Test
    fun theLocalFolderCoverButtonOpensTheFolderPicker() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                clickTheCoverButton(activity, BackendServiceFactory.SERVICE_ID_SAF)
                val picker = shadowOf(activity).nextStartedActivity
                assertNotNull(picker)
                assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, picker?.action)
            }
        }
    }

    private fun clickTheCoverButton(activity: FragmentActivity, backendId: String) {
        val fragment = showBackupHandler(activity, backendId)
        val coverActionButton = fragment.requireView().findViewById<View>(R.id.cover_action_button)
        assertNotNull(coverActionButton)
        coverActionButton?.performClick()
    }

    private fun showBackupHandler(activity: FragmentActivity, backendId: String): Fragment {
        val fragment = BackupHandlerFragment.newInstance(backendId, true, true)
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment, "BackupHandlerFragmentTest")
            .commitNow()
        return fragment
    }

    private fun assertCoverVisibility(backendId: String, expectedCover: Int, expectedPrimary: Int) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showBackupHandler(activity, backendId)
                val cover = fragment.requireView().findViewById<View>(R.id.cover_layout)
                val primary = fragment.requireView().findViewById<View>(R.id.primary_layout)
                assertEquals(expectedCover, cover.visibility)
                assertEquals(expectedPrimary, primary.visibility)
            }
        }
    }
}
