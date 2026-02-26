package roro.stellar.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.model.ServiceStatus
import roro.stellar.manager.startup.service.SelfStarterService

class StellarReceiver : BroadcastReceiver() {
    private val adbWirelessHelper = AdbWirelessHelper()

    override fun onReceive(context: Context, intent: Intent) {
        if (!ServiceStatus().isRunning) {
            val scriptModeEnabled = StellarSettings.getPreferences()
                .getBoolean(StellarSettings.BOOT_SCRIPT_ENABLED, false)
            if (!scriptModeEnabled) {
                val wirelessAdbStatus = adbWirelessHelper.validateThenEnableWirelessAdb(
                    context.contentResolver, context
                )
                if (wirelessAdbStatus) {
                    val intentService = Intent(context, SelfStarterService::class.java)
                    context.startService(intentService)
                }
            }
        }
    }
}

