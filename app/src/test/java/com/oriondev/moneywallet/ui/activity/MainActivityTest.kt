package com.oriondev.moneywallet.ui.activity

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.WalletAccount
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.multipanel.CategoryMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.TransactionMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.singlepanel.OverviewSinglePanelFragment
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.utils.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.ArrayList

/**
 * Drives the main screen's drawer on the JVM against the real content provider: which section
 * opens, what back does, and which wallet the header and the preference end up on.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val VECTOR_ICON = "{\"type\":\"resource\",\"resource\":\"ic_icon_slippers\"}"
        private const val TOTAL_ITEM = MainActivity.ID_WALLET_FIRST + PreferenceManager.TOTAL_WALLET_ID.toInt()

        /**
         * Every drawer row the menu is currently showing, after a layout pass at a fixed width.
         * A row is a label and an action area; the header and the dividers have one child each.
         */
        private fun rows(drawer: NavigationView): List<ViewGroup> {
            drawer.measure(
                View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY)
            )
            drawer.layout(0, 0, drawer.measuredWidth, drawer.measuredHeight)
            val rows = ArrayList<ViewGroup>()
            val list = drawer.getChildAt(0) as ViewGroup
            for (i in 0 until list.childCount) {
                val child = list.getChildAt(i)
                if (child is ViewGroup && child.childCount == 2 && child.getChildAt(0) is CheckedTextView) {
                    rows.add(child)
                }
            }
            assertFalse("the drawer laid out no rows", rows.isEmpty())
            return rows
        }

        private fun label(row: ViewGroup): String {
            return (row.getChildAt(0) as CheckedTextView).text.toString()
        }

        private fun actionArea(row: ViewGroup): ViewGroup {
            return row.getChildAt(1) as ViewGroup
        }

        private fun walletItem(walletId: Long): Int {
            return MainActivity.ID_WALLET_FIRST + walletId.toInt()
        }

        private fun quickWallet(icon: View): WalletAccount {
            return icon.getTag(icon.id) as WalletAccount
        }

        private fun walletRows(menu: Menu): Int {
            var rows = 0
            for (i in 0 until menu.size()) {
                if (menu.getItem(i).itemId >= MainActivity.ID_WALLET_FIRST) {
                    rows++
                }
            }
            return rows
        }

        private fun findByTitle(menu: Menu, title: String): MenuItem? {
            for (i in 0 until menu.size()) {
                if (title.contentEquals(menu.getItem(i).title)) {
                    return menu.getItem(i)
                }
            }
            return null
        }

        private fun drawer(activity: MainActivity): NavigationView {
            return activity.findViewById(R.id.navigation_view)
        }

        private fun section(activity: MainActivity): Fragment? {
            return activity.supportFragmentManager.findFragmentById(R.id.fragment_container)
        }

        private fun headerName(activity: MainActivity): String {
            return (activity.findViewById<View>(R.id.wallet_name_text_view) as TextView).text.toString()
        }

        private fun runPending(activity: MainActivity) {
            activity.supportFragmentManager.executePendingTransactions()
        }

        /**
         * The wallets arrive through a cursor loader on a background thread and land on the main
         * looper, which the test drives by hand.
         */
        private fun awaitWallets(activity: MainActivity) {
            var i = 0
            while (i < 200 && headerName(activity).isEmpty()) {
                shadowOf(Looper.getMainLooper()).idle()
                try {
                    Thread.sleep(25)
                } catch (e: InterruptedException) {
                    throw AssertionError(e)
                }
                i++
            }
            assertFalse("the wallets never loaded", headerName(activity).isEmpty())
        }

        private fun awaitTitle(toolbar: Toolbar, title: String) {
            var i = 0
            while (i < 200 && title != toolbar.title?.toString()) {
                shadowOf(Looper.getMainLooper()).idle()
                sleep()
                i++
            }
            assertEquals(title, toolbar.title?.toString().orEmpty())
        }

        private fun sleep() {
            try {
                Thread.sleep(25)
            } catch (e: InterruptedException) {
                throw AssertionError(e)
            }
        }
    }

    private lateinit var mResolver: ContentResolver
    private var mFirstWallet: Long = 0L
    private var mSecondWallet: Long = 0L

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(context)
        mResolver = context.contentResolver
        PreferenceManager.setCurrentWallet(context, PreferenceManager.NO_CURRENT_WALLET)
        // named against their order, so a list sorted by name instead of by index shows
        mFirstWallet = insertWallet("Cash", 0, 0L, countInTotal = true, archived = false)
        mSecondWallet = insertWallet("Bank", 1, 0L, countInTotal = true, archived = false)
    }

    @Test
    fun theFirstLaunchOpensTransactionsOnTheFirstWallet() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                assertEquals(mFirstWallet, PreferenceManager.getCurrentWallet())
                assertEquals("Cash", headerName(activity))
                val checkedItem = drawer(activity).checkedItem
                assertNotNull(checkedItem)
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, checkedItem?.itemId)
                assertTrue(section(activity) is TransactionMultiPanelViewPagerFragment)
                val icon = activity.findViewById<ImageView>(R.id.wallet_icon_image_view)
                assertNotNull(icon.drawable)
                assertNotNull(drawer(activity).itemBackground)
                assertNull(drawer(activity).itemIconTintList)
                assertTrue(drawer(activity).clipToOutline)
                assertTrue(drawer(activity).background is MaterialShapeDrawable)
                val bg = drawer(activity).background as? MaterialShapeDrawable
                assertEquals(
                    ThemeEngine.theme?.drawerBackgroundColor,
                    bg?.fillColor?.defaultColor
                )
                assertFalse(ElevationOverlayProvider(activity).isThemeElevationOverlayEnabled)
            }
        }
    }

    @Test
    fun theMainScreenInflatesItsFloatingActionButton() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val fab = activity.findViewById<View>(R.id.floating_action_button)
                assertTrue(fab is FloatingActionButton)
            }
        }
    }

    @Test
    fun aLaunchOpensOnTheWalletThePreferenceNames() {
        PreferenceManager.setCurrentWallet(ApplicationProvider.getApplicationContext(), mSecondWallet)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                assertEquals("Bank", headerName(activity))
                assertEquals(mSecondWallet, PreferenceManager.getCurrentWallet())
            }
        }
    }

    @Test
    fun anEmptyLedgerShowsNoWalletAndOffersOnlyTheTwoActions() {
        for (id in longArrayOf(mFirstWallet, mSecondWallet)) {
            mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, id), null, null)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                assertEquals(activity.getString(R.string.msg_no_wallet_found), headerName(activity))
                assertEquals(PreferenceManager.NO_CURRENT_WALLET, PreferenceManager.getCurrentWallet())
                val menu = drawer(activity).menu
                assertNull(menu.findItem(TOTAL_ITEM))
                activity.findViewById<View>(R.id.navigation_drawer_header).performClick()
                val newWalletItem = menu.findItem(MainActivity.ID_ACTION_NEW_WALLET)
                assertNotNull(newWalletItem)
                assertTrue(newWalletItem?.isVisible == true)
                val manageWalletItem = menu.findItem(MainActivity.ID_ACTION_MANAGE_WALLET)
                assertNotNull(manageWalletItem)
                assertTrue(manageWalletItem?.isVisible == true)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.first_wallet_image_view).visibility)
            }
        }
    }

    @Test
    fun aReloadRebuildsTheWalletRowsInsteadOfStackingThem() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val menu = drawer(activity).menu
                assertEquals(3, walletRows(menu))
                // a restore from backup asks the drawer to load the wallets again
                val restored = Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
                    .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE)
                LocalBroadcastManager.getInstance(activity).sendBroadcast(restored)
                (activity.findViewById<View>(R.id.wallet_name_text_view) as TextView).text = ""
                awaitWallets(activity)
                assertEquals(3, walletRows(menu))
                val firstItem = menu.findItem(walletItem(mFirstWallet))
                assertNotNull(firstItem)
                assertFalse(firstItem?.isVisible == true)
                assertEquals("Cash", headerName(activity))
            }
        }
    }

    @Test
    fun aPreferenceNamingNoListedWalletShowsTheFirstAndIsLeftAlone() {
        PreferenceManager.setCurrentWallet(ApplicationProvider.getApplicationContext(), 999L)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                assertEquals("Cash", headerName(activity))
                assertEquals(999L, PreferenceManager.getCurrentWallet())
            }
        }
    }

    @Test
    fun aSectionTapLoadsItsFragmentAndBackReturnsToTransactions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val menu = drawer(activity).menu
                assertTrue(menu.performIdentifierAction(MainActivity.ID_SECTION_CATEGORIES, 0))
                runPending(activity)
                assertTrue(section(activity) is CategoryMultiPanelViewPagerFragment)
                activity.onBackPressed()
                runPending(activity)
                assertTrue(section(activity) is TransactionMultiPanelViewPagerFragment)
                val checked = drawer(activity).checkedItem
                assertNotNull(checked)
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, checked?.itemId)
                assertFalse(activity.isFinishing)
                activity.onBackPressed()
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun backClosesAnOpenDrawerAndLeavesTheSectionAlone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val menu = drawer(activity).menu
                val catItem = menu.findItem(MainActivity.ID_SECTION_CATEGORIES)
                assertNotNull(catItem)
                catItem?.let { activity.onNavigationItemSelected(it) }
                runPending(activity)
                val layout = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
                layout.openDrawer(GravityCompat.START, false)
                assertTrue(layout.isDrawerOpen(GravityCompat.START))
                activity.onBackPressed()
                runPending(activity)
                assertTrue(section(activity) is CategoryMultiPanelViewPagerFragment)
                val checked = drawer(activity).checkedItem
                assertNotNull(checked)
                assertEquals(MainActivity.ID_SECTION_CATEGORIES, checked?.itemId)
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun theSectionSurvivesARecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val item = drawer(activity).menu.findItem(MainActivity.ID_SECTION_CATEGORIES)
                assertNotNull(item)
                item?.let { activity.onNavigationItemSelected(it) }
                runPending(activity)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                runPending(activity)
                assertTrue(section(activity) is CategoryMultiPanelViewPagerFragment)
                val checked = drawer(activity).checkedItem
                assertNotNull(checked)
                assertEquals(MainActivity.ID_SECTION_CATEGORIES, checked?.itemId)
            }
        }
    }

    @Test
    fun pickingAWalletFromTheListMovesTheHeaderAndThePreference() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val menu = drawer(activity).menu
                activity.findViewById<View>(R.id.navigation_drawer_header).performClick()
                val txItem = menu.findItem(MainActivity.ID_SECTION_TRANSACTIONS)
                assertNotNull(txItem)
                assertFalse(txItem?.isVisible == true)
                val setItem = menu.findItem(MainActivity.ID_SECTION_SETTING)
                assertNotNull(setItem)
                assertFalse(setItem?.isVisible == true)
                val bank = menu.findItem(walletItem(mSecondWallet))
                assertNotNull(bank)
                assertEquals("Bank", bank?.title?.toString())
                assertTrue(bank?.isVisible == true)
                val checked1 = drawer(activity).checkedItem
                assertNotNull(checked1)
                assertEquals(walletItem(mFirstWallet), checked1?.itemId)
                bank?.let { assertFalse(activity.onNavigationItemSelected(it)) }
                assertEquals(mSecondWallet, PreferenceManager.getCurrentWallet())
                assertEquals("Bank", headerName(activity))
                assertTrue(menu.findItem(MainActivity.ID_SECTION_TRANSACTIONS)?.isVisible == true)
                assertTrue(menu.findItem(MainActivity.ID_SECTION_SETTING)?.isVisible == true)
                assertFalse(menu.findItem(walletItem(mSecondWallet))?.isVisible == true)
                val checked2 = drawer(activity).checkedItem
                assertNotNull(checked2)
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, checked2?.itemId)
                activity.findViewById<View>(R.id.navigation_drawer_header).performClick()
                val checked3 = drawer(activity).checkedItem
                assertNotNull(checked3)
                assertEquals(walletItem(mSecondWallet), checked3?.itemId)
            }
        }
    }

    @Test
    fun pickingAWalletNamesItInTheToolbarBeforeItsLoaderLands() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val secView = section(activity)?.requireView()
                assertNotNull(secView)
                val toolbar = secView?.findViewById<Toolbar>(R.id.primary_toolbar)
                assertNotNull(toolbar)
                toolbar?.let { awaitTitle(it, "Cash") }
                activity.findViewById<View>(R.id.navigation_drawer_header).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                val bankItem = drawer(activity).menu.findItem(walletItem(mSecondWallet))
                assertNotNull(bankItem)
                bankItem?.let { assertFalse(activity.onNavigationItemSelected(it)) }
                shadowOf(Looper.getMainLooper()).runOneTask()
                assertEquals("Bank", toolbar?.title?.toString().orEmpty())
                toolbar?.let { awaitTitle(it, "Bank") }
                for (i in 0 until 20) {
                    shadowOf(Looper.getMainLooper()).idle()
                    sleep()
                }
                assertEquals("Bank", toolbar?.title?.toString().orEmpty())
                val values = ContentValues()
                values.put(Contract.Wallet.NAME, "Savings")
                mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, mSecondWallet), values, null, null)
                toolbar?.let { awaitTitle(it, "Savings") }
            }
        }
    }

    @Test
    fun pickingAWalletNamesItInTheOverviewToolbarBeforeItsLoaderLands() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                assertTrue(drawer(activity).menu.performIdentifierAction(MainActivity.ID_SECTION_OVERVIEW, 0))
                runPending(activity)
                assertTrue(section(activity) is OverviewSinglePanelFragment)
                val secView = section(activity)?.requireView()
                assertNotNull(secView)
                val toolbar = secView?.findViewById<Toolbar>(R.id.primary_toolbar)
                assertNotNull(toolbar)
                toolbar?.let { awaitTitle(it, "Cash") }
                activity.findViewById<View>(R.id.navigation_drawer_header).performClick()
                shadowOf(Looper.getMainLooper()).idle()
                val bankItem = drawer(activity).menu.findItem(walletItem(mSecondWallet))
                assertNotNull(bankItem)
                bankItem?.let { assertFalse(activity.onNavigationItemSelected(it)) }
                shadowOf(Looper.getMainLooper()).runOneTask()
                assertEquals("Bank", toolbar?.title?.toString().orEmpty())
                toolbar?.let { awaitTitle(it, "Bank") }
                for (i in 0 until 20) {
                    shadowOf(Looper.getMainLooper()).idle()
                    sleep()
                }
                assertEquals("Bank", toolbar?.title?.toString().orEmpty())
                val values = ContentValues()
                values.put(Contract.Wallet.NAME, "Savings")
                mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, mSecondWallet), values, null, null)
                toolbar?.let { awaitTitle(it, "Savings") }
            }
        }
    }

    @Test
    fun aRowNamingAWalletThatIsGoneIsIgnored() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val menu = drawer(activity).menu
                activity.findViewById<View>(R.id.navigation_drawer_header).performClick()
                val stale = menu.add(4, MainActivity.ID_WALLET_FIRST + 999, Menu.NONE, "Gone")
                assertFalse(activity.onNavigationItemSelected(stale))
                assertEquals(mFirstWallet, PreferenceManager.getCurrentWallet())
                assertEquals("Cash", headerName(activity))
                assertTrue(menu.findItem(MainActivity.ID_SECTION_TRANSACTIONS)?.isVisible == true)
            }
        }
    }

    @Test
    fun theTotalEntryComesAfterTheWalletsAndIsItsOwnCurrentWallet() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val total = drawer(activity).menu.findItem(TOTAL_ITEM)
                assertNotNull(total)
                assertEquals(activity.getString(R.string.total_wallet_name), total?.title?.toString())
                total?.let { activity.onNavigationItemSelected(it) }
                assertEquals(PreferenceManager.TOTAL_WALLET_ID, PreferenceManager.getCurrentWallet())
                assertEquals(activity.getString(R.string.total_wallet_name), headerName(activity))
            }
        }
    }

    @Test
    fun anArchivedWalletStaysOutOfTheListAndInTheTotalAndAnExcludedOneStaysOut() {
        insertWallet("Old", 2, 50000L, countInTotal = true, archived = true)
        insertWallet("Side", 3, 70000L, countInTotal = false, archived = false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val menu = drawer(activity).menu
                activity.findViewById<View>(R.id.navigation_drawer_header).performClick()
                assertNull(findByTitle(menu, "Old"))
                val side = findByTitle(menu, "Side")
                assertNotNull(side)
                assertTrue(side?.isVisible == true)
                val expected = MoneyFormatter.getInstance().getNotTintedString(Money("EUR", 50000L))
                val totalItem = menu.findItem(TOTAL_ITEM)
                assertNotNull(totalItem)
                val actionView = totalItem?.actionView as? TextView
                assertNotNull(actionView)
                assertEquals(expected, actionView?.text?.toString())
                totalItem?.let { activity.onNavigationItemSelected(it) }
                val moneyView = activity.findViewById<TextView>(R.id.wallet_money_text_view)
                assertEquals(expected, moneyView.text.toString())
            }
        }
    }

    @Test
    fun theQuickSwitchIconsHoldTheNextTwoWalletsAndSwitchToThem() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val first = activity.findViewById<View>(R.id.first_wallet_image_view)
                val second = activity.findViewById<View>(R.id.second_wallet_image_view)
                assertEquals(mSecondWallet, quickWallet(first).id)
                assertEquals(PreferenceManager.TOTAL_WALLET_ID, quickWallet(second).id)
                assertEquals(View.VISIBLE, second.visibility)
                assertNotNull((first as ImageView).drawable)
                assertEquals("Bank", first.contentDescription.toString())
                first.performClick()
                assertEquals(mSecondWallet, PreferenceManager.getCurrentWallet())
                assertEquals("Bank", headerName(activity))
                assertEquals(mFirstWallet, quickWallet(first).id)
                assertEquals("Cash", first.contentDescription.toString())
                assertEquals(PreferenceManager.TOTAL_WALLET_ID, quickWallet(second).id)
                second.performClick()
                assertEquals(PreferenceManager.TOTAL_WALLET_ID, PreferenceManager.getCurrentWallet())
                assertEquals(mFirstWallet, quickWallet(first).id)
                assertEquals(mSecondWallet, quickWallet(second).id)
            }
        }
    }

    @Test
    fun aQuickSwitchWalletWithAPickerIconLoadsWithoutACrash() {
        val values = ContentValues()
        values.put(Contract.Wallet.ICON, VECTOR_ICON)
        mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, mSecondWallet), values, null, null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val first = activity.findViewById<ImageView>(R.id.first_wallet_image_view)
                assertEquals(mSecondWallet, quickWallet(first).id)
                assertEquals(View.VISIBLE, first.visibility)
                var i = 0
                while (i < 80 && first.drawable == null) {
                    shadowOf(Looper.getMainLooper()).idle()
                    try {
                        Thread.sleep(25)
                    } catch (e: InterruptedException) {
                        throw AssertionError(e)
                    }
                    i++
                }
                assertNotNull(first.drawable)
            }
        }
    }

    @Test
    fun noWalletCountingInTheTotalMeansNoTotalEntry() {
        val values = ContentValues()
        values.put(Contract.Wallet.COUNT_IN_TOTAL, false)
        for (id in longArrayOf(mFirstWallet, mSecondWallet)) {
            mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, id), values, null, null)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                assertNull(drawer(activity).menu.findItem(TOTAL_ITEM))
                assertEquals(mSecondWallet, quickWallet(activity.findViewById(R.id.first_wallet_image_view)).id)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.second_wallet_image_view).visibility)
            }
        }
    }

    @Test
    fun newWalletOpensTheEditorAndPutsTheSectionsBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val menu = drawer(activity).menu
                activity.findViewById<View>(R.id.navigation_drawer_header).performClick()
                val newWalletItem = menu.findItem(MainActivity.ID_ACTION_NEW_WALLET)
                assertNotNull(newWalletItem)
                newWalletItem?.let { assertFalse(activity.onNavigationItemSelected(it)) }
                val next: Intent? = shadowOf(activity).nextStartedActivity
                assertNotNull(next)
                assertEquals(NewEditWalletActivity::class.java.name, next?.component?.className)
                assertTrue(menu.findItem(MainActivity.ID_SECTION_TRANSACTIONS)?.isVisible == true)
                val checked = drawer(activity).checkedItem
                assertNotNull(checked)
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, checked?.itemId)
            }
        }
    }

    @Test
    fun goingBackToTheSectionsLeavesNoBalanceOnThemAndNoRoomForOne() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                awaitWallets(activity)
                val header = activity.findViewById<View>(R.id.navigation_drawer_header)
                header.performClick()
                shadowOf(Looper.getMainLooper()).idle()
                val wallets = rows(drawer(activity))
                assertEquals("Cash", label(wallets[0]))
                assertTrue(actionArea(wallets[0]).getChildAt(0) is TextView)
                header.performClick()
                shadowOf(Looper.getMainLooper()).idle()
                val sections = rows(drawer(activity))
                assertEquals("Transactions", label(sections[0]))
                for (row in sections) {
                    val area = actionArea(row)
                    assertFalse(label(row) + " kept a wallet balance", area.getChildAt(0) is TextView)
                    assertEquals(label(row) + " left the label no room", 0, area.width)
                }
            }
        }
    }

    private fun insertWallet(name: String, index: Int, startMoney: Long, countInTotal: Boolean, archived: Boolean): Long {
        val values = ContentValues()
        values.put(Contract.Wallet.NAME, name)
        values.put(Contract.Wallet.ICON, ICON)
        values.put(Contract.Wallet.CURRENCY, "EUR")
        values.put(Contract.Wallet.START_MONEY, startMoney)
        values.put(Contract.Wallet.COUNT_IN_TOTAL, countInTotal)
        values.put(Contract.Wallet.ARCHIVED, archived)
        values.put(Contract.Wallet.INDEX, index)
        val uri = mResolver.insert(DataContentProvider.CONTENT_WALLETS, values)
        return if (uri != null) ContentUris.parseId(uri) else -1L
    }
}
