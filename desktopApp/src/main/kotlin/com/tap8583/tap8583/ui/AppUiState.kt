package com.tap8583.tap8583.ui

import com.tap8583.tap8583.AppEngine
import com.tap8583.tap8583.SessionStats
import com.tap8583.tap8583.decoder.DecodedField
import com.tap8583.tap8583.decoder.DecodedPacket
import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.decoder.FieldCategory
import com.tap8583.tap8583.ingestion.ServerStatus

/** Which tab is active in the centre packet-inspector panel */
enum class CenterTab { REQUEST, RESPONSE, RAW }

/** Quick-filter options shown in the toolbar chip bar */
enum class MessageFilter(val label: String) {
    ALL("All"),
    FINANCIAL("Financial"),
    AUTH("Auth"),
    REVERSAL("Reversal"),
    NETWORK("Network"),
    DECLINED("Declined"),
}

/** Individual toast message shown in the overlay */
data class ToastMessage(
    val id: String,
    val message: String,
    val isError: Boolean = false,
)

/** Top-level UI state consumed by the root composable */
data class AppUiState(
    val serverStatus: ServerStatus             = ServerStatus.IDLE,
    val port: Int                              = 9999,
    val connectionCount: Int                   = 0,
    val transactions: List<DecodedTransaction> = emptyList(),
    val selectedTransactionId: String?         = null,
    val selectedFieldNumber: Int?              = null,
    val centerTab: CenterTab                   = CenterTab.REQUEST,
    val searchQuery: String                    = "",
    val messageFilter: MessageFilter           = MessageFilter.ALL,
    val stats: SessionStats                    = SessionStats(),
    val toasts: List<ToastMessage>             = emptyList(),
    /** Non-loopback IPv4 addresses of this machine — shown in the status bar */
    val localIpAddresses: List<String>         = emptyList(),
) {
    /** The selected transaction, null if nothing is selected */
    val selectedTransaction: DecodedTransaction?
        get() = transactions.find { it.id == selectedTransactionId }

    /** The packet that should be shown in the centre panel */
    val activePacket: DecodedPacket?
        get() = when (centerTab) {
            CenterTab.REQUEST  -> selectedTransaction?.request
            CenterTab.RESPONSE -> selectedTransaction?.response
            CenterTab.RAW      -> selectedTransaction?.request   // raw XML always from request
        }

    /** The selected DecodedField (for the right-panel detail view) */
    val selectedField: DecodedField?
        get() = activePacket?.fields?.find { it.number == selectedFieldNumber }
}

// ---------------------------------------------------------------------------
// Field-category → accent colour mapping (used by FieldRow)
// ---------------------------------------------------------------------------

fun FieldCategory.accentColor() = when (this) {
    FieldCategory.CARD_DATA       -> AppColors.CatCardData
    FieldCategory.AMOUNT          -> AppColors.CatAmount
    FieldCategory.DATETIME        -> AppColors.CatDatetime
    FieldCategory.IDENTIFICATION  -> AppColors.CatIdentification
    FieldCategory.RESPONSE        -> AppColors.CatResponse
    FieldCategory.POS_TERMINAL    -> AppColors.CatPosTerminal
    FieldCategory.EMV             -> AppColors.CatEmv
    FieldCategory.INSTITUTION     -> AppColors.CatInstitution
    FieldCategory.SECURITY        -> AppColors.CatSecurity
    FieldCategory.PRIVATE_NIBSS   -> AppColors.CatPrivateNibss
    FieldCategory.NETWORK         -> AppColors.CatNetwork
    FieldCategory.GENERAL         -> AppColors.CatGeneral
}
