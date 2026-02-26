package roro.stellar.manager.receiver

import android.Manifest.permission.WRITE_SECURE_SETTINGS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import roro.stellar.Stellar
import roro.stellar.manager.AppConstants
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.adb.AdbWirelessHelper
import roro.stellar.manager.startup.command.Starter
import roro.stellar.manager.startup.service.SelfStarterService
import roro.stellar.manager.util.UserHandleCompat

class BootCompleteReceiver : BroadcastReceiver() {
    private val adbWirelessHelper = AdbWirelessHelper()

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED != intent.action
            && Intent.ACTION_BOOT_COMPLETED != intent.action
        ) {
            return
        }

        if (UserHandleCompat.myUserId() > 0 || Stellar.pingBinder()) return

        val mode = StellarSettings.getBootMode()
        if (mode == StellarSettings.BootMode.NONE || mode == StellarSettings.BootMode.SCRIPT) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val started = if (hasRootPermission()) {
                    rootStart(context) || adbStart(context)
                } else {
                    adbStart(context)
                }
                if (!started) {
                    showToast(context, context.getString(R.string.boot_start_failed, "no available startup path"))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun hasRootPermission(): Boolean {
        return try {
            Shell.getShell().isRoot
        } catch (_: Exception) {
            false
        }
    }

    private fun rootStart(context: Context): Boolean {
        val result = try {
            Shell.cmd(Starter.internalCommand).exec()
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Root boot start failed", e)
            return false
        }

        if (result.code != 0) {
            val err = result.err.joinToString("\n").ifEmpty { "exit code: ${result.code}" }
            Log.w(AppConstants.TAG, "Root boot start command failed: $err")
            return false
        }

        Thread.sleep(3000)
        if (Stellar.pingBinder()) {
            showToast(context, context.getString(R.string.boot_start_success))
            return true
        }

        Log.w(AppConstants.TAG, "Root boot start command succeeded but binder not available")
        return false
    }

    private suspend fun adbStart(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            || context.checkSelfPermission(WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(AppConstants.TAG, "WRITE_SECURE_SETTINGS not granted, skip wireless startup")
            return false
        }

        return try {
            val wirelessAdbStatus = adbWirelessHelper.validateThenEnableWirelessAdbAsync(
                context.contentResolver, context, 15_000L
            )
            if (wirelessAdbStatus) {
                val intentService = Intent(context, SelfStarterService::class.java)
                context.startService(intentService)
                true
            } else {
                false
            }
        } catch (e: SecurityException) {
            Log.e(AppConstants.TAG, "Permission denied while starting wireless adb", e)
            showToast(context, context.getString(R.string.boot_start_failed, e.message ?: ""))
            false
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Failed to start wireless adb", e)
            showToast(context, context.getString(R.string.boot_start_failed, e.message ?: ""))
            false
        }
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}

