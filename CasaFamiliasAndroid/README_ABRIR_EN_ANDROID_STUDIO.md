# Casa Familias — Android Studio

## Abrir y ejecutar

1. Descomprime el ZIP y abre **CasaFamiliasAndroid** con **File → Open** en Android Studio.
2. Espera a que termine **Gradle Sync**. En **Settings → Build Tools → Gradle**, selecciona un **JDK 17** completo (el JDK de Android Studio si corresponde). Instala Android SDK Platform 35 cuando lo solicite.
3. Selecciona un emulador o celular con Android 8.0 o posterior y pulsa **Run ▶**. Ingresa con una cuenta existente de Casa Familias.

Incluye Gradle Wrapper 8.9 y Android Gradle Plugin 8.7.3. La primera sincronización necesita internet. El proyecto no contiene rutas de SDK particulares: Android Studio crea `local.properties` en tu equipo.

## Qué contiene

Aplicación Android en Java con interfaz local HTML/CSS/JavaScript dentro de WebView. No es una implementación de pantallas con Jetpack Compose. Conserva la lógica funcional actual de `Casa_Familias_Completa_V4/app.js` y la configuración pública de Supabase de esa carpeta, con una nueva capa de presentación que usa las tres imágenes como referencia. Los datos reales proceden de Supabase; los ejemplos de las imágenes no se insertan en la base de datos.

La familia ve su deuda, cuotas, historial de pagos, comprobantes, turnos, tareas, intercambios, propuestas y avisos. El encargado registra gastos y servicios mensuales, revisa pagos, consulta deudas, publica avisos y obtiene reportes. El administrador también crea usuarios, configura QR, gestiona plantillas, turnos, cierre/reapertura mensual, auditoría y respaldos.

Se conservan las reglas de cuotas entre seis familias, pagos parciales, crédito individual, aportes del restaurante, promedios de luz/agua, descuentos de servicios, revisión de comprobantes y sincronización. La creación de usuarios usa la Edge Function `admin-users` ya referenciada en Drive; su despliegue y las funciones SQL deben existir en el servidor.

## Diseño

Fondo crema, tipografía azul oscuro, botones violetas, tarjetas claras, iconos y accesos rápidos pastel, navegación inferior diferenciada por rol. Las tres referencias originales están en `referencias/`.

La reproducción es aproximada: el paisaje y el logo se reconstruyeron en SVG; no son los píxeles originales. Los formularios con reglas adicionales de Drive usan controles del mismo tema y conservan sus campos. No se dibujan marcos de iPhone ni la isla negra en Android.

## Vista de los tres perfiles sin cuenta

Para revisar el diseño en un navegador, abre `app/src/main/assets/web/demo.html`. Si tu navegador restringe archivos locales, sirve esa carpeta con `python -m http.server 5500` y abre `http://localhost:5500/demo.html`. La demostración utiliza datos de ejemplo y no ejecuta operaciones contra Supabase. Permite alternar entre Familia, Encargado y Administrador.

## Archivos para editar

- `app/src/main/assets/web/mobile.css`: colores y apariencia.
- `app/src/main/assets/web/mobile.js`: pantallas y navegación por rol.
- `app/src/main/assets/web/app.js`: reglas y operaciones de la aplicación de Drive, con adaptaciones de presentación y descargas.
- `app/src/main/assets/web/config.js`: URL y clave pública de Supabase tomadas del proyecto proporcionado. No contiene una clave `service_role`.
- `app/src/main/java/com/casafamilias/app/MainActivity.java`: WebView, selección de comprobantes, enlaces de recuperación y guardado de archivos Android.

## Comprobantes y exportaciones

El selector de documentos de Android permite adjuntar imágenes. Los comprobantes se suben al Storage privado configurado por el proyecto. El PDF, Excel y respaldo JSON abren el selector **Guardar como** de Android. Los respaldos JSON exportan registros y rutas de comprobantes, siguiendo el código original; no son una copia de los archivos binarios de Storage.

## Recuperación de contraseña

En Supabase → Authentication → URL Configuration agrega `casafamilias://auth` a las URLs de redirección permitidas. No se modificó esta configuración en tu servidor. El manifiesto registra ese enlace y la aplicación recibe el fragmento de autenticación. La recuperación debe probarse con un correo real.

## Diferencias y límites

- **Fondo común:** no se incluye. El código actual de Drive eliminó esta sección; la carpeta contiene un archivo histórico `ARCHIVO_fondo_comun_eliminado_2026-08-30.json`. El README antiguo y la imagen del encargado aún lo mencionan. No se restauró un módulo retirado del código actual.
- **Notificaciones:** conserva avisos y notificaciones internas mientras la app está abierta. No incluye un servicio de notificaciones push en segundo plano (FCM); la API Notification del navegador no equivale a notificaciones nativas en WebView.
- **OCR:** conserva Tesseract para intentar leer el comprobante. Sus componentes de reconocimiento se descargan por internet; revisa los importes detectados antes de guardar.
- **Conexión:** las operaciones reales requieren internet y que las políticas RLS, tablas y funciones de Supabase estén correctamente desplegadas. No se hicieron escrituras en tu servidor durante la preparación.
- **Validación:** la compilación `assembleDebug` terminó correctamente y produjo un APK de prueba. Se revisó la sintaxis JavaScript y se probaron 26 vistas, el inicio de los tres roles y los formularios con DOM automatizado y un backend simulado. Eso no sustituye una prueba visual en emulador ni una prueba integral con tus cuentas reales.

## Compilar desde terminal

Windows: `gradlew.bat assembleDebug`

macOS/Linux: `chmod +x gradlew` y `./gradlew assembleDebug`

Se requiere JDK 17, SDK Android 35 y conexión para descargar las dependencias de construcción.
