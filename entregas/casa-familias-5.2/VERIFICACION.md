# Verificación de Casa Familias 5.2

18 de octubre de 2026. Paquete `com.casafamilias.app`, versión 5.2/código 8, mínimo Android 8/API 26, destino Android 16/API 36. Kotlin 2.0.21, AGP 8.10.1, Gradle 8.11.1 y JDK 17.

## Comprobaciones realizadas

- 33 pruebas unitarias: 24 de reglas/exportación y 9 contratos HTTP con MockWebServer. Sin fallos, errores ni casos omitidos.
- 198 comprobaciones SQL: 59 de base, 41 de rotación, 26 de hogar compartido, 43 de comunidad y 29 de servicios del encargado. PostgreSQL desechable, con datos ficticios; no se cargaron fixtures en Supabase. pg_cron no se ejecutó en PostgreSQL local.
- La regresión financiera reproduce primero el error de la antigua función al usar la columna inexistente `payment_method`. Después aplica la migración dos veces, verifica la cobertura residual sin duplicados, conserva abonos y créditos, protege las cuotas ajenas y extraordinarias, registra un mes nuevo y comprueba que ambas familias del encargado quedan en cero. También comprueba permisos y el caso de referencia histórica sin deuda.
- 8 pruebas instrumentadas en Android 11/API 30 con datos ficticios de solo lectura: navegación, roles, formularios, contraseña, PDF, recreación de Activity, turno propio y preferencias. Resolución 320 × 800 y fuente del sistema al 130 %; el caso oscuro añade el 130 % de la app, para una escala combinada del 169 %.
- Compilación debug, AndroidTest, presentación optimizada con R8 y bundle release. Android Lint: 0 errores y 17 avisos; no se deshabilitaron comprobaciones. Los avisos incluyen convenciones KTX/Compose, versiones, migración WebView y recomendación de reglas de extracción de datos.
- Bundle validado con bundletool 1.18.2, cuya descarga se verificó contra la huella publicada en GitHub. Manifest del bundle: versión 5.2/código 8, API 36 y no depurable.
- Bibliotecas nativas arm64-v8a y x86_64 del APK y el AAB con segmentos ELF alineados al menos a 16 KB. Configuración del bundle `PAGE_ALIGNMENT_16K`; alineación ZIP del APK verificada con `zipalign -P 16`.
- Certificado del APK de presentación comparado con la entrega 5.1. El AAB queda sin firmar porque no se ha proporcionado una clave de subida del propietario.

## Corrección aplicada en Supabase

La migración `202610180007_payer_services.sql` está aplicada. Conserva el reparto existente de seis identidades contables y la limpieza de cinco hogares. Corrige el registro mensual para que no dependa de una columna de pago inexistente y para guardar ambos enlaces del gasto mensual.

La regularización histórica inserta exclusivamente el importe restante de las cuotas de servicios de las familias configuradas para el encargado, con clase automática, estado aprobado y auditoría. En esta ejecución se insertaron 22 coberturas. Las verificaciones dentro de la transacción comprobaron que las dos familias quedan en cero en servicios y que los pagos previos, gastos, importes de cuotas y créditos permanecen intactos. No se modificaron los permisos, las cuentas ni las deudas de otras familias.

No ejecutar fixtures ni el SQL de pruebas contra Supabase real. No volver a aplicar migraciones como comprobación rutinaria del entorno: una futura regularización debe revisarse según la configuración y los datos del momento.

## Límites

Las pruebas de interfaz usan PreviewRepository, sin escrituras comerciales. Los contratos HTTP usan un servidor simulado. No se ha probado esta versión en un teléfono físico ni en un dispositivo con Android 16, ni se ha iniciado sesión con una contraseña personal o cargado el QR bancario real desde este entorno. Las comprobaciones de 16 KB son de empaquetado y bibliotecas; no equivalen a ejecución en un sistema de páginas de 16 KB.

Los resultados de instalación del APK optimizado figuran en `evidencias/presentation-smoke.json`. No se ha subido la aplicación a Google Play. El AAB sin firmar no es un archivo listo para enviar a revisión.

Durante el ensayo optimizado, el emulador sin aceleración mostró un ANR de **System UI** después de cambiar la configuración de pantalla. El proceso de Casa Familias seguía vivo y el búfer de fallos de la aplicación estaba vacío. Tras pulsar Esperar en el diálogo del sistema, se verificó la pantalla nativa de acceso y que el APK ignora los extras de vista previa. Se registra esta incidencia del entorno y no se presenta como una prueba física.

La configuración de firma de subida se comprobó además generando un bundle con una clave temporal local y verificando su firma. Se eliminó esa clave y se regeneró el bundle sin firmar. La clave temporal y su bundle firmado no se distribuyen; este ensayo no crea ni reemplaza la clave definitiva del propietario.
