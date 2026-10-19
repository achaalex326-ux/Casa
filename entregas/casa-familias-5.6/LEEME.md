# Casa Familias 5.6 — un depósito para varios meses

Instala `CasaFamilias-5.6-pruebas.apk` sobre la versión anterior. Paquete `com.casafamilias.app`, código 12, Android 8 o posterior. Conserva el certificado de pruebas anterior; no necesitas desinstalar ni borrar la sesión.

## Registrar un solo comprobante

1. En **Pagos → Registrar pago**, elige **Mis deudas más antiguas** en «Aplicar el pago a».
2. Escribe el **Total depositado (Bs)** y la fecha. Para QR o transferencia, adjunta una sola foto del comprobante.
3. Revisa **Así se repartirá tu depósito**: aparecen los meses, el importe para cada uno y la deuda que quedaría.
4. Envía el depósito. El encargado o administrador abre **Revisar depósito completo** y aprueba o rechaza una sola vez.
5. En tu historial, **Ver reparto del depósito** muestra los meses cubiertos y los importes aprobados. Los saldos se descuentan al aprobar.

Ejemplo: tres meses de Bs 80 suman Bs 240. Un depósito de Bs 200 aplica Bs 80 al primer mes, Bs 80 al segundo y Bs 40 al tercero; quedan Bs 40 de deuda. Para cubrir los tres meses completos, deposita Bs 240. Un depósito de Bs 300 cubre Bs 240 y deja Bs 60 a favor.

La opción **Una deuda específica** conserva el pago de un mes/deuda individual. En ese modo, cualquier excedente queda a favor de esa familia, aunque haya otras deudas fuera de la selección. La vista previa muestra ese resultado. Si usas «Pagar esta cuota» desde una deuda, se selecciona ese modo.

Los encargados y administradores eligen expresamente la familia del depósito. Como en la versión anterior, sus registros de efectivo/QR se aprueban directamente. Los comprobantes enviados por una cuenta familiar requieren revisión.

## Cómo se cuida el saldo

Un depósito aparece una sola vez en el historial y usa una sola imagen. Al aprobarlo se guarda el reparto entre las deudas; los reportes contables conservan ese detalle y la suma del dinero recibido coincide con el depósito.

El reparto mostrado antes del envío es una estimación. Al aprobar, se calcula otra vez considerando los pagos que ya se hayan aprobado. Rechazar no reduce ninguna deuda. Solo el excedente de las deudas seleccionadas genera crédito. No se descuentan importes pendientes de revisión.

Cada solicitud tiene una clave estable: reintentar tras perder la conexión no registra otro depósito. La app reutiliza la imagen ya subida durante ese reintento; una doble pulsación mientras se envía no abre dos solicitudes. Editar los datos inicia una solicitud nueva. Revisa el historial antes de crear otro depósito si hubo un error de conexión.

Salinas y Salinas2 mantienen sus cuentas de dinero separadas; compartir limpieza no combina sus deudas. Se conserva el calendario de5.5, el acuerdo de intercambios entre usuarios, la huella opcional y los avisos periódicos del celular de5.4. Los avisos siguen sujetos a internet y batería, con intervalo mínimo de15minutos; no son push instantáneo.

## Instalación, código y límites

El APK está optimizado y usa la firma de pruebas compatible con las entregas anteriores. Se verificó su actualización en emulador. No está publicado en Google Play y no se ha probado en un teléfono físico. La publicación definitiva sigue necesitando la clave del propietario y los pasos de privacidad, eliminación de cuentas y revisión de Google.

Actualiza tu copia local con **GitHub Desktop → Fetch origin → Pull origin**. Abre `CasaFamiliasAndroid` en Android Studio. El ZIP de la raíz es histórico; el de esta entrega contiene el proyecto actual sin cachés ni claves privadas.
