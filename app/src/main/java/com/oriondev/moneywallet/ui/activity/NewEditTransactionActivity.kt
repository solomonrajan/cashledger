package com.oriondev.moneywallet.ui.activity

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.*
import com.oriondev.moneywallet.picker.*
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import com.oriondev.moneywallet.utils.IconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

class NewEditTransactionActivity : AppCompatActivity(),
    MoneyPicker.Controller,
    CategoryPicker.Controller,
    DateTimePicker.Controller,
    WalletPicker.SingleWalletController,
    EventPicker.Controller,
    PersonPicker.Controller,
    PlacePicker.Controller,
    AttachmentPicker.Controller {

    companion object {
        const val TYPE = "NewEditTransactionActivity::Type"
        const val DEBT_ID = "NewEditTransactionActivity::DebtId"
        const val DEBT_ACTION = "NewEditTransactionActivity::DebtAction"
        const val SAVING_ID = "NewEditTransactionActivity::SavingId"
        const val SAVING_ACTION = "NewEditTransactionActivity::SavingAction"
        const val AUTO_OPEN_CALCULATOR = "auto_open_calculator"
        const val PERSON_ID = "NewEditTransactionActivity::PersonId"
        const val MODEL_ID = "NewEditTransactionActivity::ModelId"
        const val DUPLICATE_ID = "NewEditTransactionActivity::DuplicateId"
        const val WALLET_ID = "NewEditTransactionActivity::WalletId"
        const val TYPE_STANDARD = TransactionEditorRules.TYPE_STANDARD
        const val TYPE_TRANSFER = TransactionEditorRules.TYPE_TRANSFER
        const val TYPE_DEBT = TransactionEditorRules.TYPE_DEBT
        const val TYPE_SAVING = TransactionEditorRules.TYPE_SAVING
        const val TYPE_MODEL = TransactionEditorRules.TYPE_MODEL

        const val DEBT_PAY = TransactionEditorRules.DEBT_PAY
        const val DEBT_RECEIVE = TransactionEditorRules.DEBT_RECEIVE
        const val DEBT_PAY_IN_FULL = TransactionEditorRules.DEBT_PAY_IN_FULL
        const val DEBT_RECEIVE_IN_FULL = TransactionEditorRules.DEBT_RECEIVE_IN_FULL
        const val SAVING_DEPOSIT = TransactionEditorRules.SAVING_DEPOSIT
        const val SAVING_WITHDRAW = TransactionEditorRules.SAVING_WITHDRAW
        const val SAVING_WITHDRAW_EVERYTHING = TransactionEditorRules.SAVING_WITHDRAW_EVERYTHING

        private const val TAG_MONEY_PICKER = "NewEditTransactionActivity::Tag::MoneyPicker"
        private const val TAG_CATEGORY_PICKER = "NewEditTransactionActivity::Tag::CategoryPicker"
        private const val TAG_DATETIME_PICKER = "NewEditTransactionActivity::Tag::DateTimePicker"
        private const val TAG_WALLET_PICKER = "NewEditTransactionActivity::Tag::WalletPicker"
        private const val TAG_EVENT_PICKER = "NewEditTransactionActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditTransactionActivity::Tag::PlacePicker"
        private const val TAG_PERSON_PICKER = "NewEditTransactionActivity::Tag::PersonPicker"
        private const val TAG_ATTACHMENT_PICKER = "NewEditTransactionActivity::Tag::AttachmentPicker"

        @JvmStatic
        fun insertTransactionFromModel(context: android.content.Context, modelId: Long): Uri? {
            val contentResolver = context.contentResolver
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, modelId)
            val projection = arrayOf(
                Contract.TransactionModel.MONEY, Contract.TransactionModel.DESCRIPTION,
                Contract.TransactionModel.CATEGORY_ID, Contract.TransactionModel.DIRECTION,
                Contract.TransactionModel.WALLET_ID, Contract.TransactionModel.PLACE_ID,
                Contract.TransactionModel.NOTE, Contract.TransactionModel.EVENT_ID,
                Contract.TransactionModel.CONFIRMED, Contract.TransactionModel.COUNT_IN_TOTAL
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contentValues = TransactionContentValuesBuilder()
                        .money(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.MONEY)))
                        .date(DateUtils.getSQLDateTimeString(Date()))
                        .description(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.DESCRIPTION)))
                        .categoryId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.CATEGORY_ID)))
                        .direction(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransactionModel.DIRECTION)))
                        .type(Contract.TransactionType.STANDARD)
                        .walletId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.WALLET_ID)))
                        .note(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransactionModel.NOTE)))
                        .confirmed(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransactionModel.CONFIRMED)))
                        .countInTotal(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransactionModel.COUNT_IN_TOTAL)))
                    
                    if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_ID))) {
                        contentValues.placeId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.PLACE_ID)))
                    }
                    if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.TransactionModel.EVENT_ID))) {
                        contentValues.eventId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransactionModel.EVENT_ID)))
                    }
                    
                    return contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues.build())
                }
            }
            return null
        }
    }

    private var _state by mutableStateOf(TransactionScreenState())
    private val mRules = TransactionEditorRules()
    
    private lateinit var mMoneyPicker: MoneyPicker
    private lateinit var mCategoryPicker: CategoryPicker
    private lateinit var mDateTimePicker: DateTimePicker
    private lateinit var mWalletPicker: WalletPicker
    private lateinit var mEventPicker: EventPicker
    private lateinit var mPlacePicker: PlacePicker
    private lateinit var mPersonPicker: PersonPicker
    private lateinit var mAttachmentPicker: AttachmentPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupPickers()
        
        val mode = intent.getSerializableExtra(NewEditItemActivity.MODE) as? NewEditItemActivity.Mode ?: NewEditItemActivity.Mode.NEW_ITEM
        val itemId = intent.getLongExtra(NewEditItemActivity.ID, -1L)
        
        loadTransactionData(mode, itemId) { loadedState ->
            _state = loadedState
            
            // Re-assign currency to money picker on load
            mMoneyPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_MONEY_PICKER, _state.currency, _state.money)
        }
        
        setContent {
            NewEditTransactionScreen(
                state = _state,
                onBackClick = { finish() },
                onSaveClick = { saveTransaction() },
                onMoneyClick = { mMoneyPicker.showPicker() },
                onCategoryClick = { mCategoryPicker.showPicker() },
                onDateClick = { mDateTimePicker.showDatePicker() },
                onTimeClick = { mDateTimePicker.showTimePicker() },
                onWalletClick = { mWalletPicker.showSingleWalletPicker() },
                onEventClick = { mEventPicker.showPicker(_state.date) },
                onEventClear = { _state = _state.copy(event = null) },
                onPeopleClick = { mPersonPicker.showPicker() },
                onPeopleClear = { _state = _state.copy(people = emptyList()) },
                onPlaceClick = { mPlacePicker.showPicker() },
                onPlaceClear = { _state = _state.copy(place = null) },
                onAttachmentClick = { mAttachmentPicker.showPicker() },
                onAttachmentOpen = { /* TODO */ },
                onAttachmentDelete = { 
                    val newAttachments = _state.attachments.toMutableList()
                    newAttachments.remove(it)
                    _state = _state.copy(attachments = newAttachments)
                },
                onDescriptionChange = { _state = _state.copy(description = it) },
                onNoteChange = { _state = _state.copy(note = it) },
                onConfirmedChange = { _state = _state.copy(confirmed = it) },
                onCountInTotalChange = { _state = _state.copy(countInTotal = it) }
            )
        }
    }

    private fun setupPickers() {
        val currency = _state.currency ?: CurrencyManager.getDefaultCurrency()
        mMoneyPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_MONEY_PICKER, currency, 0L)
        mCategoryPicker = CategoryPicker.createPicker(supportFragmentManager, TAG_CATEGORY_PICKER, null as Category?)
        mDateTimePicker = DateTimePicker.createPicker(supportFragmentManager, TAG_DATETIME_PICKER, Date())
        mWalletPicker = WalletPicker.createPicker(supportFragmentManager, TAG_WALLET_PICKER, null as Wallet?)
        mEventPicker = EventPicker.createPicker(supportFragmentManager, TAG_EVENT_PICKER, null as Event?)
        mPlacePicker = PlacePicker.createPicker(supportFragmentManager, TAG_PLACE_PICKER, null as Place?)
        mPersonPicker = PersonPicker.createPicker(supportFragmentManager, TAG_PERSON_PICKER, emptyArray<Person>())
        mAttachmentPicker = AttachmentPicker.createPicker(supportFragmentManager, TAG_ATTACHMENT_PICKER, ArrayList<Attachment>())
    }

    override fun onMoneyChanged(tag: String?, currency: CurrencyUnit?, money: Long) {
        _state = _state.copy(money = money, currency = currency)
    }

    override fun onCategoryChanged(tag: String?, category: Category?) {
        _state = _state.copy(category = category)
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        _state = _state.copy(date = date)
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        _state = _state.copy(wallet = wallet)
    }

    override fun onEventChanged(tag: String?, event: Event?) {
        _state = _state.copy(event = event)
    }

    override fun onPlaceChanged(tag: String?, place: Place?) {
        _state = _state.copy(place = place)
    }

    override fun onPeopleChanged(tag: String?, people: Array<out Person>?) {
        _state = _state.copy(people = people?.toList() ?: emptyList())
    }

    override fun onAttachmentListChanged(attachments: MutableList<Attachment>?) {
        _state = _state.copy(attachments = attachments?.toList() ?: emptyList())
    }

    private fun saveTransaction() {
        val itemId = intent.getLongExtra(NewEditItemActivity.ID, -1L)
        saveTransactionData(_state, itemId)
    }
}
