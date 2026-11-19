package com.oriondev.moneywallet.ui.fragment.secondary

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SplitLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

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

        private fun ledgerDate(): String {
            return DateFormatter.getFormattedDate(DateUtils.getDateFromSQLDateTimeString(SplitLedger.DATE))
        }
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

    /** Bonus is used by the one part of PAY_SPLIT and by nothing else. */
    @Test
    fun aDeleteRefusedForAPartNamesTheTransaction() {
        SplitLedger.build(mContext)
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showCategoryFragment(activity, SplitLedger.BONUS)
                assertEquals(
                    mContext.getString(
                        R.string.message_error_delete_category_in_split,
                        SplitLedger.description(SplitLedger.PAY_SPLIT),
                        ledgerDate()
                    ),
                    deleteAndReadRefusal(fragment)
                )
            }
        }
        assertEquals(1L, count("categories", "category_id = " + SplitLedger.BONUS))
    }

    /**
     * Tools holds a part of RECEIPT and one of TWICE, both on the same date, so the higher id
     * comes first. TWICE has no description and is named by its amount, 7000.
     */
    @Test
    fun aDeleteRefusedForSeveralNamesTheNewestAndCountsTheOthers() {
        SplitLedger.build(mContext)
        val db = SplitLedger.database(mContext)
        db.execSQL(
            "INSERT INTO categories (category_id, category_name, category_icon, category_type, " +
                "category_show_report, uuid, last_edit, deleted) VALUES (970, 'Tools', ?, ?, 1, " +
                "'category-tools', 1, 0)",
            arrayOf(ICON, Contract.CategoryType.EXPENSE.value)
        )
        for (transaction in longArrayOf(SplitLedger.RECEIPT, SplitLedger.TWICE)) {
            db.execSQL(
                "INSERT INTO transaction_splits (_transaction, split_category, split_money, uuid, " +
                    "last_edit, deleted) VALUES (?, 970, 100, ?, 1, 0)",
                arrayOf<Any>(transaction, "tools-$transaction")
            )
        }
        db.execSQL("UPDATE transactions SET transaction_description = NULL WHERE transaction_id = " + SplitLedger.TWICE)
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = showCategoryFragment(activity, 970L)
                val currency = CurrencyManager.getCurrency("EUR") ?: error("currency EUR is null")
                val amount = MoneyFormatter.getInstance().getNotTintedString(currency, 7000L)
                assertEquals(
                    mContext.getString(R.string.message_error_delete_category_in_split, amount, ledgerDate()) +
                        " " + mContext.resources.getQuantityString(
                        R.plurals.message_error_delete_category_in_more_splits,
                        1,
                        1
                    ),
                    deleteAndReadRefusal(fragment)
                )
            }
        }
        assertEquals(1L, count("categories", "category_id = 970"))
    }

    private fun deleteAndReadRefusal(fragment: CategoryItemFragment): String {
        val view = fragment.view ?: error("fragment view is null")
        val toolbar = view.findViewById<Toolbar>(R.id.secondary_toolbar)
        val deleteItem = toolbar.menu.findItem(R.id.action_delete_item) ?: error("no delete item")
        fragment.onMenuItemClick(deleteItem)
        val confirm = ShadowDialog.getLatestDialog() as? AlertDialog ?: error("no confirm dialog")
        confirm.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val refusal = ShadowDialog.getLatestDialog() as? AlertDialog ?: error("no refusal dialog")
        assertNotSame("no refusal was shown", confirm, refusal)
        val message = refusal.findViewById<TextView>(android.R.id.message) ?: error("no message view")
        return message.text.toString()
    }

    private fun count(table: String, selection: String): Long {
        val cursor = SplitLedger.database(mContext).rawQuery(
            "SELECT COUNT(*) FROM $table WHERE $selection",
            null
        )
        cursor.use { c ->
            assertTrue(c.moveToFirst())
            return c.getLong(0)
        }
    }

    private fun showCategoryFragment(activity: FragmentActivity, categoryId: Long): CategoryItemFragment {
        showCategory(activity, categoryId)
        return activity.supportFragmentManager.findFragmentByTag(TAG_FRAGMENT) as CategoryItemFragment
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
