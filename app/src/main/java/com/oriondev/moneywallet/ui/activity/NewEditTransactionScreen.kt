@file:Suppress("LongMethod", "LongParameterList", "MatchingDeclarationName", "FunctionNaming", "WildcardImport", "MaxLineLength")

package com.oriondev.moneywallet.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.*
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

data class TransactionScreenState(
    val money: Long = 0L,
    val currency: CurrencyUnit? = null,
    val description: String = "",
    val category: Category? = null,
    val date: Date? = null,
    val wallet: Wallet? = null,
    val event: Event? = null,
    val people: List<Person> = emptyList(),
    val place: Place? = null,
    val note: String = "",
    val confirmed: Boolean = true,
    val countInTotal: Boolean = true,
    val attachments: List<Attachment> = emptyList(),
    val mode: NewEditItemActivity.Mode = NewEditItemActivity.Mode.NEW_ITEM,
    val titleRes: Int = R.string.title_activity_new_transaction,
    
    // UI visibility toggles from Rules
    val isCategoryEnabled: Boolean = true,
    val isMoneyEnabled: Boolean = true,
    val isWalletEnabled: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEditTransactionScreen(
    state: TransactionScreenState,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    onMoneyClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onWalletClick: () -> Unit,
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
                        Icon(painter = painterResource(id = R.drawable.ic_attach_file_black_24dp), contentDescription = "Attach Document")
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
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5E459C)),
                shape = RoundedCornerShape(28.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.padding(end = 8.dp))
                Text("Save Transaction", fontSize = 16.sp)
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
            
            // Header: Money
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEBE5FC)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Amount",
                            fontSize = 12.sp,
                            color = Color(0xFF1E1E1E),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        val formattedAmount = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN")).apply {
                            minimumFractionDigits = 2
                            maximumFractionDigits = 2
                        }.format(state.money)

                        Text(
                            text = "${state.currency?.symbol ?: "₹"} $formattedAmount", 
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1B0B43)
                        )
                    }
                    
                    IconButton(
                        onClick = onMoneyClick,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .background(Color(0xFFDCD2F5), shape = CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_icon_calculator),
                            contentDescription = "Calculator",
                            tint = Color(0xFF1B0B43)
                        )
                    }
                }
            }

            TransactionField(
                icon = Icons.Default.Menu,
                label = "Description",
                value = state.description,
                placeholder = "Enter description",
                onValueChange = onDescriptionChange,
                enabled = true
            )

            TransactionField(
                icon = Icons.Default.LocalOffer,
                label = "Category",
                value = state.category?.name ?: "",
                placeholder = "Select category",
                onClick = onCategoryClick,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )

            val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy  HH:mm:ss", Locale.getDefault())
            val dateString = state.date?.let { dateFormat.format(it) } ?: ""

            TransactionField(
                icon = Icons.Default.DateRange,
                label = "Date & time",
                value = dateString,
                placeholder = "Select date and time",
                onClick = onDateClick, // Can combine date and time clicks or just open date first
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )

            TransactionField(
                icon = Icons.Default.AccountBalanceWallet,
                label = "Wallet",
                value = state.wallet?.name ?: "",
                placeholder = "Select wallet",
                onClick = onWalletClick,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
            )
            
            TransactionField(
                icon = Icons.Default.Event,
                label = "Event",
                value = state.event?.name ?: "",
                placeholder = "Enter event",
                onClick = onEventClick
            )

            TransactionField(
                icon = Icons.Default.Group,
                label = "People",
                value = state.people.joinToString { it.name },
                placeholder = "Add people",
                onClick = onPeopleClick
            )

            TransactionField(
                icon = Icons.Default.LocationOn,
                label = "Place",
                value = state.place?.name ?: "",
                placeholder = "Enter place",
                onClick = onPlaceClick
            )

            TransactionField(
                icon = Icons.Default.Description,
                label = "Note",
                value = state.note,
                placeholder = "Add a note",
                onValueChange = onNoteChange,
                enabled = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TransactionField(
    icon: ImageVector,
    label: String,
    value: String,
    placeholder: String,
    onClick: (() -> Unit)? = null,
    onValueChange: ((String) -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.padding(end = 16.dp),
            tint = Color(0xFF49454F)
        )
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
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                modifier = modifier,
                enabled = enabled,
                trailingIcon = trailingIcon,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = Color(0xFFCAC4D0),
                    disabledLabelColor = Color(0xFF49454F),
                    disabledPlaceholderColor = Color(0xFF49454F),
                    disabledTrailingIconColor = Color(0xFF49454F),
                    unfocusedBorderColor = Color(0xFFCAC4D0),
                    unfocusedLabelColor = Color(0xFF49454F),
                    unfocusedPlaceholderColor = Color(0xFF49454F)
                ),
                shape = RoundedCornerShape(4.dp)
            )
        }
    }
}
