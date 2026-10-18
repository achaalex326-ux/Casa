# Casa Familias 5.2 — pruebas y preparación para Google Play

Versión **5.2**, código **8**, paquete `com.casafamilias.app`. Compatible con Android 8 o posterior, con destino Android 16/API 36. Mantiene la interfaz de 5.1 y las cuentas de la casa actual; el registro de comunidades independientes queda para otra etapa.

## Descargar y revisar

Los archivos están en [entregas/casa-familias-5.2](entregas/casa-familias-5.2): APK optimizado de pruebas, AAB sin firmar, código fuente, capturas, huellas y comprobaciones. Para publicar, sigue [la guía de Google Play](entregas/google-play/GUIA_GOOGLE_PLAY.md). No se ha publicado la aplicación ni creado una cuenta de desarrollador.

El APK usa la variante `presentation`, con la firma de pruebas de este entorno. La variante `release` deja de utilizar esa firma automáticamente y admite una clave de subida propia mediante Android Studio o un archivo local ignorado `casa-upload.properties`. Sin esa clave, el AAB se genera sin firmar y **no se puede enviar a Play Console**.

## Cuotas de las familias del encargado

La familia que paga las facturas no debe pagar otra vez su cuota de servicios. Las dos familias contables configuradas para el encargado tienen esa cuota cubierta automáticamente. Se regularizaron 22 cuotas históricas sin duplicar pagos previos, sin consumir créditos y sin alterar los gastos ni las cuotas de otras familias. El saldo se calcula en el servidor; no se ocultan deudas cambiando solo la pantalla.

Se corrigió el registro de servicios futuros que usaba una columna de pago inexistente. Los gastos extraordinarios mantienen su propia regla de pago y no se cancelan por tener rol de encargado. La app distingue coberturas automáticas, aplicación de crédito, efectivo y QR, y utiliza el número de versión configurado en el proyecto para el perfil y la exportación.

## Funciones e interfaz conservadas

Kotlin, Jetpack Compose Material 3, Navigation Compose y MVVM; azul eléctrico, coral y turquesa; tema claro/oscuro y texto ampliable. Se conservan Supabase, sesiones cifradas con Android Keystore, pagos parciales, QR privado, comprobantes, familias, asignaciones, propuestas, avisos, auditoría y reportes PDF/Excel. El WebView invisible y temporal solo intenta recuperar una sesión antigua; no presenta la interfaz.

El ciclo semanal sigue **Choque → Galarza → Salinas → David → Acha**. Salinas y Salinas2 comparten limpieza y conservan sus identidades contables separadas. No se modificaron la rotación ni las funciones de permisos de la comunidad.

## Verificación y pendientes

Consulta [VERIFICACION.md](entregas/casa-familias-5.2/VERIFICACION.md): 33 pruebas unitarias, 198 comprobaciones SQL aisladas, 8 pruebas Android con datos ficticios, 0 errores de Lint y 17 avisos, bundle validado y comprobaciones de alineación de 16 KB. La firma del APK se compara con 5.1. Los resultados detallados y el ensayo de instalación figuran en la entrega.

No se ha probado esta versión en un teléfono físico ni ejecutado en Android 16 o en un dispositivo de páginas de 16 KB. Las capturas usan datos ficticios; no demuestran pagos reales. Antes de publicar faltan tu clave de subida, la cuenta de Google Play, privacidad y eliminación de cuentas, recursos de tienda y las pruebas/revisión correspondientes. Las guías y borradores señalan esos pendientes expresamente.

Actualiza tu carpeta en GitHub Desktop con **Fetch origin → Pull origin**, y abre `CasaFamiliasAndroid` en Android Studio. El ZIP de la raíz del repositorio y las entregas antiguas son históricos.
