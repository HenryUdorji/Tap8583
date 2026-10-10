package com.tap8583.server.models

import com.tap8583.tap8583.decoder.*
import com.tap8583.tap8583.ingestion.ChannelErrorEvent
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

/** A timeout, disconnect, failed connect or exception reported by a terminal's jPOS channel. */
@Serializable
data class ChannelEventDto(
    val id: String,
    val serial: String? = null,
    val realm: String,
    val capturedAt: String,
    val tag: String,
    val kind: String,
    val message: String
)

@Serializable
data class WebSocketMessage(
    val type: String, // "INIT_SNAPSHOT", "TRANSACTION_UPDATED", "CHANNEL_EVENT", "DEVICE_LIST", "CLEAR"
    val transaction: DecodedTransactionDto? = null,
    val transactions: List<DecodedTransactionDto>? = null,
    val channelEvent: ChannelEventDto? = null,
    val channelEvents: List<ChannelEventDto>? = null,
    val devices: List<DeviceSummaryDto>? = null,
    /** For CLEAR: the device that was cleared, or null when all devices were. */
    val serial: String? = null
)

@Serializable
data class IngestResponse(
    val status: String,
    val eventsIngested: Int
)

/** Result of decoding a pasted or uploaded log. Nothing is stored or broadcast. */
@Serializable
data class DecodeLogResponse(
    /** In log order, oldest first. */
    val transactions: List<DecodedTransactionDto>,
    val channelEvents: List<ChannelEventDto>,
    val eventsFound: Int,
    /** Blocks that looked like events but could not be parsed (first few reasons). */
    val errors: List<String> = emptyList(),
    /** True when the log had more events than the decode limit. */
    val truncated: Boolean = false
)

/** First frame a dashboard client must send on /ws/stream. */
@Serializable
data class WsAuthMessage(
    val type: String,
    /** Shared-key mode: the viewer key. */
    val apiKey: String? = null,
    /** Accounts mode: the workspace to watch (the session cookie identifies the person). */
    val workspace: String? = null,
    val serial: String? = null
)

// ---------------------------------------------------------------------------
// Model to DTO Converters
// ---------------------------------------------------------------------------

fun ChannelErrorEvent.toDto(): ChannelEventDto = ChannelEventDto(
    id = id,
    serial = serial,
    realm = realm,
    capturedAt = capturedAt.toString(),
    tag = tag,
    kind = kind,
    message = message
)

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

// ---------------------------------------------------------------------------
// Accounts and workspaces
// ---------------------------------------------------------------------------

/** Tells the dashboard how to sign in: "accounts" (sign-in + workspaces) or "key" (shared viewer key). */
@Serializable
data class AuthConfigDto(
    val mode: String,
    /** False when accounts mode has no way to send email yet (SMTP not set up). */
    val emailSignIn: Boolean
)

@Serializable
data class UserDto(val id: String, val email: String, val name: String? = null, val avatarUrl: String? = null)

@Serializable
data class WorkspaceDto(val id: String, val name: String, val role: String)

@Serializable
data class MeDto(val user: UserDto, val workspaces: List<WorkspaceDto>)

@Serializable
data class MemberDto(
    val userId: String,
    val email: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val role: String,
    val joinedAt: String
)

@Serializable
data class InviteDto(val id: String, val email: String, val createdAt: String, val expiresAt: String)

@Serializable
data class MembersDto(val members: List<MemberDto>, val invites: List<InviteDto>, val canEmail: Boolean)

@Serializable
data class CreatedInviteDto(val invite: InviteDto, val link: String, val emailed: Boolean)

@Serializable
data class InviteDetailsDto(val workspaceName: String, val email: String)

@Serializable
data class IngestKeyDto(
    val id: String,
    val name: String,
    val prefix: String,
    val createdAt: String,
    val lastUsedAt: String? = null,
    val revokedAt: String? = null
)

/** The raw key is returned only here, once. */
@Serializable
data class CreatedIngestKeyDto(val key: IngestKeyDto, val value: String)

@Serializable data class NameRequest(val name: String)
@Serializable data class EmailRequest(val email: String, val next: String? = null)
@Serializable data class TokenRequest(val token: String)
@Serializable data class ErrorDto(val error: String)
