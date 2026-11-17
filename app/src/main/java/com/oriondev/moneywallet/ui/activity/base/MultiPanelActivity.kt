package com.oriondev.moneywallet.ui.activity.base

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.activity.ToolbarController
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment

abstract class MultiPanelActivity : BaseActivity(), ToolbarController {

    private var fragment: MultiPanelFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_root_container)
        val fragmentManager = supportFragmentManager
        val tag = getMultiPanelFragmentTag()
        val existingFragment = fragmentManager.findFragmentByTag(tag) as? MultiPanelFragment
        if (existingFragment != null) {
            fragment = existingFragment
            fragmentManager.beginTransaction().show(existingFragment).commit()
        } else {
            val newFragment = onCreateMultiPanelFragment()
            fragment = newFragment
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_container, newFragment, tag)
                .commit()
        }
    }

    protected abstract fun onCreateMultiPanelFragment(): MultiPanelFragment

    protected abstract fun getMultiPanelFragmentTag(): String

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val f = fragment
        if (f != null && !f.navigateBack()) {
            super.onBackPressed()
        }
    }

    override fun setToolbar(toolbar: Toolbar) {
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp)
        toolbar.setNavigationOnClickListener {
            @Suppress("DEPRECATION")
            onBackPressed()
        }
    }
}
