@file:Suppress("LongMethod", "LongParameterList", "MatchingDeclarationName", "FunctionNaming", "WildcardImport", "MaxLineLength", "MagicNumber", "UnusedParameter", "CyclomaticComplexMethod")

package com.oriondev.moneywallet.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val GoogleSansCode = FontFamily(Font(R.font.google_sans_code_regular))

data class TransferScreenState(
    val money: Long = 0L,
    val tax: Long = 0L,
    val currency: CurrencyUnit? = null,
    val description: String = "",
    val date: Date? = Date(),
    val walletFrom: Wallet? = null,
    val walletTo: Wallet? = null,
    val availableWallets: List<Wallet> = emptyList(),
    val event: Event? = null,
    val people: List<Person> = emptyList(),
    val place: Place? = null,
    val note: String = "",
    val confirmed: Boolean = true,
    val countInTotal: Boolean = true,
    val attachments: List<Attachment> = emptyList(),
    val mode: NewEditItemActivity.Mode = NewEditItemActivity.Mode.NEW_ITEM,
    val titleRes: Int = R.string.title_activity_new_transfer
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewEditTransferScreen(
    state: TransferScreenState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onMoneyClick: () -> Unit,
    onTaxClick: () -> Unit,
    onTaxClear: () -> Unit,
    onDateChange: (Date) -> Unit,
    onWalletFromClick: () -> Unit,
    onWalletFromSelect: (Wallet) -> Unit,
    onWalletToClick: () -> Unit,
    onWalletToSelect: (Wallet) -> Unit,
    onSwapWallets: () -> Unit,
    onEventClick: () -> Unit,
    onEventClear: () -> Unit,
    onPeopleClick: () -> Unit,
    onPeopleClear: () -> Unit,
    onPlaceClick: () -> Unit,
    onPlaceClear: () -> Unit,
    onAttachmentClick: () -> Unit,
    onAttachmentOpen: (Attachment) -> Unit,
    onAttachmentDelete: (Attachment) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onCountInTotalChange: (Boolean) -> Unit
) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val calendar = remember(state.date) {
        Calendar.getInstance().apply {
            time = state.date ?: Date()
        }
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = calendar.timeInMillis
    )

    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE)
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    val newCalendar = Calendar.getInstance()
                    datePickerState.selectedDateMillis?.let { newCalendar.timeInMillis = it }
                    newCalendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    newCalendar.set(Calendar.MINUTE, timePickerState.minute)
                    onDateChange(newCalendar.time)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(state.titleRes)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = onAttachmentClick) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach Document")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.padding(end = 12.dp))
                    Text("Save Transfer", style = MaterialTheme.typography.titleMedium)
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Amount Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Transfer Amount",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            val decimals = state.currency?.decimals ?: 2
                            val divisor = Math.pow(10.0, decimals.toDouble())
                            val formattedAmount = NumberFormat.getNumberInstance(Locale.US).apply {
                                minimumFractionDigits = decimals
                                maximumFractionDigits = decimals
                            }.format(state.money / divisor)

                            Text(
                                text = "${state.currency?.symbol ?: "₹"} $formattedAmount",
                                style = MaterialTheme.typography.displayMedium,
                                fontFamily = GoogleSansCode,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            if (state.tax > 0L) {
                                val formattedTax = NumberFormat.getNumberInstance(Locale.US).apply {
                                    minimumFractionDigits = decimals
                                    maximumFractionDigits = decimals
                                }.format(state.tax / divisor)

                                Row(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ReceiptLong,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "+ ${state.currency?.symbol ?: ""} $formattedTax transfer fee",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onMoneyClick,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = "Calculator",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Wallets Flow Card (Source & Destination)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Source Wallet (From)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallMade,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "From Wallet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = onWalletFromClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "All Wallets",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Wallet From Chips Carousel
                        val walletsToDisplay = if (state.availableWallets.isNotEmpty()) {
                            state.availableWallets
                        } else if (state.walletFrom != null) {
                            listOf(state.walletFrom)
                        } else {
                            emptyList()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            walletsToDisplay.forEach { wallet ->
                                val isSelected = state.walletFrom?.id == wallet.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onWalletFromSelect(wallet) },
                                    label = {
                                        Text(
                                            text = wallet.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        // Direction Indicator & Swap Wallets Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            FilledTonalIconButton(
                                onClick = onSwapWallets,
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = "Swap Wallets",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Destination Wallet (To)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallReceived,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "To Wallet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = onWalletToClick,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "All Wallets",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Wallet To Chips Carousel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            walletsToDisplay.forEach { wallet ->
                                val isSelected = state.walletTo?.id == wallet.id
                                val isSameAsFrom = state.walletFrom?.id == wallet.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onWalletToSelect(wallet) },
                                    label = {
                                        Text(
                                            text = wallet.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    enabled = !isSameAsFrom,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // Tax / Fee Field
                val decimals = state.currency?.decimals ?: 2
                val divisor = Math.pow(10.0, decimals.toDouble())
                val formattedTaxString = if (state.tax > 0L) {
                    val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
                        minimumFractionDigits = decimals
                        maximumFractionDigits = decimals
                    }.format(state.tax / divisor)
                    "${state.currency?.symbol ?: ""} $formatted"
                } else {
                    "0.00 (No fee)"
                }

                TransferField(
                    iconVector = Icons.Default.ReceiptLong,
                    label = "Transfer Fee / Tax",
                    value = formattedTaxString,
                    placeholder = "Select tax amount",
                    onClick = onTaxClick,
                    trailingIcon = if (state.tax > 0L) {
                        {
                            IconButton(onClick = onTaxClear) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear fee")
                            }
                        }
                    } else {
                        { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                    }
                )

                // Description Field
                TransferField(
                    iconVector = Icons.Default.Menu,
                    label = "Description",
                    value = state.description,
                    placeholder = "Enter description",
                    onValueChange = onDescriptionChange,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    enabled = true
                )

                // Date & Time Field
                val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy  HH:mm:ss", Locale.getDefault())
                val dateString = state.date?.let { dateFormat.format(it) } ?: ""

                TransferField(
                    iconVector = Icons.Default.DateRange,
                    label = "Date & time",
                    value = dateString,
                    placeholder = "Select date and time",
                    onClick = { showDatePicker = true },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                )

                // Context Tags: Event, People, Place
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InputChip(
                        selected = state.event != null,
                        onClick = onEventClick,
                        label = { Text(state.event?.name ?: "Event") },
                        leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (state.event != null) {
                            {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear Event",
                                    modifier = Modifier.size(18.dp).clickable { onEventClear() }
                                )
                            }
                        } else null
                    )

                    val peopleText = if (state.people.isNotEmpty()) state.people.joinToString { it.name } else "People"
                    InputChip(
                        selected = state.people.isNotEmpty(),
                        onClick = onPeopleClick,
                        label = { Text(peopleText) },
                        leadingIcon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (state.people.isNotEmpty()) {
                            {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear People",
                                    modifier = Modifier.size(18.dp).clickable { onPeopleClear() }
                                )
                            }
                        } else null
                    )

                    InputChip(
                        selected = state.place != null,
                        onClick = onPlaceClick,
                        label = { Text(state.place?.name ?: "Place") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (state.place != null) {
                            {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear Place",
                                    modifier = Modifier.size(18.dp).clickable { onPlaceClear() }
                                )
                            }
                        } else null
                    )
                }

                // Note Field
                TransferField(
                    iconVector = Icons.Default.Description,
                    label = "Note",
                    value = state.note,
                    placeholder = "Add a note",
                    onValueChange = onNoteChange,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    enabled = true
                )

                // Options Card: Confirmed & Count In Total
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirmedChange(!state.confirmed) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Confirmed",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Transfer is completed and effective",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.confirmed,
                                onCheckedChange = onConfirmedChange
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCountInTotalChange(!state.countInTotal) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Count in Total",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Include in total balances and overview",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.countInTotal,
                                onCheckedChange = onCountInTotalChange
                            )
                        }
                    }
                }

                // Attachments List (if present)
                if (state.attachments.isNotEmpty()) {
                    Text(
                        text = "Attachments (${state.attachments.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.attachments.forEach { attachment ->
                            AssistChip(
                                onClick = { onAttachmentOpen(attachment) },
                                label = {
                                    Text(
                                        text = attachment.name ?: "Attachment",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Delete attachment",
                                        modifier = Modifier.size(16.dp).clickable { onAttachmentDelete(attachment) }
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun TransferField(
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    label: String,
    value: String,
    placeholder: String,
    onClick: (() -> Unit)? = null,
    onValueChange: ((String) -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconVector != null || iconRes != null) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = label,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else if (iconRes != null) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            val modifier = if (onClick != null) {
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            } else {
                Modifier.fillMaxWidth()
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange ?: {},
                label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                placeholder = { Text(placeholder) },
                modifier = modifier,
                enabled = enabled,
                keyboardOptions = keyboardOptions,
                trailingIcon = trailingIcon,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}
