# Casa Familias 5.3 — acceso con huella

Versión **5.3**, código **9**, paquete `com.casafamilias.app`. Compatible con Android 8 o posterior, con destino Android 16/API 36. Mantiene la interfaz de 5.1 y las cuentas de la casa actual; el registro de comunidades independientes queda para otra etapa.

## Descargar y revisar

Los archivos actuales están en [entregas/casa-familias-5.3](entregas/casa-familias-5.3): APK optimizado, código fuente e instrucciones para activar la huella. La [entrega 5.2](entregas/casa-familias-5.2) conserva su AAB sin firmar y sus verificaciones históricas. Para publicar, sigue [la guía de Google Play](entregas/google-play/GUIA_GOOGLE_PLAY.md). No se ha publicado la aplicación ni creado una cuenta de desarrollador.

El APK usa la variante `presentation`, con la firma de pruebas de este entorno. La variante `release` deja de utilizar esa firma automáticamente y admite una clave de subida propia mediante Android Studio o un archivo local ignorado `casa-upload.properties`. Sin esa clave, el AAB se genera sin firmar y **no se puede enviar a Play Console**.

## Cuotas de las familias del encargado

Por solicitud posterior del propietario se condonaron las deudas que estaban pendientes el 18 de octubre de 2026 (hora de Bolivia). El servidor confirmó cero deudas en las seis identidades contables. Los importes anteriores y el ajuste quedaron auditados; se conservaron facturas, pagos y créditos y no se registró dinero ficticio. Ese ajuste puntual no cancela las cuotas que se registren en el futuro.

La familia que paga las facturas no debe pagar otra vez su cuota de servicios. Las dos familias contables configuradas para el encargado tienen esa cuota cubierta automáticamente. Se regularizaron 22 cuotas históricas sin duplicar pagos previos, sin consumir créditos y sin alterar los gastos ni las cuotas de otras familias. El saldo se calcula en el servidor; no se ocultan deudas cambiando solo la pantalla.

Se corrigió el registro de servicios futuros que usaba una columna de pago inexistente. Los gastos extraordinarios mantienen su propia regla de pago y no se cancelan por tener rol de encargado. La app distingue coberturas automáticas, aplicación de crédito, efectivo y QR, y utiliza el número de versión configurado en el proyecto para el perfil y la exportación.

## Funciones e interfaz conservadas

La versión 5.3 incorpora AndroidX BiometricPrompt para un bloqueo local opcional. Solo se activa tras iniciar sesión y verificar la biometría; cancelar o fallar no desbloquea la app. El correo y la contraseña válidos son la alternativa. Cerrar sesión desactiva la opción en el teléfono; otra cuenta debe activarla de nuevo. Se protege la ventana con FLAG_SECURE mientras está activa. Este bloqueo local complementa la sesión cifrada existente: no cambia los permisos de Supabase ni constituye una nueva autenticación biométrica en el servidor.

Kotlin, Jetpack Compose Material 3, Navigation Compose y MVVM; azul eléctrico, coral y turquesa; tema claro/oscuro y texto ampliable. Se conservan Supabase, sesiones cifradas con Android Keystore, pagos parciales, QR privado, comprobantes, familias, asignaciones, propuestas, avisos, auditoría y reportes PDF/Excel. El WebView invisible y temporal solo intenta recuperar una sesión antigua; no presenta la interfaz.

El ciclo semanal sigue **Choque → Galarza → Salinas → David → Acha**. Salinas y Salinas2 comparten limpieza y conservan sus identidades contables separadas. No se modificaron la rotación ni las funciones de permisos de la comunidad.

## Verificación y pendientes

Consulta [la verificación de 5.3](entregas/casa-familias-5.3/VERIFICACION.md) para la compilación actual. La [verificación de 5.2](entregas/casa-familias-5.2/VERIFICACION.md) conserva sus 33 pruebas unitarias, 198 comprobaciones SQL aisladas, 8 pruebas Android con datos ficticios y comprobaciones de preparación para Google Play. No se modificó el backend en 5.3.

No se ha probado esta versión en un teléfono físico ni ejecutado en Android 16 o en un dispositivo de páginas de 16 KB. Las capturas usan datos ficticios; no demuestran pagos reales. Antes de publicar faltan tu clave de subida, la cuenta de Google Play, privacidad y eliminación de cuentas, recursos de tienda y las pruebas/revisión correspondientes. Las guías y borradores señalan esos pendientes expresamente.

Actualiza tu carpeta en GitHub Desktop con **Fetch origin → Pull origin**, y abre `CasaFamiliasAndroid` en Android Studio. El ZIP de la raíz del repositorio y las entregas antiguas son históricos.
