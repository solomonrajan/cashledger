/*
 * Copyright (c) 2018. MoneyWallet
 * Copyright (c) 2026. solomonrajan/CashLedger
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

@file:Suppress("LongMethod", "FunctionName", "FunctionNaming", "WildcardImport", "MaxLineLength", "MagicNumber", "UnusedParameter", "LongParameterList", "ReturnCount", "CyclomaticComplexMethod")

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.utils.IconLoader

/**
 * Creator and editor for a category rule.
 * Matches description snippets against transactions to file them under a target category.
 */
class NewEditCategoryRuleActivity : AppCompatActivity(), CategoryPicker.Controller {

    private lateinit var categoryPicker: CategoryPicker

    // Screen State
    private var pattern by mutableStateOf("")
    private var category by mutableStateOf<Category?>(null)
    private var patternError by mutableStateOf(false)
    private var categoryError by mutableStateOf(false)

    private var mode = NewEditItemActivity.Mode.NEW_ITEM
    private var itemId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val intentMode = intent?.getSerializableExtra(NewEditItemActivity.MODE) as? NewEditItemActivity.Mode
        if (intentMode != null) {
            mode = intentMode
        }
        itemId = intent?.getLongExtra(NewEditItemActivity.ID, -1L) ?: -1L

        if (savedInstanceState == null && mode == NewEditItemActivity.Mode.EDIT_ITEM) {
            loadCategoryRuleData()
        }

        categoryPicker = CategoryPicker.createPicker(supportFragmentManager, TAG_CATEGORY_PICKER, category)

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewEditCategoryRuleScreen(
                        mode = mode,
                        pattern = pattern,
                        onPatternChange = {
                            pattern = it
                            if (patternError && it.trim().isNotEmpty()) {
                                patternError = false
                            }
                        },
                        patternError = patternError,
                        category = category,
                        categoryError = categoryError,
                        onCategoryClick = {
                            categoryPicker.showPicker()
                        },
                        onClearCategory = {
                            category = null
                        },
                        onBackClick = {
                            finish()
                        },
                        onSaveClick = {
                            saveCategoryRule()
                        },
                        onDeleteClick = if (mode == NewEditItemActivity.Mode.EDIT_ITEM) {
                            { deleteCategoryRule() }
                        } else null
                    )
                }
            }
        }
    }

    override fun onCategoryChanged(tag: String?, newCategory: Category?) {
        category = newCategory
        if (categoryError && newCategory != null) {
            categoryError = false
        }
    }

    private fun loadCategoryRuleData() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, itemId)
        val projection = arrayOf(
            Contract.CategoryRule.PATTERN,
            Contract.CategoryRule.CATEGORY_ID
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val patternCol = cursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)
                val categoryIdCol = cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)
                pattern = cursor.getString(patternCol) ?: ""
                val categoryId = cursor.getLong(categoryIdCol)
                category = readCategory(contentResolver, categoryId)
            }
        }
    }

    private fun saveCategoryRule() {
        val trimmedPattern = pattern.trim()
        var hasError = false

        if (trimmedPattern.isEmpty()) {
            patternError = true
            hasError = true
        }

        val targetCategory = category
        if (targetCategory == null) {
            categoryError = true
            hasError = true
        }

        if (hasError) return

        val contentValues = ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, trimmedPattern)
            put(Contract.CategoryRule.CATEGORY_ID, targetCategory!!.id)
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_CATEGORY_RULES, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }

        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun deleteCategoryRule() {
        if (itemId > 0L) {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, itemId)
            contentResolver.delete(uri, null, null)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    object Mode {
        @JvmField
        val NEW_ITEM: NewEditItemActivity.Mode = NewEditItemActivity.Mode.NEW_ITEM
        @JvmField
        val EDIT_ITEM: NewEditItemActivity.Mode = NewEditItemActivity.Mode.EDIT_ITEM
    }

    companion object {
        const val MODE: String = NewEditItemActivity.MODE
        const val ID: String = NewEditItemActivity.ID

        private const val TAG_CATEGORY_PICKER = "NewEditCategoryRuleActivity::Tag::CategoryPicker"

        /**
         * Reads the full Category entity from the database for picker initialization.
         */
        @JvmStatic
        fun readCategory(contentResolver: ContentResolver, categoryId: Long): Category? {
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId)
            val projection = arrayOf(
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.ICON,
                Contract.Category.TYPE,
                Contract.Category.TAG
            )
            return contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE))),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.TAG))
                    )
                } else {
                    null
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEditCategoryRuleScreen(
    mode: NewEditItemActivity.Mode,
    pattern: String,
    onPatternChange: (String) -> Unit,
    patternError: Boolean,
    category: Category?,
    categoryError: Boolean,
    onCategoryClick: () -> Unit,
    onClearCategory: () -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    val titleRes = if (mode == NewEditItemActivity.Mode.NEW_ITEM) {
        R.string.title_activity_new_category_rule
    } else {
        R.string.title_activity_edit_category_rule
    }
    val context = LocalContext.current
    val title = context.getString(titleRes)
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog && onDeleteClick != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.title_warning)) },
            text = { Text(stringResource(R.string.message_delete_category_rule, pattern)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteClick()
                    }
                ) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

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
                    if (onDeleteClick != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Pattern Input Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hint_category_rule_pattern),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = onPatternChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. Starbucks, Uber, Netflix...") },
                        isError = patternError,
                        supportingText = {
                            if (patternError) {
                                Text(
                                    text = stringResource(R.string.error_input_pattern_not_valid),
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("Substring matched against incoming transaction descriptions")
                            }
                        },
                        trailingIcon = {
                            if (pattern.isNotEmpty()) {
                                IconButton(onClick = { onPatternChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            }

            // Target Category Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hint_category),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (categoryError) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (categoryError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCategoryClick() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (category != null) {
                                AndroidView(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                                        }
                                    },
                                    update = { view ->
                                        IconLoader.loadInto(category.icon, view)
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = category.type.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = onClearCategory) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear category")
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Label,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Select destination category...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (categoryError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (categoryError) {
                        Text(
                            text = stringResource(R.string.error_input_missing_category),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Info / Helper Card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "💡",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "When importing or adding transactions, any description matching this pattern will be automatically assigned to the designated category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
