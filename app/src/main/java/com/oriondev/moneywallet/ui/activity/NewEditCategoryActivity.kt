@file:Suppress("LongMethod", "FunctionName", "FunctionNaming", "WildcardImport", "MaxLineLength", "MagicNumber", "UnusedParameter", "LongParameterList", "ReturnCount", "CyclomaticComplexMethod")
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

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.IconLoader

class NewEditCategoryActivity : AppCompatActivity(), IconPicker.Controller, CategoryPicker.Controller {

    private lateinit var iconPicker: IconPicker
    private lateinit var categoryPicker: CategoryPicker

    // State
    private var name by mutableStateOf("")
    private var icon by mutableStateOf<Icon?>(null)
    private var parentCategory by mutableStateOf<Category?>(null)
    private var categoryType by mutableStateOf(Contract.CategoryType.INCOME)
    private var showReport by mutableStateOf(true)
    private var isSystemCategory by mutableStateOf(false)

    private var mode = NewEditItemActivity.Mode.NEW_ITEM
    private var itemId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val intentMode = intent.getSerializableExtra(NewEditItemActivity.MODE) as? NewEditItemActivity.Mode
        if (intentMode != null) mode = intentMode
        itemId = intent.getLongExtra(NewEditItemActivity.ID, -1)

        if (savedInstanceState == null) {
            if (mode == NewEditItemActivity.Mode.EDIT_ITEM) {
                loadCategoryData()
            } else if (mode == NewEditItemActivity.Mode.NEW_ITEM) {
                val intentType = intent.getSerializableExtra(TYPE) as? Contract.CategoryType
                if (intentType != null) {
                    categoryType = intentType
                }
            }
        } else {
            isSystemCategory = savedInstanceState.getBoolean(SS_SYSTEM_CATEGORY)
        }

        // Initialize pickers
        iconPicker = IconPicker.createPicker(supportFragmentManager, TAG_ICON_PICKER, icon)
        categoryPicker = CategoryPicker.createPicker(supportFragmentManager, TAG_CATEGORY_PICKER, parentCategory)

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NewEditCategoryScreen(
                        mode = mode,
                        name = name,
                        onNameChange = { name = it },
                        icon = icon,
                        onIconClick = { iconPicker.showPicker() },
                        parentCategory = parentCategory,
                        onParentCategoryClick = { categoryPicker.showParentPicker(itemId, categoryType) },
                        onClearParentCategory = {
                            parentCategory = null
                            categoryPicker.setCategory(null)
                        },
                        categoryType = categoryType,
                        onCategoryTypeChange = { categoryType = it },
                        showReport = showReport,
                        onShowReportChange = { showReport = it },
                        isSystemCategory = isSystemCategory,
                        onBackClick = { finish() },
                        onSaveClick = { saveCategory() }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_SYSTEM_CATEGORY, isSystemCategory)
    }

    private fun loadCategoryData() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, itemId)
        val projection = arrayOf(
            Contract.Category.NAME,
            Contract.Category.ICON,
            Contract.Category.PARENT,
            Contract.Category.PARENT_NAME,
            Contract.Category.PARENT_ICON,
            Contract.Category.PARENT_TYPE,
            Contract.Category.TYPE,
            Contract.Category.SHOW_REPORT
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME))
                val iconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))
                icon = IconLoader.parse(iconStr)
                
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Category.PARENT))) {
                    parentCategory = Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.PARENT)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.PARENT_NAME)),
                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.PARENT_ICON))),
                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.PARENT_TYPE)))
                    )
                }
                categoryType = Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE)))
                showReport = cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.SHOW_REPORT)) == 1
                isSystemCategory = categoryType == Contract.CategoryType.SYSTEM
            } else {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        } ?: run {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun validate(): Boolean {
        if (name.isBlank()) {
            Toast.makeText(this, R.string.error_input_name_not_valid, Toast.LENGTH_SHORT).show()
            return false
        }
        if (parentCategory != null && parentCategory?.type != categoryType) {
            Toast.makeText(this, R.string.message_error_insert_category_not_consistent, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveCategory() {
        if (!validate()) return

        val contentValues = ContentValues().apply {
            put(Contract.Category.NAME, name)
            put(Contract.Category.ICON, icon?.toString() ?: "")
            if (!isSystemCategory) {
                put(Contract.Category.PARENT, parentCategory?.id)
                put(Contract.Category.TYPE, categoryType.value)
            }
            put(Contract.Category.SHOW_REPORT, if (showReport) 1 else 0)
        }

        try {
            when (mode) {
                NewEditItemActivity.Mode.NEW_ITEM -> {
                    contentResolver.insert(DataContentProvider.CONTENT_CATEGORIES, contentValues)
                }
                NewEditItemActivity.Mode.EDIT_ITEM -> {
                    val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, itemId)
                    contentResolver.update(uri, contentValues, null, null)
                }
            }
            setResult(Activity.RESULT_OK)
            finish()
        } catch (e: SQLiteDataException) {
            val contentRes = when (e.errorCode) {
                Contract.ErrorCode.CATEGORY_HIERARCHY_NOT_SUPPORTED -> 
                    if (mode == NewEditItemActivity.Mode.NEW_ITEM) R.string.message_error_insert_category_deep_hierarchy 
                    else R.string.message_error_update_category_deep_hierarchy
                Contract.ErrorCode.CATEGORY_NOT_CONSISTENT -> 
                    if (mode == NewEditItemActivity.Mode.NEW_ITEM) R.string.message_error_insert_category_not_consistent 
                    else R.string.message_error_update_category_not_consistent
                else -> 0
            }
            if (contentRes != 0) {
                ThemedDialog.buildMaterialDialog(this)
                    .setTitle(R.string.title_error)
                    .setMessage(contentRes)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    override fun onIconChanged(tag: String?, newIcon: Icon?) {
        icon = newIcon
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        parentCategory = category
        if (category != null && category.type != categoryType) {
            categoryType = category.type
        }
    }

    companion object {
        const val TYPE = "NewEditCategoryActivity::Type"
        private const val TAG_ICON_PICKER = "NewEditCategoryActivity::Tag::IconPicker"
        private const val TAG_CATEGORY_PICKER = "NewEditCategoryActivity::Tag::CategoryPicker"
        private const val SS_SYSTEM_CATEGORY = "NewEditCategoryActivity::SavedState::IsSystemCategory"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEditCategoryScreen(
    mode: NewEditItemActivity.Mode,
    name: String,
    onNameChange: (String) -> Unit,
    icon: Icon?,
    onIconClick: () -> Unit,
    parentCategory: Category?,
    onParentCategoryClick: () -> Unit,
    onClearParentCategory: () -> Unit,
    categoryType: Contract.CategoryType,
    onCategoryTypeChange: (Contract.CategoryType) -> Unit,
    showReport: Boolean,
    onShowReportChange: (Boolean) -> Unit,
    isSystemCategory: Boolean,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val titleRes = if (mode == NewEditItemActivity.Mode.NEW_ITEM) R.string.title_activity_new_category else R.string.title_activity_edit_category
    val context = LocalContext.current
    val title = context.getString(titleRes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Icon and Name
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon view using legacy IconLoader in AndroidView
                AndroidView(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onIconClick() },
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                            val pd = (8 * ctx.resources.displayMetrics.density).toInt()
                            setPadding(pd, pd, pd, pd)
                        }
                    },
                    update = { view ->
                        if (icon != null) {
                            IconLoader.loadInto(icon, view)
                        } else {
                            view.setImageDrawable(null) // Empty or fallback
                        }
                    }
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.hint_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            if (!isSystemCategory) {
                // Parent Category
                OutlinedTextField(
                    value = parentCategory?.name ?: "",
                    onValueChange = { },
                    label = { Text(stringResource(R.string.hint_parent_category)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onParentCategoryClick() },
                    enabled = false, // Acts as button
                    trailingIcon = {
                        if (parentCategory != null) {
                            IconButton(onClick = onClearParentCategory) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // Type Radio Buttons (Income / Expense)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onCategoryTypeChange(Contract.CategoryType.INCOME) }
                    ) {
                        RadioButton(
                            selected = categoryType == Contract.CategoryType.INCOME,
                            onClick = { onCategoryTypeChange(Contract.CategoryType.INCOME) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.hint_income))
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onCategoryTypeChange(Contract.CategoryType.EXPENSE) }
                    ) {
                        RadioButton(
                            selected = categoryType == Contract.CategoryType.EXPENSE,
                            onClick = { onCategoryTypeChange(Contract.CategoryType.EXPENSE) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.hint_expense))
                    }
                }
            }

            // Show in Report Checkbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowReportChange(!showReport) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.hint_show_category_report),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = showReport,
                    onCheckedChange = onShowReportChange
                )
            }
        }
    }
}
