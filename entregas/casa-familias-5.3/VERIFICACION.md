# Verificación de Casa Familias 5.3

La entrega conserva la firma de pruebas de 5.2 y usa versión 5.3/código 9, mínimo Android 8/API26 y destino Android 16/API36. `presentation` está optimizada con R8 y no es depurable. No se añadieron claves administrativas ni se cambiaron datos de Supabase en esta actualización.

## Comprobaciones automáticas

- Compilación de debug, pruebas Android y APK optimizado mediante Gradle 8.11.1/JDK17.
- 40 pruebas unitarias: 24 de reglas existentes, 9 de contratos HTTP y 7 de política de acceso local. Sin errores, fallos ni omisiones.
- 14 pruebas Android aprobadas: 8 de navegación y 6 del bloqueo biométrico con datos ficticios. Incluyen tema oscuro, texto ampliado y retorno desde segundo plano.
- Android Lint: 0 errores y 21 avisos; no se desactivaron comprobaciones. Los avisos incluyen recomendaciones de KTX, actualización de herramientas y las advertencias existentes de migración WebView y reglas de extracción de datos.
- Firma válida del APK y mismo certificado que 5.2. `zipalign -c -P16` validado.

Los resultados de Android y el ensayo de instalación se guardan en `evidencias/` y `verificacion.json`. Las pruebas del bloqueo utilizan cuentas y servidor ficticios, y simulan el resultado del callback de Android: verifican que no hay lecturas ni escrituras antes de desbloquear, que cancelar o una contraseña incorrecta no autorizan el acceso, que el resultado positivo permite cargar, que se vuelve a bloquear tras 30 segundos, que el cierre de sesión desactiva la opción y que una sesión del servidor expirada vuelve a exigir contraseña.

## Límites y prueba en el teléfono

La verificación usa un emulador Android11/API30. No demuestra lectura de una huella real en un teléfono ni sustituye la prueba del lector y su configuración en cada modelo. La aceptación y el rechazo físicos del sensor y los cambios de huellas registradas quedan para el teléfono. No se han probado en esta entrega dispositivos Android16 o páginas de memoria de16KB. La alineación del archivo no equivale a ejecutar en esos dispositivos.

Prueba en cada celular:

1. Instala como actualización e inicia sesión con su cuenta. Registra una huella en los ajustes de seguridad de Android.
2. Mi perfil → Activar huella. Cancelar debe mantenerla desactivada; confirmar debe activarla.
3. Cierra por completo la app y vuelve a abrirla. Debe mostrar el bloqueo sin datos de familias.
4. Comprueba huella correcta, huella incorrecta y cancelar. Solo la correcta permite entrar.
5. Usa la alternativa con contraseña: una incorrecta debe fallar y la válida debe entrar.
6. Sal más de30segundos y regresa; debe pedir verificación. Una visita breve al selector de archivos debe conservar el formulario.
7. Desactiva la huella en Mi perfil y comprueba el acceso habitual. Cerrar sesión también debe desactivar la preferencia local.

No se aplicaron migraciones ni se escribieron pagos para probar el acceso. La condonación previa de saldos es independiente de esta versión del APK. Publicar en Google Play mantiene los pendientes de la guía de la tienda.
