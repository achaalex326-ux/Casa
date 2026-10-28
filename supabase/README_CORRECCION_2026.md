# Facturas completas y hoja de enero-junio de 2026

El propietario pidió usar los totales de su hoja, gas en cero y seis cuentas financieras. Aclaró que amarillo/gris no son estados de pago. La limpieza compartida de Salinas/Salinas2 no cambia este reparto. Las cuentas del encargado siguen cubiertas, sin trasladar otra vez ese importe a las demás.

## Estado de esta entrega

El intento de lectura administrativa devolvió HTTP 401 «Invalid access token». La configuración conserva la vinculación SUPABASE_ACCESS_TOKEN para api.supabase.com, pero no permitió consultar el proyecto. **Ni la migración 010 ni la reparación se aplicaron a Supabase.** Los resultados de validacion/test_invoice_totals.py pertenecen exclusivamente a PostgreSQL desechable.

La renovación se hace en Supabase → Account → Access Tokens; el valor se guarda solo en Codex → Editar entorno → Secretos de red → SUPABASE_ACCESS_TOKEN. No guardar tokens en Git, en este documento ni en capturas. Reintentar la lectura después de renovar y guardar/publicar la configuración; el guardado del borrador no demuestra acceso operativo.

## Regla preparada

- Suma completa de agua, electricidad, gas e internet; restar solo descuentos explícitos.
- No usar promedios para reducir el importe familiar ni crear deudas automáticas de restaurante.
- Dividir entre las cuentas existentes, seis en esta casa. Redondeo decimal a centavos; diferencia asignada una vez a la última cuenta, sin importes negativos en totales pequeños.
- Cobertura automática de cuentas del encargado; aplicación normal del saldo a favor de otras cuentas.
- Referencia histórica sin deudas ni consumo de créditos. Descuento superior a su factura rechazado sin consumirlo.
- La entrada casa_create_monthly_services comprueba total y cantidad de cuentas esperados. Un cambio desde la vista previa revierte toda la operación. La función anterior conserva su firma para los APK antiguos y adopta la regla corregida después de desplegar.

## Aplicación cuando vuelva el acceso

1. Leer funciones, restricciones, meses enero-junio, pagos, saldos a favor, descuentos y aportes del restaurante actuales. Guardar la lectura privada fuera del repositorio. Comprobar seis cuentas y sus asignaciones; no confiar en una instantánea anterior.
2. Revisar/aplicar `migrations/202610280010_invoice_total_services.sql`. Es un cambio de regla y metadatos; no modifica importes históricos.
3. Revisar/aplicar `repair_worksheet_2026.sql` una sola vez. Es una operación distinta, autorizada para esta hoja, no una migración de setup. Conserva IDs, fotos, fechas y pagos previos. Si falta un mes, usa el fin de ese mes como vencimiento. Guarda la instantánea anterior en `casa_finance_repairs`, tabla con RLS y sin acceso para anon/authenticated, y audita la operación. Un identificador único impide duplicarla. Las cuentas del encargado reciben únicamente la cobertura automática faltante, identificada como tal y sin inventar depósitos.
4. Si detecta descuentos aplicados o aportes de restaurante cobrados, se detiene para conciliarlos. Si algún pago/ajuste supera el nuevo importe, revierte; nunca borra dinero ni cambia un pago aprobado para hacer coincidir una suma. Mantiene los ajustes anteriores de condonación; no supone pagos por el color de una celda.
5. Verificar la suma de cada mes contra las facturas y la suma de sus seis asignaciones; semestre Bs2.250,50. Calcular cada pendiente como asignación menos importes aprobados aplicados. Confirmar cobertura de encargado y conservación de pagos, archivos y otras fechas/meses.
6. Abrir/actualizar Gastos y las cuentas familiares en el APK con los datos corregidos. Solo entonces se puede afirmar que los saldos reales fueron corregidos.

La copia de seguridad de la reparación contiene datos privados; no exportarla como evidencia pública. Las pruebas del repositorio contienen únicamente datos de ejemplo. Los fixtures de validación nunca se ejecutan en el proyecto real.
