package com.tap8583.server.accounts

/** An email with an HTML body and a plain-text alternative for clients that don't show HTML. */
data class Email(val subject: String, val text: String, val html: String)

/**
 * Sign-in and invite emails. The HTML uses tables and inline styles only,
 * which is what Gmail, Outlook and Apple Mail render reliably.
 */
object EmailTemplates {

    fun signIn(link: String): Email = Email(
        subject = "Your Tap8583 sign-in link",
        text = "Open this link to sign in to Tap8583. It works once and expires in 15 minutes.\n\n$link\n\n" +
            "If you didn't ask for this, you can ignore this email.",
        html = layout(
            preheader = "Your sign-in link. It works once and expires in 15 minutes.",
            heading = "Sign in to Tap8583",
            body = "Click the button below to sign in. The link works once and expires in 15 minutes.",
            button = "Sign in" to link,
            footnote = "If you didn't ask for this, you can ignore this email. Nobody can sign in without the link."
        )
    )

    fun invite(inviter: String, workspaceName: String, link: String): Email = Email(
        subject = "$inviter invited you to $workspaceName on Tap8583",
        text = "$inviter invited you to the \"$workspaceName\" workspace on Tap8583, " +
            "where your team watches POS terminal transactions live.\n\nAccept the invite:\n$link\n\n" +
            "The link expires in 7 days.",
        html = layout(
            preheader = "Join $workspaceName and watch its POS transactions live.",
            heading = "Join ${escape(workspaceName)}",
            body = "<strong style=\"color:#0f172a;\">${escape(inviter)}</strong> invited you to the " +
                "<strong style=\"color:#0f172a;\">${escape(workspaceName)}</strong> workspace on Tap8583, " +
                "where your team watches POS terminal transactions live.",
            button = "Accept invite" to link,
            footnote = "The invite expires in 7 days. If you weren't expecting it, you can ignore this email."
        )
    )

    fun escape(value: String): String = buildString(value.length) {
        for (c in value) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    private const val FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"

    /** [body] may contain markup; everything else is escaped here. */
    private fun layout(preheader: String, heading: String, body: String, button: Pair<String, String>, footnote: String): String {
        val (label, url) = button
        val href = escape(url)
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light">
<meta name="supported-color-schemes" content="light">
<title>${escape(label)}</title>
</head>
<body style="margin:0;padding:0;background-color:#f1f5f9;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">${escape(preheader)}</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f1f5f9;">
  <tr>
    <td align="center" style="padding:40px 16px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:480px;">
        <tr>
          <td style="padding:0 4px 20px 4px;font-family:$FONT;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td width="32" height="32" align="center" valign="middle" style="width:32px;height:32px;border-radius:8px;background-color:#0ea5e9;background-image:linear-gradient(135deg,#0ea5e9,#4f46e5);color:#ffffff;font-size:15px;font-weight:700;font-family:$FONT;">T</td>
                <td style="padding-left:10px;font-size:16px;font-weight:700;color:#0f172a;font-family:$FONT;">Tap8583</td>
              </tr>
            </table>
          </td>
        </tr>
        <tr>
          <td style="background-color:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:32px;font-family:$FONT;">
            <h1 style="margin:0 0 12px 0;font-size:22px;line-height:1.3;font-weight:700;color:#0f172a;">$heading</h1>
            <p style="margin:0 0 24px 0;font-size:15px;line-height:1.6;color:#475569;">$body</p>
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td align="center" style="border-radius:10px;background-color:#0ea5e9;">
                  <a href="$href" target="_blank" style="display:inline-block;padding:13px 28px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:10px;font-family:$FONT;">${escape(label)}</a>
                </td>
              </tr>
            </table>
            <p style="margin:28px 0 6px 0;font-size:13px;line-height:1.5;color:#64748b;">If the button doesn't work, paste this link into your browser:</p>
            <p style="margin:0;font-size:12px;line-height:1.5;word-break:break-all;"><a href="$href" target="_blank" style="color:#0284c7;text-decoration:underline;">$href</a></p>
          </td>
        </tr>
        <tr>
          <td style="padding:20px 8px 0 8px;font-family:$FONT;font-size:12px;line-height:1.6;color:#94a3b8;text-align:center;">
            ${escape(footnote)}<br>Tap8583 &middot; live ISO 8583 transaction viewer
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</body>
</html>
""".trimIndent()
    }
}
