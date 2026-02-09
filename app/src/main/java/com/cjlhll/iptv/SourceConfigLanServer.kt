package com.cjlhll.iptv

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

class SourceConfigLanServer(
    private val appContext: Context,
    private val onPush: (liveSource: String, epgSource: String) -> Unit,
) {
    data class Info(
        val ip: String,
        val port: Int,
        val url: String,
    )

    private var server: NanoHTTPD? = null
    private var info: Info? = null

    fun start(): Info {
        val localIp = findLocalIpv4() ?: "127.0.0.1"
        val preferredPorts = intArrayOf(18080, 18081, 19090, 28080, 8080)
        var lastError: Exception? = null
        for (port in preferredPorts) {
            try {
                val s = InternalServer(
                    port = port,
                    html = SourceConfigWebPage.html(appContext),
                    onPush = onPush,
                )
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = s
                info = Info(ip = localIp, port = port, url = "http://$localIp:$port/")
                return info!!
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw lastError ?: IllegalStateException("LAN server start failed")
    }

    fun stop() {
        server?.stop()
        server = null
        info = null
    }

    private fun findLocalIpv4(): String? {
        val ifaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrNull().orEmpty()
        return ifaces
            .asSequence()
            .filter { runCatching { it.isUp }.getOrDefault(false) }
            .filterNot { runCatching { it.isLoopback }.getOrDefault(true) }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .firstOrNull { !it.isNullOrBlank() && !it.startsWith("127.") }
    }

    private class InternalServer(
        port: Int,
        private val html: String,
        private val onPush: (liveSource: String, epgSource: String) -> Unit,
    ) : NanoHTTPD(port) {

        override fun serve(session: IHTTPSession): Response {
            val path = (session.uri ?: "/").lowercase(Locale.US)
            if (session.method == Method.OPTIONS) {
                return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""))
            }

            return when {
                session.method == Method.GET && (path == "/" || path == "/index.html") -> {
                    cors(newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html))
                }

                session.method == Method.GET && path == "/api/ping" -> {
                    val body = JSONObject(mapOf("ok" to true)).toString()
                    cors(newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body))
                }

                session.method == Method.POST && path == "/api/push" -> {
                    val payload = parsePayload(session)
                    val live = payload.optString("liveSource", "")
                    val epg = payload.optString("epgSource", "")
                    onPush(live, epg)
                    val body = JSONObject(mapOf("ok" to true)).toString()
                    cors(newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body))
                }

                else -> cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain; charset=utf-8", "Not Found"))
            }
        }

        private fun parsePayload(session: IHTTPSession): JSONObject {
            val files = HashMap<String, String>()
            return try {
                session.parseBody(files)
                val raw = files["postData"]?.trim().orEmpty()
                if (raw.isNotEmpty() && (raw.startsWith("{") || raw.startsWith("["))) {
                    JSONObject(raw)
                } else {
                    val live = session.parameters["liveSource"]?.firstOrNull().orEmpty()
                    val epg = session.parameters["epgSource"]?.firstOrNull().orEmpty()
                    JSONObject(mapOf("liveSource" to live, "epgSource" to epg))
                }
            } catch (_: Exception) {
                JSONObject(mapOf("liveSource" to "", "epgSource" to ""))
            }
        }

        private fun cors(response: Response): Response {
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "Content-Type")
            response.addHeader("Cache-Control", "no-store")
            return response
        }
    }
}
