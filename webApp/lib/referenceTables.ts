export interface CodeEntry {
  code: string;
  name: string;
  description?: string;
  severity?: "success" | "warning" | "danger" | "neutral";
}

export const RESPONSE_CODES: Record<string, CodeEntry> = {
  "00": { code: "00", name: "Approved", description: "Transaction approved by card issuer.", severity: "success" },
  "01": { code: "01", name: "Refer to Card Issuer", description: "Issuer requires voice authorization or customer call.", severity: "warning" },
  "02": { code: "02", name: "Refer to Issuer, Special", description: "Special condition referral.", severity: "warning" },
  "03": { code: "03", name: "Invalid Merchant", description: "Merchant ID not recognized by host.", severity: "danger" },
  "04": { code: "04", name: "Pick-Up Card", description: "Lost or stolen card flagged by issuer.", severity: "danger" },
  "05": { code: "05", name: "Do Not Honour", description: "Issuer generic decline.", severity: "danger" },
  "06": { code: "06", name: "Error", description: "Generic processing error.", severity: "danger" },
  "07": { code: "07", name: "Pick-Up Card, Special", description: "Fraud condition suspected.", severity: "danger" },
  "12": { code: "12", name: "Invalid Transaction", description: "Transaction not permitted or invalid combination.", severity: "danger" },
  "13": { code: "13", name: "Invalid Amount", description: "Amount is zero or exceeds maximum allowed.", severity: "danger" },
  "14": { code: "14", name: "Invalid Card Number", description: "No such card number or Luhn check failed.", severity: "danger" },
  "15": { code: "15", name: "No Such Issuer", description: "Routing institution / BIN not found.", severity: "danger" },
  "19": { code: "19", name: "Re-enter Transaction", description: "System glitch; retry transaction.", severity: "warning" },
  "25": { code: "25", name: "Unable to Locate Record", description: "Record not found on file.", severity: "danger" },
  "30": { code: "30", name: "Format Error", description: "Switch rejected message syntax.", severity: "danger" },
  "41": { code: "41", name: "Lost Card", description: "Card reported lost.", severity: "danger" },
  "43": { code: "43", name: "Stolen Card", description: "Card reported stolen.", severity: "danger" },
  "51": { code: "51", name: "Insufficient Funds", description: "Customer balance is lower than transaction amount.", severity: "danger" },
  "54": { code: "54", name: "Expired Card", description: "Card expiry date has passed.", severity: "danger" },
  "55": { code: "55", name: "Incorrect PIN", description: "Customer entered wrong PIN.", severity: "danger" },
  "57": { code: "57", name: "Txn Not Permitted to Cardholder", description: "Card restriction on this transaction type.", severity: "danger" },
  "58": { code: "58", name: "Txn Not Permitted to Terminal", description: "Terminal not authorized for this transaction.", severity: "danger" },
  "61": { code: "61", name: "Exceeds Withdrawal Amount Limit", description: "Daily limit exceeded.", severity: "danger" },
  "62": { code: "62", name: "Restricted Card", description: "Card blocked by issuer.", severity: "danger" },
  "65": { code: "65", name: "Exceeds Withdrawal Frequency Limit", description: "Count of transactions exceeded.", severity: "danger" },
  "68": { code: "68", name: "Response Received Too Late", description: "Switch or issuer timed out.", severity: "warning" },
  "75": { code: "75", name: "Allowable Number of PIN Tries Exceeded", description: "Card locked due to repeated PIN failures.", severity: "danger" },
  "91": { code: "91", name: "Issuer or Switch Inoperative", description: "Host or switch is down / unreachable.", severity: "danger" },
  "92": { code: "92", name: "Financial Institution Not Found", description: "Cannot route transaction.", severity: "danger" },
  "96": { code: "96", name: "System Malfunction", description: "Unexpected hardware or software failure.", severity: "danger" }
};

export const POS_ENTRY_MODES: Record<string, string> = {
  "01": "Manual (Key-entered)",
  "02": "Magnetic Stripe (Unspecified)",
  "05": "Integrated Circuit Card (ICC / Chip)",
  "07": "Contactless ICC (NFC / Tap)",
  "10": "Credential on File (COF)",
  "90": "Magnetic Stripe (Track 2 read)",
  "91": "Contactless Magnetic Stripe",
  "92": "Integrated Circuit Card (Fallback to Magstripe)"
};

export const MERCHANT_CATEGORY_CODES: Record<string, string> = {
  "5411": "Grocery Stores, Supermarkets",
  "5812": "Eating Places, Restaurants",
  "5814": "Fast Food Restaurants",
  "5999": "Miscellaneous and Specialty Retail",
  "6010": "Financial Institutions — Manual Cash Disbursements",
  "6011": "Financial Institutions — Automated Cash Disbursements (ATM)",
  "6012": "Financial Institutions — Merchandise and Services",
  "6051": "Quasi-Cash / Foreign Currency / Money Orders",
  "5541": "Service Stations (Gas / Fuel Stations)",
  "5311": "Department Stores",
  "5912": "Drug Stores and Pharmacies",
  "7011": "Hotels, Motels, and Resorts",
  "4111": "Transportation — Commuter, Ferries",
  "4814": "Telecommunication Services & Airtime",
  "4900": "Utilities — Electric, Gas, Water, Sanitary",
  "8099": "Medical Services & Health Practitioners"
};

export const CURRENCY_CODES: Record<string, { code: string; symbol: string; name: string; decimals: number }> = {
  "566": { code: "NGN", symbol: "₦", name: "Nigerian Naira", decimals: 2 },
  "840": { code: "USD", symbol: "$", name: "United States Dollar", decimals: 2 },
  "978": { code: "EUR", symbol: "€", name: "Euro", decimals: 2 },
  "826": { code: "GBP", symbol: "£", name: "Pound Sterling", decimals: 2 },
  "936": { code: "GHS", symbol: "GH₵", name: "Ghanaian Cedi", decimals: 2 },
  "404": { code: "KES", symbol: "KSh", name: "Kenyan Shilling", decimals: 2 },
  "710": { code: "ZAR", symbol: "R", name: "South African Rand", decimals: 2 }
};

export function getCategoryBadgeClass(category: string): { bg: string; text: string; border: string; bar: string } {
  switch (category) {
    case "CARD_DATA":
      return { bg: "bg-rose-500/10", text: "text-rose-400", border: "border-rose-500/30", bar: "bg-rose-500" };
    case "AMOUNT":
      return { bg: "bg-emerald-500/10", text: "text-emerald-400", border: "border-emerald-500/30", bar: "bg-emerald-500" };
    case "DATETIME":
      return { bg: "bg-sky-500/10", text: "text-sky-400", border: "border-sky-500/30", bar: "bg-sky-500" };
    case "IDENTIFICATION":
      return { bg: "bg-purple-500/10", text: "text-purple-400", border: "border-purple-500/30", bar: "bg-purple-500" };
    case "RESPONSE":
      return { bg: "bg-amber-500/10", text: "text-amber-400", border: "border-amber-500/30", bar: "bg-amber-500" };
    case "POS_TERMINAL":
      return { bg: "bg-teal-500/10", text: "text-teal-400", border: "border-teal-500/30", bar: "bg-teal-500" };
    case "EMV":
      return { bg: "bg-orange-500/10", text: "text-orange-400", border: "border-orange-500/30", bar: "bg-orange-500" };
    case "INSTITUTION":
      return { bg: "bg-indigo-500/10", text: "text-indigo-400", border: "border-indigo-500/30", bar: "bg-indigo-500" };
    case "SECURITY":
      return { bg: "bg-red-500/10", text: "text-red-400", border: "border-red-500/30", bar: "bg-red-500" };
    case "PRIVATE_NIBSS":
      return { bg: "bg-slate-500/10", text: "text-slate-400", border: "border-slate-500/30", bar: "bg-slate-500" };
    default:
      return { bg: "bg-zinc-500/10", text: "text-zinc-400", border: "border-zinc-500/30", bar: "bg-zinc-500" };
  }
}
