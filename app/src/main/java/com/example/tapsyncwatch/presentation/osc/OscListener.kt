package com.example.tapsyncwatch.osc

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

object OscListener {

    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val running = AtomicBoolean(false)

    fun listen(
        context: Context,
        listenPort: Int,
        onFound: (ip: String, port: Int) -> Unit
    ) {
        if (running.get()) return
        running.set(true)

        Thread {
            try {
                // 🔒 Multicast/Broadcast erlauben
                val wifi = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager

                multicastLock = wifi.createMulticastLock("tapsync-osc").apply {
                    setReferenceCounted(false)
                    acquire()
                }

                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(listenPort))
                }

                val buffer = ByteArray(2048)

                while (running.get()) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val senderIp = packet.address.hostAddress
                    val senderPort = packet.port

                    onFound(senderIp, senderPort)
                    stop()
                }

            } catch (_: Exception) {
                stop()
            }
        }.start()
    }

    fun stop() {
        running.set(false)

        try {
            socket?.close()
        } catch (_: Exception) {}

        try {
            multicastLock?.release()
        } catch (_: Exception) {}

        socket = null
        multicastLock = null
    }
}
