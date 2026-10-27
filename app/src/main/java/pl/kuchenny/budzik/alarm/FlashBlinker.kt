package pl.kuchenny.budzik.alarm

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/**
 * Miga latarką aparatu, póki budzik dzwoni.
 *
 * Latarka przez [CameraManager.setTorchMode] nie wymaga uprawnienia do aparatu.
 * Gdy telefon nie ma lampy albo aparat jest zajęty — po prostu nic nie robi.
 */
class FlashBlinker(context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val cameraId: String? = findFlashCamera()

    private var running = false
    private var lit = false

    fun start() {
        if (running || cameraId == null) return
        running = true
        handler.post(blinkTask)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(blinkTask)
        setTorch(false)
    }

    private val blinkTask = object : Runnable {
        override fun run() {
            if (!running) return
            setTorch(!lit)
            handler.postDelayed(this, if (lit) ON_MS else OFF_MS)
        }
    }

    private fun setTorch(on: Boolean) {
        val id = cameraId ?: return
        lit = runCatching { cameraManager?.setTorchMode(id, on) }.isSuccess && on
    }

    private fun findFlashCamera(): String? = runCatching {
        val manager = cameraManager ?: return@runCatching null
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private companion object {
        const val ON_MS = 300L
        const val OFF_MS = 400L
    }
}
