package dev.localremote.app.connectivity.infrared

interface IrTransmitter {
    suspend fun inspect(): IrHardwareStatus

    /**
     * Returns Sent only after the platform transmit call returns.
     * This confirms signal dispatch by Android, not appliance state.
     */
    suspend fun transmit(signal: IrSignal): IrTransmitResult
}
