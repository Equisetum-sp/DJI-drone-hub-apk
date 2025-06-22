package dji.sampleV5.aircraft.util

import java.nio.ByteBuffer

const val DEFAULT_RTSP_PORT = 8554

class H264Util {
    fun extractSpsPps(data: ByteArray): Map<String, ByteBuffer> {
        val output = HashMap<String, ByteBuffer>()

        var i = 0
        while (i < data.size - 4) {
            // Check for NAL start code (4 bytes): 0x00 00 00 01
            if (data[i] == 0.toByte() && data[i+1] == 0.toByte() &&
                data[i+2] == 0.toByte() && data[i+3] == 1.toByte()) {

                val nalUnitType = data[i + 4].toInt() and 0x1F

                when (nalUnitType) {
                    7 -> { // SPS type (0x67)
                        val spsStart = i + 4
                        val spsEnd = findNextNalStart(data, spsStart)
                        val sps = data.copyOfRange(spsStart, spsEnd)
                        output["sps"] = ByteBuffer.wrap(sps)
                    }
                    8 -> { // PPS type (0x68)
                        val ppsStart = i + 4
                        val ppsEnd = findNextNalStart(data, ppsStart)
                        val pps = data.copyOfRange(ppsStart, ppsEnd)
                        output["pps"] = ByteBuffer.wrap(pps)
                    }
                }
            }

            if (output.contains("sps") && output.contains("pps")) break
            i++
        }
        return output
    }

    private fun findNextNalStart(data: ByteArray, start: Int): Int {
        for (i in start until data.size - 4) {
            if (data[i] == 0.toByte() && data[i+1] == 0.toByte() &&
                data[i+2] == 0.toByte() && data[i+3] == 1.toByte()) {
                return i
            }
        }
        return data.size
    }

    /**
     * Construct NAL unit for storing SEI metadata to be directly prepended before h.264 raw frame bytes
     *
     * @param metadata Metadata content in form of ByteArray
     * @return NAL unit containing SEI metadata in form of ByteArray
     *
     * @sample buildSeiNalUnit("metadata content".toByteArray())
     */
    fun buildSeiNalUnit(metadata: ByteArray): ByteArray {
        val seiHeader = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x06)  // Start code + NAL type 6 (SEI)
        val payloadType: Byte = 5  // User data unregistered
        val uuid = ByteBuffer.allocate(16)
            .putLong(69L)
            .putLong(69L)
            .array()
        val payload = byteArrayOf(payloadType, metadata.size.toByte()) + uuid + metadata + 0x80.toByte()

        return seiHeader + payload
    }
}