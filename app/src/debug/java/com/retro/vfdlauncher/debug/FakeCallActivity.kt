package com.retro.vfdlauncher.debug

import android.app.Activity
import android.os.Bundle

/**
 * Debug builds only. Some phones drop shell broadcasts, so the fake call is started as an
 * invisible activity instead:
 *   adb shell am start -n com.retro.vfdlauncher/.debug.FakeCallActivity --es stage ringing|calling|active|nobuttons|end
 */
class FakeCallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FakeCallReceiver().onReceive(this, intent)
        finish()
    }
}
