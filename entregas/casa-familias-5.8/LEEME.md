# Casa Familias 5.8

Instala CasaFamilias-5.8-pruebas.apk sobre la versión anterior para conservar la instalación. Usa la misma firma de pruebas. No desinstales si Android rechaza la firma: revisa primero el origen de las instalaciones.

## Consultar gastos y deudas

- Gastos: arriba aparece «Servicios registrados hasta…». Se muestran todos los meses, comenzando por el último. El selector permite consultar un mes.
- Cada tarjeta distingue «Total de las facturas» y «Total repartido». «Ver facturas y cálculo» muestra agua, luz, gas, internet, descuentos y las seis cuentas.
- Familias → Ver cuenta: el último mes, cuánto falta y tarjetas breves por mes. En cuentas familiares se abre desde Mi cuenta.
- Registrar servicios del mes: elige mes/fecha, escribe importes y adjunta fotos si las tienes, revisa descuentos y suma, confirma. Gas empieza en 0. La fecha límite se ajusta al fin del mes elegido y puedes cambiarla.
- Los comprobantes pendientes no descuentan. Los pagos aprobados, saldos a favor y cobertura del encargado sí se reflejan. Un comprobante puede cubrir varios meses.

## Datos de la hoja

| Mes de 2026 | Agua | Luz | Internet | Gas | Total | Por cuenta aprox. |
|---|---:|---:|---:|---:|---:|---:|
| Enero |36,00|211,50|149,00|0,00|396,50|66,08|
| Febrero |24,00|165,60|149,00|0,00|338,60|56,43|
| Marzo |32,00|145,20|149,00|0,00|326,20|54,37|
| Abril |36,00|243,60|149,00|0,00|428,60|71,43|
| Mayo |36,50|198,00|149,00|0,00|383,50|63,92|
| Junio |36,50|191,60|149,00|0,00|377,10|62,85|

Total de facturas del semestre: Bs2.250,50. El total de las seis cuentas conserva los centavos: la última puede variar unos centavos respecto al importe aproximado. Salinas y Salinas2 siguen siendo dos cuentas financieras y un hogar para limpieza, tal como las seis filas de la hoja. Los colores no indican pagos.

## Pendiente en tus datos reales

Supabase rechazó el acceso administrativo con HTTP 401. La regla nueva y la reparación histórica están preparadas y probadas en una base aislada; no se ejecutaron en el proyecto real. El APK muestra los datos reales que tenga el servidor, sin inventar saldos nuevos. Para registrar servicios con la regla nueva debe desplegarse la migración 010; si falta, aparece un mensaje y no se guarda el mes con el cálculo anterior.

Renueva SUPABASE_ACCESS_TOKEN únicamente en los secretos seguros de Codex. Después debe leerse el estado actual, guardar el respaldo, aplicar la migración y la reparación solicitada y comprobar totales y pagos. Los scripts están en el ZIP del proyecto. No ejecutes fixtures de validación en Supabase.

Conserva cámara/galería, huella opcional, avisos periódicos, calendario y permisos existentes. La cámara física, huella real, Android13+ para permisos de avisos y Android 16 deben comprobarse en teléfonos. No es una publicación en Google Play.
