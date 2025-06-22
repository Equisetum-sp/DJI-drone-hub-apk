package dji.sampleV5.aircraft.views

import dji.v5.common.utils.GeoidManager
import dji.v5.ux.core.communication.DefaultGlobalPreferences
import dji.v5.ux.core.communication.GlobalPreferencesManager
import dji.v5.ux.core.util.UxSharedPreferencesUtil

class DJIAircraftMainActivity : DJIMainActivity() {
    override fun prepareUxActivity() {
        UxSharedPreferencesUtil.initialize(this)
        GlobalPreferencesManager.initialize(DefaultGlobalPreferences(this))
        GeoidManager.getInstance().init(this)
    }

    override fun prepareStreamActivity() {
        enableStartButton(CameraStreamActivity::class.java)
    }
}