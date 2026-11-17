package com.oriondev.moneywallet.ui.activity

import android.app.ActivityManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAppTask
import java.util.Collections

/**
 * The launcher hands off to the main screen unless its own task already has the main screen at
 * the base, which must then be left as it was.
 */
@RunWith(RobolectricTestRunner::class)
class LauncherActivityTest {

    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        PreferenceManager.setIsFirstStartDone(false)
    }

    @Test
    fun aLaunchOnTopOfTheMainScreenStartsNoSecondOne() {
        insertWallet()
        val activity = launch(false, MainActivity::class.java, true)
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun aLaunchOnTopOfTheTransactionEditorOpensTheMainScreen() {
        insertWallet()
        val activity = launch(false, NewEditTransactionActivity::class.java, true)
        assertMainScreenStarted(activity)
    }

    @Test
    fun aLaunchThatStartsTheTaskOpensTheMainScreen() {
        insertWallet()
        val activity = launch(true, null, false)
        assertMainScreenStarted(activity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun aLaunchInsideAnotherAppsTaskOpensTheMainScreen() {
        insertWallet()
        val activity = launch(false, MainActivity::class.java, false)
        assertMainScreenStarted(activity)
    }

    @Test
    fun theMainScreenIsLeftAsItWasBeforeTheWelcomeScreenIsConsidered() {
        val activity = launch(false, MainActivity::class.java, true)
        assertTrue(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    companion object {
        private fun assertMainScreenStarted(activity: LauncherActivity) {
            val next: Intent? = shadowOf(activity).nextStartedActivity
            assertEquals(MainActivity::class.java.name, next?.component?.className)
        }
    }

    private fun insertWallet() {
        val values = ContentValues()
        values.put(Contract.Wallet.NAME, "Cash")
        values.put(Contract.Wallet.ICON, "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"C\"}")
        values.put(Contract.Wallet.CURRENCY, "EUR")
        values.put(Contract.Wallet.START_MONEY, 0L)
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true)
        values.put(Contract.Wallet.ARCHIVED, false)
        values.put(Contract.Wallet.INDEX, 0)
        mContext.contentResolver.insert(DataContentProvider.CONTENT_WALLETS, values)
    }

    /**
     * @param base the activity at the base of the app task listed for this activity
     * @param ownTask whether that app task has this activity's task id, or belongs to another task
     */
    private fun launch(taskRoot: Boolean, base: Class<*>?, ownTask: Boolean): LauncherActivity {
        val intent = Intent(mContext, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val controller: ActivityController<LauncherActivity> = Robolectric.buildActivity(LauncherActivity::class.java, intent)
        val activity = controller.get()
        shadowOf(activity).setIsTaskRoot(taskRoot)
        val info = ActivityManager.RecentTaskInfo()
        info.persistentId = if (ownTask) activity.taskId else activity.taskId + 1
        info.baseActivity = if (base != null) ComponentName(mContext, base) else null
        val task = ShadowAppTask.newInstance()
        shadowOf(task).setTaskInfo(info)
        val manager = mContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(manager).setAppTasks(Collections.singletonList(task))
        return controller.create().get()
    }
}
