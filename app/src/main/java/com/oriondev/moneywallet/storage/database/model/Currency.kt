package com.oriondev.moneywallet.storage.database.model

/**
 * This class acts as a Contract between the Schema of the SQLDatabase and the structure
 * of the exported item.
 */
class Currency : BaseItem() {
    @JvmField
    var mIso: String? = null

    @JvmField
    var mName: String? = null

    @JvmField
    var mSymbol: String? = null

    @JvmField
    var mDecimals: Int = 0

    @JvmField
    var mFavourite: Boolean = false
}
