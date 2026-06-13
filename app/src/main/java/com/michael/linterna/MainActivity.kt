package com.michael.linterna

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var switchLinterna: Switch
    private lateinit var seekBrillo: SeekBar
    private lateinit var tvBrillo: TextView
    private lateinit var tvEstado: TextView
    private lateinit var btnFacebook: Button
    private lateinit var btnTelegram: Button
    private lateinit var btnWhatsApp: Button
    private lateinit var btnYouTube: Button

    // Camera
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var encendida = false
    private var nivelActual = 50

    // Native brightness (Android 13+ via reflection)
    private var maxStrengthLevel = 1
    private var soportaBrilloNativo = false
    private val handler = Handler(Looper.getMainLooper())

    // Root mode
    private var rootModeActivo = false
    private lateinit var prefs: SharedPreferences

    // Reflection methods
    private var setTorchStrengthMethod: java.lang.reflect.Method? = null
    private var getMaxStrengthMethod: java.lang.reflect.Method? = null

    companion object {
        private val SYSFS_PATHS = arrayOf(
            "/sys/class/leds/flashlight/brightness",
            "/sys/class/leds/led:torch_0/brightness",
            "/sys/class/leds/torch_flash/brightness",
            "/sys/class/leds/white/brightness",
            "/sys/devices/platform/leds-mt65xx/leds/flashlight/brightness"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchLinterna = findViewById(R.id.switchLinterna)
        seekBrillo = findViewById(R.id.seekBrillo)
        tvBrillo = findViewById(R.id.tvBrillo)
        tvEstado = findViewById(R.id.tvEstado)
        btnFacebook = findViewById(R.id.btnFacebook)
        btnTelegram = findViewById(R.id.btnTelegram)
        btnWhatsApp = findViewById(R.id.btnWhatsApp)
        btnYouTube = findViewById(R.id.btnYouTube)

        prefs = getSharedPreferences("linterna_prefs", MODE_PRIVATE)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Detectar flash y capacidades via reflection total
        detectarFlashYCapacidades()

        // Cargar preferencias
        rootModeActivo = prefs.getBoolean("root_mode", false)
        nivelActual = prefs.getInt("brillo", 50)

        // Configurar SeekBar
        configurarSeekBar()

        // Switch
        switchLinterna.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) encender() else apagar()
        }

        // SeekBar
        seekBrillo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                nivelActual = p
                actualizarLabelBrillo()
                prefs.edit().putInt("brillo", p).apply()
                if (encendida) aplicarIntensidad(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Long press → Advanced config
        tvEstado.setOnLongClickListener {
            mostrarDialogoAvanzado()
            true
        }

        // Contact buttons
        btnFacebook.setOnClickListener { abrirUrl("https://www.facebook.com/share/1FvH35yGTn/") }
        btnTelegram.setOnClickListener { abrirUrl("https://t.me/Michael_Antonio_Rodriguez") }
        btnWhatsApp.setOnClickListener { abrirUrl("https://wa.me/message/IABPSKHOKNXLL1") }
        btnYouTube.setOnClickListener { abrirUrl("https://youtube.com/@androidmovil?si=o3AxSWrl1_R2H5us") }
    }

    // =========================================================================
    // DETECCIÓN — TOTAL VIA REFLECTION
    // =========================================================================

    private fun detectarFlashYCapacidades() {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (flashAvailable) {
                    cameraId = id

                    // Intentar obtener FLASH_INFO_STRENGTH_MAXIMUM via reflection
                    if (Build.VERSION.SDK_INT >= 33) {
                        try {
                            val keyField = CameraCharacteristics::class.java
                                .getDeclaredField("FLASH_INFO_STRENGTH_MAXIMUM")
                            keyField.isAccessible = true
                            val keyObj = keyField.get(null) as? CameraCharacteristics.Key<*>
                            if (keyObj != null) {
                                val getMethod = CameraCharacteristics::class.java
                                    .getMethod("get", CameraCharacteristics.Key::class.java)
                                val result = getMethod.invoke(chars, keyObj)
                                if (result is Int && result > 1) {
                                    maxStrengthLevel = result
                                    soportaBrilloNativo = true
                                }
                            }
                        } catch (_: Exception) {}

                        // Obtener método setTorchStrengthLevel via reflection
                        try {
                            setTorchStrengthMethod = CameraManager::class.java
                                .getMethod("setTorchStrengthLevel", String::class.java, Int::class.java)
                        } catch (_: Exception) {}
                    }
                    break
                }
            }
        } catch (e: Exception) {
            mostrarEstado("⚠️ Error: ${e.message}")
        }

        if (cameraId == null) {
            mostrarEstado("⚠️ No se detectó flash")
            switchLinterna.isEnabled = false
            seekBrillo.isEnabled = false
        }
    }

    // =========================================================================
    // SEEK BAR
    // =========================================================================

    private fun configurarSeekBar() {
        if (rootModeActivo && verificarRoot()) {
            seekBrillo.max = 100
            seekBrillo.progress = nivelActual.coerceIn(0, 100)
            seekBrillo.isEnabled = false
            mostrarEstado("🔧 Modo Root activo")
            actualizarLabelBrillo()
            return
        }

        if (soportaBrilloNativo && setTorchStrengthMethod != null) {
            seekBrillo.max = maxStrengthLevel
            seekBrillo.progress = nivelActual.coerceIn(0, maxStrengthLevel)
            seekBrillo.isEnabled = false
            mostrarEstado("💡 Brillo nativo disponible")
        } else {
            seekBrillo.max = 1
            seekBrillo.progress = if (nivelActual > 0) 1 else 0
            seekBrillo.isEnabled = false
            mostrarEstado("ℹ️ Ajuste fino no compatible")
        }
        actualizarLabelBrillo()
    }

    // =========================================================================
    // ENCENDER / APAGAR
    // =========================================================================

    private fun encender() {
        val id = cameraId ?: return
        try {
            if (rootModeActivo) {
                val ok = escribirSysfs(nivelActual.coerceIn(0, 100))
                if (ok) {
                    encendida = true
                    seekBrillo.isEnabled = true
                    mostrarEstado("🔦 Root: ENCENDIDA")
                    tvEstado.setTextColor(0xFFFFDD00.toInt())
                } else {
                    mostrarEstado("❌ Root: fallo. Revisa permisos.")
                    switchLinterna.isChecked = false
                    rootModeActivo = false
                    prefs.edit().putBoolean("root_mode", false).apply()
                    configurarSeekBar()
                }
                return
            }

            if (soportaBrilloNativo && setTorchStrengthMethod != null) {
                val nivel = nivelActual.coerceIn(0, maxStrengthLevel)
                try {
                    setTorchStrengthMethod!!.invoke(cameraManager, id, nivel)
                } catch (_: Exception) {}
                cameraManager.setTorchMode(id, true)
            } else {
                if (nivelActual <= 0) {
                    mostrarEstado("Sube el brillo para encender")
                    switchLinterna.isChecked = false
                    return
                }
                cameraManager.setTorchMode(id, true)
            }

            encendida = true
            seekBrillo.isEnabled = true
            mostrarEstado("🔦 Linterna: ENCENDIDA")
            tvEstado.setTextColor(0xFFFFDD00.toInt())

        } catch (e: Exception) {
            mostrarEstado("⚠️ Error: ${e.message}")
            encendida = false
            switchLinterna.isChecked = false
            seekBrillo.isEnabled = false
        }
    }

    private fun apagar() {
        try {
            val id = cameraId ?: return
            if (rootModeActivo) escribirSysfs(0)
            else cameraManager.setTorchMode(id, false)
            encendida = false
            seekBrillo.isEnabled = false
            mostrarEstado("🔦 Linterna: APAGADA")
            tvEstado.setTextColor(0xFFaaaaaa.toInt())
        } catch (e: Exception) {
            mostrarEstado("⚠️ Error: ${e.message}")
        }
    }

    // =========================================================================
    // APLICAR INTENSIDAD
    // =========================================================================

    private fun aplicarIntensidad(progress: Int) {
        if (!encendida) return
        try {
            val id = cameraId ?: return
            if (rootModeActivo) {
                escribirSysfs(progress.coerceIn(0, 100))
                return
            }
            if (soportaBrilloNativo && setTorchStrengthMethod != null) {
                val nivel = progress.coerceIn(0, maxStrengthLevel)
                try {
                    setTorchStrengthMethod!!.invoke(cameraManager, id, nivel)
                } catch (_: Exception) {}
                cameraManager.setTorchMode(id, true)
            } else {
                if (progress <= 0) {
                    cameraManager.setTorchMode(id, false)
                    encendida = false
                    switchLinterna.isChecked = false
                    seekBrillo.isEnabled = false
                    mostrarEstado("🔦 Linterna: APAGADA")
                    tvEstado.setTextColor(0xFFaaaaaa.toInt())
                } else {
                    cameraManager.setTorchMode(id, true)
                }
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // MODO ROOT — SYSFS
    // =========================================================================

    private fun escribirSysfs(porcentaje: Int): Boolean {
        val valor = (porcentaje * 255 / 100).coerceIn(0, 255)
        for (ruta in SYSFS_PATHS) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c",
                    "chmod 666 $ruta 2>/dev/null; echo $valor > $ruta"))
                val code = proc.waitFor()
                if (code == 0) {
                    val err = BufferedReader(InputStreamReader(proc.errorStream)).readText()
                    if (err.isBlank()) return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun verificarRoot(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val out = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            proc.waitFor()
            out.contains("uid=0") || out.contains("root")
        } catch (_: Exception) { false }
    }

    // =========================================================================
    // DIÁLOGO AVANZADO
    // =========================================================================

    private fun mostrarDialogoAvanzado() {
        val rootOK = verificarRoot()
        val cb = CheckBox(this)
        cb.text = if (rootOK) "Forzar control por hardware (Root)"
                   else "❌ Root no detectado"
        cb.isChecked = rootModeActivo
        cb.isEnabled = rootOK

        val builder = AlertDialog.Builder(this)
        builder.setTitle("⚙️ Configuración Avanzada")
        builder.setView(cb)
        builder.setPositiveButton("Aceptar") { _, _ ->
            if (cb.isChecked && !rootOK) {
                mostrarEstado("❌ Root no disponible")
                return@setPositiveButton
            }
            if (cb.isChecked != rootModeActivo) {
                rootModeActivo = cb.isChecked
                prefs.edit().putBoolean("root_mode", rootModeActivo).apply()
                if (encendida) { apagar(); switchLinterna.isChecked = false }
                configurarSeekBar()
                mostrarEstado(if (rootModeActivo) "🔧 Modo Root" else "ℹ️ Modo Normal")
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    // =========================================================================
    // UTILERÍAS
    // =========================================================================

    private fun actualizarLabelBrillo() {
        tvBrillo.text = when {
            rootModeActivo -> "Brillo: ${nivelActual}% (Root)"
            soportaBrilloNativo -> {
                val pct = (nivelActual * 100 / maxStrengthLevel).coerceIn(0, 100)
                "Brillo: ${pct}% (${nivelActual}/${maxStrengthLevel})"
            }
            else -> if (nivelActual > 0) "Brillo: 100% (ON)" else "Brillo: 0% (OFF)"
        }
    }

    private fun mostrarEstado(msg: String) {
        tvEstado.text = msg
        handler.postDelayed({
            tvEstado.text = if (encendida) "🔦 Linterna: ENCENDIDA"
                            else "🔦 Linterna: APAGADA"
            tvEstado.setTextColor(if (encendida) 0xFFFFDD00.toInt() else 0xFFaaaaaa.toInt())
        }, 4000)
    }

    private fun abrirUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Error al abrir enlace", Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        if (encendida) {
            try {
                if (rootModeActivo) escribirSysfs(0)
                else cameraId?.let { cameraManager.setTorchMode(it, false) }
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
