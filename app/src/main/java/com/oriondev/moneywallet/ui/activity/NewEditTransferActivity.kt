@file:Suppress(
    "TooManyFunctions",
    "WildcardImport",
    "MaxLineLength",
    "UnusedPrivateProperty",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MagicNumber"
)

package com.oriondev.moneywallet.ui.activity

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.*
import com.oriondev.moneywallet.picker.*
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.DateUtils
import java.util.ArrayList
import java.util.Date

class NewEditTransferActivity : AppCompatActivity(),
    MoneyPicker.Controller,
    DateTimePicker.Controller,
    WalletPicker.SingleWalletController,
    EventPicker.Controller,
    PersonPicker.Controller,
    PlacePicker.Controller,
    AttachmentPicker.Controller {

    companion object {
        const val TYPE = "NewEditTransferActivity::Type"
        const val MODEL_ID = "NewEditTransferActivity::ModelId"

        const val TYPE_STANDARD = 0
        const val TYPE_MODEL = 1
        const val AUTO_OPEN_CALCULATOR = "auto_open_calculator"

        private const val TAG_MONEY_PICKER = "NewEditTransferModelActivity::Tag::MoneyPicker"
        private const val TAG_DATETIME_PICKER = "NewEditTransferModelActivity::Tag::DateTimePicker"
        private const val TAG_WALLET_FROM_PICKER = "NewEditTransferModelActivity::Tag::WalletFromPicker"
        private const val TAG_WALLET_TO_PICKER = "NewEditTransferModelActivity::Tag::WalletToPicker"
        private const val TAG_TAX_PICKER = "NewEditTransferModelActivity::Tag::TaxPicker"
        private const val TAG_EVENT_PICKER = "NewEditTransferModelActivity::Tag::EventPicker"
        private const val TAG_PLACE_PICKER = "NewEditTransferModelActivity::Tag::PlacePicker"
        private const val TAG_PERSON_PICKER = "NewEditTransferModelActivity::Tag::PersonPicker"
        private const val TAG_ATTACHMENT_PICKER = "NewEditTransferModelActivity::Tag::AttachmentPicker"

        @JvmStatic
        fun insertTransferFromModel(context: Context, modelId: Long): Uri? {
            val contentResolver = context.contentResolver
            val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFER_MODELS, modelId)
            val projection = arrayOf(
                Contract.TransferModel.DESCRIPTION,
                Contract.TransferModel.WALLET_FROM_ID,
                Contract.TransferModel.WALLET_TO_ID,
                Contract.TransferModel.MONEY_FROM,
                Contract.TransferModel.MONEY_TO,
                Contract.TransferModel.MONEY_TAX,
                Contract.TransferModel.NOTE,
                Contract.TransferModel.EVENT_ID,
                Contract.TransferModel.PLACE_ID,
                Contract.TransferModel.CONFIRMED,
                Contract.TransferModel.COUNT_IN_TOTAL
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val builder = TransferContentValuesBuilder()
                        .description(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.DESCRIPTION)))
                        .date(DateUtils.getSQLDateTimeString(Date()))
                        .fromWalletId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_ID)))
                        .toWalletId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_TO_ID)))
                        .taxWalletId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.WALLET_FROM_ID)))
                        .fromMoney(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_FROM)))
                        .toMoney(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_TO)))
                        .taxMoney(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.MONEY_TAX)))
                        .note(cursor.getString(cursor.getColumnIndexOrThrow(Contract.TransferModel.NOTE)))
                        .confirmed(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransferModel.CONFIRMED)))
                        .countInTotal(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.TransferModel.COUNT_IN_TOTAL)))

                    if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ID))) {
                        builder.placeId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.PLACE_ID)))
                    }
                    if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_ID))) {
                        builder.eventId(cursor.getLong(cursor.getColumnIndexOrThrow(Contract.TransferModel.EVENT_ID)))
                    }

                    return contentResolver.insert(DataContentProvider.CONTENT_TRANSFERS, builder.build())
                }
            }
            return null
        }
    }

    private var _state by mutableStateOf(TransferScreenState())

    private lateinit var mMoneyPicker: MoneyPicker
    private lateinit var mTaxPicker: MoneyPicker
    private lateinit var mDateTimePicker: DateTimePicker
    private lateinit var mWalletFromPicker: WalletPicker
    private lateinit var mWalletToPicker: WalletPicker
    private lateinit var mEventPicker: EventPicker
    private lateinit var mPlacePicker: PlacePicker
    private lateinit var mPersonPicker: PersonPicker
    private lateinit var mAttachmentPicker: AttachmentPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setupPickers()

        if (intent.getBooleanExtra(AUTO_OPEN_CALCULATOR, false)) {
            intent.removeExtra(AUTO_OPEN_CALCULATOR)
            supportFragmentManager.executePendingTransactions()
            mMoneyPicker.showPicker()
        }

        val mode = intent.getSerializableExtra(NewEditItemActivity.MODE) as? NewEditItemActivity.Mode ?: NewEditItemActivity.Mode.NEW_ITEM
        val itemId = intent.getLongExtra(NewEditItemActivity.ID, -1L)

        loadTransferData(mode, itemId) { loadedState ->
            _state = loadedState
            mMoneyPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_MONEY_PICKER, _state.currency, _state.money)
            mTaxPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_TAX_PICKER, _state.currency, _state.tax)
        }

        setContent {
            NewEditTransferScreen(
                state = _state,
                onBackClick = { finish() },
                onSaveClick = { saveTransfer() },
                onMoneyClick = { mMoneyPicker.showPicker() },
                onTaxClick = { mTaxPicker.showPicker() },
                onTaxClear = { _state = _state.copy(tax = 0L) },
                onDateChange = { _state = _state.copy(date = it) },
                onWalletFromClick = { mWalletFromPicker.showSingleWalletPicker() },
                onWalletFromSelect = { selectedWallet ->
                    _state = _state.copy(
                        walletFrom = selectedWallet,
                        currency = selectedWallet.currency ?: _state.currency
                    )
                    mMoneyPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_MONEY_PICKER, _state.currency, _state.money)
                    mTaxPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_TAX_PICKER, _state.currency, _state.tax)
                },
                onWalletToClick = { mWalletToPicker.showSingleWalletPicker() },
                onWalletToSelect = { selectedWallet ->
                    _state = _state.copy(walletTo = selectedWallet)
                },
                onSwapWallets = {
                    val newFrom = _state.walletTo
                    val newTo = _state.walletFrom
                    _state = _state.copy(
                        walletFrom = newFrom,
                        walletTo = newTo,
                        currency = newFrom?.currency ?: _state.currency
                    )
                    mMoneyPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_MONEY_PICKER, _state.currency, _state.money)
                    mTaxPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_TAX_PICKER, _state.currency, _state.tax)
                },
                onEventClick = { mEventPicker.showPicker(_state.date) },
                onEventClear = { _state = _state.copy(event = null) },
                onPeopleClick = { mPersonPicker.showPicker() },
                onPeopleClear = { _state = _state.copy(people = emptyList()) },
                onPlaceClick = { mPlacePicker.showPicker() },
                onPlaceClear = { _state = _state.copy(place = null) },
                onAttachmentClick = { mAttachmentPicker.showPicker() },
                onAttachmentOpen = { attachment ->
                    try {
                        startActivity(attachment.getActionViewIntent(this@NewEditTransferActivity))
                    } catch (e: ActivityNotFoundException) {
                        ThemedDialog.buildMaterialDialog(this@NewEditTransferActivity)
                            .setTitle(R.string.title_error)
                            .setMessage(R.string.message_error_activity_not_found)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                },
                onAttachmentDelete = {
                    val newAttachments = _state.attachments.toMutableList()
                    newAttachments.remove(it)
                    _state = _state.copy(attachments = newAttachments)
                    mAttachmentPicker.remove(it)
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
        mTaxPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_TAX_PICKER, currency, 0L)
        mDateTimePicker = DateTimePicker.createPicker(supportFragmentManager, TAG_DATETIME_PICKER, Date())
        mWalletFromPicker = WalletPicker.createPicker(supportFragmentManager, TAG_WALLET_FROM_PICKER, null as Wallet?)
        mWalletToPicker = WalletPicker.createPicker(supportFragmentManager, TAG_WALLET_TO_PICKER, null as Wallet?)
        mEventPicker = EventPicker.createPicker(supportFragmentManager, TAG_EVENT_PICKER, null as Event?)
        mPlacePicker = PlacePicker.createPicker(supportFragmentManager, TAG_PLACE_PICKER, null as Place?)
        mPersonPicker = PersonPicker.createPicker(supportFragmentManager, TAG_PERSON_PICKER, emptyArray<Person>())
        mAttachmentPicker = AttachmentPicker.createPicker(supportFragmentManager, TAG_ATTACHMENT_PICKER, ArrayList<Attachment>())
    }

    override fun onMoneyChanged(tag: String?, currency: CurrencyUnit?, money: Long) {
        when (tag) {
            TAG_MONEY_PICKER -> _state = _state.copy(money = money, currency = currency ?: _state.currency)
            TAG_TAX_PICKER -> _state = _state.copy(tax = money)
        }
    }

    override fun onDateTimeChanged(tag: String?, date: Date?) {
        _state = _state.copy(date = date)
    }

    override fun onWalletChanged(tag: String?, wallet: Wallet?) {
        when (tag) {
            TAG_WALLET_FROM_PICKER -> {
                _state = _state.copy(
                    walletFrom = wallet,
                    currency = wallet?.currency ?: _state.currency
                )
                mMoneyPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_MONEY_PICKER, _state.currency, _state.money)
                mTaxPicker = MoneyPicker.createPicker(supportFragmentManager, TAG_TAX_PICKER, _state.currency, _state.tax)
            }
            TAG_WALLET_TO_PICKER -> {
                _state = _state.copy(walletTo = wallet)
            }
        }
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

    private fun saveTransfer() {
        val itemId = intent.getLongExtra(NewEditItemActivity.ID, -1L)
        saveTransferData(_state, itemId)
    }

    fun cleanUpAttachments() {
        if (::mAttachmentPicker.isInitialized) {
            mAttachmentPicker.cleanUp(false)
        }
    }

    fun finishSaveDatedAt(date: Date?) {
        if (date != null && date.after(Date())) {
            android.widget.Toast.makeText(this, R.string.message_saved_dated_ahead, android.widget.Toast.LENGTH_LONG).show()
        }
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        cleanUpAttachments()
    }

    // Exposed for backward compatibility with existing test suites
    fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_save_changes) {
            saveTransfer()
            return true
        }
        return false
    }
}
