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

@file:Suppress(
    "LongMethod",
    "LongParameterList",
    "MatchingDeclarationName",
    "FunctionNaming",
    "WildcardImport",
    "MaxLineLength",
    "MagicNumber",
    "UnusedParameter",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
    "SwallowedException",
    "NestedBlockDepth",
    "ReturnCount",
    "ComplexCondition"
)

package com.oriondev.moneywallet.ui.activity

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.ColorIcon
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.utils.IconLoader

class NewEditPersonActivity : AppCompatActivity(), IconPicker.Controller {

    private lateinit var iconPicker: IconPicker

    // State
    private var name by mutableStateOf("")
    private var icon by mutableStateOf<Icon?>(null)
    private var note by mutableStateOf("")
    private var nameError by mutableStateOf(false)

    private var mode = NewEditItemActivity.Mode.NEW_ITEM
    private var itemId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val intentMode = intent?.getSerializableExtra(NewEditItemActivity.MODE) as? NewEditItemActivity.Mode
        if (intentMode != null) {
            mode = intentMode
        }
        itemId = intent?.getLongExtra(NewEditItemActivity.ID, -1L) ?: -1L

        if (savedInstanceState == null && mode == NewEditItemActivity.Mode.EDIT_ITEM && itemId > 0L) {
            loadPersonData()
        }

        iconPicker = IconPicker.createPicker(supportFragmentManager, TAG_ICON_PICKER, icon)

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewEditPersonScreen(
                        mode = mode,
                        name = name,
                        onNameChange = { newName ->
                            name = newName
                            nameError = false
                            val currentIcon = iconPicker.currentIcon ?: icon
                            if (currentIcon is ColorIcon) {
                                val initial = IconLoader.getColorIconString(newName)
                                val updatedIcon = ColorIcon(currentIcon, initial)
                                iconPicker.setCurrentIcon(updatedIcon)
                                icon = updatedIcon
                            }
                        },
                        nameError = nameError,
                        icon = icon,
                        onIconClick = { iconPicker.showPicker() },
                        note = note,
                        onNoteChange = { note = it },
                        onBackClick = { finish() },
                        onSaveClick = { savePerson() }
                    )
                }
            }
        }
    }

    private fun loadPersonData() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, itemId)
        val projection = arrayOf(
            Contract.Person.ID,
            Contract.Person.NAME,
            Contract.Person.ICON,
            Contract.Person.NOTE
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NAME)) ?: ""
                val iconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.ICON))
                icon = IconLoader.parse(iconStr)
                note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Person.NOTE)) ?: ""
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
            nameError = true
            Toast.makeText(this, R.string.error_input_name_not_valid, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun savePerson() {
        if (!validate()) return

        val contentValues = ContentValues().apply {
            put(Contract.Person.NAME, name.trim())
            put(Contract.Person.ICON, (iconPicker.currentIcon ?: icon)?.toString() ?: "")
            put(Contract.Person.NOTE, note.trim())
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_PEOPLE, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onIconChanged(tag: String?, newIcon: Icon?) {
        icon = newIcon
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
        private const val TAG_ICON_PICKER = "NewEditPersonActivity::Tag::IconPicker"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEditPersonScreen(
    mode: NewEditItemActivity.Mode,
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    icon: Icon?,
    onIconClick: () -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val titleRes = if (mode == NewEditItemActivity.Mode.NEW_ITEM) {
        R.string.title_activity_new_person
    } else {
        R.string.title_activity_edit_person
    }
    val context = LocalContext.current
    val title = context.getString(titleRes)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSaveClick) {
                        Icon(Icons.Default.Check, contentDescription = "Save Person")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = stringResource(R.string.menu_action_save_changes),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
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
            // Main Header Card: Icon and Name
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar / Icon
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onIconClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
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
                                    view.setImageDrawable(null)
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.hint_name)) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text(stringResource(R.string.error_input_name_not_valid)) }
                        } else null,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            // Note Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        label = { Text(stringResource(R.string.hint_note)) },
                        leadingIcon = {
                            Icon(Icons.Default.EditNote, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
