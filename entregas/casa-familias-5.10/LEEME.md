# Casa Familias 5.10 — letra grande y recordatorio de limpieza

## Instalar la actualización

Descarga **CasaFamilias-5.10-pruebas.apk**, ábrelo en el celular y pulsa **Actualizar**. Necesita Android 8 o posterior. Mantiene el certificado de pruebas de la entrega 5.9 y no hace falta desinstalar. Los saldos, pagos y turnos existentes se conservan.

**Validación:** compilación, firma, 96 pruebas unitarias y pruebas web aprobadas. La prueba en dispositivo Android de esta versión quedó pendiente porque el emulador falló al iniciar; comprueba en tu celular los pasos de abajo antes de distribuirla a todos.

## Hacer las letras más grandes

1. Entra a la aplicación y toca **Cuenta**, arriba a la derecha.
2. Al principio encontrarás **Letras y colores**.
3. Toca **Grande** o **Extra grande**. La elección queda guardada en ese celular.
4. Si prefieres el fondo oscuro, toca la fila **Modo oscuro**.

Los tamaños se eligen con botones grandes. Puedes tocar toda la fila del modo oscuro. Los botones y tarjetas crecen con el texto y permiten desplazarse para ver el resto de la pantalla.

## Recibir el aviso de tu semana de limpieza

Hazlo en **cada celular** donde quieras recibir el aviso:

1. Inicia sesión con el correo de tu familia.
2. Toca **Limpieza**, en la barra inferior. Administración y encargado también pueden entrar desde Inicio o Más.
3. Busca **Aviso de limpieza en tu celular** y toca **Activar recordatorio**.
4. Si Android pregunta, pulsa **Permitir**.
5. Cuando aparezca **Recordatorio activado en este celular**, toca **Probar aviso de limpieza**.
6. Baja la barra de notificaciones y comprueba el aviso de prueba.

Si el teléfono bloquea ese tipo de avisos, aparecerá **Permitir avisos de limpieza**, que abre sus ajustes. La prueba usa el mismo canal de Android que el recordatorio real. La activación conserva también los avisos de pagos y de la casa que ya ofrecía la aplicación.

Recibirás el recordatorio durante la semana asignada, de lunes a domingo. Se comprueba el calendario de tu familia y se evita repetir el mismo aviso en cada revisión. Al completar la limpieza se retira el recordatorio pendiente. Los administradores reciben la familia de esta semana; los familiares reciben su propio turno. Los alias Salinas/Salinas2 siguen compartiendo limpieza y mantienen sus cuentas separadas.

**Necesita internet y permiso de notificaciones.** Android revisa los avisos en segundo plano, con intervalo mínimo de 15 minutos, y puede retrasarlos por batería, red o restricciones del fabricante. No se garantiza una hora exacta ni entrega instantánea. Cerrar la pantalla de la app no impide la revisión; forzar su detención desde Ajustes la suspende hasta volver a abrirla. Cerrar sesión desactiva los avisos de esa cuenta en ese teléfono.

El permiso se concede en el propio celular; no se activa remotamente desde Supabase para todos. El APK conserva la huella opcional y el bloqueo al regresar tras 30 segundos.

## Qué cambia en el inicio

La próxima limpieza ahora se busca desde la semana actual hacia adelante. Un turno antiguo pendiente no aparece como si fuera la próxima semana. Si es tu turno actual, el inicio dice **Esta semana te toca limpiar** y muestra el rango de fechas.

## Web y proyecto

El ZIP **CasaFamilias-5.10-web-Netlify.zip** conserva el diseño compartido y coloca los botones grandes de letra al inicio de Cuenta. Para actualizar `casafliagalarza.netlify.app`, descomprime el ZIP y arrastra la carpeta con `index.html` a **Deploys** del sitio existente en Netlify. Esa carga manual sigue a cargo del propietario; no se publicó desde este entorno. Los recordatorios en segundo plano descritos aquí corresponden al APK Android.

El ZIP **CasaFamilias-5.10-proyecto.zip** contiene el proyecto para Android Studio. En GitHub Desktop usa **Fetch origin → Pull origin** para actualizar tu carpeta. JDK 17, SDK 36 y Gradle 8.11.1; el proyecto tiene versión 5.10/código 16. No hay nuevas migraciones ni cambios de saldos en esta entrega. No está publicado en Google Play.
