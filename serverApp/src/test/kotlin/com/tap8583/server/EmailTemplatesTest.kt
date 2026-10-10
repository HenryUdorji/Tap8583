package com.tap8583.server

import com.tap8583.server.accounts.EmailTemplates
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailTemplatesTest {

    @Test
    fun `invite escapes names people choose`() {
        val email = EmailTemplates.invite("<b>Ada</b>", "Acme & Co", "https://tap.example.com/?invite=abc&x=1")
        assertFalse("<b>Ada</b>" in email.html)
        assertTrue("&lt;b&gt;Ada&lt;/b&gt;" in email.html)
        assertTrue("Acme &amp; Co" in email.html)
        assertTrue("href=\"https://tap.example.com/?invite=abc&amp;x=1\"" in email.html)
        assertTrue("https://tap.example.com/?invite=abc&x=1" in email.text)
    }

    @Test
    fun `sign-in email has the link in both parts`() {
        val email = EmailTemplates.signIn("https://tap.example.com/?emailToken=t")
        assertTrue("emailToken=t" in email.text)
        assertTrue("emailToken=t" in email.html)
    }

    /** Set TAP8583_EMAIL_PREVIEW_DIR to write the emails out as HTML files to look at. */
    @Test
    fun `write previews when asked`() {
        val dir = System.getenv("TAP8583_EMAIL_PREVIEW_DIR") ?: return
        java.io.File(dir).mkdirs()
        java.io.File(dir, "sign-in.html").writeText(EmailTemplates.signIn("https://tap8583.example.com/?emailToken=t8_example").html)
        java.io.File(dir, "invite.html").writeText(
            EmailTemplates.invite("Ada Obi", "Acme POS QA", "https://tap8583.example.com/?invite=example").html
        )
    }
}
