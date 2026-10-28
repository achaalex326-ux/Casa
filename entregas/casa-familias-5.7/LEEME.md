# Casa Familias 5.7 — fotos y cuentas por mes

Instala `CasaFamilias-5.7-pruebas.apk` sobre la versión anterior. Versión5.7/código13, Android8 o posterior. Mantiene el certificado de las entregas anteriores, las cuentas y los datos existentes.

## Tomar fotos desde la aplicación

En las facturas de agua, luz, gas e internet, los gastos y los comprobantes aparecen **Tomar foto** y **Elegir de galería**. Pulsa Tomar foto, utiliza la cámara del teléfono y confirma la imagen. Regresar o cancelar conserva la imagen anterior. Puedes quitarla o repetirla antes de guardar.

La foto queda adjunta al campo correspondiente, por ejemplo «Factura de agua». La aplicación la orienta y reduce su tamaño para facilitar el envío; se guarda en un archivo privado del teléfono. El envío ocurre cuando guardas el formulario, no al abrir la cámara. La copia local de esa foto se elimina después de que se confirme el registro. Una imagen elegida de la galería no se borra.

Se utiliza la cámara instalada en Android mediante su pantalla de captura; no hace falta abrirla por separado y buscar después el archivo. La app conserva la opción de galería si el dispositivo no puede abrir una cámara. La lectura de importe sigue siendo opcional: revisa el número antes de guardar.

## Entender qué meses faltan

Abre **Mi cuenta**. Ahora se muestra:

- **Te falta pagar:** saldo real de la familia, calculado con los pagos aprobados.
- **Gastos registrados hasta:** último mes que tiene gastos publicados. No se inventan deudas de meses futuros.
- **Primer mes que falta cubrir:** el mes pendiente más antiguo.
- **Por pagar / Al día / Todos:** filtros para encontrar rápidamente cada mes.
- Una tarjeta por mes con **Al día**, **Pago parcial** o **Pendiente**, lo asignado, lo cubierto y lo que falta. **Ver detalle del mes** muestra los gastos y sus fechas límite.

Si hay un mes sin registros entre otros publicados, se informa expresamente; no se lo considera pagado. «Al día» significa sin deuda en los registros existentes, incluso cuando hubo una cobertura del encargado o un ajuste, y no afirma que se haya recibido efectivo.

Ejemplo: agosto cubierto, septiembre con Bs40 pendientes y octubre con Bs80 pendientes. La pantalla muestra Bs120 por pagar, dos meses pendientes y septiembre como el primero que falta cubrir.

## Comprobantes separados del saldo

**Pagos** muestra los depósitos enviados y si están **Por revisar**, **Aprobados** o **Rechazados**. Cada comprobante indica para qué meses está previsto o a cuáles se aplicó, aunque el depósito se haya hecho en otro mes. Un comprobante por revisar no descuenta la deuda.

Se conserva un solo comprobante para varios meses y su aprobación conjunta. Bs200 frente a tres meses de Bs80 se aplica80/80/40 al aprobar. El saldo a favor, los pagos históricos y la cobertura de las familias del encargado siguen las reglas existentes del servidor. Salinas y Salinas2 comparten limpieza, pero sus cuentas financieras continúan separadas.

Los administradores y encargados ven la cuenta mensual desde **Familias → Ver cuenta** y el historial de esa familia. Se conservan los turnos de limpieza, los intercambios entre familias, la huella opcional y las notificaciones periódicas. No se modificó la base de datos para esta actualización.

## Código e instalación

En GitHub Desktop usa **Fetch origin → Pull origin** para actualizar tu carpeta. Abre `CasaFamiliasAndroid` en Android Studio. El ZIP de la raíz es histórico; el de esta entrega contiene el proyecto actual sin cachés ni claves privadas.

El APK está optimizado y usa la firma de pruebas compatible con las versiones anteriores. No está publicado en Google Play. La publicación definitiva sigue pendiente de la clave del propietario, privacidad/eliminación de cuentas y las pruebas y revisión de Google. Las verificaciones en emulador y sus límites se describen en VERIFICACION.md.
