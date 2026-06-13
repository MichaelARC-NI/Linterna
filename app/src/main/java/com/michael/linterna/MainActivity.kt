package com.michael.linterna

import android.content.Intent
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import java.lang.reflect.Method

class MainActivity : AppCompatActivity() {

    private lateinit var switchLinterna: SwitchCompat
    private lateinit var seekBrillo: SeekBar
    private lateinit var tvEstado: TextView
    private lateinit var tvBrillo: TextView
    private lateinit var btnFacebook: Button
    private lateinit var btnTelegram: Button
    private lateinit var btnWhatsApp: Button
    private lateinit var btnYouTube: Button

    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var encendida = false
    private var currentStrength = 100
    private var soportaBrillo = false
    private lateinit var prefs: SharedPreferences

    // Reflection para control de intensidad
    private var setTorchStrengthLevel: Method? = null
    private var maxStrengthValue = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        switchLinterna = findViewById(R.id.switchLinterna)
        seekBrillo = findViewById(R.id.seekBrillo)
        tvEstado = findViewById(R.id.tvEstado)
        tvBrillo = findViewById(R.id.tvBrillo)
        btnFacebook = findViewById(R.id.btnFacebook)
        btnTelegram = findViewById(R.id.btnTelegram)
        btnWhatsApp = findViewById(R.id.btnWhatsApp)
        btnYouTube = findViewById(R.id.btnYouTube)

        prefs = getSharedPreferences("linterna_prefs", MODE_PRIVATE)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        // Detectar cámara con flash
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (flashAvailable) {
                    cameraId = id
                    // Intentar detectar soporte de intensidad via reflection (Android 13+)
                    if (Build.VERSION.SDK_INT >= 33) {
                        try {
                            // Buscar el método setTorchStrengthLevel
                            val method = CameraManager::class.java.getMethod(
                                "setTorchStrengthLevel", String::class.java, Int::class.java
                            )
                            setTorchStrengthLevel = method

                            // Obtener el máximo nivel de intensidad usando reflection
                            val charsClass = chars.javaClass
                            val getMethod = charsClass.getMethod("get", Any::class.java)

                            // Buscar el campo FLASH_INFO_STRENGTH_MAXIMUM en CameraCharacteristics
                            val fields = CameraCharacteristics::class.java.declaredFields
                            for (f in fields) {
                                if (f.name.contains("STRENGTH_MAXIMUM") || f.name.contains("strength")) {
                                    f.isAccessible = true
                                    val key = f.get(null)
                                    if (key != null) {
                                        val result = getMethod.invoke(chars, key)
                                        if (result is Int && result > 0) {
                                            maxStrengthValue = result
                                            soportaBrillo = true
                                        }
                                    }
                                    break
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    break
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al detectar flash: ${e.message}", Toast.LENGTH_LONG).show()
        }

        if (cameraId == null) {
            tvEstado.text = "⚠️ No se detectó flash"
            switchLinterna.isEnabled = false
            seekBrillo.isEnabled = false
        }

        if (!soportaBrillo) {
            tvBrillo.text = "Brillo: 100% (control no disponible)"
            seekBrillo.isEnabled = false
        }

        // Cargar brillo guardado
        currentStrength = prefs.getInt("brillo", 100)
        seekBrillo.progress = currentStrength
        if (soportaBrillo) {
            tvBrillo.text = "Brillo: ${currentStrength}%"
        }

        // Switch ON/OFF
        switchLinterna.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) encenderLinterna() else apagarLinterna()
        }

        // Brillo slider (solo si soportado)
        if (soportaBrillo) {
            seekBrillo.isEnabled = false
            seekBrillo.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    currentStrength = progress
                    tvBrillo.text = "Brillo: ${progress}%"
                    prefs.edit().putInt("brillo", progress).apply()
                    if (encendida) actualizarBrillo()
                }
                override fun onStartTrackingTouch(seek: SeekBar?) {}
                override fun onStopTrackingTouch(seek: SeekBar?) {}
            })
        }

        // Botones de contacto
        btnFacebook.setOnClickListener { abrirUrl("https://www.facebook.com/share/1FvH35yGTn/") }
        btnTelegram.setOnClickListener { abrirUrl("https://t.me/Michael_Antonio_Rodriguez") }
        btnWhatsApp.setOnClickListener { abrirUrl("https://wa.me/message/IABPSKHOKNXLL1") }
        btnYouTube.setOnClickListener { abrirUrl("https://youtube.com/@androidmovil?si=o3AxSWrl1_R2H5us") }
    }

    private fun abrirUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun encenderLinterna() {
        try {
            val id = cameraId ?: return
            if (soportaBrillo && setTorchStrengthLevel != null) {
                try {
                    val nivel = (currentStrength * maxStrengthValue / 100)
                        .coerceIn(1, maxStrengthValue)
                    setTorchStrengthLevel!!.invoke(cameraManager, id, nivel)
                } catch (_: Exception) {}
            }
            cameraManager.setTorchMode(id, true)
            encendida = true
            tvEstado.text = "🔦 Linterna: ENCENDIDA"
            tvEstado.setTextColor(0xFFFFDD00.toInt())
            if (soportaBrillo) seekBrillo.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(this, "Error al encender: ${e.message}", Toast.LENGTH_LONG).show()
            switchLinterna.isChecked = false
        }
    }

    private fun apagarLinterna() {
        try {
            val id = cameraId ?: return
            cameraManager.setTorchMode(id, false)
            encendida = false
            tvEstado.text = "🔦 Linterna: APAGADA"
            tvEstado.setTextColor(0xFFaaaaaa.toInt())
            seekBrillo.isEnabled = false
        } catch (e: Exception) {
            Toast.makeText(this, "Error al apagar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun actualizarBrillo() {
        if (!encendida || !soportaBrillo || setTorchStrengthLevel == null) return
        try {
            val id = cameraId ?: return
            val nivel = (currentStrength * maxStrengthValue / 100)
                .coerceIn(1, maxStrengthValue)
            setTorchStrengthLevel!!.invoke(cameraManager, id, nivel)
            cameraManager.setTorchMode(id, true)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        if (encendida) apagarLinterna()
        super.onDestroy()
    }
}
