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

package com.oriondev.moneywallet.ui.preference

import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * A settings row keeps the current title color when its value changes.
 */
@RunWith(RobolectricTestRunner::class)
class PreferenceTitleThemeTest {

    private var adapter: PreferenceGroupAdapter? = null
    private var titleColorAsBuilt: Int = 0

    @After
    fun restoreTheDefaultMode() {
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT)
    }

    @Test
    fun aRowReboundAfterAModeChangeKeepsTheNewModesTitleColor() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertAModeChangeSurvivesARebind(activity, ThemedPreference(activity))
            assertAModeChangeSurvivesARebind(activity, ColorPreference(activity))
            assertAModeChangeSurvivesARebind(activity, ThemedListPreference(activity))
            assertAModeChangeSurvivesARebind(activity, ThemedInputPreference(activity))
            assertAModeChangeSurvivesARebind(activity, ThemedSwitchPreference(activity))
        } finally {
            controller.close()
        }
    }

    @Test
    fun aCategoryReboundAfterAnAccentChangeKeepsTheNewAccent() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val originalAccent = ThemeEngine.getTheme().colorAccent
        try {
            val activity = controller.get()
            ThemeEngine.setMode(ThemeEngine.Mode.LIGHT)
            val holder = buildRow(activity, ThemedPreferenceCategory(activity))
            val header = titleOf(holder)

            ThemeEngine.setColorAccent(0xFF009688.toInt())
            ThemeEngine.applyTheme(holder.itemView, true)
            val after = header.currentTextColor
            assertEquals(
                "the contrast floor dropped the accent before it reached the header",
                ThemeEngine.getTheme().colorAccent,
                after
            )
            assertNotEquals(
                "the header was built in the color the rebind has to keep, so a rebind proves nothing",
                titleColorAsBuilt,
                after
            )

            adapter?.onBindViewHolder(holder, 0)
            assertEquals(
                "the rebind put back the accent the header was built with",
                after,
                header.currentTextColor
            )
        } finally {
            ThemeEngine.setColorAccent(originalAccent)
            controller.close()
        }
    }

    private fun assertAModeChangeSurvivesARebind(activity: AppCompatActivity, preference: Preference) {
        val name = preference.javaClass.simpleName
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT)
        val holder = buildRow(activity, preference)
        val title = titleOf(holder)
        val light = ThemeEngine.getTheme().textColorPrimary
        assertEquals(
            "$name did not start in the light mode color",
            light,
            title.currentTextColor
        )

        ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK)
        ThemeEngine.applyTheme(holder.itemView, true)
        val deepDark = ThemeEngine.getTheme().textColorPrimary
        assertEquals(
            "$name was not repainted for deep dark",
            deepDark,
            title.currentTextColor
        )
        assertNotEquals(
            "$name was built in the color the rebind has to keep, so a rebind proves nothing",
            titleColorAsBuilt,
            deepDark
        )

        adapter?.onBindViewHolder(holder, 0)
        assertEquals(
            "$name lost its title to the color it was built with",
            deepDark,
            title.currentTextColor
        )
    }

    private fun buildRow(activity: AppCompatActivity, preference: Preference): PreferenceViewHolder {
        preference.title = "Income color"
        val manager = PreferenceManager(activity)
        val screen = manager.createPreferenceScreen(activity)
        screen.addPreference(preference)
        val groupAdapter = PreferenceGroupAdapter(screen)
        adapter = groupAdapter
        val holder = groupAdapter.onCreateViewHolder(
            FrameLayout(activity),
            groupAdapter.getItemViewType(0)
        )
        titleColorAsBuilt = titleOf(holder).currentTextColor
        ThemeEngine.applyTheme(holder.itemView, true)
        return holder
    }

    private fun titleOf(holder: PreferenceViewHolder): TextView {
        val title = holder.findViewById(android.R.id.title) as? TextView
        assertNotNull("the row has no title view", title)
        return title ?: TextView(holder.itemView.context)
    }
}
