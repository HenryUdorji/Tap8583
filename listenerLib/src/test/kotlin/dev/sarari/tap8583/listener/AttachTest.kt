package dev.sarari.tap8583.listener

import com.sun.net.httpserver.HttpServer
import org.jpos.iso.ISOMsg
import org.jpos.iso.channel.ASCIIChannel
import org.jpos.iso.packager.ISO87APackager
import org.jpos.util.Logger
import org.jpos.util.NameRegistrar
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AttachTest {
    private lateinit var ingest: HttpServer
    private val received = LinkedBlockingQueue<String>()
    private lateinit var host: ServerSocket

    @BeforeTest
    fun start() {
        ingest = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/api/v1/events") { ex ->
                received += ex.requestBody.readBytes().decodeToString()
                ex.sendResponseHeaders(202, -1)
                ex.close()
            }
            start()
        }
        // Accepts the channel's connection and swallows what it sends.
        host = ServerSocket(0)
        thread(isDaemon = true) {
            runCatching { while (true) host.accept().getInputStream().let { s -> thread(isDaemon = true) { runCatching { s.readBytes() } } } }
        }
    }

    @AfterTest
    fun stop() {
        ingest.stop(0)
        host.close()
    }

    private fun endpoint() = "http://127.0.0.1:${ingest.address.port}/api/v1/events"

    private fun sendThrough(channel: ASCIIChannel) {
        channel.connect()
        channel.send(ISOMsg("0200").apply {
            set(3, "000000")
            set(4, "000000001000")
            set(11, "123456")
        })
        channel.disconnect()
    }

    @Test
    fun `attach gives a channel without a logger one and sends its messages`() {
        val channel = ASCIIChannel("127.0.0.1", host.localPort, ISO87APackager())
        assertNull(channel.logger)

        VisualizerLogListener(apiKey = "k", endpoint = endpoint(), deviceSerial = "T1").attach(channel)
        assertNotNull(channel.logger)
        sendThrough(channel)

        val body = received.poll(5, TimeUnit.SECONDS)
        assertNotNull(body, "nothing reached the ingest endpoint")
        assertTrue(body.contains("tag=\"send\""), body)
        assertTrue(body.contains("123456"), body)
    }

    @Test
    fun `attach reuses the logger a channel already has`() {
        val logger = Logger().apply { setName("app-logger") }
        val channel = ASCIIChannel("127.0.0.1", host.localPort, ISO87APackager())
        channel.setLogger(logger, "pos-channel")

        val listener = VisualizerLogListener(apiKey = "k", endpoint = endpoint(), deviceSerial = "T1")
        listener.attach(channel)
        listener.attach(channel)
        assertSame(logger, channel.logger)

        sendThrough(channel)
        val body = received.poll(5, TimeUnit.SECONDS)
        assertNotNull(body)
        assertTrue(body.contains("realm=\"pos-channel"), body)
        // Attaching twice must not send every event twice.
        assertNull(received.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun `install does not create a Q2 logger`() {
        VisualizerLogListener.install(apiKey = "k", endpoint = endpoint(), deviceSerial = "T1")
        assertNull(NameRegistrar.getIfExists<Logger>(Logger.NRPREFIX + "Q2"))
    }
}
