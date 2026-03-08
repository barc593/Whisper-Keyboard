# 🎤 Whisper Keyboard

Teclado Android con transcripción de voz usando **Whisper** a través de la API de **Groq**.

## Características

- Teclado QWERTY completo con diseño dark mode
- Botón de micrófono integrado para dictado por voz
- Transcripción ultra-rápida via Groq (Whisper Large V3)
- Soporte para 18+ idiomas
- Feedback háptico
- Panel de símbolos y números

## Requisitos

- API key de Groq (gratis en [console.groq.com](https://console.groq.com))
- Android 8.0+ (API 26)

## Compilar el APK

### Opción 1: GitHub Actions (sin instalar nada)

1. Sube este proyecto a un repositorio en GitHub
2. Ve a la pestaña **Actions**
3. Selecciona el workflow **Build APK**
4. Click en **Run workflow**
5. Una vez termine, descarga el APK desde **Artifacts**

### Opción 2: Android Studio

1. Abre el proyecto en Android Studio
2. Espera a que Gradle sincronice
3. **Build > Build Bundle(s) / APK(s) > Build APK(s)**
4. El APK estará en `app/build/outputs/apk/debug/`

### Opción 3: Línea de comandos

```bash
./gradlew assembleDebug
```

## Instalación y configuración

1. Instala el APK en tu dispositivo Android
2. Abre la app **Whisper Keyboard**
3. Ingresa tu **API key de Groq**
4. Toca **Open Keyboard Settings**
5. Activa **Whisper Keyboard** en la lista de teclados
6. Al escribir en cualquier app, cambia al teclado Whisper
7. Toca el botón **🎤** (morado) para dictar por voz

## Estructura del proyecto

```
app/src/main/
├── java/com/whisperkey/keyboard/
│   ├── WhisperKeyboardService.kt  → Servicio IME principal
│   ├── GroqApiService.kt          → Cliente API Groq/Whisper
│   ├── AudioRecorder.kt           → Grabación de audio WAV
│   └── SettingsActivity.kt        → Pantalla de configuración
├── res/
│   ├── layout/
│   │   ├── keyboard_view.xml      → Layout QWERTY
│   │   ├── keyboard_symbols.xml   → Layout de símbolos
│   │   └── activity_settings.xml  → Pantalla de ajustes
│   └── xml/method.xml             → Declaración IME
└── AndroidManifest.xml
```

## Notas técnicas

- El audio se graba en formato WAV 16-bit mono a 16kHz (óptimo para Whisper)
- La API de Groq es compatible con la API de OpenAI, pero significativamente más rápida
- El modelo usado es `whisper-large-v3`
- Las credenciales se guardan en SharedPreferences del dispositivo

## Licencia

MIT
