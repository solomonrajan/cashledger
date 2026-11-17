/*
 * Copyright (c) 2018.
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

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment

import com.github.paolorotolo.appintro.AppIntro2
import com.github.paolorotolo.appintro.AppIntroFragment
import com.github.paolorotolo.appintro.model.SliderPage
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.VectorIcon
import org.json.JSONObject
import org.json.JSONException
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.SystemBars
import com.oriondev.moneywallet.utils.Utils

class TutorialActivity : AppIntro2() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        addSlide(R.drawable.ic_intro_slide_1, R.string.activity_intro_title_slide_1, R.string.activity_intro_description_slide_1, Color.parseColor("#4285F4"))
        addSlide(R.drawable.ic_intro_slide_2, R.string.activity_intro_title_slide_2, R.string.activity_intro_description_slide_2, Color.parseColor("#EA4335"))
        addSlide(R.drawable.ic_intro_slide_3, R.string.activity_intro_title_slide_3, R.string.activity_intro_description_slide_3, Color.parseColor("#FBBC05"))
        addSlide(R.drawable.ic_intro_slide_4, R.string.activity_intro_title_slide_4, R.string.activity_intro_description_slide_4, Color.parseColor("#34A853"))
        addSlide(R.drawable.ic_intro_slide_5, R.string.activity_intro_title_slide_5, R.string.activity_intro_description_slide_5, Color.parseColor("#4285F4"))
        setColorTransitionsEnabled(true)
        applySystemBarInsets()
    }

    // This intro screen does not go through ThemedActivity, it extends the AppIntro base class, so it
    // asks for edge to edge itself. Only the bar holding Skip, Next and Done is held clear of the
    // navigation bar; the slides keep their full bleed colors, which is what the color transition
    // between them is for. The status bar is hidden here, so nothing is owed at the top.
    private fun applySystemBarInsets() {
        val content = findViewById<View>(android.R.id.content)
        if (content != null) {
            // Below Android 11 the first child to consume an inset stops its siblings from seeing
            // it, and something in the intro's own layout does. Without this the bar below never
            // hears about the navigation bar at all, which is what it looked like on Android 7.
            ViewGroupCompat.installCompatInsetsDispatch(content)
        }
        val bottomBar = findViewById<View>(com.github.paolorotolo.appintro.R.id.bottom) ?: return
        SystemBars.pad(bottomBar, false, true, true)
    }

    private fun addSlide(@DrawableRes drawable: Int, @StringRes title: Int, @StringRes description: Int, backgroundColor: Int) {
        val sliderPage = SliderPage()
        sliderPage.imageDrawable = drawable
        sliderPage.title = getString(title)
        sliderPage.description = getString(description)
        sliderPage.bgColor = backgroundColor
        addSlide(AppIntroFragment.newInstance(sliderPage))
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        pager.setCurrentItem(slides.size - 1, true)
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        startActivityForResult(Intent(this, NewEditWalletActivity::class.java), REQUEST_NEW_WALLET)
    }

    override fun onResume() {
        super.onResume()
        val content = findViewById<View>(android.R.id.content)
        content?.post {
            if (slides != null && slides.isNotEmpty() && pager != null) {
                val current = slides[pager.currentItem] as? Fragment
                if (current?.view != null) {
                    val imageView = current.view?.findViewById<ImageView>(com.github.paolorotolo.appintro.R.id.image)
                    if (imageView?.drawable is Animatable) {
                        (imageView.drawable as Animatable).start()
                    }
                }
            }
        }
    }

    override fun onSlideChanged(oldFragment: Fragment?, newFragment: Fragment?) {
        super.onSlideChanged(oldFragment, newFragment)
        if (newFragment?.view != null) {
            val imageView = newFragment.view?.findViewById<ImageView>(com.github.paolorotolo.appintro.R.id.image)
            if (imageView?.drawable is Animatable) {
                (imageView.drawable as Animatable).start()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_NEW_WALLET) {
            if (resultCode == RESULT_OK) {
                insertDefaultCategories()
                setResult(RESULT_OK)
                finish()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun insertDefaultCategories() {
        val contentResolver = contentResolver
        val uri = DataContentProvider.CONTENT_CATEGORIES
        for (category in generateDefaultCategories(this)) {
            val contentValues = ContentValues()
            contentValues.put(Contract.Category.NAME, category.name)
            contentValues.put(Contract.Category.ICON, category.icon.toString())
            contentValues.put(Contract.Category.TYPE, category.type.value)
            contentValues.put(Contract.Category.SHOW_REPORT, true)
            contentValues.put(Contract.Category.TAG, category.tag)
            contentResolver.insert(uri, contentValues)
        }
    }

    companion object {
        private const val REQUEST_NEW_WALLET = 374

        private fun generateDefaultCategories(context: Context): List<Category> {
            val categoryList = ArrayList<Category>()
            categoryList.add(getDefaultCategory(context, R.string.default_category_tip, Contract.CategoryType.INCOME, "default::tip", Utils.getRandomMDColor(), "ic_coin_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_prize, Contract.CategoryType.INCOME, "default::prize", Utils.getRandomMDColor(), "ic_star_black_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_salary, Contract.CategoryType.INCOME, "default::salary", Utils.getRandomMDColor(), "ic_account_balance_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_interests, Contract.CategoryType.INCOME, "default::interests", Utils.getRandomMDColor(), "ic_equalizer_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_sale, Contract.CategoryType.INCOME, "default::sale", Utils.getRandomMDColor(), "ic_shopping_cart_24dp"))
            // add expense categories
            categoryList.add(getDefaultCategory(context, R.string.default_category_car_expenses, Contract.CategoryType.EXPENSE, "default::car_expenses", Utils.getRandomMDColor(), "ic_map_black_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_travel, Contract.CategoryType.EXPENSE, "default::travel", Utils.getRandomMDColor(), "ic_airplanemode_on_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_friends, Contract.CategoryType.EXPENSE, "default::friends", Utils.getRandomMDColor(), "ic_people_black_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_technology, Contract.CategoryType.EXPENSE, "default::technology", Utils.getRandomMDColor(), "ic_storage_black_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_food_drinks, Contract.CategoryType.EXPENSE, "default::food_drinks", Utils.getRandomMDColor(), "ic_receipt_black_24dp"))
            categoryList.add(getDefaultCategory(context, R.string.default_category_hobby, Contract.CategoryType.EXPENSE, "default::hobby", Utils.getRandomMDColor(), "ic_color_lens_black_24dp"))
            return categoryList
        }

        private fun getDefaultCategory(context: Context, nameRes: Int, type: Contract.CategoryType, tag: String, color: Int, iconName: String): Category {
            var icon: Icon? = null
            try {
                val json = JSONObject()
                json.put("resource", iconName)
                json.put("color", Utils.getHexColor(color))
                icon = VectorIcon(json)
            } catch (e: JSONException) {
                val name = context.getString(nameRes)
                val label = IconLoader.getColorIconString(name)
                icon = ColorIcon(Utils.getHexColor(color), label)
            }
            return Category(-1L, context.getString(nameRes), icon, type, tag)
        }
    }
}
