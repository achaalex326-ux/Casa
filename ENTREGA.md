# Casa Familias 5.9 — interfaz sencilla para Android y web

Versión **5.9**, código **15**, paquete `com.casafamilias.app`. Android 8 o posterior; destino Android 16/API 36. La casa, sus cuentas autorizadas y los datos existentes se conservan.

El inicio presenta tareas con botones grandes y una explicación corta. Las familias ven cuánto falta pagar, hasta qué mes hay registros, sus pagos y la próxima limpieza. Administración y encargado acceden a facturas, comprobantes por revisar, deudas de las familias y calendario. La navegación habitual se reduce a cuatro pestañas; las funciones administrativas y ajustes están en Más.

**Pagar** tiene tres pasos: importe, comprobante y revisión. La foto puede tomarse con la cámara o elegirse de la galería. La pantalla final muestra los meses cubiertos y cuánto quedaría pendiente antes de enviar. Los pagos pendientes de aprobación no reducen el saldo mostrado. Las opciones de una deuda específica, cambio de fecha y mensajes se abren cuando hacen falta.

**Registrar facturas** también tiene tres pasos: mes, importes/fotos y revisión del reparto. Las tarjetas de deudas y gastos son breves; los detalles se abren con un botón. Se mantienen tema oscuro, texto grande, huella opcional, avisos y permisos según el rol.

La web utiliza el mismo diseño, navegación y formularios guiados. El sitio actual es `https://casafliagalarza.netlify.app/` y usa el mismo Supabase que el APK. El ZIP web se entrega preparado para actualizar ese sitio mediante carga manual. **La publicación en Netlify sigue pendiente de que el propietario suba la carpeta:** no se ha modificado el sitio publicado desde este entorno. La huella nativa y los recordatorios WorkManager corresponden al APK; el navegador conserva sus propias capacidades y permisos.

## Archivos de la entrega

En [entregas/casa-familias-5.9](entregas/casa-familias-5.9) están el APK, el ZIP web para Netlify, el código fuente, las instrucciones y las evidencias. [LEEME](entregas/casa-familias-5.9/LEEME.md) explica la instalación y la actualización del sitio. [VERIFICACION](entregas/casa-familias-5.9/VERIFICACION.md) detalla los resultados y límites.

El APK `presentation` está optimizado y firmado con el certificado de pruebas de las entregas anteriores. Puede instalarse directamente sin Google Play. Una actualización requiere el mismo certificado y un código de versión mayor; no desinstales antes si quieres conservar la sesión y ajustes locales. La variante `release` requiere la clave de subida propia antes de enviar un AAB a Play Console. Consulta [la guía de Google Play](entregas/google-play/GUIA_GOOGLE_PLAY.md); esta entrega no está publicada allí.

## Cálculo y datos conservados

La corrección financiera de **5.8 ya está aplicada en Supabase**. Se suman las facturas completas de agua, luz, gas e internet, se restan únicamente descuentos explícitos y se divide entre las seis identidades contables. Enero-junio2026 suma Bs2.250,50 con gas cero. Las dos cuentas del encargado tienen su cobertura automática y saldo cero para esos servicios. Se conservaron pagos, fotos y fechas, otros meses, créditos y calendario. [Evidencia del despliegue anterior](entregas/casa-familias-5.8/evidencias/backend-deployment.json).

5.9 no ejecuta reparaciones, migraciones ni escrituras en los datos reales. Los formularios nuevos usan las mismas funciones protegidas del servidor. Un depósito de Bs200 ante tres meses de Bs80 se aplica 80/80/40 al aprobar y deja Bs40; el sobrante queda a favor. El historial agrupa un solo depósito y los reintentos conservan su identificador. Los formularios web preparan las fotos antes de subirlas y conservan la imagen si falla el envío.

El ciclo semanal sigue **Choque → Galarza → Salinas → David → Acha**. Salinas y Salinas2 comparten limpieza, con cuentas financieras separadas. Solo los participantes de las familias correspondientes pueden proponer y responder intercambios; ser administrador no permite aceptar por otra familia.

## Activar funciones en cada teléfono

Tras entrar, abre **Cuenta** o **Más → Mi cuenta**. Activa huella y confirma con el sensor del teléfono. Registra antes una huella en los ajustes de Android. La opción es local y se solicita al reiniciar el proceso o volver después de 30 segundos fuera de la app; puede usarse la contraseña como alternativa.

Activa notificaciones y permite el permiso de Android cuando aparezca. WorkManager consulta limpieza, publicaciones y deudas con un intervalo mínimo de 15 minutos, sujeto a conexión, batería y restricciones del fabricante. No son avisos push instantáneos. La activación es por cuenta y teléfono y se detiene al cerrar sesión.

Actualiza tu carpeta con **Fetch origin → Pull origin** en GitHub Desktop y abre `CasaFamiliasAndroid` en Android Studio. El ZIP antiguo de la raíz y las entregas anteriores son históricos.
