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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Warning
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
import com.oriondev.moneywallet.picker.DateTimePicker
import com.oriondev.moneywallet.picker.IconPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.utils.DateFormatter
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import java.util.Calendar
import java.util.Date

class NewEditEventActivity : AppCompatActivity(), IconPicker.Controller, DateTimePicker.Controller {

    private lateinit var iconPicker: IconPicker
    private lateinit var startDatePicker: DateTimePicker
    private lateinit var endDatePicker: DateTimePicker

    // State
    private var name by mutableStateOf("")
    private var icon by mutableStateOf<Icon?>(null)
    private var startDate by mutableStateOf(Date())
    private var endDate by mutableStateOf(Date())
    private var note by mutableStateOf("")

    private var nameError by mutableStateOf(false)
    private var dateRangeError by mutableStateOf(false)

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

        val calendar = Calendar.getInstance()
        startDate = calendar.time
        endDate = DateUtils.addMonths(calendar, 1)

        if (savedInstanceState == null && mode == NewEditItemActivity.Mode.EDIT_ITEM && itemId > 0L) {
            loadEventData()
        }

        val fm = supportFragmentManager
        iconPicker = IconPicker.createPicker(fm, TAG_ICON_PICKER, icon)
        startDatePicker = DateTimePicker.createPicker(fm, TAG_START_DATE_PICKER, startDate)
        endDatePicker = DateTimePicker.createPicker(fm, TAG_END_DATE_PICKER, endDate)

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NewEditEventScreen(
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
                        startDate = startDate,
                        onStartDateClick = { startDatePicker.showDatePicker() },
                        endDate = endDate,
                        onEndDateClick = { endDatePicker.showDatePicker() },
                        dateRangeError = dateRangeError,
                        note = note,
                        onNoteChange = { note = it },
                        onBackClick = { finish() },
                        onSaveClick = { saveEvent() }
                    )
                }
            }
        }
    }

    private fun loadEventData() {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, itemId)
        val projection = arrayOf(
            Contract.Event.NAME,
            Contract.Event.ICON,
            Contract.Event.START_DATE,
            Contract.Event.END_DATE,
            Contract.Event.NOTE
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.NAME)) ?: ""
                val iconStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.ICON))
                icon = IconLoader.parse(iconStr)
                val startStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.START_DATE))
                if (!startStr.isNullOrEmpty()) {
                    startDate = DateUtils.getDateFromSQLDateString(startStr) ?: startDate
                }
                val endStr = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.END_DATE))
                if (!endStr.isNullOrEmpty()) {
                    endDate = DateUtils.getDateFromSQLDateString(endStr) ?: endDate
                }
                note = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Event.NOTE)) ?: ""
                dateRangeError = startDate.time > endDate.time
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
        if (startDate.time > endDate.time) {
            dateRangeError = true
            Toast.makeText(this, R.string.error_input_invalid_date_range, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveEvent() {
        if (!validate()) return

        val contentValues = ContentValues().apply {
            put(Contract.Event.NAME, name.trim())
            put(Contract.Event.ICON, (iconPicker.currentIcon ?: icon)?.toString() ?: "")
            put(Contract.Event.START_DATE, DateUtils.getSQLDateString(startDate))
            put(Contract.Event.END_DATE, DateUtils.getSQLDateString(endDate))
            put(Contract.Event.NOTE, note.trim())
        }

        when (mode) {
            NewEditItemActivity.Mode.NEW_ITEM -> {
                contentResolver.insert(DataContentProvider.CONTENT_EVENTS, contentValues)
            }
            NewEditItemActivity.Mode.EDIT_ITEM -> {
                val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, itemId)
                contentResolver.update(uri, contentValues, null, null)
            }
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    override fun onIconChanged(tag: String?, newIcon: Icon?) {
        icon = newIcon
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        val selectedDate = date ?: return
        when (tag) {
            TAG_START_DATE_PICKER -> {
                startDate = selectedDate
                dateRangeError = startDate.time > endDate.time
            }
            TAG_END_DATE_PICKER -> {
                endDate = selectedDate
                dateRangeError = startDate.time > endDate.time
            }
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

        private const val TAG_ICON_PICKER = "NewEditEventActivity::Tag::IconPicker"
        private const val TAG_START_DATE_PICKER = "NewEditEventActivity::Tag::StartDatePicker"
        private const val TAG_END_DATE_PICKER = "NewEditEventActivity::Tag::EndDatePicker"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEditEventScreen(
    mode: NewEditItemActivity.Mode,
    name: String,
    onNameChange: (String) -> Unit,
    nameError: Boolean,
    icon: Icon?,
    onIconClick: () -> Unit,
    startDate: Date,
    onStartDateClick: () -> Unit,
    endDate: Date,
    onEndDateClick: () -> Unit,
    dateRangeError: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val titleRes = if (mode == NewEditItemActivity.Mode.NEW_ITEM) {
        R.string.title_activity_new_event
    } else {
        R.string.title_activity_edit_event
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
                        Icon(Icons.Default.Check, contentDescription = "Save Event")
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
                            Icon(Icons.Default.Event, contentDescription = null)
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

            // Date Range Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.title_activity_calendar),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Start Date item
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onStartDateClick() },
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.hint_start_date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = DateFormatter.getFormattedDate(startDate),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // End Date item
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onEndDateClick() },
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.hint_end_date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = DateFormatter.getFormattedDate(endDate),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (dateRangeError) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.error_input_invalid_date_range),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
