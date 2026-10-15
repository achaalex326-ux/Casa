# Diseño nativo de Casa Familias 5.0

Se realizó primero la auditoría de funciones y después se generaron las nueve propuestas individuales solicitadas. Luego se implementó la aplicación Kotlin/Compose, conservando los contratos de Supabase.

- **propuestas/**: nueve imágenes generadas de alta resolución (Inicio, Gastos, Pagos, Familias, Limpieza, Usuarios, Reportes, Administración y Más). Contienen ejemplos ficticios y son referencias de diseño.
- **capturas-android/**: imágenes tomadas al ejecutar la interfaz nativa en el emulador Android11. PreviewRepository es de solo lectura; el aviso visible identifica los datos de ejemplo. Incluye los nueve módulos, la vista familiar, su turno, modo oscuro y texto ampliado. Las capturas finales de los módulos se tomaron a320×800con letra del sistema130%; el caso oscuro añade la preferencia130% de la app. El acceso de la compilación release se tomó a390×844.
- **AUDITORIA.md**: ubicación única de funciones y reglas conservadas.

El diseño final adapta las referencias a controles nativos y datos reales. La aplicación normal se conecta a Supabase; la compilación de entrega no permite activar los ejemplos con extras de depuración. Las capturas no acreditan un inicio de sesión real ni operaciones comerciales.

Los filtros se acomodan en filas completas. Las etiquetas de navegación se ajustan al ancho disponible para evitar separar una letra de su palabra con el texto ampliado. El contenido de formularios y botones puede crecer y desplazarse.
