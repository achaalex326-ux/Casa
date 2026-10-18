# Casa Familias

Entrega actual: **5.2 nativa, para probar y preparar Google Play**. Conserva el diseño de 5.1. Consulta [la entrega y las pruebas](ENTREGA.md) y [la guía de publicación](entregas/google-play/GUIA_GOOGLE_PLAY.md). Todavía no está publicada en la tienda.

La interfaz utiliza Kotlin, Jetpack Compose, Material 3, Navigation Compose y MVVM. Incluye azul eléctrico con acentos coral y turquesa, botones con iconos, accesos con texto, Inter, tema claro y oscuro, texto ampliable, formularios por pasos y navegación según el rol. Administrador: Inicio, Gastos, Pagos, Familias y Más. Las capturas de la actualización visual están en [diseno/nativo-v5.1](diseno/nativo-v5.1); las propuestas de la versión anterior se conservan en diseno/nativo-v5/propuestas; la [auditoría de funciones](diseno/nativo-v5/AUDITORIA.md) explica su ubicación.

Conserva Supabase, cuentas, cuotas, pagos parciales, crédito, comprobantes, QR privado, nombres y asignaciones de familias, limpieza semanal, propuestas, avisos, cierre de meses, auditoría y reportes PDF/Excel. Fondo Común no aparece en las consultas ni en las rutas activas. Los datos históricos no se eliminan.

El ciclo semanal sigue siendo **Choque → Galarza → Salinas → David → Acha**. Salinas y Salinas2 comparten limpieza; sus registros contables permanecen separados. Las funciones familiares de propuestas, votación e intercambio tienen comprobaciones de propiedad en Supabase.

El código actual está en **CasaFamiliasAndroid/**. `CasaFamiliasAndroid.zip` y las fichas de la versión anterior se conservan como archivos históricos. El APK de presentación se compila con `assemblePresentation`, optimizado y con el certificado de pruebas anterior. La variante `release` utiliza una clave de subida propia cuando se configura localmente; sin esa clave, el AAB queda sin firmar y no se puede enviar a Google Play. Android 8 o posterior, destino Android 16/API 36. La aplicación sigue configurada para una sola casa y sus cuentas autorizadas.

## Tener el proyecto en tu computadora

La opción más sencilla es GitHub Desktop:

1. Instala [GitHub Desktop](https://desktop.github.com/) e inicia sesión con tu cuenta de GitHub.
2. Elige **File → Clone repository → URL** y pega `https://github.com/achaalex326-ux/Casa.git`.
3. Elige una carpeta nueva para guardar el repositorio. Conserva tu carpeta anterior mientras compruebas que tienes todos tus archivos.
4. Abre la carpeta `CasaFamiliasAndroid` del repositorio clonado en Android Studio.
5. Cuando haya cambios hechos desde Codex o GitHub, pulsa **Fetch origin** y luego **Pull origin** en GitHub Desktop. Se actualizarán los archivos de esta carpeta.
6. Cuando edites archivos en tu computadora, escribe un resumen del cambio, pulsa **Commit to main** y después **Push origin** para subirlos a GitHub. Si tienes cambios locales pendientes, guárdalos con un commit antes de hacer Pull y resuelve cualquier conflicto que GitHub Desktop indique.

Si tienes archivos adicionales en tu carpeta anterior que no venían en el ZIP, copia solo esos archivos de tu proyecto al repositorio clonado, comprueba los cambios en GitHub Desktop y haz Commit y Push. No copies carpetas `.git`, cachés, contraseñas ni claves de firma.

Si prefieres la terminal y tienes Git instalado:

```bash
git clone https://github.com/achaalex326-ux/Casa.git
cd Casa
```

Para descargar cambios de GitHub, desde esa carpeta y con tus cambios locales guardados:

```bash
git pull --ff-only origin main
```

## Compilar y probar

Consulta [las instrucciones de Android Studio](CasaFamiliasAndroid/README_ABRIR_EN_ANDROID_STUDIO.md). El proyecto requiere JDK 17, Gradle 8.11.1 y Android SDK Platform 36.

En macOS/Linux, desde `CasaFamiliasAndroid`:

```bash
./gradlew assembleDebug
```

En Windows:

```bat
gradlew.bat assembleDebug
```

Pruebas nativas, desde `CasaFamiliasAndroid`:

```bash
./gradlew testDebugUnitTest lintDebug assemblePresentation bundleRelease
./gradlew connectedDebugAndroidTest
```

La segunda orden necesita un dispositivo o emulador Android. Pruebas SQL con Python 3 y Docker, desde `CasaFamiliasAndroid/validacion`: `python test_backend.py`. Usan PostgreSQL desechable; los fixtures nunca se ejecutan en Supabase real. Incluye un conjunto financiero separado: reproduce el fallo de registro mensual, prueba la regularización idempotente y comprueba que ambas familias del encargado tienen su cuota de servicios cubierta sin afectar otras deudas. Las pruebas DOM conservadas corresponden a los recursos web de compatibilidad de 4.3.1 y no validan la nueva interfaz Compose.

En el entorno de Codex, las herramientas y las dependencias ya preparadas se activan así:

```bash
source /workspace/casa-environment/activate.sh
cd "$CASA_PROJECT"
gradle --no-daemon --max-workers=2 --console=plain testDebugUnitTest assembleDebug
```
