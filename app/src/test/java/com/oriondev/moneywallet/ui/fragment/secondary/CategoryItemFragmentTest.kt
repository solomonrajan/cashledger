package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The reports keep a transaction only when its category and its parent are both included, so a
 * subcategory under a hidden parent reaches no report however its own box reads. This screen used
 * to read that box alone and say the category was in the reports when none of its money was.
 */
@RunWith(RobolectricTestRunner::class)
class CategoryItemFragmentTest {

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val TAG_FRAGMENT = "CategoryItemFragmentTest::Fragment"
    }

    private lateinit var mContext: Context
    private lateinit var mResolver: ContentResolver

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        mResolver = mContext.contentResolver
    }

    @Test
    fun aTopLevelCategoryInTheReportsSaysSo() {
        val groceries = insertCategory("Groceries", null, true)
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val box = showCategory(activity, groceries)
                assertTrue(box.isChecked)
                assertEquals(
                    mContext.getString(R.string.hint_show_category_report_on),
                    box.text.toString()
                )
            }
        }
    }

    @Test
    fun aTopLevelCategoryKeptOutSaysSo() {
        val groceries = insertCategory("Groceries", null, false)
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val box = showCategory(activity, groceries)
                assertFalse(box.isChecked)
                assertEquals(
                    mContext.getString(R.string.hint_show_category_report_off),
                    box.text.toString()
                )
            }
        }
    }

    @Test
    fun aSubcategoryUnderAnIncludedParentSaysItIsIn() {
        val groceries = insertCategory("Groceries", null, true)
        val snacks = insertCategory("Snacks", groceries, true)
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val box = showCategory(activity, snacks)
                assertTrue(box.isChecked)
                assertEquals(
                    mContext.getString(R.string.hint_show_category_report_on),
                    box.text.toString()
                )
            }
        }
    }

    @Test
    fun aSubcategoryUnderAHiddenParentNamesTheParent() {
        val groceries = insertCategory("Groceries", null, false)
        val snacks = insertCategory("Snacks", groceries, true)
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val box = showCategory(activity, snacks)
                assertTrue(box.isChecked)
                assertEquals(
                    mContext.getString(R.string.hint_show_category_report_off_parent, "Groceries"),
                    box.text.toString()
                )
                assertTrue(box.text.toString().contains("Groceries"))
            }
        }
    }

    @Test
    fun aSubcategoryKeptOutOnItsOwnDoesNotNameTheParent() {
        val groceries = insertCategory("Groceries", null, false)
        val snacks = insertCategory("Snacks", groceries, false)
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val box = showCategory(activity, snacks)
                assertFalse(box.isChecked)
                assertEquals(
                    mContext.getString(R.string.hint_show_category_report_off),
                    box.text.toString()
                )
            }
        }
    }

    private fun showCategory(activity: FragmentActivity, categoryId: Long): CheckBox {
        val fragment = CategoryItemFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(android.R.id.content, fragment, TAG_FRAGMENT)
            .commitNow()
        fragment.showItemId(categoryId)
        val view = fragment.view ?: throw AssertionError("fragment view is null")
        val wheel = view.findViewById<View>(R.id.secondary_panel_progress_wheel)
        var i = 0
        while (i < 200 && wheel.visibility != View.GONE) {
            shadowOf(Looper.getMainLooper()).idle()
            try {
                Thread.sleep(25)
            } catch (e: InterruptedException) {
                throw AssertionError(e)
            }
            i++
        }
        assertEquals("the category never loaded", View.GONE, wheel.visibility)
        return view.findViewById(R.id.show_report_check_box)
    }

    private fun insertCategory(name: String, parent: Long?, showReport: Boolean): Long {
        val values = ContentValues()
        values.put(Contract.Category.NAME, name)
        values.put(Contract.Category.ICON, ICON)
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
        values.put(Contract.Category.SHOW_REPORT, showReport)
        if (parent != null) {
            values.put(Contract.Category.PARENT, parent)
        }
        val uri = mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }
}
