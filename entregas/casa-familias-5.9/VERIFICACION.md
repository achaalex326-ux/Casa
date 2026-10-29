# Verificación de Casa Familias 5.9

Los resultados definitivos y hashes están en `verificacion.json` y `SHA256SUMS.txt`. Las pruebas usan datos ficticios: no se enviaron comprobantes, pagos, facturas ni cambios a Supabase real durante este rediseño.

## Comprobaciones

- Compilación de APK debug, pruebas Android y APK optimizado `presentation`: correcta. JDK 17, Gradle 8.11.1, SDK 36, AGP 8.10.1 y Kotlin 2.0.21.
- 94 pruebas unitarias JVM, sin fallos ni omisiones. Incluyen cálculo decimal, contratos HTTP, cuentas, pagos, permisos y definición de pasos del formulario.
- 39 pruebas Android en emulador Android 11/API 30. Incluyen navegación por rol, los accesos de inicio, foto obligatoria antes de revisar un pago QR, historial de una familia al día, bloqueo biométrico, notificaciones, reintentos de depósitos y fotos. Tras el último ajuste de tamaño del título y las etiquetas se repitieron las dos pruebas de inicio y texto extra grande; no se repitió toda la suite funcional. Estas comprueban los límites de cada letra de las etiquetas y que se mantengan en una línea.
- Pruebas Android con pantalla 320×800 y letra del sistema 130%; también tema oscuro con ampliación propia. Los escenarios de cámara automatizados usan archivos y JPEG reales con resultado de cámara inyectado; no prueban un sensor físico.
- Suite web con servidor simulado: 18 comprobaciones de cuentas/centavos, 20 de arranque, 11 de autenticación, acceso de restaurante, 37 de administración, 42 de rotación, 26 pantallas DOM y 7 formularios fuente.
- 168 comprobaciones adicionales en Chromium: cuatro roles, anchos 320/390/768, texto normal/ampliado, ausencia de desbordamiento horizontal y texto de botones dentro de sus límites. Preparación real de imagen a JPEG, cancelación de selección, reintento con el mismo identificador y una sola subida, resumen 80/80/40 y reparto 396,50/66,08 antes de escribir. Todas las solicitudes se interceptan con fixtures locales.
- Firma del APK verificada y coincidente con las entregas anteriores; alineación verificada con `zipalign -P16`. APK versión5.9/código15, sin atributo debuggable. Instalación de actualización y apertura del acceso nativo comprobadas. Los extras de demostración se ignoran en el APK de entrega.
- Fuentes Kotlin revisados con ktfmt 0.54; JavaScript y CSS principales con Prettier 3.5.3.

## Incidencia resuelta durante las pruebas

La primera ejecución Android pasó 37 de 38 escenarios. Un aviso de éxito tras tomar una foto podía tapar **Paso anterior** al ampliar las letras. Se cambió a **Foto adjunta** junto a la imagen. La prueba enfocada y la ejecución completa posterior verifican que la foto y el importe se conservan tras volver del bloqueo de 30 segundos. El informe inicial queda separado del resultado final.

El emulador mostró un aviso del sistema Android al arrancar en frío; se recuperó con Esperar. Las capturas finales se obtuvieron después de esa recuperación. No se incluye esa pantalla del sistema en las capturas de la entrega.

## Límites y publicación

No se ha probado 5.9 en un teléfono físico, sensor biométrico real, Android 16 ni dispositivo de páginas de 16 KB. `zipalign -P16` comprueba alineación, no sustituye esa prueba de ejecución. No se repitió la suite SQL financiera: el servidor no cambió en 5.9; el despliegue verificado de 5.8 conserva sus propias evidencias.

El sitio `casafliagalarza.netlify.app` tiene la misma configuración pública de Supabase que el APK. Se entrega un ZIP estático con `index.html` en la raíz, sin pruebas, APK ni secretos administrativos. **Su despliegue público queda pendiente de la carga manual del propietario.** Las pruebas de navegador fueron locales, no una prueba del nuevo diseño publicado.

El APK usa firma de pruebas y no se publicó en Google Play. La huella es opcional y local; los avisos nativos son periódicos con WorkManager, no push instantáneo. El navegador no obtiene automáticamente estas capacidades nativas.
