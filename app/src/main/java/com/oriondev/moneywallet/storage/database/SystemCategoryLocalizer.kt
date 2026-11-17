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

package com.oriondev.moneywallet.storage.database

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.content.res.XmlResourceParser
import android.database.Cursor
import android.util.Log
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.VectorIcon
import com.oriondev.moneywallet.utils.IconLoader
import com.oriondev.moneywallet.utils.Utils
import org.json.JSONObject
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.Locale

/**
 * The nine system categories are seeded once, at database create, with their names resolved
 * against whatever locale the app happened to be in. Almost everything that shows a category
 * reads that stored column, so those names keep the seeding language forever while the rest of
 * the app follows the current one. The exceptions are the recurrent transfer and transfer model
 * rows, which use the string resource directly and were always correct.
 *
 * This rewrites the stored name, and the icon glyph derived from it, on the rows it still
 * recognises as its own.
 */
object SystemCategoryLocalizer {

    private const val TAG = "SystemCategoryLocalizer"

    /**
     * Never throws. Both callers are Application, on the main thread, so anything escaping would
     * take the process down and would do it again on the next launch.
     *
     * Costs one query, and on any run where a stored name does not match, about 47ms to build the
     * name table plus an update per row rewritten.
     */
    @JvmStatic
    fun relocalize(context: Context) {
        try {
            relocalizeSystemCategories(context)
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            Log.e(TAG, "unable to relocalize the system categories", t)
        }
    }

    /**
     * A row is only rewritten when its stored name is still one this app would itself have seeded,
     * in any language it ships. Anything else is a name the user chose, and is left alone.
     * Recognising every shipped language rather than only the previously used one is what repairs
     * an install whose seeding language was never recorded, which is every install predating this
     * code.
     *
     * Two cases are indistinguishable, and they fall opposite ways.
     *
     * A user who renames a category to exactly the string some shipped translation already uses
     * for that same category IS overwritten, because that name is in the table and nothing
     * separates it from a stale one. Renaming Debt to Deuda on an English install reverts on the
     * next launch. This is the one way the class can destroy a rename and it is accepted, since
     * the alternative is storing a flag for a case nobody has reported.
     *
     * A row seeded by an older release whose translation has since been edited is NOT repaired,
     * because the table is built from the current build's strings and no longer contains the one
     * it carries. It stays in the old language for good.
     */
    private fun relocalizeSystemCategories(context: Context) {
        val contentResolver = context.contentResolver
        val byTag = HashMap<String, SystemCategory>()
        for (category in SystemCategory.mSystemCategories) {
            byTag[category.tag] = category
        }
        var seededNames: Map<String, Set<String>>? = null
        for (stored in querySystemCategories(contentResolver)) {
            try {
                val category = byTag[stored.tag] ?: continue
                val currentName = category.getName(context)
                val parsedIcon = IconLoader.parse(stored.icon)
                val isColorIcon = parsedIcon is ColorIcon

                if (currentName == stored.name && !isColorIcon) {
                    continue
                }
                if (seededNames == null) {
                    seededNames = seededNamesOrEmpty(context)
                }
                val names = seededNames[stored.tag]
                if (names != null && names.contains(stored.name)) {
                    rewrite(contentResolver, stored, category, context, currentName)
                }
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                // per row, so that one unwritable row does not leave the others stale
                Log.e(TAG, "unable to relocalize the system category " + stored.tag, t)
            }
        }
    }

    /**
     * Latches an empty table on failure. Without that the next mismatching row would try again,
     * so one broken build would be paid once per row and repaired nothing either way.
     */
    private fun seededNamesOrEmpty(context: Context): Map<String, Set<String>> {
        return try {
            seededNames(context)
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            Log.e(TAG, "unable to build the seeded name table", t)
            Collections.emptyMap()
        }
    }

    /**
     * @return every name this build would seed, per category tag.
     */
    private fun seededNames(context: Context): Map<String, Set<String>> {
        val languages = shippedLanguages(context)
        val seededNames = HashMap<String, Set<String>>()
        for (category in SystemCategory.mSystemCategories) {
            val names = HashSet<String>()
            for (language in languages) {
                names.add(category.getName(language))
            }
            seededNames[category.tag] = names
        }
        return seededNames
    }

    /**
     * The locale config lists this app's own twenty entries. getAssets().getLocales() lists over
     * a hundred, because it counts every language the bundled libraries were translated into, and
     * those resolve to the default and contribute nothing but cost.
     *
     * Reading fewer languages than intended only ever means fewer rows are recognised as seeded,
     * never that a user name is overwritten, so a partial read is safe to continue from.
     *
     * @return one context per config entry, plus the untranslated default. The config happens to
     *          list en-US and there is no values-en, so today that entry already covers the
     *          default and ROOT is insurance rather than the only source of it.
     */
    private fun shippedLanguages(context: Context): List<Context> {
        val contexts = ArrayList<Context>()
        var parser: XmlResourceParser? = null
        try {
            contexts.add(localizedContext(context, Locale.ROOT))
            parser = context.resources.getXml(R.xml._generated_res_locale_config)
            var event = parser.next()
            while (event != XmlResourceParser.END_DOCUMENT) {
                if (event == XmlResourceParser.START_TAG && "locale" == parser.name) {
                    val tag = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                    if (tag != null) {
                        contexts.add(localizedContext(context, Locale.forLanguageTag(tag)))
                    }
                }
                event = parser.next()
            }
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            Log.e(TAG, "stopped reading languages after " + contexts.size + " contexts", t)
        } finally {
            parser?.close()
        }
        return contexts
    }

    private fun localizedContext(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    /**
     * One query rather than one per tag, because the view behind it is a join.
     */
    private fun querySystemCategories(contentResolver: ContentResolver): List<StoredCategory> {
        val categories = ArrayList<StoredCategory>()
        val projection = arrayOf(
            Contract.Category.ID,
            Contract.Category.NAME,
            Contract.Category.ICON,
            Contract.Category.TAG
        )
        val selection = "${Contract.Category.TYPE} = ?"
        val selectionArgs = arrayOf(Contract.CategoryType.SYSTEM.value.toString())
        val cursor: Cursor? = contentResolver.query(
            DataContentProvider.CONTENT_CATEGORIES,
            projection,
            selection,
            selectionArgs,
            null
        )
        cursor?.use {
            val idIndex = it.getColumnIndex(Contract.Category.ID)
            val nameIndex = it.getColumnIndex(Contract.Category.NAME)
            val iconIndex = it.getColumnIndex(Contract.Category.ICON)
            val tagIndex = it.getColumnIndex(Contract.Category.TAG)
            while (it.moveToNext()) {
                categories.add(
                    StoredCategory(
                        it.getLong(idIndex),
                        it.getString(nameIndex),
                        it.getString(iconIndex),
                        it.getString(tagIndex)
                    )
                )
            }
        }
        return categories
    }

    private fun rewrite(
        contentResolver: ContentResolver,
        stored: StoredCategory,
        category: SystemCategory,
        context: Context,
        currentName: String
    ) {
        val contentValues = ContentValues().apply {
            put(Contract.Category.NAME, currentName)
            val icon = relocalizedIcon(stored.icon, currentName, category, context)
            if (icon != null) {
                put(Contract.Category.ICON, icon)
            }
        }
        contentResolver.update(
            ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, stored.id),
            contentValues,
            null,
            null
        )
    }

    /**
     * The glyph is derived from the name, so it goes stale with it. The colour is random per
     * seeding process and is not recoverable, so it is carried over rather than regenerated.
     *
     * IconLoader returns null on malformed json but throws on an icon type it does not recognise,
     * hence the catch.
     *
     * @return the updated icon, or null to leave the stored one alone, which happens for a vector
     *          icon and for anything unreadable. The name is corrected either way.
     */
    private fun relocalizedIcon(
        storedIcon: String?,
        currentName: String,
        category: SystemCategory,
        context: Context
    ): String? {
        return try {
            val icon = IconLoader.parse(storedIcon)
            if (icon !is ColorIcon) {
                return null
            }
            val resourceName = category.resourceName
            if (resourceName != null) {
                val jsonObject = JSONObject().apply {
                    put("resource", resourceName)
                    put("color", Utils.getHexColor(icon.color))
                }
                VectorIcon(jsonObject).toString()
            } else {
                ColorIcon(icon, IconLoader.getColorIconString(currentName)).toString()
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "unreadable icon on a system category, keeping the old one", e)
            null
        }
    }

    private class StoredCategory(
        val id: Long,
        val name: String?,
        val icon: String?,
        val tag: String?
    )
}
