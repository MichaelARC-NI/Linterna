# 🔦 Michael Linterna

**Linterna LED inteligente para Android** con control de intensidad ajustable.

Aplicación que enciende el flash LED de la cámara con un interruptor y permite controlar la potencia de la luz (en dispositivos compatibles con Android 13+).

---

## 🎯 Funcionalidades

- **Switch on/off** para encender y apagar la linterna
- **Control de intensidad** — slider para ajustar el brillo del LED (0–100%)
- **Persistencia** — guarda el nivel de brillo al cerrar la app
- **Apagado automático** al salir de la app
- **Sección de contacto** con enlaces a Facebook, Telegram, WhatsApp y YouTube
- **Interfaz oscura** moderna y limpia

---

## ⚠️ Instalación

> **IMPORTANTE**: En algunos dispositivos (especialmente Android 13+ con políticas de seguridad restrictivas como HyperOS, MIUI, ColorOS, OneUI, etc.) el instalador de paquetes predeterminado **puede bloquear la instalación** mostrando errores como *"No se puede instalar"*, *"Archivo dañado"* o simplemente no dejando instalar.

Si te ocurre eso, usa uno de estos métodos alternativos:

### Método 1 — ADB (recomendado)

Conecta el teléfono a la PC con depuración USB activada:

```bash
adb install MichaelLinterna.apk
```

Si ya está instalada y quieres actualizar:

```bash
adb install -r MichaelLinterna.apk
```

### Método 2 — Shizuku + MT Manager

1. Activa **Shizuku** en tu dispositivo
2. Abre **MT Manager** con permisos de Shizuku
3. Navega a la carpeta donde tienes el APK
4. Mantén presionado el archivo → **Instalar**

### Método 3 — Instalador de sistema (si funciona)

1. Descarga el APK desde la sección **Releases**
2. Ábrelo desde el gestor de archivos
3. Concede el permiso "Instalar apps desconocidas" si lo pide
4. Toca **Instalar**

---

## 🔧 Permisos necesarios

- **Cámara** (`CAMERA`) — para acceder al flash LED
- **Flash** (`FLASHLIGHT`) — para encender el LED

---

## 📱 Cómo usar

1. Abre la app **Michael Linterna**
2. Presiona el interruptor para encender la linterna
3. Ajusta la intensidad con el slider (si tu dispositivo lo soporta)
4. Presiona nuevamente el interruptor para apagar

> **Nota sobre el control de intensidad**: El brillo variable solo funciona en dispositivos con **Android 13+** y hardware de flash compatible. En dispositivos más antiguos, la linterna se encenderá al 100% de brillo.

---

## 🛠 Compilar desde código

```bash
git clone https://github.com/MichaelARC-NI/Linterna.git
cd Linterna
./gradlew assembleRelease
```

El APK firmado estará en `app/build/outputs/apk/release/`.

### Requisitos

- Android Studio Hedgehog (2023.1.1) o superior
- JDK 17
- Android SDK 35
- Kotlin 2.1+

---

## 📋 Notas técnicas

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **Lenguaje**: Kotlin
- **Ofuscación**: R8 (minifyEnabled = true)
- **Package**: `com.michael.linterna`
- **Control de intensidad**: Reflection API (`CameraManager.setTorchStrengthLevel`) en Android 13+

---

## 🔗 Enlaces

- **Repositorio**: https://github.com/MichaelARC-NI/Linterna
- **Descargar APK**: https://github.com/MichaelARC-NI/Linterna/releases
- **Reportar errores**: https://github.com/MichaelARC-NI/Linterna/issues
- **Desarrollador**: Michael Antonio Rodríguez
  - [Facebook](https://www.facebook.com/share/1FvH35yGTn/)
  - [Telegram](https://t.me/Michael_Antonio_Rodriguez)
  - [WhatsApp](https://wa.me/message/IABPSKHOKNXLL1)
  - [YouTube](https://youtube.com/@androidmovil?si=o3AxSWrl1_R2H5us)
