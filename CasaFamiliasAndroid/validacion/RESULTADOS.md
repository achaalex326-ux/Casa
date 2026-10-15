# Validación del rediseño

- Gradle `assembleDebug`: BUILD SUCCESSFUL con JDK 17 y Android SDK 35; firma del APK de depuración verificada.
- Primer inicio sin sesión: formulario de correo y contraseña visible. Verificado con la biblioteca Supabase incluida en Chromium y con la prueba `test_startup.cjs`.
- DOM automatizado: 26 vistas y arranque de Familia, Encargado y Administrador aprobados.
- Navegación y diseño en Chromium: perfiles, pagos, detalles, informes y configuración revisados a 320, 360, 390, 430 y 768 píxeles, sin desbordamientos horizontales ni errores JavaScript.
- Formularios con backend simulado: 40 aperturas y restauraciones aprobadas a 320 y 390 píxeles. Conservan sus nodos y manejadores.
- Texto ampliado: preferencias y vistas familiares revisadas a 320 píxeles sin desbordamientos.
- Control para mostrar y ocultar la contraseña: aprobado; no borra el contenido del campo.
- Fuente e ilustraciones locales: carga verificada, sin recursos visuales faltantes.
- No se realizaron escrituras ni pruebas de acceso con cuentas reales de Supabase.
- No se ejecutó un emulador ni se verificó el dispositivo físico del usuario.

El APK generado es de depuración. La distribución en producción requiere una versión release firmada con la clave del propietario.
