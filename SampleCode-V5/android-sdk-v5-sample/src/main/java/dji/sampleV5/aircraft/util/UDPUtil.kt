package dji.sampleV5.aircraft.util

import dji.sampleV5.aircraft.models.FlightData
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class UDPUtil {
    val dataStreamScheduler= Executors.newSingleThreadScheduledExecutor()

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

    fun startSendFlightData(streamStartTimeMs: Long, interval: Long = 80){
        //Start thread, periodically send flight data (and other stuff) using UDP
//        val dataStreamScheduler= Executors.newSingleThreadScheduledExecutor()
        dataStreamScheduler.scheduleWithFixedDelay({
            val pts = (System.currentTimeMillis() - streamStartTimeMs)
            sendFlightData(pts)
        }, 0, interval, TimeUnit.MILLISECONDS)
    }

    fun stopSendFlightData(){
        dataStreamScheduler.shutdown()
    }

    fun sendFlightData(currPTS: Long){
        val flightDataHeader = createUDPHeader(currPTS)
        val flightDataBody = createUDPFlightDataBody()
        val flightDataPacket = flightDataHeader + flightDataBody

        val packet = DatagramPacket(flightDataPacket, flightDataPacket.size, udpIPAddr, udpPort)
        socket.send(packet)
    }

    private fun createUDPHeader(presentationTimeMs: Long): ByteArray{
        return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(presentationTimeMs).array()
    }

    private fun createUDPFlightDataBody(): ByteArray{
        val buffer = ByteBuffer.allocate(16)

        buffer.put(FlightData.batteryPercent.toByte())

        buffer.put(FlightData.gpsCount.toByte())

        buffer.putInt((FlightData.altitude * 1000).toInt())

        buffer.putInt((FlightData.location.latitude * 1000000).toInt())

        buffer.putInt((FlightData.location.longitude * 1000000).toInt())

        buffer.putShort((FlightData.facing * 10).toInt().toShort())

        return buffer.array()
    }
}