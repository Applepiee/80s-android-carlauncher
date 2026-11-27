package com.retro.vfdlauncher.data.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel

/** One band of the NWD/ATOTO DSP equalizer, as the kernel service and the DSP app see it. */
data class DspBand(val index: Int, val freq: Int, val q: Float, val gain: Int, val channel: Int) {
    /** gain is stored 0..30 with 15 = 0 dB. */
    val db: Int get() = gain - 15
}

/**
 * Client for the head unit's own system service (package com.nwd.kernel, interface
 * com.nwd.kernel.aidl.IKernelFeature), which talks to the car-audio MCU. On ATOTO S8-class units the
 * MCU holds the real volume and the DSP equalizer; Android's own music volume is fixed at max.
 *
 * The service is exported without a permission. Transaction codes and parcel layouts come from a
 * read-only probe of the S8L (dexdump of the service's AIDL stub), not from a decompile, so every
 * call is wrapped and failures are reported rather than thrown.
 *
 * On any other device (a phone, the emulator) [available] stays false and callers use Android audio.
 */
class NwdKernel(private val context: Context) {

    @Volatile private var binder: IBinder? = null
    @Volatile var lastError: String? = null
        private set
    private val waiters = mutableListOf<() -> Unit>()

    val available: Boolean get() = binder?.isBinderAlive == true

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service
            val run = synchronized(waiters) { waiters.toList().also { waiters.clear() } }
            run.forEach { it() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }
    }

    /** True if the unit has the service at all (cheap; no bind). */
    fun isPresent(): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Bind once; [onReady] runs (on the main thread) when the binder is there. */
    fun connect(onReady: () -> Unit = {}) {
        if (available) { onReady(); return }
        synchronized(waiters) { waiters += onReady }
        if (!isPresent()) return
        val intent = Intent(ACTION).setPackage(PACKAGE)
        val ok = runCatching { context.bindService(intent, conn, Context.BIND_AUTO_CREATE) }
            .onFailure { lastError = "bind: ${it.javaClass.simpleName}: ${it.message}" }
            .getOrDefault(false)
        if (!ok && lastError == null) lastError = "bind refused"
    }

    fun disconnect() {
        runCatching { context.unbindService(conn) }
        binder = null
    }

    private inline fun <T> call(code: Int, write: (Parcel) -> Unit, read: (Parcel) -> T): T? {
        val b = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            write(data)
            b.transact(code, data, reply, 0)
            reply.readException()
            read(reply)
        } catch (e: Exception) {
            lastError = "tx $code: ${e.javaClass.simpleName}: ${e.message}"
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** Same path as the unit's own VOL+/VOL- panel keys (MCU key type 0, value 14 / 15). */
    fun pressKey(keyType: Int, keyValue: Int): Boolean =
        call(TX_SEND_KEY_PRESS, { it.writeByte(keyType.toByte()); it.writeByte(keyValue.toByte()) }) { true } == true

    fun volumeUp() = pressKey(0, KEY_VOL_UP)
    fun volumeDown() = pressKey(0, KEY_VOL_DOWN)

    fun getAudioParam(type: Int): Int? = call(TX_GET_AUDIO_PARAM, { it.writeInt(type) }) { it.readInt() }

    /** Sets one EQ band in the MCU (what the DSP app sends when a slider moves). */
    fun setBand(b: DspBand): Boolean = call(TX_SET_NEW_DSP_EQ, { p ->
        p.writeInt(1) // non-null parcelable
        p.writeInt(b.index)
        p.writeInt(b.freq)
        p.writeFloat(b.q)
        p.writeInt(b.gain)
        p.writeInt(b.channel)
    }) { true } == true

    companion object {
        const val PACKAGE = "com.nwd.kernel"
        const val ACTION = "com.nwd.kernel.service.KernelService"
        const val DESCRIPTOR = "com.nwd.kernel.aidl.IKernelFeature"

        const val TX_SEND_KEY_PRESS = 10
        const val TX_GET_AUDIO_PARAM = 23
        const val TX_SET_NEW_DSP_EQ = 47

        const val KEY_VOL_UP = 14
        const val KEY_VOL_DOWN = 15

        /** getAudioParam type for the MCU's system volume. */
        const val PARAM_SYSTEM_VOLUME = 14
    }
}
