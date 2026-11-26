package dev.localremote.app.platform.infrared

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import dev.localremote.app.connectivity.infrared.IrFrequencyRange

interface ConsumerIrDriver {
    fun hasEmitter(): Boolean
    fun getCarrierFrequencies(): List<IrFrequencyRange>?
    fun transmit(carrierFrequencyHz: Int, patternMicros: IntArray)
}

class AndroidConsumerIrDriver(context: Context) : ConsumerIrDriver {
    private val appContext = context.applicationContext
    private val manager: ConsumerIrManager? =
        appContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    override fun hasEmitter(): Boolean {
        val featureDeclared = appContext.packageManager
            .hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)
        return featureDeclared && manager?.hasIrEmitter() == true
    }

    override fun getCarrierFrequencies(): List<IrFrequencyRange>? {
        val ranges = manager?.carrierFrequencies ?: return null
        return ranges.map { range ->
            IrFrequencyRange(
                minHz = range.minFrequency,
                maxHz = range.maxFrequency
            )
        }
    }

    override fun transmit(carrierFrequencyHz: Int, patternMicros: IntArray) {
        val irManager = manager ?: throw IllegalStateException(
            "Android consumer infrared service is unavailable."
        )
        irManager.transmit(carrierFrequencyHz, patternMicros)
    }
}
