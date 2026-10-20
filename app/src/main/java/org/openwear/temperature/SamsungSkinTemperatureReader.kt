package org.openwear.temperature

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Proxy

internal data class TemperatureSample(
    val skinCelsius: Float,
    val ambientCelsius: Float,
    val accuracy: Int,
    val timestampMillis: Long
)

internal data class MeasurementResult(
    val meanCelsius: Float?,
    val spreadCelsius: Float?,
    val sampleCount: Int,
    val message: String
)

internal class SamsungSkinTemperatureReader(
    private val context: Context,
    private val onResult: (MeasurementResult) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val samples = mutableListOf<TemperatureSample>()
    private var manager: Any? = null
    private var listener: Any? = null
    private var timeout: Runnable? = null
    private var stopped = true

    fun start() {
        stop()
        samples.clear()
        stopped = false
        try {
            val managerClass = Class.forName("com.samsung.android.hardware.sensormanager.SemSensorManager")
            val listenerClass = Class.forName("com.samsung.android.hardware.sensormanager.SemSensorListener")
            val sensorEventClass = Class.forName("com.samsung.android.hardware.sensormanager.SemSensorEvent")
            val skinEventClass = Class.forName("com.samsung.android.hardware.sensormanager.SemSkinTemperatureSensorEvent")
            val sensorId = 65578
            val sensorManager = managerClass.getConstructor(Context::class.java).newInstance(context)
            val sensorListener = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                when (method.name) {
                    "onEventChanged" -> {
                        args?.firstOrNull()?.let { event ->
                            if (sensorEventClass.isInstance(event) && skinEventClass.isInstance(event)) {
                                mainHandler.post { collect(event, skinEventClass) }
                            }
                        }
                    }
                    "onAccuracyChanged" -> Unit
                    "toString" -> "SkinTemperatureListener"
                    "hashCode" -> System.identityHashCode(this)
                    "equals" -> args?.firstOrNull() === this
                }
                null
            }
            manager = sensorManager
            listener = sensorListener

            val attributeClass = Class.forName("com.samsung.android.hardware.sensormanager.SemSkinTemperatureSensorAttribute")
            val attribute = attributeClass.getConstructor().newInstance()
            val paramClass = Class.forName("com.samsung.android.hardware.sensormanager.SemSkinTemperatureSensorParam")
            val modeClass = Class.forName("com.samsung.android.hardware.sensormanager.SemSkinTemperatureSensorParam\$Mode")
            val onDemand = modeClass.enumConstants?.firstOrNull { it.toString().equals("ONDEMAND", true) }
            if (onDemand == null) {
                finish("三星皮肤温度 SDK 不支持按需测量")
                return
            }
            attributeClass.getMethod("setMode", modeClass).invoke(attribute, onDemand)

            val registerMethod = managerClass.methods.firstOrNull {
                it.name == "registerListener" && it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == listenerClass && it.parameterTypes[1] == Int::class.javaPrimitiveType
            } ?: run {
                finish("找不到三星温度传感器注册接口")
                return
            }
            val resultCode = registerMethod.invoke(sensorManager, sensorListener, sensorId, attribute) as? Int ?: -1
            if (resultCode != 0) {
                finish("手表未接受测量请求；请确认机型、Health Platform 和权限支持")
                return
            }
            timeout = Runnable { finish("测量超时：未取得足够的有效样本") }.also {
                mainHandler.postDelayed(it, MEASUREMENT_TIMEOUT_MS)
            }
        } catch (_: ClassNotFoundException) {
            finish("未发现三星皮肤温度 SDK。请使用支持该传感器的 Galaxy Watch")
        } catch (_: SecurityException) {
            finish("传感器访问被拒绝，请检查皮肤温度和身体传感器权限")
        } catch (_: Throwable) {
            finish("无法连接三星皮肤温度传感器或 Health Platform")
        }
    }

    fun stop() {
        stopped = true
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
        val currentManager = manager
        val currentListener = listener
        if (currentManager != null && currentListener != null) {
            runCatching {
                val method = currentManager.javaClass.methods.first {
                    it.name == "unregisterListener" && it.parameterTypes.size == 2
                }
                method.invoke(currentManager, currentListener, 65578)
            }
        }
        manager = null
        listener = null
    }

    private fun collect(event: Any, eventClass: Class<*>) {
        if (stopped) return
        runCatching {
            val objectValues = eventClass.getMethod("getObjectTemperatureList").invoke(event) as? FloatArray
            val ambientValues = eventClass.getMethod("getAmbientTemperatureList").invoke(event) as? FloatArray
            val accuracyValues = eventClass.getMethod("getAccuracyList").invoke(event) as? IntArray
            val size = minOf(objectValues?.size ?: 0, ambientValues?.size ?: 0, accuracyValues?.size ?: 0)
            val now = System.currentTimeMillis()
            for (index in 0 until size) {
                val skin = objectValues!![index]
                val ambient = ambientValues!![index]
                val accuracy = accuracyValues!![index]
                if (TemperatureQuality.isValidSample(skin, ambient, accuracy)) {
                    samples += TemperatureSample(skin, ambient, accuracy, now)
                }
            }
        }
        TemperatureQuality.summarize(samples)?.let { summary ->
            finish(summary.message, summary.meanCelsius, summary.spreadCelsius)
        }
    }

    private fun finish(message: String, mean: Float? = null, spread: Float? = null) {
        val wasActive = !stopped
        val count = samples.size
        stop()
        if (wasActive) mainHandler.post { onResult(MeasurementResult(mean, spread, count, message)) }
    }

    companion object {
        private const val MEASUREMENT_TIMEOUT_MS = 90_000L
    }
}
