package com.appkitz.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * Manifest-registered receiver for the PackageInstaller result broadcast.
 *
 * It is registered statically in AndroidManifest.xml (exported=false) so it
 * belongs to the application process rather than to any particular Activity.
 * This is what makes the install flow robust: even if [InstallActivity] has
 * been destroyed by the time the system PackageInstaller reports back (for
 * example after the user confirms the install dialog), this receiver is still
 * alive to dispatch the result.
 *
 * It also forwards the [PackageInstaller.EXTRA_STATUS] / message to
 * [InstallService] so the service can stop itself.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
        )
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system wants to show its install-confirmation Activity.
                // Launch it from here (FLAG_ACTIVITY_NEW_TASK) so it does not
                // depend on the originating Activity being alive.
                val confirmIntent = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    try {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Cannot launch confirm intent", e)
                        toast(context, "无法打开安装确认页面：${e.message ?: "未知错误"}")
                        finishInstall(context)
                    }
                } else {
                    toast(context, "无法打开安装确认页面")
                    finishInstall(context)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                toast(context, "安装成功")
                finishInstall(context)
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                toast(context, "安装失败 [$status]: ${message ?: "未知错误"}")
                finishInstall(context)
            }
        }
    }

    private fun finishInstall(context: Context) {
        // Stop the foreground install service; the work is done.
        context.stopService(Intent(context, InstallService::class.java))
    }

    private fun toast(context: Context, text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "InstallResultReceiver"
    }
}
