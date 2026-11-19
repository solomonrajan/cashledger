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

package com.oriondev.moneywallet.ui.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Money
import com.oriondev.moneywallet.model.WalletAccount
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.base.BaseActivity
import com.oriondev.moneywallet.ui.fragment.base.NavigableFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.BudgetMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.CategoryMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.DebtMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.EventMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.ModelMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.PersonMultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.PlaceMultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.RecurrenceMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.SavingMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.SettingMultiPanelFragment
import com.oriondev.moneywallet.ui.fragment.multipanel.TransactionMultiPanelViewPagerFragment
import com.oriondev.moneywallet.ui.fragment.singlepanel.OverviewSinglePanelFragment
import com.oriondev.moneywallet.ui.view.theme.ITheme
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.ui.view.theme.ThemedRecyclerView
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.MoneyFormatter
import com.oriondev.moneywallet.utils.SystemBars
import java.util.ArrayList
import java.util.Locale

class MainActivity : BaseActivity(),
    DrawerController,
    NavigationView.OnNavigationItemSelectedListener,
    LoaderManager.LoaderCallbacks<Cursor> {

    private val mMoneyFormatter: MoneyFormatter = MoneyFormatter.getInstance()
    private val mWallets: MutableList<WalletAccount> = ArrayList()

    private var mDrawerLayout: DrawerLayout? = null
    private var mNavigationView: NavigationView? = null
    private var mDrawerToggle: ActionBarDrawerToggle? = null
    private var mHeaderView: View? = null

    private var mWalletIconView: ImageView? = null
    private var mFirstWalletView: ImageView? = null
    private var mSecondWalletView: ImageView? = null
    private var mWalletNameView: TextView? = null
    private var mWalletMoneyView: TextView? = null
    private var mWalletListArrowView: ImageView? = null

    private var mCurrentWallet: WalletAccount? = null
    private var mWalletListShown = false

    private var mCurrentSelection = ID_SECTION_TRANSACTIONS
    private var mCurrentFragment: Fragment? = null

    private var mCursor: Cursor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeUi()
        loadUi(savedInstanceState)
        registerReceiver()
    }

    /**
     * Initialize all the ui elements of the activity.
     */
    private fun initializeUi() {
        setContentView(R.layout.activity_main)
        initializeNavigationDrawer()
    }

    /**
     * This method must be called during the initialization of the activity in order to
     * setup the account header and the navigation drawer.
     */
    private fun initializeNavigationDrawer() {
        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        mDrawerLayout = drawerLayout
        // Here and not beside the toggle in setToolbar, which every section calls, so a listener
        // added there would be added again on each one and never taken off.
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                onThemeSystemBarIcons(ThemeEngine.getTheme())
            }

            override fun onDrawerClosed(drawerView: View) {
                onThemeSystemBarIcons(ThemeEngine.getTheme())
            }
        })
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        mNavigationView = navigationView
        SystemBars.padDrawerStart(drawerLayout, navigationView)
        navigationView.setNavigationItemSelectedListener(this)
        // the section icons are tinted one by one below, so the wallet icons keep their colors
        navigationView.itemIconTintList = null
        val menu = navigationView.menu
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_TRANSACTIONS, R.drawable.ic_shopping_cart_24dp, R.string.menu_transaction)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_CATEGORIES, R.drawable.ic_table_large_24dp, R.string.menu_category)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_OVERVIEW, R.drawable.ic_equalizer_24dp, R.string.menu_overview)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_DEBTS, R.drawable.ic_debt_24dp, R.string.menu_debt)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_BUDGETS, R.drawable.ic_budget_24dp, R.string.menu_budget)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_SAVINGS, R.drawable.ic_saving_24dp, R.string.menu_saving)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_EVENTS, R.drawable.ic_assistant_photo_24dp, R.string.menu_event)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_RECURRENCES, R.drawable.ic_restore_24dp, R.string.menu_recurrences)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_MODELS, R.drawable.ic_bookmark_black_24dp, R.string.menu_models)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_PLACES, R.drawable.ic_place_24dp, R.string.menu_place)
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_PEOPLE, R.drawable.ic_people_black_24dp, R.string.menu_people)
        menu.setGroupCheckable(GROUP_SECTIONS, true, false)
        addEntry(menu, GROUP_SETTINGS, ID_SECTION_SETTING, R.drawable.ic_settings_24dp, R.string.menu_setting).isCheckable = true
        val headerView = navigationView.getHeaderView(0)
        mHeaderView = headerView
        // Top only, to match the menu below it. The navigation view pads its own list from the
        // top and bottom and leaves the sides to its inset scrims, so a header that also took
        // the sides would sit inset from the rows under it.
        SystemBars.pad(headerView, true, false, false)
        headerView.setOnClickListener { showWalletList(!mWalletListShown) }
        mWalletIconView = headerView.findViewById(R.id.wallet_icon_image_view)
        mFirstWalletView = headerView.findViewById(R.id.first_wallet_image_view)
        mSecondWalletView = headerView.findViewById(R.id.second_wallet_image_view)
        mWalletNameView = headerView.findViewById(R.id.wallet_name_text_view)
        mWalletMoneyView = headerView.findViewById(R.id.wallet_money_text_view)
        mWalletListArrowView = headerView.findViewById(R.id.wallet_list_arrow_image_view)
        val quickSwitch = View.OnClickListener { view ->
            val wallet = view.getTag(view.id) as? WalletAccount
            if (wallet != null) {
                switchWallet(wallet)
            }
        }
        mFirstWalletView?.setOnClickListener(quickSwitch)
        mSecondWalletView?.setOnClickListener(quickSwitch)
    }

    /**
     * Add one entry to the navigation drawer, with its icon tinted from the current theme.
     * @param menu the drawer menu.
     * @param group of the entry, the drawer draws a divider where the group changes.
     * @param identifier integer id of the entry.
     * @param icon drawable resource of the icon of the entry.
     * @param name string resource of the name of the entry.
     * @return the created entry.
     */
    private fun addEntry(menu: Menu, group: Int, identifier: Int, @DrawableRes icon: Int, @StringRes name: Int): MenuItem {
        val item = menu.add(group, identifier, Menu.NONE, name).setIcon(icon).setActionView(Space(this))
        tintEntry(item, ThemeEngine.getTheme())
        return item
    }

    private fun tintEntry(item: MenuItem, theme: ITheme) {
        val iconDrawable = item.icon ?: return
        val icon = DrawableCompat.wrap(iconDrawable).mutate()
        DrawableCompat.setTintList(icon, checkedStates(theme.drawerSelectedIconColor, theme.drawerIconColor))
        item.icon = icon
    }

    private fun loadUi(savedInstanceState: Bundle?) {
        var selection = ID_SECTION_TRANSACTIONS
        if (savedInstanceState != null) {
            selection = savedInstanceState.getInt(SAVED_SELECTION, ID_SECTION_TRANSACTIONS)
        }
        selectSection(selection)
        supportLoaderManager.restartLoader(LOADER_WALLETS, null, this)
    }

    private fun registerReceiver() {
        val filter = IntentFilter(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter)
    }

    /**
     * Store all the instance information in order to restore them if the activity is recreated.
     * The only information to store here is the current section loaded. The fragment will manage
     * the lifecycle internally, no need to save his state here.
     * @param savedState of the current instance of the activity.
     */
    override fun onSaveInstanceState(savedState: Bundle) {
        super.onSaveInstanceState(savedState)
        savedState.putInt(SAVED_SELECTION, mCurrentSelection)
    }

    /**
     * Override this method to check when the back button is pressed if the drawer is open.
     * If open it will be closed.
     */
    override fun onBackPressed() {
        if (mDrawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            closeDrawer()
        } else {
            val fragment = mCurrentFragment
            if (fragment is NavigableFragment) {
                if (!fragment.navigateBack() && !selectTransactionsSection()) {
                    super.onBackPressed()
                }
            } else if (!selectTransactionsSection()) {
                super.onBackPressed()
            }
        }
    }

    /**
     * Move back to the transactions section the way a drawer tap does, so the highlighted
     * drawer entry and the fragment on screen cannot drift apart.
     * @return true when the section change has been queued, false when transactions is
     * already showing, in which case the caller should fall back to the default behavior.
     * The fragment transaction is committed, not run, so the previous section is still on
     * screen when this returns.
     */
    private fun selectTransactionsSection(): Boolean {
        if (mCurrentSelection == ID_SECTION_TRANSACTIONS) {
            return false
        }
        selectSection(ID_SECTION_TRANSACTIONS)
        return true
    }

    /**
     * Highlight a section in the drawer and load its fragment.
     * @param identifier of the section.
     */
    private fun selectSection(identifier: Int) {
        mCurrentSelection = identifier
        mNavigationView?.setCheckedItem(identifier)
        loadSection(identifier)
    }

    private fun closeDrawer() {
        mDrawerLayout?.closeDrawer(GravityCompat.START)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver)
    }

    /**
     * Set toolbar for this activity. Every section brings its own toolbar, so the toggle that
     * draws the drawer icon on it and opens the drawer from it is rebuilt on each one.
     * @param toolbar to set as main toolbar.
     */
    override fun setToolbar(toolbar: Toolbar) {
        val drawerLayout = mDrawerLayout ?: return
        val currentToggle = mDrawerToggle
        if (currentToggle != null) {
            drawerLayout.removeDrawerListener(currentToggle)
        }
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.description_navigation_drawer_open, R.string.description_navigation_drawer_close
        )
        mDrawerToggle = toggle
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }

    /**
     * Set the lock mode for the activity drawer.
     * @param lockMode to set to the navigation drawer.
     */
    override fun setDrawerLockMode(lockMode: Int) {
        mDrawerLayout?.setDrawerLockMode(lockMode)
    }

    /**
     * Callback when a drawer entry is tapped, a section, a tool, or one of the wallet list
     * entries the header shows in their place.
     * @param item tapped.
     * @return true if the entry should be highlighted, which is only right for a section.
     */
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val identifier = item.itemId
        if (item.groupId == GROUP_WALLETS) {
            if (identifier == ID_ACTION_NEW_WALLET) {
                startActivity(Intent(this, NewEditWalletActivity::class.java))
            } else if (identifier == ID_ACTION_MANAGE_WALLET) {
                startActivity(Intent(this, WalletListActivity::class.java))
            } else {
                val wallet = findWallet((identifier - ID_WALLET_FIRST).toLong())
                if (wallet != null) {
                    switchWallet(wallet)
                    return false
                }
            }
            showWalletList(false)
            closeDrawer()
            return false
        }
        selectSection(identifier)
        closeDrawer()
        return true
    }

    /**
     * Make a wallet the current one: the header shows it, the preference that every screen
     * filters by is written, and the drawer goes back to the sections and closes.
     * @param wallet to switch to.
     */
    private fun switchWallet(wallet: WalletAccount) {
        mCurrentWallet = wallet
        PreferenceManager.setCurrentWallet(this, wallet.id, wallet.name)
        bindHeader()
        buildWalletMenu()
        showWalletList(false)
        closeDrawer()
    }

    /**
     * Swap the sections for the wallet list, or back. The highlighted entry follows: the
     * current wallet while the list is shown, the current section otherwise.
     * @param show true for the wallet list.
     */
    private fun showWalletList(show: Boolean) {
        mWalletListShown = show
        val navigationView = mNavigationView ?: return
        val menu = navigationView.menu
        menu.setGroupVisible(GROUP_SECTIONS, !show)
        menu.setGroupVisible(GROUP_SETTINGS, !show)
        menu.setGroupVisible(GROUP_WALLETS, show)
        if (show) {
            val wallet = mCurrentWallet
            if (wallet != null) {
                navigationView.setCheckedItem(walletItemId(wallet))
            }
        } else {
            navigationView.setCheckedItem(mCurrentSelection)
        }
        mWalletListArrowView?.animate()?.rotation(if (show) 180f else 0f)?.start()
    }

    /**
     * Load the fragment of the specified section inside the frame of the activity.
     * If the fragment is already in the stack of the fragment manager this method will
     * reuse it without spending time in recreating a new one.
     * @param identifier of the section.
     */
    private fun loadSection(identifier: Int) {
        // The outgoing screen may have left the icons set for a scrolled panel, and nothing
        // else runs on this path.
        resetStatusBarIconsToAppBar()
        val manager: FragmentManager = supportFragmentManager
        val tag = getTagById(identifier)
        var fragment = manager.findFragmentByTag(tag)
        if (fragment == null) {
            fragment = buildFragmentById(identifier)
        }
        mCurrentFragment = fragment
        manager.beginTransaction().replace(R.id.fragment_container, fragment, tag).commit()
    }

    /**
     * Generate a unique string as tag to identify every fragment into the fragment manager.
     * @param identifier of the drawer item.
     * @return a unique tag.
     */
    private fun getTagById(identifier: Int): String {
        return String.format(Locale.ENGLISH, "MainActivity::drawer::%d", identifier)
    }

    /**
     * This method creates a new fragment of the specified section.
     * @param identifier of the section.
     * @return the new created fragment.
     * @throws IllegalArgumentException if the provided id is not a
     * valid section identifier.
     */
    private fun buildFragmentById(identifier: Int): Fragment {
        return when (identifier) {
            ID_SECTION_TRANSACTIONS -> TransactionMultiPanelViewPagerFragment()
            ID_SECTION_CATEGORIES -> CategoryMultiPanelViewPagerFragment()
            ID_SECTION_OVERVIEW -> OverviewSinglePanelFragment()
            ID_SECTION_DEBTS -> DebtMultiPanelViewPagerFragment()
            ID_SECTION_BUDGETS -> BudgetMultiPanelViewPagerFragment()
            ID_SECTION_SAVINGS -> SavingMultiPanelViewPagerFragment()
            ID_SECTION_EVENTS -> EventMultiPanelViewPagerFragment()
            ID_SECTION_RECURRENCES -> RecurrenceMultiPanelViewPagerFragment()
            ID_SECTION_MODELS -> ModelMultiPanelViewPagerFragment()
            ID_SECTION_PLACES -> PlaceMultiPanelFragment()
            ID_SECTION_PEOPLE -> PersonMultiPanelFragment()
            ID_SECTION_SETTING -> SettingMultiPanelFragment()
            else -> throw IllegalArgumentException("Invalid section id: $identifier")
        }
    }

    /**
     * Query content resolver to retrieve all wallets from the database.
     * @param id of the loader.
     * @param args bundle of arguments.
     * @return the cursor loader that will retrieve the content from the database.
     */
    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val projection = arrayOf(
            Contract.Wallet.ID,
            Contract.Wallet.NAME,
            Contract.Wallet.ICON,
            Contract.Wallet.COUNT_IN_TOTAL,
            Contract.Wallet.CURRENCY,
            Contract.Wallet.START_MONEY,
            Contract.Wallet.TOTAL_MONEY,
            Contract.Wallet.ARCHIVED
        )
        val uri: Uri = DataContentProvider.CONTENT_WALLETS
        val sortOrder = Contract.Wallet.INDEX + " ASC, " + Contract.Wallet.NAME + " ASC"
        return CursorLoader(this, uri, projection, null, null, sortOrder)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        mCursor = cursor
        mWallets.clear()
        val total = Money()
        if (cursor != null) {
            val indexWalletId = cursor.getColumnIndexOrThrow(Contract.Wallet.ID)
            val indexWalletName = cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)
            val indexWalletIcon = cursor.getColumnIndexOrThrow(Contract.Wallet.ICON)
            val indexCurrency = cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY)
            val indexWalletInitial = cursor.getColumnIndexOrThrow(Contract.Wallet.START_MONEY)
            val indexWalletTotal = cursor.getColumnIndexOrThrow(Contract.Wallet.TOTAL_MONEY)
            val indexWalletArchived = cursor.getColumnIndexOrThrow(Contract.Wallet.ARCHIVED)
            val indexInTotal = cursor.getColumnIndexOrThrow(Contract.Wallet.COUNT_IN_TOTAL)
            for (i in 0 until cursor.count) {
                cursor.moveToPosition(i)
                val currency = cursor.getString(indexCurrency)
                val money = cursor.getLong(indexWalletInitial) + cursor.getLong(indexWalletTotal)
                if (cursor.getInt(indexWalletArchived) == 0) {
                    mWallets.add(
                        WalletAccount(
                            cursor.getLong(indexWalletId),
                            cursor.getString(indexWalletName),
                            IconLoader.parse(cursor.getString(indexWalletIcon)),
                            Money(currency, money)
                        )
                    )
                }
                // an archived wallet stays out of the list but still counts toward the total
                if (cursor.getInt(indexInTotal) == 1) {
                    total.addMoney(currency, money)
                }
            }
        }
        if (mWallets.isNotEmpty() && total.numberOfCurrencies > 0) {
            val name = getString(R.string.total_wallet_name)
            val initial = if (name.isNotEmpty()) name.substring(0, 1) else ""
            mWallets.add(
                WalletAccount(
                    PreferenceManager.TOTAL_WALLET_ID,
                    name,
                    ColorIcon("#000000", initial),
                    total
                )
            )
        }
        val currentWalletId = PreferenceManager.getCurrentWallet()
        mCurrentWallet = findWallet(currentWalletId)
        if (mCurrentWallet == null && mWallets.isNotEmpty()) {
            // the header falls back to the first wallet. Only when no wallet has ever been chosen
            // is that choice written as the current one, which also broadcasts the change
            mCurrentWallet = mWallets[0]
            val wallet = mCurrentWallet
            if (currentWalletId == PreferenceManager.NO_CURRENT_WALLET && wallet != null) {
                PreferenceManager.setCurrentWallet(this, wallet.id, wallet.name)
            }
        }
        buildWalletMenu()
        bindHeader()
    }

    private fun findWallet(id: Long): WalletAccount? {
        for (wallet in mWallets) {
            if (wallet.id == id) {
                return wallet
            }
        }
        return null
    }

    /**
     * Rebuild the wallet group of the drawer menu from the wallet list: one entry per wallet
     * with its balance at the end of the row, then the two wallet actions.
     */
    private fun buildWalletMenu() {
        val navigationView = mNavigationView ?: return
        val theme = ThemeEngine.getTheme()
        val menu = navigationView.menu
        menu.removeGroup(GROUP_WALLETS)
        for (wallet in mWallets) {
            val moneyView = TextView(this)
            moneyView.text = mMoneyFormatter.getNotTintedString(wallet.money)
            moneyView.setTextColor(if (wallet == mCurrentWallet) theme.drawerSelectedTextColor else theme.drawerTextColor)
            moneyView.gravity = Gravity.CENTER_VERTICAL
            menu.add(GROUP_WALLETS, walletItemId(wallet), Menu.NONE, wallet.name)
                .setIcon(wallet.icon.getDrawable(this))
                .setActionView(moneyView)
                .isCheckable = true
        }
        addEntry(menu, GROUP_WALLETS, ID_ACTION_NEW_WALLET, R.drawable.ic_add_24dp, R.string.action_new_wallet)
        addEntry(menu, GROUP_WALLETS, ID_ACTION_MANAGE_WALLET, R.drawable.ic_settings_24dp, R.string.action_manage_wallets)
        menu.setGroupVisible(GROUP_WALLETS, mWalletListShown)
        val currentWallet = mCurrentWallet
        if (mWalletListShown && currentWallet != null) {
            navigationView.setCheckedItem(walletItemId(currentWallet))
        }
    }

    /**
     * Show the current wallet in the header, and the first two other wallets as the one tap
     * switches at its top end.
     */
    private fun bindHeader() {
        val currentWallet = mCurrentWallet
        val walletIconView = mWalletIconView
        val walletNameView = mWalletNameView
        val walletMoneyView = mWalletMoneyView
        if (currentWallet != null) {
            if (walletIconView != null) {
                IconLoader.loadInto(currentWallet.icon, walletIconView)
                walletIconView.visibility = View.VISIBLE
            }
            walletNameView?.text = currentWallet.name
            walletMoneyView?.text = mMoneyFormatter.getNotTintedString(currentWallet.money)
        } else {
            walletIconView?.visibility = View.INVISIBLE
            walletNameView?.setText(R.string.msg_no_wallet_found)
            walletMoneyView?.setText(R.string.msg_add_one_wallet)
        }
        val others: MutableList<WalletAccount> = ArrayList(mWallets)
        if (currentWallet != null) {
            others.remove(currentWallet)
        }
        mFirstWalletView?.let { bindQuickSwitch(it, if (others.isNotEmpty()) others[0] else null) }
        mSecondWalletView?.let { bindQuickSwitch(it, if (others.size > 1) others[1] else null) }
    }

    private fun bindQuickSwitch(view: ImageView, wallet: WalletAccount?) {
        // keyed, because Glide keeps its own request in the plain tag of any view it loads into
        view.setTag(view.id, wallet)
        view.visibility = if (wallet != null) View.VISIBLE else View.GONE
        if (wallet != null) {
            IconLoader.loadInto(wallet.icon, view)
            view.contentDescription = wallet.name
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        val cursor = mCursor
        if (cursor != null) {
            if (!cursor.isClosed) {
                cursor.close()
            }
            mCursor = null
        }
    }

    override fun onThemeSetup(theme: ITheme) {
        super.onThemeSetup(theme)
        applyNavigationDrawerTheme(theme)
    }

    private fun applyNavigationDrawerTheme(theme: ITheme) {
        applyNavigationDrawerHeaderTheme(theme)
        applyNavigationDrawerBodyTheme(theme)
    }

    private fun applyNavigationDrawerHeaderTheme(theme: ITheme) {
        val headerView = mHeaderView ?: return
        val backgroundColor = theme.drawerBackgroundColor
        val textColorPrimary = theme.drawerTextColor
        val textColorSecondary = theme.textColorSecondary
        headerView.setBackgroundColor(backgroundColor)
        mWalletNameView?.setTextColor(textColorPrimary)
        mWalletMoneyView?.setTextColor(textColorSecondary)
        mWalletListArrowView?.setColorFilter(textColorSecondary, PorterDuff.Mode.SRC_ATOP)
        // a subtle ring around the wallet icon
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(Math.round(resources.displayMetrics.density), ColorUtils.setAlphaComponent(textColorPrimary, 0x24))
        }
        mWalletIconView?.background = ring
        headerView.findViewById<View>(R.id.header_divider)?.setBackgroundColor(
            ColorUtils.setAlphaComponent(textColorPrimary, 0x1F)
        )
    }

    private fun applyNavigationDrawerBodyTheme(theme: ITheme) {
        val navigationView = mNavigationView ?: return
        // the library gives the drawer its outline, and with it its shadow, only while the background
        // is still the shape drawable it built, so that drawable is recolored and not replaced
        val background = navigationView.background
        if (background is MaterialShapeDrawable) {
            background.fillColor = ColorStateList.valueOf(theme.drawerBackgroundColor)
        } else {
            navigationView.setBackgroundColor(theme.drawerBackgroundColor)
        }
        navigationView.itemTextColor = checkedStates(theme.drawerSelectedTextColor, theme.drawerTextColor)
        val density = resources.displayMetrics.density
        val cornerRadius = 28f * density
        val shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornerRadius)
            .build()
        val pillShape = MaterialShapeDrawable(shapeAppearanceModel).apply {
            fillColor = checkedStates(theme.drawerSelectedItemColor, Color.TRANSPARENT)
        }
        val insetHorizontal = (12 * density).toInt()
        navigationView.itemBackground = InsetDrawable(pillShape, insetHorizontal, 0, insetHorizontal, 0)
        val menu = navigationView.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.groupId != GROUP_WALLETS) {
                tintEntry(item, theme)
            }
        }
        buildWalletMenu()
        for (i in 0 until navigationView.childCount) {
            val child = navigationView.getChildAt(i)
            if (child is RecyclerView) {
                ThemedRecyclerView.applyTheme(child, theme)
            }
        }
    }

    /**
     * An open drawer covers the toolbar, and the drawer header extends under the status bar,
     * so what is behind the status bar is the drawer's own background color. The drawer is asked directly,
     * because a drawer restored already open after a rotation never fires the opened callback.
     */
    override fun getColorBehindStatusBar(theme: ITheme): Int {
        if (mDrawerLayout?.isDrawerOpen(GravityCompat.START) == true) {
            return theme.drawerBackgroundColor
        }
        return super.getColorBehindStatusBar(theme)
    }

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                val action = intent.getIntExtra(BackupHandlerIntentService.ACTION, 0)
                if (action == BackupHandlerIntentService.ACTION_RESTORE) {
                    supportLoaderManager.restartLoader(LOADER_WALLETS, null, this@MainActivity)
                }
            }
        }
    }

    companion object {
        private const val SAVED_SELECTION = "MainActivity::current_selection"

        private const val LOADER_WALLETS = 1

        // The drawer menu holds the sections in the first three groups and the wallet list in the
        // last one; the header arrow swaps which of the two is visible.
        private const val GROUP_SECTIONS = 1
        private const val GROUP_SETTINGS = 3
        private const val GROUP_WALLETS = 4

        internal const val ID_SECTION_TRANSACTIONS = 0
        internal const val ID_SECTION_CATEGORIES = 1
        internal const val ID_SECTION_OVERVIEW = 2
        internal const val ID_SECTION_DEBTS = 3
        internal const val ID_SECTION_BUDGETS = 4
        internal const val ID_SECTION_SAVINGS = 5
        internal const val ID_SECTION_EVENTS = 6
        internal const val ID_SECTION_RECURRENCES = 7
        internal const val ID_SECTION_MODELS = 8
        internal const val ID_SECTION_PLACES = 9
        internal const val ID_SECTION_PEOPLE = 10
        internal const val ID_SECTION_SETTING = 15
        internal const val ID_ACTION_NEW_WALLET = 18
        internal const val ID_ACTION_MANAGE_WALLET = 19
        // Every wallet entry takes this plus the wallet's own id, above every other id, so a lookup
        // by id can never land on a section, and a row bound before a reload still names its wallet.
        internal const val ID_WALLET_FIRST = 100

        private fun checkedStates(checkedColor: Int, color: Int): ColorStateList {
            return ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(checkedColor, color))
        }

        private fun walletItemId(wallet: WalletAccount): Int {
            return ID_WALLET_FIRST + wallet.id.toInt()
        }
    }
}
