package com.tap8583.tap8583.ingestion

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Non-blocking TCP ingestion server.
 *
 * Listens on all network interfaces on [port] for connections from
 * VisualizerLogListener instances running on POS devices on the same network.
 *
 * Protocol:
 *  - Each event is a UTF-8 XML block beginning with <visualizer-event> and
 *    ending with a line containing exactly "---END---"
 *  - Events are parsed by [JposXmlParser] and emitted as [RawLogEvent] objects
 *  - Multiple simultaneous POS connections are supported; each runs in its own
 *    coroutine. Each connection is labelled with a session ID for the UI.
 *
 * Usage:
 * ```kotlin
 * val server = TcpIngestionServer(port = 9999, scope = viewModelScope)
 * server.start()
 * server.events.collect { event -> ... }
 * server.stop()
 * ```
 */
class TcpIngestionServer(
    val port: Int = DEFAULT_PORT,
    private val scope: CoroutineScope
) {

    companion object {
        const val DEFAULT_PORT = 9999
        const val END_DELIMITER = "---END---"
        /** Bind address — wildcard so the server accepts on every NIC (LAN, WiFi, USB RNDIS, etc.) */
        const val BIND_ALL = "0.0.0.0"
    }

    // -----------------------------------------------------------------------
    // Public state / event streams
    // -----------------------------------------------------------------------

    private val _status = MutableStateFlow(ServerStatus.IDLE)
    val status: StateFlow<ServerStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<RawLogEvent>(
        replay = 0,
        extraBufferCapacity = 256
    )
    val events: SharedFlow<RawLogEvent> = _events.asSharedFlow()

    /** Error messages for the status bar / toast system */
    private val _errors = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 32
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Number of currently active POS connections */
    private val _connectionCount = MutableStateFlow(0)
    val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    // -----------------------------------------------------------------------
    // Internal state
    // -----------------------------------------------------------------------

    @Volatile private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Binds to [port] on all network interfaces and starts accepting connections.
     * Safe to call from any thread/coroutine — actual work runs in [scope].
     *
     * No-op if already listening or connected.
     */
    fun start() {
        if (_status.value == ServerStatus.LISTENING || _status.value == ServerStatus.CONNECTED) return

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                // null address = bind to all interfaces (equivalent to 0.0.0.0)
                val ss = ServerSocket(port, /* backlog */ 10, null)
                serverSocket = ss
                _status.value = ServerStatus.LISTENING

                while (isActive) {
                    val client: Socket = try {
                        ss.accept()
                    } catch (e: SocketException) {
                        // Server socket was closed deliberately — normal shutdown
                        break
                    }
                    _connectionCount.update { it + 1 }
                    _status.value = ServerStatus.CONNECTED
                    launchClientHandler(client)
                }
            } catch (e: java.net.BindException) {
                _status.value = ServerStatus.ERROR
                _errors.emit("Port $port already in use. Change port in settings.")
            } catch (e: Exception) {
                _status.value = ServerStatus.ERROR
                _errors.emit("Server error: ${e.message}")
            }
        }
    }

    /**
     * Stops accepting new connections and closes all active client sockets.
     * Existing [events] already in the buffer are still consumable.
     */
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        serverSocket?.runCatching { close() }
        serverSocket = null
        _connectionCount.value = 0
        _status.value = ServerStatus.IDLE
    }

    // -----------------------------------------------------------------------
    // Per-client coroutine
    // -----------------------------------------------------------------------

    private fun CoroutineScope.launchClientHandler(socket: Socket) = launch(Dispatchers.IO) {
        val remoteAddress = socket.remoteSocketAddress.toString()

        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
            val eventBuffer = StringBuilder()

            val lines = generateSequence { reader.readLine() }
            for (currentLine in lines) {
                if (!isActive) break
                if (currentLine.trimEnd() == END_DELIMITER) {
                    // Full event assembled — parse and emit
                    val xmlBlock = eventBuffer.toString().trim()
                    eventBuffer.clear()
                    if (xmlBlock.isNotBlank()) {
                        processXmlBlock(xmlBlock, remoteAddress)
                    }
                } else {
                    eventBuffer.appendLine(currentLine)
                }
            }

        } catch (e: SocketException) {
            // Connection reset by peer — normal POS disconnect
        } catch (e: Exception) {
            _errors.emit("Client $remoteAddress error: ${e.message}")
        } finally {
            socket.runCatching { close() }
            _connectionCount.update { maxOf(0, it - 1) }
            if (_connectionCount.value == 0) {
                _status.value = ServerStatus.LISTENING
            }
        }
    }

    // -----------------------------------------------------------------------
    // XML processing
    // -----------------------------------------------------------------------

    private suspend fun processXmlBlock(xml: String, sourceAddress: String) {
        try {
            val event = JposXmlParser.parse(xml, rawXml = xml)
            _events.emit(event)
        } catch (e: JposXmlParser.ParseException) {
            _errors.emit(
                "Parse error from $sourceAddress: ${e.message}. Raw: ${xml.take(80)}…"
            )
        } catch (e: Exception) {
            _errors.emit("Unexpected error processing event from $sourceAddress: ${e.message}")
        }
    }
}
