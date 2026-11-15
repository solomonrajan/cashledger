package com.oriondev.moneywallet.ui.activity.base

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.view.theme.ITheme
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.utils.Utils
import java.lang.reflect.Constructor

/**
 * This activity is used as base activity for all the application activities.
 * It will automatically apply the current theme to all the views that are subscribed
 * to the ThemeEngine.
 * The first step is done during the inflation of the layout: here the theme properties are
 * automatically set to the view that is subscribed just after the creation.
 * The activity will than register itself as an observer for the current theme changes.
 * Whenever a property of the current theme changes, the observer will be notified.
 * Before the destruction the activity MUST un subscribe as observer to avoid memory leaks.
 */
abstract class ThemedActivity : AppCompatActivity(), ThemeEngine.ThemeObserver {

    private var appBarScrolledPastStatusBar: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Both bars go transparent and the window stops reserving space for them, on every release
        // and not only where Android 15 enforces it, so one arrangement covers the whole range.
        // Each surface then asks for the insets it needs through SystemBars.
        WindowCompat.enableEdgeToEdge(window)
        ThemeEngine.registerObserver(this)
    }

    /**
     * Themed views are handed the current theme as they are inflated. AppCompatActivity is itself
     * the layout inflater factory and routes through here every view it does not create itself, so
     * this is the hook for it.
     */
    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
        val themed = onCreateThemedView(name, context, attrs)
        if (themed != null) {
            return themed
        }
        return super.onCreateView(parent, name, context, attrs)
    }

    private fun onCreateThemedView(name: String, context: Context, attrs: AttributeSet): View? {
        if (!name.startsWith(THEMED_VIEW_PACKAGE)) {
            return null
        }
        val view: View = try {
            getThemedViewConstructor(name).newInstance(context, attrs) as View
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // let the normal inflation path build it: it uses the same constructor and will report
            // a genuinely missing class or constructor far better than this can
            e.printStackTrace()
            return null
        }
        // deliberately outside the catch above. A view that was built correctly is worth keeping
        // even if theming it fails, and rebuilding it would run its constructor a second time
        ThemeEngine.applyTheme(view, false)
        return view
    }

    @Throws(Exception::class)
    private fun getThemedViewConstructor(name: String): Constructor<*> {
        val cached = themedViewConstructors[name]
        if (cached != null) {
            return cached
        }
        val constructor = Class.forName(name).getConstructor(Context::class.java, AttributeSet::class.java)
        themedViewConstructors[name] = constructor
        return constructor
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        onThemeSetup(ThemeEngine.getTheme())
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyKeyboardInset()
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyKeyboardInset()
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        applyKeyboardInset()
    }

    /**
     * The window no longer resizes itself for the keyboard once it stops fitting system windows, so
     * the keyboard arrives as an inset and the content root is lifted by it here.
     */
    private fun applyKeyboardInset() {
        val content = findViewById<View>(android.R.id.content) ?: return
        ViewGroupCompat.installCompatInsetsDispatch(content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(0, 0, 0, (keyboard - bars).coerceAtLeast(0))
            insets
        }
        content.requestApplyInsets()
    }

    override fun onDestroy() {
        // Before the teardown, not after it: a fragment being torn down inside super.onDestroy
        // can change the theme, and this activity is in no state to be repainted.
        ThemeEngine.unregisterObserver(this)
        super.onDestroy()
    }

    override fun onThemeChanged(theme: ITheme) {
        ThemeEngine.applyTheme(window.peekDecorView(), true)
        onThemeSetup(theme)
    }

    /**
     * This method is called by the activity when the activity has been created and
     * dynamically when the theme engine detects a change of a value of the theme.
     *
     * @param theme current theme to apply
     */
    @CallSuper
    protected open fun onThemeSetup(theme: ITheme) {
        setupActivityBaseTheme(theme)
    }

    private fun setupActivityBaseTheme(theme: ITheme) {
        onThemeSystemBarScrim(theme)
        onThemeSystemBarIcons(theme)
        onThemeTaskDescription(theme)
        onThemeWindowBackground(theme)
    }

    /**
     * The color the app itself draws behind the status bar.
     */
    protected open fun getColorBehindStatusBar(theme: ITheme): Int {
        return if (appBarScrolledPastStatusBar) {
            theme.colorWindowForeground
        } else {
            theme.colorPrimary
        }
    }

    /**
     * For the screens that put their toolbar inside the scrolling content.
     */
    fun followScrollForStatusBarIcons(scroller: View?, appBar: View?) {
        if (scroller == null || appBar == null || !isDescendant(scroller, appBar)) {
            return
        }
        scroller.setOnScrollChangeListener { _, _, _, _, _ ->
            updateStatusBarIconsForScroll(scroller, appBar)
        }
        // A rotation restores the scroll position without a scroll event, so the answer has to be
        // worked out again once the restored geometry exists.
        scroller.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateStatusBarIconsForScroll(scroller, appBar)
        }
    }

    private fun updateStatusBarIconsForScroll(scroller: View, appBar: View) {
        val insets = ViewCompat.getRootWindowInsets(scroller)
        val statusBar = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val past = appBar.bottom - scroller.scrollY <= statusBar
        if (past != appBarScrolledPastStatusBar) {
            appBarScrolledPastStatusBar = past
            onThemeSystemBarIcons(ThemeEngine.getTheme())
        }
    }

    /**
     * A screen whose toolbar scrolls away hands the icon color back when it leaves.
     */
    fun resetStatusBarIconsToAppBar() {
        if (appBarScrolledPastStatusBar) {
            appBarScrolledPastStatusBar = false
            onThemeSystemBarIcons(ThemeEngine.getTheme())
        }
    }

    private fun isDescendant(ancestor: View, view: View): Boolean {
        var parent: Any? = view.parent
        while (parent is View) {
            if (parent == ancestor) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    /**
     * The color the app itself draws behind the navigation bar.
     */
    protected open fun getColorBehindNavigationBar(theme: ITheme): Int {
        return theme.colorWindowForeground
    }

    protected open fun onThemeSystemBarScrim(theme: ITheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return
        }
        window.navigationBarColor = ContextCompat.getColor(this, R.color.system_bar_scrim_dark)
    }

    protected open fun onThemeSystemBarIcons(theme: ITheme) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = Utils.isColorLight(getColorBehindStatusBar(theme))
        controller.isAppearanceLightNavigationBars = Utils.isColorLight(getColorBehindNavigationBar(theme))
    }

    protected open fun onThemeTaskDescription(theme: ITheme) {
        val name = getString(R.string.app_name)
        val icon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription(name, icon, theme.colorPrimary))
    }

    protected open fun onThemeWindowBackground(theme: ITheme) {
        val view = window.decorView
        view.setBackgroundColor(theme.colorWindowBackground)
    }

    companion object {
        private const val THEMED_VIEW_PACKAGE = "com.oriondev.moneywallet.ui.view.theme.Themed"
        private val themedViewConstructors = HashMap<String, Constructor<*>>()
    }
}
