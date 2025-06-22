package dji.sampleV5.aircraft.models

import android.media.MediaCodec
import android.util.Log
import android.view.Surface
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtspClient
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sampleV5.aircraft.util.DEFAULT_RTSP_PORT
import dji.sampleV5.aircraft.util.H264Util
import dji.sampleV5.aircraft.util.UDPUtil
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.value.camera.CameraType
import dji.sdk.keyvalue.value.camera.CameraVideoStreamSourceType
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.flightassistant.VisionAssistDirection
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager
import dji.v5.manager.datacenter.MediaDataCenter
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.interfaces.ICameraStreamManager.ScaleType
import dji.v5.utils.common.DJIExecutor
import dji.v5.utils.common.StringUtils
import dji.v5.ux.R
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean


class CameraStreamDetailVM: ViewModel() {
    private val cameraStreamManager = MediaDataCenter.getInstance().cameraStreamManager

    private val _availableLensListData = MutableLiveData<List<CameraVideoStreamSourceType>>(ArrayList())
    private val _currentLensData = MutableLiveData(CameraVideoStreamSourceType.DEFAULT_CAMERA)
    private val _cameraName = MutableLiveData("Unknown")
    private val _isVisionAssistEnabled = MutableLiveData(false)
    private val _visionAssistViewDirection = MutableLiveData(VisionAssistDirection.UNKNOWN)
    private val _visionAssistViewDirectionRange = MutableLiveData<List<VisionAssistDirection>>(ArrayList())

    val streamPTSMs = MutableLiveData<Long>()

    private val isStreaming : AtomicBoolean = AtomicBoolean(false)


    //UDP
    private val udpUtil = UDPUtil()

    //RTSP livestream
    private val h264Util = H264Util()
    var rtspUsername = "dji"
    var rtspPassword = "tes123"
    private val rtspPort = DEFAULT_RTSP_PORT
    private var rtspServerURL = ""
    private var rtspClient: RtspClient = RtspClient(object : ConnectChecker {
        override fun onConnectionSuccess() {
            Log.d("RTSP", "Connection success")
        }

        override fun onDisconnect(){
            Log.d("RTSP", "Disconnect success")
        }

        override fun onAuthError(){
            //do nothing
        }


        override fun onAuthSuccess(){
            //do nothing
        }

        override fun onConnectionFailed(reason: String) {
            Log.e("RTSP", "Connection failed: $reason")
        }

        override fun onConnectionStarted(url: String) {
            Log.w("RTSP", "Connection started: $url")
        }
    })


    private var cameraIndex = ComponentIndexType.UNKNOWN
    private var cameraType = ""
    private var isMotorOn = false
    private val visionAssistStatusListener = object :
        ICameraStreamManager.VisionAssistStatusListener {
        override fun onVisionAssistEnabled(isEnable: Boolean) {
            _isVisionAssistEnabled.postValue(isEnable)
        }

        override fun onVisionAssistViewDirectionUpdated(mode: VisionAssistDirection) {
            _visionAssistViewDirection.postValue(mode)
        }

        override fun onVisionAssistViewDirectionRangeUpdated(modes: MutableList<VisionAssistDirection>) {
            _visionAssistViewDirectionRange.postValue(modes)
        }
    }

    private var streamStartTimeMs = 0L
    private val streamListener = ICameraStreamManager.ReceiveStreamListener { frameData, frameOffset, frameLength, frameInfo ->
        if (frameInfo.isKeyFrame){ //update sps and pps
            val extractedSpsPps = h264Util.extractSpsPps(frameData)
            if (extractedSpsPps.contains("sps".lowercase()) && extractedSpsPps.contains("pps".lowercase()) &&
                extractedSpsPps["sps".lowercase()] != null && extractedSpsPps["pps".lowercase()] != null){
                rtspClient.setVideoInfo(
                    extractedSpsPps["sps".lowercase()]!!,
                    extractedSpsPps["pps".lowercase()],
                    null
                )
            }
            else if (!rtspClient.isStreaming) return@ReceiveStreamListener //skip current frame
        }
        if (!rtspClient.isStreaming){ //setup RTSP client
            rtspClient.setAuthorization(rtspUsername, rtspPassword)

            rtspClient.connect(rtspServerURL)

            streamStartTimeMs = frameInfo.presentationTimeMs
        }

        val currPTS = frameInfo.presentationTimeMs - streamStartTimeMs
        streamPTSMs.postValue(currPTS)

        val currPTSBytes = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(currPTS).array()
        val seiMetadata = h264Util.buildSeiNalUnit(currPTSBytes)

        val bufferInfo = MediaCodec.BufferInfo().apply {
            offset = frameOffset
            size = frameData.size
            presentationTimeUs = currPTS * 90 // Standard RTP ticks:  1 ms = 90 RTP ticks
            flags = if (frameInfo.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        }

        DJIExecutor.getExecutor().execute {
            try {
                udpUtil.sendFlightData(currPTS)
                rtspClient.sendVideo(ByteBuffer.wrap(seiMetadata + frameData), bufferInfo)
                //Log.w("DJI Frame info", "(${System.currentTimeMillis()}}) ${frameInfo.presentationTimeMs}")
                //Log.w("Packet Info", "Msg startTime: $streamStartTimeMs infoTime: (${frameInfo.presentationTimeMs})")
                //Log.w("RTSP Stream info", "Sent frames: ${rtspClient.sentVideoFrames}")
            } catch (e: Exception) {
                //do nothing
                Log.e("DJI CameraStream", e.message ?: "", e.cause)
            }
        }

    }

    override fun onCleared() {
        super.onCleared()
        setCameraIndex(ComponentIndexType.UNKNOWN)
        cameraStreamManager.removeVisionAssistStatusListener(visionAssistStatusListener)
        KeyManager.getInstance().cancelListen(this)
    }

    fun setCameraIndex(cameraIndex: ComponentIndexType) {
        KeyManager.getInstance().cancelListen(this)
        if (this.cameraIndex == cameraIndex) {
            return
        }
        this.cameraIndex = cameraIndex
        if (this.cameraIndex == ComponentIndexType.UNKNOWN) {
            return
        }
        listenCameraName()
        listenAvailableLens()
        listenCurrentLens()
        listenVisionAssistStatus()
    }

    private fun listenCameraName() {
        CameraKey.KeyCameraType.create(cameraIndex).listen(this) {
            cameraType = it?.name ?: CameraType.NOT_SUPPORTED.name
            updateCameraName()
        }

        FlightControllerKey.KeyAreMotorsOn.create().listen(this) {
            isMotorOn = it == true
            updateCameraName()
        }
    }

    private fun updateCameraName() {
        _cameraName.postValue("")
        if (cameraIndex == ComponentIndexType.UNKNOWN) {
            return
        }
        if (cameraIndex == ComponentIndexType.FPV) {
            _cameraName.postValue(ComponentIndexType.FPV.name)
            return
        }
        if (cameraIndex == ComponentIndexType.VISION_ASSIST) {
            var msg = ComponentIndexType.VISION_ASSIST.name
            if (!isMotorOn) {
                msg = "$msg(${StringUtils.getResStr(R.string.uxsdk_assistant_video_empty_text)})"
            }
            _cameraName.postValue(msg)
            return
        }
        _cameraName.postValue(cameraType)
    }

    private fun listenAvailableLens() {
        _availableLensListData.postValue(arrayListOf())
        if (cameraIndex == ComponentIndexType.UNKNOWN) {
            return
        }
        CameraKey.KeyCameraVideoStreamSourceRange.create(cameraIndex).listen(this) {
            val list: List<CameraVideoStreamSourceType> = it ?: arrayListOf()
            _availableLensListData.postValue(list)
        }
    }

    private fun listenCurrentLens() {
        _currentLensData.postValue(CameraVideoStreamSourceType.DEFAULT_CAMERA)
        if (cameraIndex == ComponentIndexType.UNKNOWN) {
            return
        }
        CameraKey.KeyCameraVideoStreamSource.create(cameraIndex).listen(this) {
            if (it != null) {
                _currentLensData.postValue(it)
            } else {
                _currentLensData.postValue(CameraVideoStreamSourceType.DEFAULT_CAMERA)
            }
        }
    }

    private fun listenVisionAssistStatus() {
        cameraStreamManager.addVisionAssistStatusListener(visionAssistStatusListener)
    }

    fun putCameraStreamSurface(surface: Surface, width: Int, height: Int, scaleType: ScaleType){
        cameraStreamManager.putCameraStreamSurface(cameraIndex, surface, width, height, scaleType)
    }

    fun removeCameraStreamSurface(surface: Surface) {
        cameraStreamManager.removeCameraStreamSurface(surface)
    }

    fun beginSendStream(targetIP: String, rtspUsername: String, rtspPassword: String, isUDP: Boolean) {
        if (isStreaming.get()) {
            ToastUtils.showToast("Pls stop first.")
            return
        }

        if (targetIP.isBlank() || rtspUsername.isBlank() || rtspPassword.isBlank()) {
            ToastUtils.showToast("Input must not be empty")
            return
        }

        udpUtil.initUDPSocket(targetIP)
        //udpUtil.startSendFlightData(System.currentTimeMillis())

        setRTSPConfig(targetIP, rtspUsername, rtspPassword, isUDP)

        ToastUtils.showToast("Start streaming")

        cameraStreamManager.addReceiveStreamListener(cameraIndex, streamListener)

        isStreaming.set(true)
    }

    fun stopSendStream() {
        if (!isStreaming.get()) {
            ToastUtils.showToast("Pls begin first.")
            return
        }

        ToastUtils.showToast("stop streaming")

        //udpUtil.stopSendFlightData()
        udpUtil.closeUDPSocket()

        rtspClient.disconnect()

        streamPTSMs.postValue(0)
        cameraStreamManager.removeReceiveStreamListener(streamListener)

        isStreaming.set(false)
    }

    private fun setRTSPConfig(ipAddr: String, userName: String, password: String, isUDP: Boolean, port: Int = rtspPort) {
        rtspClient.setProtocol(if (isUDP) Protocol.UDP else Protocol.TCP)

        rtspClient.setVideoCodec(VideoCodec.H264)

        rtspUsername = userName
        rtspPassword = password

        rtspServerURL = "rtsp://${ipAddr}:${port}/djistream" // URL doesn't support username and password. Instead configure authorization on rtspClient
    }

    fun getCurrIP(): String{
        return udpUtil.ipAddrStr
    }

    val cameraName: LiveData<String>
        get() = _cameraName
}