# Verificación ejecutada — 15 de octubre de 2026

| Área | Resultado | Alcance |
|---|---|---|
| Compilación | PASS | debug, AndroidTest y release optimizada con R8 |
| Reglas y exportaciones | 21/21 PASS | dinero decimal; solo importes aprobados aplicados; alias únicamente limpieza; roles; cuotas parciales; contraseñas; nombres; tareas; formularios; XLSX literal y tres hojas |
| Contratos HTTP | 9/9 PASS | Auth/RPC; renovación de sesión; fallo de cierre; recuperación; lecturas familiares; paginación de501filas; sesión migrada conserva token renovado |
| Android real en emulador | 8/8 PASS | nueve pantallas del administrador; cinco pestañas; menús por rol; cuenta familiar abre su turno; encargado; restaurante; recreación; contraseña; PDF; preferencias de tema y texto |
| Android Lint | 0errores, 13avisos | principalmente versiones de librerías; convención de nombres; migración temporal WebView; preferencias síncronas y reglas de backup. No se deshabilitaron comprobaciones |
| Base PostgreSQL desechable | 169/169 PASS | 59backend +41rotación +26hogar compartido +43comunidad nativa; migraciones de lógica aplicadas dos veces |
| Supabase real | PASS en despliegue de006 | tres nuevas funciones disponibles; huellas/conteos de15tablas sin cambios; cero registros comerciales insertados |
| Firma de APK | PASS | certificado SHA256 idéntico a la entrega4.3.1; paquete/version verificados |

Los contratos HTTP usan MockWebServer. Las pruebas Compose se ejecutaron dentro de Android11/API30, emulador x86_64 de320×800con datos ficticios identificados; no acceden a Supabase ni escriben registros reales. La prueba de recreación de Activity verifica estado/navigation y preferencias, sin simular el cierre forzoso de todo Android. La ejecución final incluye desplazamiento real hasta elementos de las listas virtualizadas, restauración de Perfil y comprobación de que Limpieza permanezca en una línea sin recorte.

Las capturas nativas están separadas de las nueve propuestas generadas. La suite final usa letra del sistema130% a320×800; el caso oscuro añade130% de la preferencia de la app (169% combinado). Son ejemplos de diseño, no comprobantes de pagos reales. La prueba SQL excluye explícitamente pg_cron porque la imagen local no lo incluye; la tarea semanal hospedada ya se verificó previamente y no se volvió a ejecutar como prueba.

No se probaron credenciales de una persona real, envío de correo de recuperación, subida del QR bancario o persistencia de registros nuevos en un teléfono físico. Tampoco se verificó una sesión autenticada anterior migrada desde un dispositivo físico. La guía de pruebas del teléfono marca esas comprobaciones pendientes. La instalación como actualización y el arranque de la compilación release pasaron: abrió el acceso nativo e ignoró los extras de datos ficticios; no se publicó en Google Play.

Verificacion.json contiene el resumen estructurado. La evidencia de ejecución se incluye sin registros privados ni tokens.
