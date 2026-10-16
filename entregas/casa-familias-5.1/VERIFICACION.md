# Comprobaciones de Casa Familias 5.1

Actualización visual del 16 de octubre de 2026. Android 8 o posterior, paquete com.casafamilias.app, versión 5.1, código 7.

- Compilación debug, AndroidTest y release optimizada con R8; firma validada y comparada con la entrega 5.0.
- 30 pruebas unitarias existentes: reglas, dinero decimal, límites de formularios, permisos de navegación, paginación, sesión y contratos HTTP. Los contratos HTTP usan MockWebServer.
- 8 pruebas de interfaz dentro de Android 11/API30: pantallas del administrador, cinco pestañas, familia, encargado, restaurante, recreación de Activity, contraseña, PDF y preferencias de tema/texto. La ejecución usa 320×800 con letra del sistema al 130%; el caso oscuro añade el 130% de la app, equivalente al 169% combinado.
- Android Lint: 0 errores; los 13 avisos conservados corresponden principalmente a versiones de librerías, convenciones y compatibilidad existente. No se deshabilitaron comprobaciones.
- Contraste calculado de las combinaciones principales de texto y fondo: azul/blanco, turquesa/blanco, texto secundario/fondo, coral y botones secundarios. Ratios superiores a 4.5:1; los iconos y estados mantienen etiquetas de texto.
- Instalación del APK release sobre la aplicación existente y comprobación del acceso nativo. La compilación release ignora los extras de datos ficticios.

Las capturas muestran PreviewRepository, un conjunto de ejemplos de solo lectura que no escribe en Supabase. No representan operaciones comerciales reales. En 5.1 no se modificaron las funciones del servidor, sus permisos ni los datos; no se volvieron a ejecutar las pruebas SQL históricas de 5.0 para este cambio visual.

No se ha instalado esta actualización en un teléfono físico desde este entorno ni se han usado contraseñas personales. Verificacion.json y evidencias contienen los resultados de la ejecución. La entrega está destinada a presentar y probar, sin publicación en Google Play.
