# Abrir Casa Familias 5.1 en Android Studio

1. Actualiza el repositorio desde GitHub Desktop: Fetch origin → Pull origin.
2. Abre la carpeta **CasaFamiliasAndroid**, que contiene settings.gradle. No abras el ZIP histórico.
3. Usa JDK 17, Android SDK Platform 35 y conexión para descargar dependencias. El Wrapper fija Gradle 8.9; AGP 8.7.3 y Kotlin 2.0.21 están declarados en el proyecto.
4. Espera Gradle Sync. Selecciona un dispositivo Android 8.0 o posterior y pulsa Run.
5. Inicia sesión con una cuenta existente. No se crean cuentas de demostración en Supabase.

En Windows: `gradlew.bat testDebugUnitTest assembleDebug`. En Linux/macOS: `./gradlew testDebugUnitTest assembleDebug`. Salida: app/build/outputs/apk/debug/app-debug.apk. Para la entrega optimizada: assembleRelease; salida app/build/outputs/apk/release/app-release.apk. La entrega actual usa firma de pruebas compatible con los APK entregados previamente desde este entorno; una compilación hecha en otro equipo puede tener un certificado diferente.

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

La aplicación necesita internet para autenticarse y sincronizar registros. ML Kit está incluido para leer comprobantes sin descargar un modelo; la lectura requiere revisión humana. El selector de documentos de Android proporciona acceso a imágenes sin pedir permiso general de archivos. La campana muestra notificaciones guardadas en Supabase, no se implementa un servicio nuevo de notificaciones push.
