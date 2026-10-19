# Casa Familias 5.6 — depósitos para varios meses

Versión **5.6**, código **12**, paquete `com.casafamilias.app`. Compatible con Android 8 o posterior, con destino Android 16/API 36. Mantiene la interfaz de 5.1 y las cuentas de la casa actual; el registro de comunidades independientes queda para otra etapa.

Añade un depósito con una sola foto para varias deudas: vista previa por mes, revisión conjunta y reparto definitivo de más antiguo a más reciente al aprobar. Bs200 frente a tres meses de Bs80 se aplica 80/80/40. El sobrante de las deudas seleccionadas queda a favor. El historial agrupa el depósito, la contabilidad conserva sus asignaciones y las solicitudes repetidas con la misma clave no duplican el dinero. Se mantiene el modo de deuda individual y los pagos anteriores. La migración009 se aplica sin crear ni cambiar registros comerciales.

Incluye la familia de esta semana en Inicio y Limpieza, rango de lunes a domingo, calendario por mes de todas las familias para el ingeniero/administrador y encargado, y filtros de solicitudes recibidas/enviadas. Los usuarios solicitan cambios para sus propias familias; otra persona del hogar receptor acepta o rechaza. Las semanas conservan su asignación hasta la aceptación.

Incorpora avisos nativos de Android mediante WorkManager: limpieza de la semana, deudas nuevas con saldo, recordatorios diarios desde tres días antes del vencimiento y publicaciones nuevas de servicios. Se activan por cuenta y teléfono desde Mi perfil. Revisión mínima cada15minutos; Android puede retrasarla por batería, red o restricciones del fabricante. No utiliza Firebase ni ofrece entrega push instantánea. Cerrar la actividad no cancela el trabajo; forzar la detención desde Android impide su ejecución hasta reabrir la app.

## Descargar y revisar

Los archivos actuales están en [entregas/casa-familias-5.6](entregas/casa-familias-5.6): APK optimizado, código fuente e instrucciones para activar notificaciones y huella. La [entrega 5.2](entregas/casa-familias-5.2) conserva su AAB sin firmar y sus verificaciones históricas. Para publicar, sigue [la guía de Google Play](entregas/google-play/GUIA_GOOGLE_PLAY.md). No se ha publicado la aplicación ni creado una cuenta de desarrollador.

El APK usa la variante `presentation`, con la firma de pruebas de este entorno. La variante `release` deja de utilizar esa firma automáticamente y admite una clave de subida propia mediante Android Studio o un archivo local ignorado `casa-upload.properties`. Sin esa clave, el AAB se genera sin firmar y **no se puede enviar a Play Console**.

## Cuotas de las familias del encargado

Por solicitud posterior del propietario se condonaron las deudas que estaban pendientes el 18 de octubre de 2026 (hora de Bolivia). El servidor confirmó cero deudas en las seis identidades contables. Los importes anteriores y el ajuste quedaron auditados; se conservaron facturas, pagos y créditos y no se registró dinero ficticio. Ese ajuste puntual no cancela las cuotas que se registren en el futuro.

La familia que paga las facturas no debe pagar otra vez su cuota de servicios. Las dos familias contables configuradas para el encargado tienen esa cuota cubierta automáticamente. Se regularizaron 22 cuotas históricas sin duplicar pagos previos, sin consumir créditos y sin alterar los gastos ni las cuotas de otras familias. El saldo se calcula en el servidor; no se ocultan deudas cambiando solo la pantalla.

Se corrigió el registro de servicios futuros que usaba una columna de pago inexistente. Los gastos extraordinarios mantienen su propia regla de pago y no se cancelan por tener rol de encargado. La app distingue coberturas automáticas, aplicación de crédito, efectivo y QR, y utiliza el número de versión configurado en el proyecto para el perfil y la exportación.

## Funciones e interfaz conservadas

La versión 5.3 incorpora AndroidX BiometricPrompt para un bloqueo local opcional. Solo se activa tras iniciar sesión y verificar la biometría; cancelar o fallar no desbloquea la app. El correo y la contraseña válidos son la alternativa. Cerrar sesión desactiva la opción en el teléfono; otra cuenta debe activarla de nuevo. Se protege la ventana con FLAG_SECURE mientras está activa. Este bloqueo local complementa la sesión cifrada existente: no cambia los permisos de Supabase ni constituye una nueva autenticación biométrica en el servidor.

Desde5.4, activar notificaciones autoriza consultas mínimas en segundo plano incluso con el bloqueo biométrico activo; no desbloquea la interfaz. La sesión sigue cifrada y los permisos del servidor limitan la cuenta. Las notificaciones usan una versión pública genérica para la pantalla bloqueada, canales configurables y un acceso que vuelve a pasar por la autenticación habitual. No almacenan saldos ni textos financieros en el historial de deduplicación local. Se serializan las renovaciones de sesión para evitar que dos repositorios usen a la vez el mismo refresh token.

Kotlin, Jetpack Compose Material 3, Navigation Compose y MVVM; azul eléctrico, coral y turquesa; tema claro/oscuro y texto ampliable. Se conservan Supabase, sesiones cifradas con Android Keystore, pagos parciales, QR privado, comprobantes, familias, asignaciones, propuestas, avisos, auditoría y reportes PDF/Excel. El WebView invisible y temporal solo intenta recuperar una sesión antigua; no presenta la interfaz.

El ciclo semanal sigue **Choque → Galarza → Salinas → David → Acha**. Salinas y Salinas2 comparten limpieza y conservan sus identidades contables separadas. Se conserva la rotación. La migración008 limita los intercambios a participantes del hogar: solo otra persona de la familia receptora puede responder. El administrador sin familia asignada consulta los acuerdos.

## Verificación y pendientes

Consulta [la verificación de 5.6](entregas/casa-familias-5.6/VERIFICACION.md) para la compilación actual. Las verificaciones de5.2 y5.3 son históricas. Se añadieron depósitos de varios meses y un revisor compatible en Supabase, sin modificar los saldos, pagos ni turnos existentes.

No se ha probado esta versión en un teléfono físico ni ejecutado en Android 16 o en un dispositivo de páginas de 16 KB. Las capturas usan datos ficticios; no demuestran pagos reales. Antes de publicar faltan tu clave de subida, la cuenta de Google Play, privacidad y eliminación de cuentas, recursos de tienda y las pruebas/revisión correspondientes. Las guías y borradores señalan esos pendientes expresamente.

Actualiza tu carpeta en GitHub Desktop con **Fetch origin → Pull origin**, y abre `CasaFamiliasAndroid` en Android Studio. El ZIP de la raíz del repositorio y las entregas antiguas son históricos.
