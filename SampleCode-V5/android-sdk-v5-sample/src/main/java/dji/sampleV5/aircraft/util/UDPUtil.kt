package dji.sampleV5.aircraft.util

import dji.sampleV5.aircraft.models.FlightData
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class UDPUtil {
    private var socket = DatagramSocket(null)
    private var udpIPAddr : InetAddress = InetAddress.getByName("")
    private val udpPort = 10004

    var ipAddrStr = "localhost"

    fun initUDPSocket(udpIP: String = ipAddrStr) {
        ipAddrStr = udpIP
        udpIPAddr = InetAddress.getByName(udpIP)

        if (!socket.isBound || socket.port != udpPort || socket.isClosed) {
            socket = DatagramSocket(null)
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(udpPort))
        }
    }

    fun closeUDPSocket() {
        socket.close()
    }

    fun sendFlightData(currPTS: Long){
        val flightDataPacket = createUDPFlightDataBody(currPTS)

        val packet = DatagramPacket(flightDataPacket, flightDataPacket.size, udpIPAddr, udpPort)
        socket.send(packet)
    }

    private fun createUDPFlightDataBody(presentationTimeMs: Long): ByteArray{
        val json = JSONObject()

        json.put("pts", presentationTimeMs)
        json.put("battery", FlightData.batteryPercent)
        json.put("gpsCount", FlightData.gpsCount)
        json.put("alt", FlightData.altitude)
        json.put("lat", FlightData.location.latitude)
        json.put("lng", FlightData.location.longitude)
        json.put("facing", FlightData.facing)

        return json.toString().toByteArray(Charsets.UTF_8)
    }
}
