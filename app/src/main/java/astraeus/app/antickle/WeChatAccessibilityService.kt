package astraeus.app.antickle

import android.accessibilityservice.AccessibilityService
import android.app.AppOpsManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class WeChatAccessibilityService : AccessibilityService() {

    companion object {

        private const val FLOAT_VIEW_INITIAL_SIZE = 2        // px

        private const val FLOAT_VIEW_INITIAL_POSITION = 0    // px

        private const val FLOAT_VIEW_RESET_DELAY = 1000L     // ms

        var mIsEnable = false
    }

    private val mWindowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    private val mFloatView by lazy {
        View(applicationContext).apply {
            if (BuildConfig.DEBUG) setBackgroundColor(Color.RED)
        }
    }

    private val mLayoutParams by lazy {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        WindowManager.LayoutParams(
            FLOAT_VIEW_INITIAL_SIZE,
            FLOAT_VIEW_INITIAL_SIZE,
            FLOAT_VIEW_INITIAL_POSITION,
            FLOAT_VIEW_INITIAL_POSITION,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            // 取消 WindowManager updateViewLayout 时的动画
            // https://stackoverflow.com/a/33171254
            try {
                val currentFlags = javaClass.getField("privateFlags").get(this) as Int
                javaClass.getField("privateFlags").set(this, currentFlags or 0x00000040)
            } catch (ignore: Exception) {
            }
        }
    }

    private var mFloatViewHasAdd = false

    private val mHandler = Handler(Looper.getMainLooper())

    private var mNotificationId = 704

    private var mIsForeground = false

    private val mAppOpsManager by lazy { getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager }

    private val mCanDrawOverlaysListener = AppOpsManager.OnOpChangedListener { _, _ ->
        mHandler.post {
            canDrawOverlays {
                if (it) {
                    notifyRunning()
                    if (!mFloatViewHasAdd) addFloatView()
                } else notifyToDrawOverlays()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_ACCESSIBILITY_SERVICE_ENABLE -> {
                mIsEnable = true
                notifyRunning()
            }
            Constants.ACTION_ACCESSIBILITY_SERVICE_DISABLE -> {
                stopForeground(true)
                mIsForeground = false
                mIsEnable = false
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        notifyRunning()
        registerCanDrawOverlaysListener()
        resetLayoutParams()
        addFloatView()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (mIsEnable && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && event.className == "android.widget.ImageView") {
            mHandler.removeCallbacksAndMessages(null)
            val windowRect = Rect()
            event.source?.getBoundsInScreen(windowRect)
            mLayoutParams.apply {
                width = windowRect.width()
                height = windowRect.height()
                x = windowRect.left
                y = windowRect.top
            }
            updateFloatView()
            mHandler.postDelayed({ resetFloatView() }, FLOAT_VIEW_RESET_DELAY)
        }
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent): Boolean {
        mHandler.removeCallbacksAndMessages(null)
        mWindowManager.removeView(mFloatView)
        mFloatViewHasAdd = false
        unRegisterCanDrawOverlaysListener()
        stopForeground(true)
        mIsForeground = false
        return super.onUnbind(intent)
    }

    private fun addFloatView() {
        canDrawOverlays {
            if (it) {
                mWindowManager.addView(mFloatView, mLayoutParams)
                mFloatViewHasAdd = true
            } else notifyToDrawOverlays()
        }
    }

    private fun updateFloatView() {
        canDrawOverlays {
            if (it) mWindowManager.updateViewLayout(mFloatView, mLayoutParams)
            else notifyToDrawOverlays()
        }
    }

    private fun resetFloatView() {
        resetLayoutParams()
        updateFloatView()
    }

    private fun resetLayoutParams() {
        mLayoutParams.apply {
            width = FLOAT_VIEW_INITIAL_SIZE
            height = FLOAT_VIEW_INITIAL_SIZE
            x = FLOAT_VIEW_INITIAL_POSITION
            y = FLOAT_VIEW_INITIAL_POSITION
        }
    }

    private fun notifyRunning() {
        if (!mIsEnable) return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification =
            createNotification(R.string.was_notification_title_running, null, pendingIntent)
        if (!mIsForeground) {
            mNotificationId++
            startForeground(mNotificationId, notification)
            mIsForeground = true
        } else NotificationManagerCompat.from(this).notify(mNotificationId, notification)
    }

    private fun notifyToDrawOverlays() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !mIsForeground) return
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        NotificationManagerCompat.from(this).notify(
            mNotificationId,
            createNotification(
                R.string.was_notification_title_paused,
                R.string.was_notification_content_click_to_setting,
                pendingIntent
            )
        )
    }

    private fun createNotification(
        titleResId: Int,
        contentResId: Int?,
        pIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(this, Constants.WECHAT_ACCESSIBILITY_SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status_bar).setContentTitle(getString(titleResId))
            .setPriority(NotificationCompat.PRIORITY_LOW).setContentIntent(pIntent).apply {
                contentResId?.also { setContentText(getString(it)) }
            }.build()
    }

    private fun registerCanDrawOverlaysListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        mAppOpsManager.startWatchingMode(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            packageName,
            mCanDrawOverlaysListener
        )
    }

    private fun unRegisterCanDrawOverlaysListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        mAppOpsManager.stopWatchingMode(mCanDrawOverlaysListener)
    }
}