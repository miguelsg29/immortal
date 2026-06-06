/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.immortal.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full-screen photo frame as a normal activity. Two jobs:
 *  - on-demand preview (Screensaver tile), and
 *  - the continuation frame: when the system force-wakes the real screensaver
 *    (see [DreamPolicy]), this activity takes over seamlessly.
 *
 * Keep-screen-on policy: held on mains-powered Portals, while charging, or with
 * the battery saver off → the frame is permanent. On the Go on battery with the
 * saver on, the flag is NOT held: at each screen timeout the system's presence
 * policy decides — someone around → the dream (same visuals) takes over again;
 * empty room → the device truly sleeps. Plug/unplug just re-evaluates the flag.
 */
class PhotoFramePreviewActivity : ComponentActivity() {
  private lateinit var frame: PhotoFrameController
  private var powerReceiver: BroadcastReceiver? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Immersive fullscreen.
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    applyKeepScreenOn()
    frame = PhotoFrameController(this)
    frame.onExit = { finish() }
    setContentView(frame.view)
    frame.start()

    if (DreamPolicy.hasBattery(this)) {
      powerReceiver =
          object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
              applyKeepScreenOn()
            }
          }
      registerReceiver(
          powerReceiver,
          IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
          })
    }
  }

  private fun applyKeepScreenOn() {
    val keep =
        DreamPolicy.holdScreenOn(
            hasBattery = DreamPolicy.hasBattery(this),
            batterySaver = ScreensaverConfig.load(this).batterySaver,
            powered = DreamPolicy.isPowered(this),
        )
    if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  // Feed all touches to the controller's gesture detector (tap = exit,
  // horizontal swipe = prev/next) at the window level for reliability.
  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    frame.onTouch(ev)
    return true
  }

  override fun onDestroy() {
    powerReceiver?.let { runCatching { unregisterReceiver(it) } }
    if (this::frame.isInitialized) frame.stop()
    super.onDestroy()
  }
}
