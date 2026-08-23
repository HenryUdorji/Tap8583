export interface DecodedTransactionDto {
  id: string;
  stan: string;
  serial?: string | null;
  status: "PENDING" | "APPROVED" | "DECLINED" | "TIMEOUT" | "NETWORK_ERROR" | "ORPHAN";
  rttMs?: number | null;
  request?: DecodedPacketDto | null;
  response?: DecodedPacketDto | null;
  createdAt?: string | null;
}

export interface DecodedPacketDto {
  id: string;
  realm: string;
  capturedAt: string;
  lifespanMs: number;
  direction: "OUTGOING" | "INCOMING";
  serial?: string | null;
  mti: MtiDto;
  fields: DecodedFieldDto[];
  parseErrors: FieldParseErrorDto[];
  rawXml: string;
}

export interface MtiDto {
  raw: string;
  version: string;
  messageClass: string;
  messageFunction: string;
  messageOrigin: string;
  label: string;
}

export interface DecodedFieldDto {
  number: number;
  name: string;
  shortName: string;
  dataType: string;
  isoType: string;
  category:
    | "CARD_DATA"
    | "AMOUNT"
    | "DATETIME"
    | "IDENTIFICATION"
    | "RESPONSE"
    | "POS_TERMINAL"
    | "EMV"
    | "INSTITUTION"
    | "SECURITY"
    | "PRIVATE_NIBSS"
    | "NETWORK"
    | "GENERAL";
  rawValue: string;
  isBinary: boolean;
  decodedValue?: string | null;
  subFields?: SubFieldDto[] | null;
  tlvEntries?: TlvEntryDto[] | null;
  isSensitive: boolean;
  isCurrentlyMasked: boolean;
}

export interface SubFieldDto {
  name: string;
  rawValue: string;
  decodedValue: string;
}

export interface TlvEntryDto {
  tag: string;
  name?: string | null;
  length: number;
  valueHex: string;
  decoded?: string | null;
  children?: TlvEntryDto[] | null;
  isConstructed: boolean;
}

export interface FieldParseErrorDto {
  fieldNumber: number;
  message: string;
}

export interface DeviceSummaryDto {
  serial: string;
  lastSeenAt: string;
  transactionCount: number;
}

export interface WebSocketMessage {
  type: "INIT_SNAPSHOT" | "TRANSACTION_UPDATED" | "DEVICE_LIST" | "CLEAR";
  transaction?: DecodedTransactionDto;
  transactions?: DecodedTransactionDto[];
  devices?: DeviceSummaryDto[];
}
