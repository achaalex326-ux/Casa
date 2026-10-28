# Verificación 5.8

Paquete com.casafamilias.app; versión 5.8/código 14; mínimo API 26, destino API 36. La entrega incluye APK optimizado de presentación, fuente sin claves locales, capturas de cuentas ficticias y evidencias.

- Compilación: testDebugUnitTest, lintDebug, assembleDebug, assembleDebugAndroidTest y assemblePresentation completados.
- JVM: 92 pruebas, 0 fallos,0 errores,0 omitidas. Incluye ocho casos nuevos de sumas/centavos/descuentos/meses y un contrato HTTP para servidor sin migración.
- Android 11/API 30, emulador 320×800, fuente del sistema 130%:36 pruebas, 0 fallos. Incluye el último mes registrado, factura completa de enero, resumen al principio de Mi cuenta y formulario con Bs396,50 antes de guardar. Conserva navegación por rol, cámara/archivo real con resultado inyectado, pago de varios meses, bloqueo de 30segundos, avisos y texto ampliado/oscuro.
- PostgreSQL 17 desechable, sin red: 55 comprobaciones de la regla y permisos, 7 de reparación/idempotencia/conservación de pagos y2 de reversión ante sobrepago.64 en total. Ejecuta los seis meses de la hoja, verifica suma de cada reparto, gas cero, total semestral 2250,50, ausencia de recorte por promedio, coberturas del encargado, descuento explícito usado una vez, referencia sin deuda, prevención de duplicados y rechazo de un total distinto de la vista previa. La reparación ejecutada dos veces conserva el pago ficticio de 20 y no duplica el respaldo; un sobrepago incompatible revierte toda la operación.
- Lint: 0 errores y 30 avisos, sin desactivar reglas.
- Formato: 34 fuentes Kotlin móviles y de pruebas, ktfmt 0.54/kotlinlang; comprobación dry-run sin cambios. El artefacto del formateador coincide con el checksum SHA1 de Maven Central, descargado con TLS validado.
- APK:apksigner válido, mismo certificado que la entrega anterior; zipalign -P 16 correcto. Instalado como actualización sobre el APK debug de esta sesión; acceso nativo visible y extras de diseño ignorados por la variante optimizada. El certificado común también coincide con 5.7; no se simuló una migración financiera real ni se borraron datos reales.

## Qué no se ha hecho

La lectura administrativa de Supabase devolvióHTTP 401. No se aplicó la migración 010 ni se cargaron/corrigieron las deudas reales. No se subió ningún comprobante ni se creó un pago real. La renovación del secreto y la lectura del estado actual son requisitos para continuar ese trabajo. `supabase/README_CORRECCION_2026.md` contiene los pasos y las condiciones de conciliación.

Las pruebas de fotos usan FileProvider, archivos y JPEG reales, con el resultado de cámara inyectado. La prueba manual de cámara virtual de 5.7 es histórica y no se repitió aquí. No se probó cámara física, sensor de huella real, permiso de notificación en Android 13+, Android 16 ni ejecución en hardware de páginas de 16 KB. La alineación de archivo no equivale a esa prueba de dispositivo. Los avisos usan WorkManager, no push instantáneo. No está publicado en Google Play ni firmado con la clave definitiva del propietario.

Las 277 comprobaciones SQL y 3 escenarios concurrentes de 5.6 son históricos; en 5.8 se ejecutaron las 64 comprobaciones nuevas anteriores. La migración 007 y la condonación puntual anterior no se volvieron a ejecutar. No se borraron familias ni se fusionaron sus cuentas financieras.
