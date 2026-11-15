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

package com.oriondev.moneywallet.ui.fragment.single

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.holders.MaterialAboutItemViewHolder
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import com.danielstone.materialaboutlibrary.util.DefaultViewTypeManager
import com.danielstone.materialaboutlibrary.util.ViewTypeManager
import com.oriondev.moneywallet.BuildConfig
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.License
import com.oriondev.moneywallet.ui.fragment.dialog.ChangeLogDialog
import com.oriondev.moneywallet.ui.fragment.dialog.LicenseDialog
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine
import com.oriondev.moneywallet.utils.SystemBars
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Created by andrea on 31/03/18.
 */
class AboutFragment : MaterialAboutFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        if (view != null) {
            SystemBars.pad(
                view.findViewById(com.danielstone.materialaboutlibrary.R.id.mal_recyclerview),
                false,
                resources.getBoolean(R.bool.panel_fills_window),
                true
            )
        }
        return view
    }

    override fun getMaterialAboutList(context: Context): MaterialAboutList? {
        try {
            val jsonBuilder = StringBuilder()
            context.assets.open("resources/about.json").use { inputStream ->
                val bufferedReader = BufferedReader(InputStreamReader(inputStream))
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    jsonBuilder.append(line)
                }
            }
            val aboutObj = JSONObject(jsonBuilder.toString())
            return generateAboutScreen(context, aboutObj)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    @Throws(JSONException::class)
    private fun generateAboutScreen(context: Context, aboutObj: JSONObject): MaterialAboutList {
        val screenBuilder = MaterialAboutList.Builder()
        // the main card contains static information about the project
        screenBuilder.addCard(
            createThemedAboutCard()
                .addItem(
                    MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_restore_black_24dp)
                        .text(R.string.about_hint_version)
                        .subText(BuildConfig.VERSION_NAME)
                        .build()
                )
                .addItem(
                    MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_track_changes_black_24dp)
                        .text(R.string.about_hint_changelog)
                        .setOnClickAction {
                            val fragmentManager = childFragmentManager
                            ChangeLogDialog.showSafely(fragmentManager, TAG_CHANGE_LOG)
                        }
                        .build()
                )
                .addItem(
                    MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_star_black_24dp)
                        .text(R.string.about_hint_rate_app)
                        .setOnClickAction {
                            val uri = Uri.parse("market://details?id=" + context.packageName)
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            try {
                                startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("http://play.google.com/store/apps/details?id=" + context.packageName)
                                    )
                                )
                            }
                        }
                        .build()
                )
                .addItem(
                    MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_library_books_black_24dp)
                        .text(R.string.about_hint_open_source_libraries)
                        .setOnClickAction {
                            val fragmentManager = childFragmentManager
                            LicenseDialog.showSafely(fragmentManager, TAG_LICENSE, object : LicenseDialog.Callback {
                                override fun onLicenseClick(license: License) {
                                    try {
                                        val uri = Uri.parse(license.url)
                                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    } catch (ignore: ActivityNotFoundException) {}
                                }
                            })
                        }
                        .build()
                )
                .addItem(
                    MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_bug_report_black_24dp)
                        .text(R.string.about_hint_report_bug)
                        .setOnClickAction {
                            try {
                                val repositoryObj = aboutObj.getJSONObject("repository")
                                val uri = Uri.parse(repositoryObj.getString("issue_url"))
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (ignore: ActivityNotFoundException) {
                            } catch (ignore: JSONException) {}
                        }
                        .build()
                )
                .build()
        )
        // now we have to iterate the other info object and build all the other cards
        if (aboutObj.has("other_info")) {
            val otherInfo = aboutObj.getJSONArray("other_info")
            for (i in 0 until otherInfo.length()) {
                val cardBuilder = createThemedAboutCard()
                val infoObj = otherInfo.getJSONObject(i)
                val nameRes = infoObj.getString("name_resource")
                val title = getStringByResource(context, nameRes)
                if (title != null) {
                    cardBuilder.title(title)
                }
                val itemArray = infoObj.getJSONArray("items")
                for (j in 0 until itemArray.length()) {
                    val itemBuilder = MaterialAboutActionItem.Builder()
                    val itemObj = itemArray.getJSONObject(j)
                    if (itemObj.has("icon")) {
                        val iconRes = itemObj.getString("icon")
                        itemBuilder.icon(getIconResourceId(context, iconRes))
                    }
                    if (itemObj.has("title")) {
                        val text = itemObj.getString("title")
                        itemBuilder.text(text)
                    } else if (itemObj.has("title_resource")) {
                        val textRes = itemObj.getString("title_resource")
                        val textStr = getStringByResource(context, textRes)
                        if (textStr != null) {
                            itemBuilder.text(textStr)
                        }
                    }
                    if (itemObj.has("subtitle")) {
                        val subtext = itemObj.getString("subtitle")
                        itemBuilder.subText(subtext)
                    } else if (itemObj.has("subtitle_resource")) {
                        val subtextRes = itemObj.getString("subtitle_resource")
                        val subTextStr = getStringByResource(context, subtextRes)
                        if (subTextStr != null) {
                            itemBuilder.subText(subTextStr)
                        }
                    }
                    if (itemObj.has("link")) {
                        val url = itemObj.getString("link")
                        itemBuilder.setOnClickAction {
                            try {
                                val uri = Uri.parse(url)
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (ignore: ActivityNotFoundException) {}
                        }
                    }
                    cardBuilder.addItem(itemBuilder.build())
                }
                screenBuilder.addCard(cardBuilder.build())
            }
        }
        return screenBuilder.build()
    }

    private fun getIconResourceId(context: Context, resource: String): Int {
        val packageName = context.packageName
        return context.resources.getIdentifier(resource, "drawable", packageName)
    }

    private fun getStringByResource(context: Context, resource: String): String? {
        val packageName = context.packageName
        val resId = context.resources.getIdentifier(resource, "string", packageName)
        return if (resId > 0) context.getString(resId) else null
    }

    private fun createThemedAboutCard(): MaterialAboutCard.Builder {
        val theme = ThemeEngine.getTheme()
        return MaterialAboutCard.Builder()
            .cardColor(theme.colorCardBackground)
            .titleColor(theme.textColorPrimary)
    }

    override fun getViewTypeManager(): ViewTypeManager {
        return ThemedViewTypeManager()
    }

    private class ThemedViewTypeManager : DefaultViewTypeManager() {

        override fun setupItem(itemType: Int, holder: MaterialAboutItemViewHolder, item: MaterialAboutItem, context: Context) {
            val theme = ThemeEngine.getTheme()
            when (itemType) {
                0 -> {
                    if (holder is MaterialAboutActionItem.MaterialAboutActionItemViewHolder && item is MaterialAboutActionItem) {
                        MaterialAboutActionItem.setupItem(holder, item, context)
                        holder.text.setTextColor(theme.textColorPrimary)
                        holder.subText.setTextColor(theme.textColorSecondary)
                        holder.icon.setColorFilter(theme.iconColor, PorterDuff.Mode.SRC_IN)
                    }
                }
                1 -> {
                    if (holder is MaterialAboutTitleItem.MaterialAboutTitleItemViewHolder && item is MaterialAboutTitleItem) {
                        MaterialAboutTitleItem.setupItem(holder, item, context)
                        holder.text.setTextColor(theme.textColorPrimary)
                        holder.desc.setTextColor(theme.textColorSecondary)
                        holder.icon.setColorFilter(theme.iconColor, PorterDuff.Mode.SRC_IN)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG_CHANGE_LOG = "AboutFragment::Tag::ChangeLogDialog"
        private const val TAG_LICENSE = "AboutFragment::Tag::LicenseDialog"
    }
}
