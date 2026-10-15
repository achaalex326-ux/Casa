# Casa Familias: auditoría previa al diseño nativo

Base confirmada por el usuario: proyecto Casa_Familias_Completa_V4, equivalente a la versión 4.3.1 disponible en este repositorio. Se usa la misma integración y las mismas cuentas; el diseño no inserta datos de ejemplo en Supabase.

## Arquitectura de navegación

| Rol | Barra inferior | Rutas adicionales |
|---|---|---|
| Administrador | Inicio, Gastos, Pagos, Familias, Más | Limpieza, Usuarios, Reportes, Avisos, Propuestas y Administración desde Más. Perfil desde el avatar. |
| Encargado | Inicio, Gastos, Pagos, Familias, Más | Limpieza, Avisos, Propuestas y Reportes; Perfil desde el avatar. |
| Familia | Inicio, Mis cuotas, Pagos, Limpieza, Más | Avisos, propuestas y Perfil. Historial en Pagos; no segundo módulo idéntico. |
| Restaurante existente | Servicios, Perfil | Se conserva la consulta autorizada de luz y agua; no se lo transforma en familia ni se elimina su cuenta. |

La barra inferior aparece en destinos principales. Los detalles y formularios usan una cabecera con retroceso y título, sin un segundo menú completo. Cada ruta comprueba su rol; RLS y las funciones existentes mantienen la protección en el servidor.

## Ubicación única de acciones

| Función | Ubicación principal | Contrato existente |
|---|---|---|
| Saldo propio | Mis cuotas (familia); Familias (administración) | family_portal_debts / expense_shares y pagos aprobados |
| Servicios mensuales e históricos | Gastos / Servicios | create_monthly_basic_services; conserva aportes, referencias, promedios y recibos |
| Gasto extraordinario o descuento de servicio | Gastos / Registrar gasto | create_expense / create_utility_credit; modo explícito |
| Pago parcial, total y comprobante | Pagos / Registrar pago | record_payment_v7; QR requiere comprobante |
| Aprobar o rechazar | Pagos / Detalle / Confirmación | review_payment; no módulo Aprobar pagos duplicado |
| Deuda, cuotas y crédito de cada familia | Familias / Detalle | No se repiten listas de deuda en Inicio o Gastos |
| Nombre de familia | Familias / Detalle / Editar nombre | casa_admin_rename_family, solo administrador |
| Cuentas y asignaciones | Usuarios / Detalle y Crear | admin-users / casa_admin_assign_user_families |
| QR de pago | Administración / QR | payment-qr privado; 1 byte a 5 MiB, PNG/JPEG/WebP |
| Familia pagadora (dos cuartos) | Administración / Familias del encargado | set_payer_families; identidades diferentes |
| Turnos y ciclo semanal | Limpieza | create_cleaning_turn / casa_configure_cleaning_rotation |
| Completar limpieza | Limpieza / Detalle | complete_cleaning_turn; hogar compartido y foto protegida |
| Intercambios | Limpieza / Solicitudes | request_cleaning_swap / review_cleaning_swap |
| Avisos y notificaciones | Avisos; campana para notificaciones | create_notice / notifications |
| Propuestas y votación | Más / Propuestas | create_proposal / cast_proposal_vote |
| PDF y Excel | Reportes | Exportación nativa de los registros autorizados |
| Cierre, auditoría y respaldo | Administración | close_period, reopen_period, audit_log, exportación JSON |
| Contraseña, texto y apariencia | Perfil | Auth y preferencias locales; no configuración duplicada en Administración |
| Plantillas y comentarios | Administración / Plantillas; Gastos / Detalle | recurring_expense_templates / add_expense_comment |

## Reglas que se conservan

Seis identidades contables; Salinas y Salinas2 comparten solo limpieza. Ciclo Choque → Galarza → Salinas → David → Acha. Pagos parciales, crédito individual, aportes de restaurante, promedios y descuentos se procesan con los contratos del servidor, sin reimplementar repartos en la interfaz. Nunca se usa una clave service_role en el APK.

No hay referencias activas a Fondo Común en las rutas, consultas ni RPC de la versión 4.3.1. No se recrean tablas ni se borran datos históricos para retirar un módulo que ya estaba ausente.

## Diferencias detectadas entre el pedido y los permisos actuales

Las funciones actuales de intercambio permiten solicitar y revisar solo a administrador y encargado. La nueva interfaz familiar solicitada requiere adaptar de forma acotada la solicitud al turno propio y la revisión al hogar receptor, preservando administrador/encargado, bloqueos y auditoría. Se debe verificar con pruebas SQL antes de desplegarlo; no basta con mostrar un botón.

Las funciones de propuestas y votos también están restringidas actualmente a administrador/encargado, aunque la interfaz anterior las mostraba a familias. La publicación familiar y el voto solicitados requieren permisos del servidor: un voto por identidad contable, umbral existente de cuatro votos, sin unir votos por el alias de limpieza.

Las imágenes son propuestas visuales con datos ficticios. La aplicación obtiene sus datos reales de Supabase. La cuenta Restaurante existente se conserva por compatibilidad, aunque el pedido organice tres roles principales.

## Implementación y permisos entregados

La migración 006 habilita las funciones familiares solicitadas: propuestas y voto por identidad contable; intercambio solo desde turno propio y revisión por el hogar receptor. Los objetivos de intercambio se consultan mediante una función que devuelve únicamente identificador, familia y fecha, sin notas ni fotografías de otros hogares. Los conteos de votos se agregan sin revelar otras identidades. Las políticas para fotografías se limitan al comprobante de propuesta que corresponde y preservan las restricciones de pagos. Se conservan bloqueo de filas, auditoría y umbral de cuatro votos.

Se desplegó sin insertar registros comerciales: se compararon huellas y conteos de 15 tablas antes y después, sin cambios. Las 169 comprobaciones SQL se ejecutaron en una base local desechable, incluyendo aplicar de nuevo las migraciones de lógica.

Los permisos de crear turnos y consultar su detalle son distintos: una familia puede abrir el turno de su hogar y completar sus tareas, mientras que crear asignaciones sigue reservado al administrador. Plantillas y configuración operativa tienen su ubicación principal en Administración; Perfil contiene exclusivamente cuenta y preferencias personales. Los reportes Excel conservan hojas Gastos, Pagos y Familias para el período elegido.
