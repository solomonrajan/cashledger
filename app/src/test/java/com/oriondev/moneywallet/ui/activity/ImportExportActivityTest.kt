package com.oriondev.moneywallet.ui.activity

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.service.ImportExportIntentService
import com.oriondev.moneywallet.storage.database.TestDatabases
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.util.ArrayList
import java.util.Collections

/**
 * Only the screen that started an import or export reacts to the service's broadcasts, so a
 * stopped screen underneath never commits a dialog after its state was saved (#311).
 */
@RunWith(RobolectricTestRunner::class)
class ImportExportActivityTest {

    companion object {
        private const val TAG_PROGRESS_DIALOG = "ImportExportActivity::tag::GenericProgressDialog"

        private fun mine(): ArrayList<String> {
            return ArrayList(Collections.singletonList("mine"))
        }

        /** The token on the next start of the import and export service, skipping any other service. */
        private fun nextServiceToken(application: Application): String {
            var intent: Intent?
            while (shadowOf(application).nextStartedService.also { intent = it } != null) {
                val current = intent ?: continue
                if (current.component != null && ImportExportIntentService::class.java.name == current.component?.className) {
                    return current.getStringExtra(ImportExportIntentService.TOKEN)
                        ?: throw AssertionError("token was null in service intent")
                }
            }
            throw AssertionError("the import and export service was not started")
        }
    }

    private lateinit var mContext: Context
    private val mControllers = ArrayList<ActivityController<ImportExportActivity>>()

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
    }

    /**
     * The broadcast manager is one instance for the whole run, so a screen left registered would
     * still receive the next test's broadcasts.
     */
    @After
    fun tearDown() {
        for (controller in mControllers) {
            if (!controller.get().isDestroyed) {
                controller.destroy()
            }
        }
    }

    @Test
    fun aStoppedScreenIgnoresTheStartOfWorkItDidNotStart() {
        val controller = build().setup()
        controller.pause().saveInstanceState(Bundle()).stop()
        val actions = arrayOf(LocalAction.ACTION_IMPORT_SERVICE_STARTED, LocalAction.ACTION_EXPORT_SERVICE_STARTED)
        for (action in actions) {
            send(Intent(action).putExtra(ImportExportIntentService.TOKEN, "someone else"))
            send(Intent(action))
        }
        assertNull(controller.get().supportFragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG))
    }

    @Test
    fun aRecreatedScreenShowsTheProgressOfItsOwnWork() {
        val state = Bundle()
        build().setup().saveInstanceState(state)
        state.putStringArrayList(ImportExportActivity.SS_TOKEN, mine())
        val controller = build().setup(state)
        send(Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, "mine"))
        assertNotNull(controller.get().supportFragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG))
    }

    /** A screen that holds a token of its own still ignores work somebody else started. */
    @Test
    fun aStoppedScreenWithWorkOfItsOwnIgnoresWorkItDidNotStart() {
        val controller = build().setup(restoredState())
        controller.pause().saveInstanceState(Bundle()).stop()
        send(Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, "someone else"))
        send(Intent(LocalAction.ACTION_EXPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, "someone else"))
        assertNull(controller.get().supportFragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG))
    }

    /** The screen saves its tokens itself, so they last through a second recreation too. */
    @Test
    fun theTokensLastThroughASecondRecreation() {
        val second = build().setup(restoredState())
        val saved = Bundle()
        second.pause().saveInstanceState(saved).stop().destroy()
        val third = build().setup(saved)
        send(Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, "mine"))
        assertNotNull(third.get().supportFragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG))
    }

    /**
     * Export starts with no dialog to confirm it, so two taps queue two jobs before the first one
     * starts. The screen keeps both tokens and still shows the progress of the first job.
     */
    @Test
    fun aSecondExportDoesNotHideTheFirstOne() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val controller = build(ImportExportActivity.MODE_EXPORT).setup()
        controller.get().exportData()
        controller.get().exportData()
        val first = nextServiceToken(application)
        val second = nextServiceToken(application)
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
        send(Intent(LocalAction.ACTION_EXPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, first))
        assertNotNull(controller.get().supportFragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG))

        val importer = build().setup()
        importer.get().importData()
        importer.get().importData()
        val token = nextServiceToken(application)
        assertNotNull(token)
        send(Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, token))
        assertNotNull(importer.get().supportFragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG))
    }

    /**
     * The crash in #311. Two import screens each started an import, and the lower one was stopped
     * after its state was saved, so only the upper one may react to the upper one's work.
     */
    @Test
    fun aStoppedImportScreenIgnoresTheImportOfTheScreenAboveIt() {
        val application: Application = ApplicationProvider.getApplicationContext()
        val lower = build().setup()
        lower.get().importData()
        assertNotNull(nextServiceToken(application))
        lower.pause().saveInstanceState(Bundle()).stop()
        val upper = build().setup()
        upper.get().importData()
        val token = nextServiceToken(application)
        assertNotNull(token)
        send(Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, token))
        assertNull(lower.get().supportFragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG))
        assertNotNull(upper.get().supportFragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG))
    }

    /** The state of a first screen, with a token it issued before it went away. */
    private fun restoredState(): Bundle {
        val state = Bundle()
        build().setup().saveInstanceState(state)
        state.putStringArrayList(ImportExportActivity.SS_TOKEN, mine())
        return state
    }

    private fun build(): ActivityController<ImportExportActivity> {
        return build(ImportExportActivity.MODE_IMPORT)
    }

    private fun build(mode: Int): ActivityController<ImportExportActivity> {
        val intent = Intent(mContext, ImportExportActivity::class.java)
            .putExtra(ImportExportActivity.MODE, mode)
        val controller = Robolectric.buildActivity(ImportExportActivity::class.java, intent)
        mControllers.add(controller)
        return controller
    }

    private fun send(intent: Intent) {
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
