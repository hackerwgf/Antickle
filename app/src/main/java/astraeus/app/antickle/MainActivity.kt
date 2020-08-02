package astraeus.app.antickle

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {

        private const val ANIMATION_DURATION = 200L    // ms
    }

    private var mIsInitialSetup = false

    private var mIsSetup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initNotificationChannel()

        switcherContainer.background = GradientDrawable().apply {
            color = ColorStateList.valueOf(Color.WHITE)
            // radius 100dp
            cornerRadius = 100 * resources.displayMetrics.density + 0.5f
        }

        switcherView.background = ShapeDrawable(OvalShape()).apply {
            paint.color = ContextCompat.getColor(this@MainActivity, R.color.background_color)
        }

        switcherColorView.background = ShapeDrawable(OvalShape()).apply {
            paint.color = ContextCompat.getColor(this@MainActivity, R.color.theme_color_primary)
        }

        switcherContainer.setOnClickListener {
            canDrawOverlays { canDrawOver ->
                val accessibilityEnabled = isAccessibilityEnabled()
                val serviceEnable = WeChatAccessibilityService.mIsEnable
                when {
                    canDrawOver && accessibilityEnabled && serviceEnable -> {
                        startService(
                            Intent(this, WeChatAccessibilityService::class.java).apply {
                                action = Constants.ACTION_ACCESSIBILITY_SERVICE_DISABLE
                            }
                        )
                        animateToOff()
                    }
                    canDrawOver && accessibilityEnabled && !serviceEnable -> {
                        startService(
                            Intent(this, WeChatAccessibilityService::class.java).apply {
                                action = Constants.ACTION_ACCESSIBILITY_SERVICE_ENABLE
                            }
                        )
                        animateToOn()
                    }
                    !canDrawOver && !accessibilityEnabled -> {
                        showNeedPermissionDialog(R.string.was_need_draw_over_and_accessibility) {
                            getManageOverlayIntent()?.also {
                                mIsInitialSetup = true
                                startActivity(it)
                            }
                        }
                    }
                    !canDrawOver -> showNeedPermissionDialog(R.string.was_need_draw_over) {
                        getManageOverlayIntent()?.also {
                            mIsSetup = true
                            startActivity(it)
                        }
                    }
                    !accessibilityEnabled -> showNeedPermissionDialog(R.string.was_need_accessibility) {
                        getAccessibilitySettingIntent()?.also {
                            mIsSetup = true
                            startActivity(it)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        switcherView.post {
            canDrawOverlays { canDrawOver ->
                val accessibilityEnabled = isAccessibilityEnabled()
                val serviceEnable = WeChatAccessibilityService.mIsEnable
                when {
                    mIsInitialSetup ->
                        when {
                            canDrawOver && !accessibilityEnabled -> showNeedPermissionDialog(R.string.was_need_accessibility_almost_done) {
                                getAccessibilitySettingIntent()?.also {
                                    mIsInitialSetup = true
                                    startActivity(it)
                                }
                            }
                            canDrawOver && accessibilityEnabled -> {
                                startService(
                                    Intent(this, WeChatAccessibilityService::class.java).apply {
                                        action = Constants.ACTION_ACCESSIBILITY_SERVICE_ENABLE
                                    }
                                )
                                animateToOn()
                            }
                        }
                    mIsSetup && canDrawOver && accessibilityEnabled -> {
                        startService(
                            Intent(this, WeChatAccessibilityService::class.java).apply {
                                action = Constants.ACTION_ACCESSIBILITY_SERVICE_ENABLE
                            }
                        )
                        animateToOn()
                    }
                    canDrawOver && accessibilityEnabled && serviceEnable -> animateToOn()
                    else -> animateToOff()
                }
                mIsInitialSetup = false
                mIsSetup = false
            }
        }
    }

    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannel(
                Constants.WECHAT_ACCESSIBILITY_SERVICE_CHANNEL_ID,
                getString(R.string.was_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
            }
        )
    }

    private fun animateToOn() {
        val xOffset = switcherContainer.width - switcherContainer.paddingLeft - switcherView.width
        switcherView.animate().x(xOffset.toFloat()).setDuration(ANIMATION_DURATION).start()
        switcherColorView.animate().alpha(1f).setDuration(ANIMATION_DURATION).start()
        backgroundColorView.animate().alpha(1f).setDuration(ANIMATION_DURATION).start()
    }

    private fun animateToOff() {
        switcherView.animate().x(switcherContainer.paddingLeft.toFloat())
            .setDuration(ANIMATION_DURATION).start()
        switcherColorView.animate().alpha(0f).setDuration(ANIMATION_DURATION).start()
        backgroundColorView.animate().alpha(0f).setDuration(ANIMATION_DURATION).start()
    }

    private fun isAccessibilityEnabled() =
        (getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .find { it.id.startsWith(packageName) }?.let { true } ?: false

    private fun showNeedPermissionDialog(msgId: Int, confirmAction: () -> Unit) {
        AlertDialog.Builder(this, R.style.DialogTheme).setMessage(msgId)
            .setPositiveButton(R.string.confirm) { _, _ -> confirmAction() }
            .setNegativeButton(R.string.cancel, null).show()
    }
}


