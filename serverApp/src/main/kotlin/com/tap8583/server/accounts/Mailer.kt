package com.tap8583.server.accounts

import com.tap8583.server.SmtpConfig
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import java.util.Properties

/** Sends sign-in links and invites. */
interface Mailer {
    /** False when email isn't set up; callers then offer a copyable link instead. */
    val canSend: Boolean
    suspend fun send(to: String, subject: String, text: String)
}

class SmtpMailer(private val config: SmtpConfig) : Mailer {
    override val canSend = true

    private val session: Session = Session.getInstance(Properties().apply {
        put("mail.smtp.host", config.host)
        put("mail.smtp.port", config.port.toString())
        put("mail.smtp.auth", (config.user != null).toString())
        // 465 is implicit TLS; anything else upgrades with STARTTLS.
        if (config.port == 465) put("mail.smtp.ssl.enable", "true") else put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.connectiontimeout", "10000")
        put("mail.smtp.timeout", "10000")
    })

    override suspend fun send(to: String, subject: String, text: String) = withContext(Dispatchers.IO) {
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.from))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            setSubject(subject, "UTF-8")
            setText(text, "UTF-8")
        }
        if (config.user != null) {
            Transport.send(message, config.user, config.password ?: "")
        } else {
            Transport.send(message)
        }
    }
}

/** Writes emails to the log. Only for local development (TAP8583_LOG_EMAIL_LINKS=true). */
class LogMailer(private val log: Logger) : Mailer {
    override val canSend = true
    override suspend fun send(to: String, subject: String, text: String) {
        log.info("Email to {} — {}\n{}", to, subject, text)
    }
}

object NoMailer : Mailer {
    override val canSend = false
    override suspend fun send(to: String, subject: String, text: String) =
        throw IllegalStateException("Email is not configured")
}
