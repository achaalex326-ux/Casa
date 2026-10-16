# Casa Familias 5.1 — entrega Android nativa

Versión **5.1**, código **7**, paquete com.casafamilias.app, Android 8.0 o posterior. Compilación optimizada con firma de pruebas, para presentar y probar.

## Cambios principales

La actualización 5.1 renueva la presentación: resumen con degradado azul/turquesa, marca de la casa, accesos con indicación Ver, botones más grandes con iconos, contraste reforzado y tarjetas de inicio que abren las mismas secciones de Pagos y Familias. Los estados conservan sus nombres y colores. No incluye cambios de reglas, cuentas, permisos, datos ni servidor.

Interfaz hecha en Kotlin con Jetpack Compose, Material3, Navigation Compose y MVVM. Azul eléctrico con acentos coral y turquesa, Inter incluida, iconos lineales, tarjetas, modos claro/oscuro, texto ampliable y filtros que pasan a otra fila cuando falta espacio. Las pantallas tienen carga, estados vacíos, mensajes de error y formularios largos divididos en pasos. Los detalles y formularios usan retroceso jerárquico.

Administrador y encargado: Inicio, Gastos, Pagos, Familias y Más. Las opciones cambian según el rol; Usuarios y Administración son solo para administrador. Cada acción tiene una ubicación principal. Historial y revisión se concentran en Pagos; las deudas detalladas están en Familias/Mis cuotas. Fondo Común está ausente de la navegación y consultas activas, sin eliminar datos históricos.

Se conserva la integración real con Supabase: autenticación y recuperación, QR privado, pagos parciales/efectivo/comprobante, gastos y servicios, crédito, aportes de restaurante, nombres y asignaciones familiares, turnos, avisos, propuestas, cierre de meses, auditoría y respaldo. PDF se genera en Android y Excel contiene Gastos, Pagos y Familias para el período elegido. El servidor sigue calculando repartos; no se sustituyeron por divisiones del cliente. ML Kit lee fotos sin descargar un modelo y pide revisar el importe.

Las familias pueden publicar propuestas, votar y solicitar intercambios desde sus propios turnos. El servidor valida propiedad y hogar receptor; la consulta de turnos para intercambio no expone fotografías ni notas privadas. Los conteos de propuestas no revelan los otros votantes. Se mantiene un voto por identidad contable y el umbral de cuatro votos.

El ciclo semanal sigue **Choque → Galarza → Salinas → David → Acha**. Salinas y Salinas2 comparten únicamente limpieza; sus cuentas, pagos y saldos siguen separados. Ubicación: Más → Limpieza → Repetición semanal. El plan ya está activo; guardar cambios conserva turnos asignados. Pausar detiene la creación de semanas nuevas.

## Descargar e instalar

Los archivos actuales están en [entregas/casa-familias-5.1](entregas/casa-familias-5.1). Instala CasaFamilias-5.1-pruebas.apk sobre la versión anterior, sin desinstalarla ni borrar datos. Usa internet para sincronizar. Si es necesario, ingresa con tu cuenta habitual.

El certificado de la entrega coincide con el APK 4.3.1 generado aquí. Se verificó la instalación como actualización en un emulador; no se probó la transferencia de una sesión real autenticada. La aplicación intenta recuperar la sesión anterior una vez y almacena las nuevas sesiones cifradas con Android Keystore. Las contraseñas no se guardan.

Para actualizar tu carpeta de trabajo: GitHub Desktop → Fetch origin → Pull origin. Abre CasaFamiliasAndroid en Android Studio. El ZIP original del repositorio es histórico y no se actualiza automáticamente.

## Validación y límites

Las pruebas y su evidencia están en VERIFICACION.md de la entrega. Se comprueban dinero decimal, roles, formularios, paginación, renovación de sesión, contratos HTTP, exportaciones y navegación Android con datos ficticios. Las 169 comprobaciones SQL usan PostgreSQL desechable. En la base 5.0, la migración006 se instaló en el Supabase real conservando huellas y conteos de 15 tablas, sin insertar registros comerciales.

Las imágenes de propuestas se generaron antes de implementar. Las capturas Android se obtienen ejecutando Compose en un emulador con PreviewRepository: están identificadas como datos ficticios y no demuestran operaciones reales de Supabase. Los recursos web antiguos conservados no presentan la interfaz del APK nuevo; solo hay un WebView temporal invisible para intentar migrar la sesión.

No se ha probado el APK en un teléfono físico, con credenciales de una persona real, seleccionando su QR bancario ni enviando un pago real. La guía PRUEBAS-EN-TU-CELULAR.md enumera esas comprobaciones. No se publicó en Google Play.
