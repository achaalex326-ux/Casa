# Abrir Casa Familias 5.6 en Android Studio

1. Actualiza el repositorio desde GitHub Desktop: Fetch origin → Pull origin.
2. Abre la carpeta **CasaFamiliasAndroid**, que contiene settings.gradle. No abras el ZIP histórico.
3. Usa JDK 17, Android SDK Platform 36 y conexión para descargar dependencias. El Wrapper fija Gradle 8.11.1; AGP 8.10.1 y Kotlin 2.0.21 están declarados en el proyecto.
4. Espera Gradle Sync. Selecciona un dispositivo Android 8.0 o posterior y pulsa Run.
5. Inicia sesión con una cuenta existente. No se crean cuentas de demostración en Supabase.
6. Para la huella, registra primero una en los ajustes de Android. En la app: Mi perfil → Activar huella. La activación requiere verificar la biometría y se guarda solo en ese teléfono. AndroidX BiometricPrompt conserva el acceso con contraseña cuando no hay lector o no está disponible.
7. Para avisos del celular: Mi perfil → Activar notificaciones. Android13 o posterior solicita POST_NOTIFICATIONS. Hay un botón de prueba y acceso a los ajustes del sistema. WorkManager revisa los datos con la actividad cerrada, mínimo cada15minutos y sujeto a las restricciones de Android. No es FCM/push instantáneo. No requiere un proyecto Firebase.

En Windows: `gradlew.bat testDebugUnitTest assembleDebug`. En Linux/macOS: `./gradlew testDebugUnitTest assembleDebug`. Salida: app/build/outputs/apk/debug/app-debug.apk. Para la entrega optimizada de pruebas: assemblePresentation; salida app/build/outputs/apk/presentation/app-presentation.apk. La entrega actual usa firma de pruebas compatible con los APK entregados previamente desde este entorno; una compilación hecha en otro equipo puede tener un certificado diferente.

## Organización

- MainActivity.kt: entrada Compose, recuperación y migración única de la sesión anterior.
- ui/: componentes Material 3, pantallas, formularios, navegación y CasaViewModel.
- domain/: roles, registros, dinero decimal y definiciones de formularios.
- data/: REST/Auth/Storage de Supabase, sesión AES-GCM con Android Keystore, OCR y exportaciones.
- app/src/test/: reglas y contratos HTTP con servidor local.
- app/src/androidTest/: navegación y comportamiento de los perfiles en Android.
- validacion/: pruebas SQL aisladas y pruebas web históricas.
- ../supabase/migrations/: permisos y funciones del servidor.

Todas las pantallas son nativas. Solo existe un WebView invisible y temporal para intentar recuperar una vez la sesión de 4.3.1; no presenta la aplicación ni carga scripts. Los recursos web permanecen por compatibilidad y configuración pública. No hay clave administrativa en el APK. Los permisos reales se comprueban en el servidor, además de la navegación por rol.

## Vista de diseño para desarrollo

Solo en compilación debug se puede iniciar MainActivity con extras design_preview=true y preview_role=sysadmin, family, payer o restaurant. Utiliza PreviewRepository de solo lectura y muestra un aviso de datos ficticios. La entrega release ignora esos extras y abre la autenticación real. No uses capturas de ejemplo como evidencia de operaciones reales.

La aplicación necesita internet para autenticarse y sincronizar registros. ML Kit está incluido para leer comprobantes sin descargar un modelo; la lectura requiere revisión humana. El selector de documentos de Android proporciona acceso a imágenes sin pedir permiso general de archivos. La campana muestra avisos guardados en Supabase; los avisos nativos del celular se revisan con WorkManager tras la activación del usuario. Los recordatorios de deuda se calculan desde el saldo vigente, evitando reenviar avisos históricos de deudas saldadas.

## Preparación para Google Play (5.2)

Conserva la interfaz y las cuentas de la casa actual. Usa SDK 36/AGP 8.10.1 y Gradle 8.11.1. `assemblePresentation` genera el APK de pruebas firmado con la clave de pruebas anterior. `bundleRelease` genera el AAB: queda sin firmar hasta configurar una clave de subida propia en Android Studio o en el archivo local ignorado `casa-upload.properties`. No subir claves ni contraseñas a GitHub.

Sigue [la guía de Google Play](../entregas/google-play/GUIA_GOOGLE_PLAY.md). La publicación requiere además completar privacidad, eliminación de cuenta, datos de la tienda, pruebas y revisión de Google.
