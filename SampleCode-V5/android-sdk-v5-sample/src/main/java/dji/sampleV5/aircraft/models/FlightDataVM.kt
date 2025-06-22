package dji.sampleV5.aircraft.models

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager

class FlightDataVM: ViewModel() {
    //Flight Data Listeners Variable
    val batteryPercent = MutableLiveData<Int>()
    val gpsCount = MutableLiveData<Int>()
    val altitude = MutableLiveData<Double>()
    val location = MutableLiveData<LocationCoordinate2D>()
    val facing = MutableLiveData<Double>()

    fun initFlightDataListener(){
        //Set listener for aircraft data

        BatteryKey.KeyChargeRemainingInPercent.create().listen(this) { percent ->
            percent?.let {
                batteryPercent.postValue(it)
            }
        }
        FlightControllerKey.KeyGPSSatelliteCount.create().listen(this) { gps ->
            gps?.let {
                gpsCount.postValue(it)
            }
        }
        FlightControllerKey.KeyAltitude.create().listen(this) { height ->
            height?.let {
                altitude.postValue(it)
            }
        }
        FlightControllerKey.KeyAircraftLocation.create().listen(this) { location ->
            location?.let {
                this.location.postValue(it)
            }
        }
        FlightControllerKey.KeyCompassHeading.create().listen(this) { degree ->
            degree?.let {
                facing.postValue(it)
            }
        }
    }

    fun cleanFlightDataListener() {
        KeyManager.getInstance().cancelListen(this)
    }
}

object FlightData {
    //Flight Data Current Variable
    var batteryPercent = 0
    var gpsCount = 0
    var altitude = 0.0
    var location = LocationCoordinate2D()
    var facing = 0.0
}