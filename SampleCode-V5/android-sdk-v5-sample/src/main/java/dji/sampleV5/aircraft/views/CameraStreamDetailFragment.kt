package dji.sampleV5.aircraft.views

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.CameraStreamDetailVM
import dji.sampleV5.aircraft.models.FlightData
import dji.sampleV5.aircraft.models.FlightDataVM
import dji.sampleV5.aircraft.util.ToastUtils
import dji.sampleV5.aircraft.util.Util
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.utils.common.LogPath
import dji.v5.utils.common.LogUtils

class CameraStreamDetailFragment : Fragment() {

    companion object {
        private const val KEY_CAMERA_INDEX = "cameraIndex"
        private const val KEY_ONLY_ONE_CAMERA = "onlyOneCamera"

        fun newInstance(cameraIndex: ComponentIndexType, onlyOneCamera: Boolean): CameraStreamDetailFragment {
            val args = Bundle()
            args.putInt(KEY_CAMERA_INDEX, cameraIndex.value())
            args.putBoolean(KEY_ONLY_ONE_CAMERA, onlyOneCamera)
            val fragment = CameraStreamDetailFragment()
            fragment.arguments = args
            return fragment
        }
    }
    private val flightDataVM: FlightDataVM by viewModels()
    private val cameraStreamVM: CameraStreamDetailVM by viewModels()

    private lateinit var tvPTS: TextView
    private lateinit var tvBatteryPreview: TextView
    private lateinit var tvGPSPreview: TextView
    private lateinit var tvAltitudePreview: TextView
    private lateinit var tvLocationPreview: TextView
    private lateinit var tvFacingPreview: TextView

    private lateinit var rgRTSPMode: RadioGroup
    private lateinit var cameraSurfaceView: SurfaceView
    private lateinit var tvCameraName: TextView
    private lateinit var btnCloseOpenCamera: Button
    private lateinit var btnBeginStream: Button
    private lateinit var btnStopStream: Button

    private lateinit var cameraIndex: ComponentIndexType
    private var onlyOneCamera = false
    private var isNeedPreviewCamera = false
    private var surface: Surface? = null
    private var width = -1
    private var height = -1

    private var isUDP = false
    private val scaleType = ICameraStreamManager.ScaleType.CENTER_INSIDE


    //Flight Data
    private var streamPTSMs: Long = 0
    private var batteryPercent = 0
    private var gpsCount = 0
    private var altitude = 0.0
    private var location = LocationCoordinate2D()
    private var facing = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraIndex = ComponentIndexType.find(arguments?.getInt(KEY_CAMERA_INDEX, 0) ?: 0)
        onlyOneCamera = arguments?.getBoolean(KEY_ONLY_ONE_CAMERA, false) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        LogUtils.i(LogPath.SAMPLE, "onCreateView,onlyOneCamera:", onlyOneCamera)
        val layoutId: Int = R.layout.fragment_camera_stream_detail_single

        return inflater.inflate(layoutId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvPTS = view.findViewById(R.id.tv_camerastream_pts)
        tvBatteryPreview = view.findViewById(R.id.tv_camerastream_battery_preview)
        tvGPSPreview = view.findViewById(R.id.tv_camerastream_gps_preview)
        tvAltitudePreview = view.findViewById(R.id.tv_camerastream_altitude_preview)
        tvLocationPreview = view.findViewById(R.id.tv_camerastream_location_preview)
        tvFacingPreview = view.findViewById(R.id.tv_camerastream_facing_preview)

        rgRTSPMode = view.findViewById(R.id.rg_rtsp_mode)

        cameraSurfaceView = view.findViewById(R.id.sv_camera)

        tvCameraName = view.findViewById(R.id.tv_camera_name)
        btnCloseOpenCamera = view.findViewById(R.id.btn_close_or_open)

        btnBeginStream = view.findViewById(R.id.btn_begin_stream)
        btnStopStream = view.findViewById(R.id.btn_stop_stream)

        rgRTSPMode.setOnCheckedChangeListener(onRTSPModeChangeListener)


        btnCloseOpenCamera.setOnClickListener(onOpenOrCloseCameraCheckListener)

        cameraSurfaceView.holder.addCallback(cameraSurfaceCallback)



        btnBeginStream.setOnClickListener {
            //show dialog to set IP port
            showStreamConfigDialog()

        }

        btnStopStream.setOnClickListener {
            stopStream()
        }

        initViewModel()

        onOpenOrCloseCameraCheckListener.onClick(btnCloseOpenCamera)
    }

    private fun initViewModel() {
        initCameraStreamVM()
        initFlightDataVM()
    }

    private fun initCameraStreamVM() {
        LogUtils.i(LogPath.SAMPLE, "initViewModel,cameraIndex:", cameraIndex)

        cameraStreamVM.setCameraIndex(cameraIndex)

        cameraStreamVM.cameraName.observe(viewLifecycleOwner) { name ->
            tvCameraName.text = name
        }
    }

    private fun updateStreamPTS()       { tvPTS.text                = "Presentation timestamp: ${streamPTSMs} ms" }
    private fun updateBatteryInfo()     { tvBatteryPreview.text     = "battery: ${batteryPercent}%" }
    private fun updateGPSInfo()         { tvGPSPreview.text         = "GPS count: ${gpsCount}" }
    private fun updateAltitudeInfo()    { tvAltitudePreview.text    = "alt: ${altitude}" }
    private fun updateLocationInfo()    { tvLocationPreview.text    = "lat: ${location.latitude}\t\tlng:${location.longitude}"}
    private fun updateFacingInfo()      { tvFacingPreview.text      = "facing: ${facing}" }

    private fun initFlightDataVM(){
        flightDataVM.initFlightDataListener()

        flightDataVM.batteryPercent.observe(viewLifecycleOwner){ percent ->
            batteryPercent = percent
            FlightData.batteryPercent = percent
            updateBatteryInfo()
        }
        flightDataVM.gpsCount.observe(viewLifecycleOwner){ gps ->
            gpsCount = gps
            FlightData.gpsCount = gps
            updateGPSInfo()
        }
        flightDataVM.altitude.observe(viewLifecycleOwner) { alt ->
            val truncatedAlt = Util.truncateToNDecimalPlaces(alt, 2)
            altitude = truncatedAlt
            FlightData.altitude = truncatedAlt
            updateAltitudeInfo()
        }
        flightDataVM.location.observe(viewLifecycleOwner) { coordinate ->
            val truncatedCoordinate = LocationCoordinate2D(
                Util.truncateToNDecimalPlaces(coordinate.latitude, 6),
                Util.truncateToNDecimalPlaces(coordinate.longitude, 6)
            )
            location = truncatedCoordinate
            FlightData.location = truncatedCoordinate
            updateLocationInfo()
        }
        flightDataVM.facing.observe(viewLifecycleOwner){ degree ->
            val truncatedDegree = Util.truncateToNDecimalPlaces(degree, 2)
            facing = truncatedDegree //range: (-180) - (+180); value: 0 = North, 90 = East, +-180 = South, -90 = West
            FlightData.facing = truncatedDegree
            updateFacingInfo()
        }
    }

    private fun showStreamConfigDialog() {
        val factory = LayoutInflater.from(requireContext())
        val ipConfigView = factory.inflate(R.layout.dialog_camerastream_udp_config_view, null)

        val etTargetIP = ipConfigView.findViewById<EditText>(R.id.et_camerastream_targetip)
        val etRTSPUsername = ipConfigView.findViewById<EditText>(R.id.et_camerastream_rtsp_username)
        val etRTSPPassword = ipConfigView.findViewById<EditText>(R.id.et_camerastream_rtsp_password)

        etTargetIP.setText(cameraStreamVM.getCurrIP())
        if (cameraStreamVM.rtspUsername.isNotBlank()) etRTSPUsername.setText(cameraStreamVM.rtspUsername)
        if (cameraStreamVM.rtspPassword.isNotBlank()) etRTSPPassword.setText(cameraStreamVM.rtspPassword)

        val configDialog = requireContext().let {
            AlertDialog.Builder(it, R.style.Base_ThemeOverlay_AppCompat_Dialog_Alert)
                .setIcon(android.R.drawable.ic_menu_camera)
                .setTitle("UDP IP config")
                .setCancelable(false)
                .setView(ipConfigView)
                .setPositiveButton(R.string.ad_confirm) { configDialog, _ ->
                    kotlin.run {
                        val inputTargetIP = etTargetIP.text.toString()
                        val inputRTSPUsername = etRTSPUsername.text.toString()
                        val inputRTSPPassword = etRTSPPassword.text.toString()

                        if (TextUtils.isEmpty(inputTargetIP) || TextUtils.isEmpty(inputRTSPUsername) || TextUtils.isEmpty(inputRTSPPassword)) {
                            ToastUtils.showToast("Input must not be empty")
                        } else {
                            startStream(inputTargetIP, inputRTSPUsername, inputRTSPPassword)
                        }
                        configDialog.dismiss()
                    }
                }
                .setNegativeButton(R.string.ad_cancel) { configDialog, _ ->
                    kotlin.run {
                        configDialog.dismiss()
                    }
                }
                .create()
        }
        configDialog.show()
    }

    private fun startStream(targetIP: String, rtspUsername: String, rtspPassword: String) {
        enableRTSPRadioGroup(false)
        clearPTSObserver()

        cameraStreamVM.streamPTSMs.observe(viewLifecycleOwner){ pts ->
            streamPTSMs = pts
            updateStreamPTS()
        }
        cameraStreamVM.beginSendStream(targetIP, rtspUsername, rtspPassword, isUDP)
    }

    private fun stopStream(){
        cameraStreamVM.stopSendStream()

        enableRTSPRadioGroup(true)
        clearPTSObserver()
    }

    private fun clearPTSObserver(){
        cameraStreamVM.streamPTSMs.removeObservers(viewLifecycleOwner)
        streamPTSMs = 0
        updateStreamPTS()
    }

    private fun enableRTSPRadioGroup(isEnabled: Boolean) {
        for (view in rgRTSPMode.children){
            view.isEnabled = isEnabled
        }
    }

    private fun updateCameraStream() {
        if (isNeedPreviewCamera) {
            cameraSurfaceView.visibility = View.VISIBLE
        } else {
            cameraSurfaceView.visibility = View.GONE
        }
        if (width <= 0 || height <= 0 || surface == null || !isNeedPreviewCamera) {
            if (surface != null) {
                cameraStreamVM.removeCameraStreamSurface(surface!!)
            }
            return
        }
        cameraStreamVM.putCameraStreamSurface(
            surface!!,
            width,
            height,
            scaleType
        )
    }

    private val onRTSPModeChangeListener = RadioGroup.OnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
        when (checkedId) {
            R.id.rb_rtsp_tcp -> {
                isUDP = false
            }
            R.id.rb_rtsp_udp -> {
                isUDP = true
            }
        }
    }
    private val onOpenOrCloseCameraCheckListener = View.OnClickListener { _ ->
        btnCloseOpenCamera.isSelected = !btnCloseOpenCamera.isSelected
        isNeedPreviewCamera = btnCloseOpenCamera.isSelected
        if (btnCloseOpenCamera.isSelected) {
            btnCloseOpenCamera.text = "close cam"
        } else {
            btnCloseOpenCamera.text = "open cam"
        }
        updateCameraStream()
    }

    private val cameraSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surface = holder.surface
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            this@CameraStreamDetailFragment.width = width
            this@CameraStreamDetailFragment.height = height
            updateCameraStream()
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            width = 0
            height = 0
            updateCameraStream()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        flightDataVM.cleanFlightDataListener()
        stopStream()
    }
}