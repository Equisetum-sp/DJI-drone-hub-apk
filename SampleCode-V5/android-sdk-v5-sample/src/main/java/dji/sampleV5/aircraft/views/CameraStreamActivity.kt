package dji.sampleV5.aircraft.views

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.CameraStreamListVM
import dji.sampleV5.aircraft.models.MSDKInfoVm
import dji.sdk.keyvalue.value.common.ComponentIndexType

open class CameraStreamActivity : AppCompatActivity() {
    private lateinit var llBase: LinearLayout

    private lateinit var msdkInfoTextMain: TextView
    private lateinit var msdkInfoTextSecond: TextView
    private lateinit var returnBtn: ImageButton

    private val cameraVM: CameraStreamListVM by viewModels()
    private val msdkInfoVM: MSDKInfoVm by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_camera_stream)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initView()
        initMSDKInfo()

        cameraVM.availableCameraListData.observe(this) { availableCameraList ->
            updateAvailableCamera(availableCameraList)
        }
    }

    private fun initView() {
        llBase = findViewById(R.id.ll_camera_base_layout)

        msdkInfoTextMain = findViewById(R.id.msdk_info_text_main)
        msdkInfoTextSecond = findViewById(R.id.msdk_info_text_second)
        returnBtn = findViewById(R.id.return_btn)

        returnBtn.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun initMSDKInfo() {
        msdkInfoVM.initListener()
        msdkInfoVM.msdkInfo.observe(this) { msdkInfo ->
            val mainInfo = "MSDK Info:[Ver:${msdkInfo.SDKVersion} BuildVer:${msdkInfo.buildVer} Debug:${msdkInfo.isDebug} ProductCategory:${msdkInfo.packageProductCategory} LDMLicenseLoaded:${msdkInfo.isLDMLicenseLoaded} ]"
            val secondInfo = "Device:${msdkInfo.productType.name} | Network:${msdkInfo.networkInfo} | CountryCode:${msdkInfo.countryCode} | FirmwareVer:${msdkInfo.firmwareVer} | LDMEnabled:${msdkInfo.isLDMEnabled}"

            msdkInfoTextMain.text = mainInfo
            msdkInfoTextSecond.text = secondInfo
        }
        msdkInfoVM.refreshMSDKInfo()
    }

    private fun updateAvailableCamera(availableCameraList: List<ComponentIndexType>) {
        var ft = supportFragmentManager.beginTransaction()
        val fragmentList = supportFragmentManager.fragments
        for (fragment in fragmentList) {
            ft.remove(fragment!!)
        }
        ft.commitAllowingStateLoss()
        llBase.removeAllViews()
        ft = supportFragmentManager.beginTransaction()
        val onlyOneCamera = availableCameraList.size == 1
        for (cameraIndex in availableCameraList) {
            val frameLayout = FrameLayout(llBase.context)
            frameLayout.id = View.generateViewId()
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            llBase.addView(frameLayout, lp)
            ft.replace(frameLayout.id, CameraStreamDetailFragment.newInstance(cameraIndex, onlyOneCamera), cameraIndex.name)
        }
        ft.commitAllowingStateLoss()
    }
}