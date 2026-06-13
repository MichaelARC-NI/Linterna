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
    private lateinit var layoutBrillo: android.view.View
    private lateinit var btnFacebook: Button
    private lateinit var btnTelegram: Button
    private lateinit var btnWhatsApp: Button
    private lateinit var btnYouTube: Button

    // Camera
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var encendida = false
    private var nivelBrillo = 80

    // Native brightness (Android 13+ API pública)
    private var brilloNativo = false
    private var maxStrength = 1

    // Root mode
    private var rootMode = false
    private lateinit var prefs: SharedPreferences

    companion object {
        private val SYSFS = arrayOf(
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
        layoutBrillo = findViewById(R.id.layoutBrillo)
        btnFacebook = findViewById(R.id.btnFacebook)
        btnTelegram = findViewById(R.id.btnTelegram)
        btnWhatsApp = findViewById(R.id.btnWhatsApp)
        btnYouTube = findViewById(R.id.btnYouTube)

        prefs = getSharedPreferences("linterna_prefs", MODE_PRIVATE)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Detectar flash
        detectarFlash()

        // Cargar preferencias
        rootMode = prefs.getBoolean("root_mode", false)
        nivelBrillo = prefs.getInt("brillo", 80)

        // Configurar SeekBar de 0 a 100
        seekBrillo.max = 100
        seekBrillo.progress = nivelBrillo
        tvBrillo.text = "Brillo: ${nivelBrillo}%"
        // Slider siempre activo para preconfigurar brillo
        seekBrillo.isEnabled = true

        // Mostrar/ocultar slider según soporte nativo
        checkBrilloVisibility()

        // Switch
        switchLinterna.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) encender() else apagar()
        }

        // SeekBar
        seekBrillo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                nivelBrillo = p
                tvBrillo.text = "Brillo: ${p}%"
                prefs.edit().putInt("brillo", p).apply()
                if (encendida) cambiarBrillo(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Long press → Root config
        tvEstado.setOnLongClickListener {
            mostrarConfigRoot()
            true
        }

        // Contacto
        btnFacebook.setOnClickListener { abrir("https://www.facebook.com/share/1FvH35yGTn/") }
        btnTelegram.setOnClickListener { abrir("https://t.me/Michael_Antonio_Rodriguez") }
        btnWhatsApp.setOnClickListener { abrir("https://wa.me/message/IABPSKHOKNXLL1") }
        btnYouTube.setOnClickListener { abrir("https://youtube.com/@androidmovil?si=o3AxSWrl1_R2H5us") }
    }

    // =========================================================================
    // DETECCIÓN
    // =========================================================================

    private fun detectarFlash() {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    cameraId = id
                    // Android 13+ API pública para brillo nativo
                    if (Build.VERSION.SDK_INT >= 33) {
                        val fieldNames = arrayOf(
                            "FLASH_INFO_STRENGTH_MAX_LEVEL",
                            "FLASH_INFO_STRENGTH_MAXIMUM_LEVEL"
                        )
                        for (name in fieldNames) {
                            try {
                                val field = CameraCharacteristics::class.java.getField(name)
                                val key = field.get(null)
                                if (key is CameraCharacteristics.Key<*>) {
                                    @Suppress("UNCHECKED_CAST")
                                    val maxLevel = chars.get(key as CameraCharacteristics.Key<Int>)
                                    if (maxLevel != null && maxLevel > 1) {
                                        maxStrength = maxLevel
                                        brilloNativo = true
                                        break
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    break
                }
            }
        } catch (_: Exception) {}

        if (cameraId == null) {
            tvEstado.text = "❌ No se detectó flash en el dispositivo"
            switchLinterna.isEnabled = false
            seekBrillo.isEnabled = false
        }
    }

    private fun checkBrilloVisibility() {
        if (rootMode) {
            layoutBrillo.visibility = android.view.View.VISIBLE
            seekBrillo.isEnabled = true
            tvEstado.text = "🔧 Modo Root activo | Control total de brillo"
        } else if (brilloNativo) {
            layoutBrillo.visibility = android.view.View.VISIBLE
            seekBrillo.isEnabled = true
            tvEstado.text = "✨ Brillo ajustable disponible | Arrastra para cambiar intensidad"
        } else {
            layoutBrillo.visibility = android.view.View.GONE
            if (cameraId != null) {
                tvEstado.text = "ℹ️ Brillo graduado no soportado en este dispositivo"
            }
        }
    }

    // =========================================================================
    // ENCENDER / APAGAR
    // =========================================================================

    private fun encender() {
        try {
            val id = cameraId ?: return

            if (brilloNativo && Build.VERSION.SDK_INT >= 33) {
                val nivel = (nivelBrillo * maxStrength / 100).coerceIn(1, maxStrength)
                try {
                    val metodo = CameraManager::class.java
                        .getMethod("turnOnTorchWithStrengthLevel", String::class.java, Int::class.java)
                    metodo.invoke(cameraManager, id, nivel)
                } catch (e1: Exception) {
                    cameraManager.setTorchMode(id, true)
                }
            } else if (rootMode) {
                cameraManager.setTorchMode(id, true)
                escribirSysfs(nivelBrillo)
            } else {
                cameraManager.setTorchMode(id, true)
            }

            encendida = true
            seekBrillo.isEnabled = true
            tvEstado.text = "🔦 Linterna: ENCENDIDA"
            tvEstado.setTextColor(0xFFFFDD00.toInt())

        } catch (e: Exception) {
            tvEstado.text = "⚠️ Error: ${e.message}"
            tvEstado.setTextColor(0xFFFF4444.toInt())
            encendida = false
            switchLinterna.isChecked = false
        }
    }

    private fun apagar() {
        try {
            val id = cameraId ?: return
            if (rootMode) escribirSysfs(0)
            else cameraManager.setTorchMode(id, false)
            encendida = false
            seekBrillo.isEnabled = true
            tvEstado.text = "🔦 Linterna: APAGADA"
            tvEstado.setTextColor(0xFFaaaaaa.toInt())
        } catch (e: Exception) {
            tvEstado.text = "⚠️ Error: ${e.message}"
        }
    }

    // =========================================================================
    // CAMBIAR BRILLO
    // =========================================================================

    private fun cambiarBrillo(porcentaje: Int) {
        if (!encendida) return
        try {
            val id = cameraId ?: return
            if (rootMode) {
                escribirSysfs(porcentaje)
                return
            }
            if (brilloNativo && Build.VERSION.SDK_INT >= 33) {
                val nivel = (porcentaje * maxStrength / 100).coerceIn(1, maxStrength)
                try {
                    val metodo = CameraManager::class.java
                        .getMethod("turnOnTorchWithStrengthLevel", String::class.java, Int::class.java)
                    metodo.invoke(cameraManager, id, nivel)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // ROOT
    // =========================================================================

    private fun escribirSysfs(porcentaje: Int): Boolean {
        val valor = (porcentaje * 255 / 100).coerceIn(0, 255)
        for (ruta in SYSFS) {
            try {
                val proc = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "chmod 666 $ruta 2>/dev/null; echo $valor > $ruta")
                )
                if (proc.waitFor() == 0) {
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
    // CONFIGURACIÓN AVANZADA
    // =========================================================================

    private fun mostrarConfigRoot() {
        val rootOK = verificarRoot()
        val cb = CheckBox(this)
        cb.isChecked = rootMode
        cb.isEnabled = rootOK
        cb.text = if (rootOK) "Forzar control por hardware (Root)"
                  else "❌ Root no detectado en este dispositivo"

        AlertDialog.Builder(this)
            .setTitle("⚙️ Configuración Avanzada")
            .setView(cb)
            .setPositiveButton("Aceptar") { _, _ ->
                if (cb.isChecked && !rootOK) {
                    tvEstado.text = "❌ Root no disponible"
                    return@setPositiveButton
                }
                if (cb.isChecked != rootMode) {
                    rootMode = cb.isChecked
                    prefs.edit().putBoolean("root_mode", rootMode).apply()
                    if (encendida) { apagar(); switchLinterna.isChecked = false }
                    checkBrilloVisibility()
                    tvEstado.text = if (rootMode) "🔧 Modo Root activado" else "ℹ️ Modo Normal"
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =========================================================================
    // UTILERÍAS
    // =========================================================================

    private fun abrir(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Error al abrir enlace", Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        if (encendida) {
            try {
                if (rootMode) escribirSysfs(0)
                else cameraId?.let { cameraManager.setTorchMode(it, false) }
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
