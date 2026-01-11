package com.example.tapsyncwatch.osc

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

object ResolumeDiscovery {

    data class Result(
        val ip: String,
        val port: Int
    )

    /**
     * Scannt das lokale /24-Netz nach einem erreichbaren OSC-Target.
     * Erster Host, bei dem send() nicht crasht, gilt als Treffer.
     */
    fun scan(
        ports: List<Int> = listOf(7002, 7000),
        timeoutMs: Int = 250
    ): Result? {

        val localIp = getLocalIp() ?: return null
        val base = localIp.substringBeforeLast(".")

        for (i in 1..254) {
            val host = "$base.$i"

            for (port in ports) {
                if (trySend(host, port, timeoutMs)) {
                    return Result(host, port)
                }
            }
        }

        return null
    }

    private fun trySend(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            val socket = DatagramSocket().apply {
                soTimeout = timeoutMs
            }

            // Minimal gültiges OSC-Paket: leere Message
            val data = byteArrayOf(0, 0, 0, 0)
            val packet = DatagramPacket(
                data,
                data.size,
                InetAddress.getByName(ip),
                port
            )

            socket.send(packet)
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ermittelt die lokale IPv4-Adresse der Watch
     */
    private fun getLocalIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull {
                    !it.isLoopbackAddress &&
                            it is java.net.Inet4Address
                }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
