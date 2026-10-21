package com.fares.flux.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.ServerSocket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.tukaani.xz.XZInputStream
import org.xmlpull.v1.XmlPullParser
import android.util.Xml

/** The account sent from the iPhone over the local network, as to the
 *  Apple TV: same Bonjour service, same message. */
@Serializable
data class PairingMessage(
    val code: String,
    val account: Account,
    val library: LibrarySnapshot? = null,
    val settings: Map<String, String>? = null,
)

/** Waits for an account while the sign-in screen is up: advertises
 *  "Android TV · 1234" and takes what comes with the right code. */
class PairingReceiver(private val context: Context, private val kind: String = "Android TV", private val onMessage: (PairingMessage) -> Unit) {
    val code: String = String.format(Locale.ROOT, "%04d", (0..9999).random())

    private var server: ServerSocket? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private val main = Handler(Looper.getMainLooper())
    private val json = pairingJson

    fun start() {
        if (server != null) return
        val socket = runCatching { ServerSocket(0) }.getOrNull() ?: return
        server = socket
        thread(name = "pairing", isDaemon = true) {
            while (!socket.isClosed) {
                val connection = runCatching { socket.accept() }.getOrNull() ?: break
                runCatching {
                    connection.soTimeout = 10_000
                    // The iPhone sends one message and closes its side.
                    val data = connection.getInputStream().readBytes()
                    connection.close()
                    val message = json.decodeFromString(PairingMessage.serializer(), data.decodeToString())
                    if (message.code == code) main.post { onMessage(message) }
                }
                runCatching { connection.close() }
            }
        }
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicast = wifi?.createMulticastLock("flux-pairing")?.apply { setReferenceCounted(false); acquire() }
        val info = NsdServiceInfo().apply {
            serviceName = "$kind · $code"
            serviceType = SERVICE
            port = socket.localPort
            setAttribute("code", code)
            setAttribute("kind", kind)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        registration = listener
        (context.getSystemService(Context.NSD_SERVICE) as NsdManager).registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        registration?.let { runCatching { (context.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(it) } }
        registration = null
        runCatching { server?.close() }
        server = null
        multicast?.release()
        multicast = null
    }

    companion object {
        const val SERVICE = "_fluxpair._tcp"
    }
}

/** Sends this device's account to another one nearby: lists the devices
 *  waiting with their code (iPhone, Mac, Apple TV, Android…), sends to one. */
class PairingSender(private val context: Context) {
    data class Screen(val name: String, val code: String, val kind: String, val host: java.net.InetAddress, val port: Int)

    var screens by androidx.compose.runtime.mutableStateOf<List<Screen>>(emptyList())
        private set
    /** The code of the device the account last went to. */
    var sent by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var failed by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    private val nsd get() = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    // Older Android resolves one service at a time.
    private val toResolve = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        if (discovery != null) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicast = wifi?.createMulticastLock("flux-pairing-send")?.apply { setReferenceCounted(false); acquire() }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) {}
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) { main.post { toResolve.addLast(info); resolveNext() } }
            override fun onServiceLost(info: NsdServiceInfo) { main.post { screens = screens.filter { it.name != info.serviceName } } }
        }
        discovery = listener
        runCatching { nsd.discoverServices(PairingReceiver.SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun resolveNext() {
        if (resolving) return
        val info = toResolve.removeFirstOrNull() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) { main.post { resolving = false; resolveNext() } }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val code = info.attributes["code"]?.decodeToString()
                val kind = info.attributes["kind"]?.decodeToString() ?: "Apple TV"
                val host = info.host
                main.post {
                    if (code != null && host != null) {
                        screens = (screens.filter { it.name != info.serviceName } + Screen(info.serviceName, code, kind, host, info.port)).sortedBy { it.name }
                    }
                    resolving = false
                    resolveNext()
                }
            }
        })
    }

    fun stop() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        multicast?.release()
        multicast = null
        toResolve.clear()
    }

    /** The account, what goes with it and the settings, as the Apple apps send them. */
    fun send(state: AppState, screen: Screen) {
        val account = state.accounts.active ?: return
        val l = state.library
        val message = PairingMessage(screen.code, account, LibrarySnapshot(l.favorites, l.progress, l.recentChannels, l.qualityChoices), state.settings.exported())
        val data = pairingJson.encodeToString(PairingMessage.serializer(), message).toByteArray()
        failed = null
        thread(name = "pairing-send", isDaemon = true) {
            val ok = runCatching {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(screen.host, screen.port), 5_000)
                    socket.getOutputStream().apply { write(data); flush() }
                    // Closing our side ends the message, as the Apple apps expect.
                    socket.shutdownOutput()
                }
            }.isSuccess
            main.post { if (ok) sent = screen.code else failed = screen.code }
        }
    }
}

/** As the Apple apps encode it: every field there, optional ones left out when empty. */
private val pairingJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

/** An account received: checked against the server like a typed one (or
 *  the one already here with that login), then what goes with it merged. */
suspend fun AppState.receive(message: PairingMessage) {
    val existing = accounts.accounts.firstOrNull { it.credentials == message.account.credentials }
    if (existing != null) accounts.select(existing.id)
    else accounts.add(Account(name = message.account.name, credentials = message.account.credentials))
    message.settings?.let { settings.apply(it) }
    library.use(accounts.activeID, settings.sharesFavorites)
    message.library?.let { library.merge(it) }
    catalog.optionsChanged()
}

/** The French TNT's guide from XML TV Fr (xmltvfr.fr): titles, summaries and
 *  a picture for every programme. Fetched at most twice a day. */
object OpenGuide {
    private const val SOURCE = "https://xmltvfr.fr/xmltv/xmltv_tnt.xz"
    private const val MAX_AGE = 12 * 3600 * 1000L

    /** Channel key ("fr|tf1") → its programmes from an hour ago on. Blocking: call off the main thread. */
    fun load(context: Context): Map<String, List<Programme>> {
        val file = File(context.cacheDir, "openguide-tnt.xz")
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE) {
            runCatching {
                val connection = URL(SOURCE).openConnection().apply {
                    connectTimeout = 20_000; readTimeout = 60_000
                    setRequestProperty("User-Agent", XtreamClient.USER_AGENT)
                }
                val bytes = connection.getInputStream().use { it.readBytes() }
                if (bytes.isNotEmpty()) File(file.path + ".tmp").apply { writeBytes(bytes); renameTo(file) }
            }
        }
        if (!file.exists()) return emptyMap()
        return runCatching { XZInputStream(file.inputStream().buffered()).use { parse(it) } }.getOrDefault(emptyMap())
    }

    private fun parse(input: java.io.InputStream): Map<String, List<Programme>> {
        val dates = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, "UTF-8")
        val channels = LinkedHashMap<String, String>()
        val programmes = mutableListOf<Pair<String, Programme>>()
        var channelID: String? = null
        var current: Triple<String, Long, Long>? = null
        var title: String? = null
        var summary: String? = null
        var image: String? = null
        val text = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    text.setLength(0)
                    when (parser.name) {
                        "channel" -> channelID = parser.getAttributeValue(null, "id")
                        "programme" -> {
                            val ch = parser.getAttributeValue(null, "channel")
                            val start = parser.getAttributeValue(null, "start")?.let { runCatching { dates.parse(it)?.time }.getOrNull() }
                            val end = parser.getAttributeValue(null, "stop")?.let { runCatching { dates.parse(it)?.time }.getOrNull() }
                            current = if (ch != null && start != null && end != null) Triple(ch, start / 1000, end / 1000) else null
                            title = null; summary = null; image = null
                        }
                        "icon" -> if (current != null) image = parser.getAttributeValue(null, "src")
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    // Quotes come escaped in some summaries (\"La Quotidienne\").
                    val value = text.toString().replace("\\\"", "\"").trim()
                    when (parser.name) {
                        "display-name" -> if (current == null) channelID?.let { channels.putIfAbsent(it, value) }
                        "channel" -> channelID = null
                        "title" -> if (current != null && title == null) title = value.nonEmpty
                        "desc" -> if (current != null && summary == null) summary = value.nonEmpty
                        "programme" -> {
                            val c = current
                            val t = title
                            if (c != null && t != null) programmes.add(c.first to Programme(t, summary, c.second, c.third, image))
                            current = null
                        }
                    }
                    text.setLength(0)
                }
            }
            event = parser.next()
        }
        // XML TV ids ("TF1.fr") → the keys channel groups go by.
        val keys = channels.mapValues { (_, name) ->
            val key = ChannelName.parse("|FR| $name").key
            FrenchChannels.known(key)?.key ?: key
        }
        val since = nowSeconds() - 3600
        return programmes.filter { it.second.end > since }
            .mapNotNull { (id, p) -> keys[id]?.let { it to p } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sortedBy { it.start } }
    }
}
