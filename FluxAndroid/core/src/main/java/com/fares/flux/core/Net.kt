package com.fares.flux.core

import okhttp3.Call
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Who turns a server's name into its address: the network's own, or a
 *  public resolver over HTTPS, for when the box's blocks a provider. */
enum class DnsProvider(val raw: String, val title: String, val url: String?, val bootstrap: List<String>) {
    SYSTEM("system", "Système", null, emptyList()),
    CLOUDFLARE("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    GOOGLE("google", "Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("quad9", "Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112"));

    val label: String get() = if (this == SYSTEM) tr("Celui du réseau", "The network’s") else title

    companion object {
        const val STORAGE_KEY = "dns"
        fun of(raw: String?) = entries.firstOrNull { it.raw == raw } ?: SYSTEM
    }
}

/** Every request the app makes, the player's and the images' too, so the
 *  DNS picked applies to all of them at once. */
object Net {
    private val base = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    @Volatile var client: OkHttpClient = base
        private set

    fun use(provider: DnsProvider) {
        val url = provider.url ?: run { client = base; return }
        val doh = DnsOverHttps.Builder().client(base).url(url.toHttpUrl())
            .bootstrapDnsHosts(provider.bootstrap.map(InetAddress::getByName)).build()
        // A resolver that can't be reached leaves the network's to answer.
        val dns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                try { doh.lookup(hostname) } catch (e: UnknownHostException) { Dns.SYSTEM.lookup(hostname) }
        }
        client = base.newBuilder().dns(dns).build()
    }

    /** For the player and images: always the client in use now. */
    val callFactory = Call.Factory { client.newCall(it) }

    /** A GET, its body left for the caller to read and close. */
    fun get(url: String, connectSeconds: Long, readSeconds: Long, headers: Map<String, String> = emptyMap()): Response =
        client.newBuilder().connectTimeout(connectSeconds, TimeUnit.SECONDS).readTimeout(readSeconds, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()).execute()
}
