package com.tap8583.server.models

import com.tap8583.tap8583.decoder.*
import kotlinx.serialization.Serializable

@Serializable
data class DecodedTransactionDto(
    val id: String,
    val stan: String,
    val serial: String? = null,
    val status: String,
    val rttMs: Long? = null,
    val request: DecodedPacketDto? = null,
    val response: DecodedPacketDto? = null,
    val createdAt: String? = null
)

@Serializable
data class DecodedPacketDto(
    val id: String,
    val realm: String,
    val capturedAt: String,
    val lifespanMs: Long,
    val direction: String,
    val serial: String? = null,
    val mti: MtiDto,
    val fields: List<DecodedFieldDto>,
    val parseErrors: List<FieldParseErrorDto> = emptyList(),
    val rawXml: String
)

@Serializable
data class MtiDto(
    val raw: String,
    val version: String,
    val messageClass: String,
    val messageFunction: String,
    val messageOrigin: String,
    val label: String
)

@Serializable
data class DecodedFieldDto(
    val number: Int,
    val name: String,
    val shortName: String,
    val dataType: String,
    val isoType: String,
    val category: String,
    val rawValue: String,
    val isBinary: Boolean,
    val decodedValue: String? = null,
    val subFields: List<SubFieldDto>? = null,
    val tlvEntries: List<TlvEntryDto>? = null,
    val isSensitive: Boolean = false,
    val isCurrentlyMasked: Boolean = false
)

@Serializable
data class SubFieldDto(
    val name: String,
    val rawValue: String,
    val decodedValue: String
)

@Serializable
data class TlvEntryDto(
    val tag: String,
    val name: String? = null,
    val length: Int,
    val valueHex: String,
    val decoded: String? = null,
    val children: List<TlvEntryDto>? = null,
    val isConstructed: Boolean = false
)

@Serializable
data class FieldParseErrorDto(
    val fieldNumber: Int,
    val message: String
)

@Serializable
data class DeviceSummaryDto(
    val serial: String,
    val lastSeenAt: String,
    val transactionCount: Int
)

@Serializable
data class WebSocketMessage(
    val type: String, // "INIT_SNAPSHOT", "TRANSACTION_UPDATED", "DEVICE_LIST", "CLEAR"
    val transaction: DecodedTransactionDto? = null,
    val transactions: List<DecodedTransactionDto>? = null,
    val devices: List<DeviceSummaryDto>? = null,
    /** For CLEAR: the device that was cleared, or null when all devices were. */
    val serial: String? = null
)

@Serializable
data class IngestResponse(
    val status: String,
    val eventsIngested: Int
)

/** First frame a dashboard client must send on /ws/stream. */
@Serializable
data class WsAuthMessage(
    val type: String,
    val apiKey: String? = null,
    val serial: String? = null
)

// ---------------------------------------------------------------------------
// Model to DTO Converters
// ---------------------------------------------------------------------------

fun DecodedTransaction.toDto(): DecodedTransactionDto {
    val createdStr = request?.rawEvent?.capturedAt?.toString()
        ?: response?.rawEvent?.capturedAt?.toString()

    return DecodedTransactionDto(
        id = id,
        stan = stan,
        serial = serial,
        status = status.name,
        rttMs = rttMs,
        request = request?.toDto(),
        response = response?.toDto(),
        createdAt = createdStr
    )
}

fun DecodedPacket.toDto(): DecodedPacketDto {
    return DecodedPacketDto(
        id = rawEvent.id,
        realm = rawEvent.realm,
        capturedAt = rawEvent.capturedAt.toString(),
        lifespanMs = rawEvent.lifespanMs,
        direction = rawEvent.direction.name,
        serial = rawEvent.serial,
        mti = mti.toDto(),
        fields = fields.map { it.toDto() },
        parseErrors = parseErrors.map { FieldParseErrorDto(it.fieldNumber, it.message) },
        rawXml = rawEvent.rawXml
    )
}

fun MTI.toDto(): MtiDto {
    return MtiDto(
        raw = raw,
        version = version,
        messageClass = messageClass,
        messageFunction = messageFunction,
        messageOrigin = messageOrigin,
        label = label
    )
}

fun DecodedField.toDto(): DecodedFieldDto {
    return DecodedFieldDto(
        number = number,
        name = definition.name,
        shortName = definition.shortName,
        dataType = definition.dataType,
        isoType = definition.isoType,
        category = definition.category.name,
        rawValue = rawValue,
        isBinary = isBinary,
        decodedValue = decodedValue,
        subFields = subFields?.map { SubFieldDto(it.name, it.rawValue, it.decodedValue) },
        tlvEntries = tlvEntries?.map { it.toDto() },
        isSensitive = isSensitive,
        isCurrentlyMasked = isCurrentlyMasked
    )
}

fun TLVEntry.toDto(): TlvEntryDto {
    return TlvEntryDto(
        tag = tag,
        name = name,
        length = length,
        valueHex = valueHex,
        decoded = decoded,
        children = children?.map { it.toDto() },
        isConstructed = isConstructed
    )
}
