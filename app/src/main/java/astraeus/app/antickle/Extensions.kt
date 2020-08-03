package astraeus.app.antickle

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi

fun Context.canDrawOverlays(callback: (Boolean) -> Unit) {
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this) -> callback(
            true
        )
        Build.VERSION.SDK_INT == Build.VERSION_CODES.O -> {
            val handler = Handler(Looper.getMainLooper())
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = View(applicationContext).apply {
                addOnAttachStateChangeListener(
                    object : View.OnAttachStateChangeListener {

                        override fun onViewAttachedToWindow(v: View) {
                            handler.removeCallbacksAndMessages(null)
                            callback(true)
                            wm.removeView(v)
                        }

                        override fun onViewDetachedFromWindow(v: View) {
                        }
                    }
                )
            }
            val p = WindowManager.LayoutParams(
                10,
                10,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            try {
                wm.addView(v, p)
                handler.postDelayed({ callback(false) }, 500)
            } catch (ignore: Exception) {
                callback(false)
            }
        }
        else -> callback(false)
    }
}

@RequiresApi(Build.VERSION_CODES.M)
fun Context.getManageOverlayIntent() = Intent(
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    Uri.parse("package:${packageName}")
).takeIf { it.resolveActivity(packageManager) != null }

fun Context.getAccessibilitySettingIntent() =
    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).takeIf { it.resolveActivity(packageManager) != null }
