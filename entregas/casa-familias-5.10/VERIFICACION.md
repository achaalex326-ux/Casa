# Verificación 5.10

La entrega comprueba accesibilidad de los controles y recordatorios semanales. `verificacion.json`, las evidencias y `SHA256SUMS.txt` contienen los resultados y archivos comprobados.

- 96 pruebas unitarias JVM: incluyen la selección del próximo turno propio, descarte de semanas pasadas, turnos completados y alias familiares. Las reglas financieras se conservan.
- Se prepararon 41 pruebas Android, con una nueva prueba del Worker de limpieza y una de instrucciones de activación. Se intentó ejecutar una selección de 8 casos para los cambios, pero **no se ejecutó ningún caso de la aplicación**: Android no terminó de iniciar sus servicios. El perfil previo reiniciaba servicios del sistema; se conservó y se creó uno nuevo, que tampoco quedó operativo durante esta validación. La instalación falló dentro de Android con `PackageManagerInternal.freeStorage` nulo. No se presentó ese fallo como un fallo de la app ni como pruebas aprobadas. Ver `evidencias/android-environment-blocked.json`.
- Pruebas web con servidor simulado: 19 comprobaciones de cuentas/calendario, 20 de arranque, 11 de autenticación, restaurante de consulta, 37 de administración, 42 de rotación, 26 pantallas DOM y 7 formularios fuente.
- 168 comprobaciones en Chromium de cuatro roles, anchos 320/390/768, letras normales/ampliadas, límites de texto en botones, imagen real a JPEG y reintento de un depósito con una sola subida. Todas las solicitudes de esas pruebas se interceptan localmente.
- APK optimizado versión 5.10/código 16, firma compatible con 5.9 y alineación comprobada. La variante se compiló sin el atributo debuggable y conserva la guarda que desactiva los extras de demostración. No se pudo ejecutar la apertura del APK optimizado en esta entrega. Kotlin con ktfmt 0.54 y JavaScript/CSS con Prettier 3.5.3.

Los datos de pruebas son ficticios. Se leyó únicamente la cobertura de fechas del calendario real para confirmar que existe la semana actual; no se cambiaron turnos, orden, pagos, saldos, permisos ni funciones del servidor. No se repitió la reparación financiera de 5.8.

La ejecución actual en Android 11/API30 quedó bloqueada antes de instalar la aplicación; la notificación real de 5.10 debe verificarse en un celular. Las pruebas anteriores de 5.9 son históricas y no se contabilizan como nuevas. No prueban un celular físico, el diálogo de permiso de Android 13+, restricciones de fabricantes, ni una espera real de 15 minutos contra Supabase. Cámara y biometría conservan sus pruebas simuladas y de archivos reales; no se afirma prueba de sensores físicos ni ejecución en Android 16.

Los avisos son sincronización periódica con WorkManager y requieren permiso, sesión y conexión. No son push instantáneo ni prometen una hora fija. El ZIP web está preparado para carga manual en el sitio existente; no se ha desplegado desde este entorno. El APK usa firma de pruebas y no está publicado en Google Play.
