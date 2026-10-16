from pathlib import Path
import re,json
from docx import Document
from docx.shared import Inches,Pt,RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT,WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

OUT=Path(__file__).resolve().parent
TITLE='Evaluación de Casa Familias: usabilidad, funcionalidad, límites y estrés'
DOC=Document();section=DOC.sections[0]
section.page_width=Inches(8.5);section.page_height=Inches(11)
for name in ['top_margin','bottom_margin','left_margin','right_margin']:setattr(section,name,Inches(1))
section.header_distance=Inches(.5);section.footer_distance=Inches(.5)
styles=DOC.styles
for name in ['Normal','Title','Subtitle','Heading 1','Heading 2','Heading 3','Caption','Header','Footer']:
 s=styles[name];s.font.name='Times New Roman';s.font.size=Pt(12);s.font.color.rgb=RGBColor(0,0,0)
 s._element.get_or_add_rPr().get_or_add_rFonts().set(qn('w:ascii'),'Times New Roman')
 s._element.rPr.rFonts.set(qn('w:hAnsi'),'Times New Roman')
 s._element.rPr.rFonts.set(qn('w:eastAsia'),'Times New Roman')
 s._element.rPr.rFonts.set(qn('w:cs'),'Times New Roman')
 for attr in list(s._element.rPr.rFonts.attrib):
  if 'theme' in attr.lower():del s._element.rPr.rFonts.attrib[attr]
 for tag in ['pBdr']:
  if s.element.pPr is not None:
   for child in list(s.element.pPr):
    if child.tag==qn('w:'+tag):s.element.pPr.remove(child)
 for tag in ['spacing','kern','szCs']:
  for child in list(s.element.rPr):
   if child.tag==qn('w:'+tag):s.element.rPr.remove(child)
 cs_size=OxmlElement('w:szCs');cs_size.set(qn('w:val'),'24');s.element.rPr.append(cs_size)
 f=s.paragraph_format;f.line_spacing=2;f.space_before=Pt(0);f.space_after=Pt(0)
 f.widow_control=True
normal=styles['Normal'];normal.paragraph_format.first_line_indent=Inches(.5)
for name in ['Heading 1','Heading 2','Heading 3']:
 s=styles[name];s.font.bold=True;s.paragraph_format.first_line_indent=Inches(0)
 s.paragraph_format.keep_with_next=True;s.paragraph_format.space_before=Pt(0)
styles['Heading 1'].paragraph_format.alignment=WD_ALIGN_PARAGRAPH.CENTER
styles['Heading 2'].paragraph_format.alignment=WD_ALIGN_PARAGRAPH.LEFT
styles['Title'].font.bold=True
styles['Title'].paragraph_format.alignment=WD_ALIGN_PARAGRAPH.CENTER
styles['Title'].paragraph_format.first_line_indent=Inches(0)
# Set the proofing language and avoid a theme color/font override.
settings=DOC.settings.element
lang=OxmlElement('w:themeFontLang');lang.set(qn('w:val'),'es-BO');settings.append(lang)
header=section.header.paragraphs[0];header.alignment=WD_ALIGN_PARAGRAPH.RIGHT
header.paragraph_format.first_line_indent=Inches(0);header.paragraph_format.line_spacing=1
field=OxmlElement('w:fldSimple');field.set(qn('w:instr'),'PAGE')
r=OxmlElement('w:r');pr=OxmlElement('w:rPr');fonts=OxmlElement('w:rFonts');fonts.set(qn('w:ascii'),'Times New Roman');fonts.set(qn('w:hAnsi'),'Times New Roman');pr.append(fonts);sz=OxmlElement('w:sz');sz.set(qn('w:val'),'24');pr.append(sz);r.append(pr);txt=OxmlElement('w:t');txt.text='1';r.append(txt);field.append(r);header._p.append(field)
# No running head or footer is required for an APA student paper.
for p in section.footer.paragraphs:p.text=''

def rich(par,text):
 for i,part in enumerate(re.split(r'\*(.*?)\*',text)):
  if part:
   run=par.add_run(part);run.italic=i%2==1
 return par

def para(text,indent=True):
 p=DOC.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.LEFT
 if not indent:p.paragraph_format.first_line_indent=Inches(0)
 rich(p,text);return p

def centered(text,bold=False):
 p=DOC.add_paragraph();p.alignment=WD_ALIGN_PARAGRAPH.CENTER;p.paragraph_format.first_line_indent=Inches(0)
 p.add_run(text).bold=bold;return p


# APA 7 student paper; all human observations are intentionally unfilled.
def heading(text): DOC.add_heading(text, level=1)

def table(title, headers, rows, widths=None, note=None):
    number=len(DOC.tables)+1
    p=para('',False);p.paragraph_format.keep_with_next=True;p.add_run(f'Tabla {number}').bold=True
    p=para('',False);p.paragraph_format.keep_with_next=True;p.add_run(title).italic=True
    t=DOC.add_table(rows=1,cols=len(headers));t.autofit=False;t.alignment=WD_TABLE_ALIGNMENT.CENTER
    widths=widths or [1/len(headers)]*len(headers)
    for col,w in zip(t.columns,widths): col.width=Inches(6.5*w)
    for c,v in zip(t.rows[0].cells,headers): c.text=v
    for values in rows:
        for c,v in zip(t.add_row().cells,values): c.text=str(v)
    borders=OxmlElement('w:tblBorders')
    for tag in ['top','bottom','left','right','insideH','insideV']:
        e=OxmlElement('w:'+tag);e.set(qn('w:val'),'single' if tag in ['top','bottom'] else 'nil');e.set(qn('w:sz'),'6');borders.append(e)
    t._tbl.tblPr.append(borders)
    for i,row in enumerate(t.rows):
        rp=row._tr.get_or_add_trPr();rp.append(OxmlElement('w:cantSplit'))
        if i==0: rp.append(OxmlElement('w:tblHeader'))
        for c,w in zip(row.cells,widths):
            c.width=Inches(6.5*w);c.vertical_alignment=WD_CELL_VERTICAL_ALIGNMENT.TOP
            if i==0:
                cb=OxmlElement('w:tcBorders');b=OxmlElement('w:bottom');b.set(qn('w:val'),'single');b.set(qn('w:sz'),'6');cb.append(b);c._tc.get_or_add_tcPr().append(cb)
            for p in c.paragraphs:
                f=p.paragraph_format;f.first_line_indent=Inches(0);f.line_spacing=1;f.space_before=Pt(3);f.space_after=Pt(3);f.keep_with_next=i==0
                for r in p.runs: r.font.name='Times New Roman';r.font.size=Pt(10.5);r.bold=i==0
    if note:
        p=para('',False);p.add_run('Nota. ').italic=True;p.add_run(note);p.paragraph_format.line_spacing=1
    para('',False).paragraph_format.line_spacing=1
    return t

p=DOC.add_paragraph(style='Title');p.paragraph_format.space_before=Pt(72);p.add_run(TITLE)
centered('')
for text in ['Integrante 1: __________________________________','Integrante 2: __________________________________','Institución: ___________________________________','Desarrollo de Aplicaciones Móviles I','Sexto semestre','Docente: _____________________________________','Fecha: _______________________________________']: centered(text)
DOC.add_page_break();centered(TITLE,True)
para('Este informe desarrolla las actividades de la Ficha de Trabajo 02 mediante un plan de pruebas, el registro de evidencias técnicas y la identificación de comprobaciones pendientes (*Ficha de trabajo 02*, s. f.). Se presenta en formato APA 7 para trabajo estudiantil. Los nombres y la fecha de entrega se dejan en blanco.')
para('La versión evaluada es Casa Familias 5.1, código 7, una aplicación Android nativa desarrollada con Kotlin y Jetpack Compose Material 3, conectada con Supabase. La evidencia técnica utilizada corresponde al 16 de octubre de 2026 y está conservada en el repositorio del proyecto (achaalex326-ux, 2026).')

heading('Propósito y alcance')
para('El objetivo es comprobar si las funciones de gestión familiar responden a las necesidades de sus usuarios, detectar dificultades y justificar las mejoras con evidencia. El proceso comprende probar, observar, registrar, analizar, corregir y volver a probar. Se distinguen las pruebas de lógica, de integración simulada y de interfaz, de acuerdo con las orientaciones de Google (s. f.-b).')
para('La aplicación organiza gastos, pagos, familias, usuarios y turnos de limpieza. El orden semanal solicitado es Choque → Galarza → Salinas → David → Acha. Salinas y Salinas2 corresponden a un mismo hogar para la limpieza; sus identificadores contables se mantienen separados para no alterar pagos ni deudas.')
para('Se dispone de 30 pruebas unitarias y 8 pruebas instrumentadas aprobadas. Los contratos HTTP usan un servidor simulado y las pantallas automatizadas utilizan datos ficticios de solo lectura. Estas comprobaciones no equivalen a una sesión real en Supabase, a una prueba con usuarios ni a un ensayo en un teléfono físico. Las mediciones que todavía requieren observación se registran como pendientes.')

table('Verificación inicial de Casa Familias 5.1',['Aspecto','Resultado documentado','Alcance o pendiente'],[
('Apertura','Correcto en emulador: el APK release muestra el acceso nativo.','Probar la instalación en el teléfono de presentación.'),
('Navegación','Correcto en los ocho ensayos de interfaz.','Repetir con una persona que no conozca la aplicación.'),
('Operaciones principales','Parcial: reglas, formularios y permisos comprobados localmente.','Completar autenticación, carga de QR y guardados reales con cuentas de prueba.'),
('Procesamiento','Correcto en las pruebas de dinero decimal, saldo y exportación.','Contrastar con los registros de una cuenta de prueba.'),
('Validaciones','Correcto en los casos automatizados de nombres, claves, importes y turnos.','Evaluar si el participante comprende cada mensaje.'),
('Mensajes','Se muestra el rechazo de contraseñas diferentes en la interfaz.','Comprensión y satisfacción pendientes.'),
('Conservación','Tema y tamaño de texto sobreviven a la recreación de Activity.','El cierre completo y la recuperación de registros nuevos siguen pendientes.')],[.21,.42,.37])

heading('Fase 1. Tareas de usabilidad')
para('Se proponen cinco tareas reales. Las acciones que cambian información se realizarán con cuentas y registros de prueba. No es necesario efectuar transferencias bancarias. La prueba de usabilidad debe estudiar la efectividad y las dificultades observadas, además de la opinión del participante (International Software Testing Qualifications Board [ISTQB], s. f.-b).')
table('Tareas y resultados esperados',['ID','Acción solicitada','Resultado esperado'],[
('T1','Con una cuenta de familia, encontrar la deuda o saldo pendiente.','Identifica su propia información sin acceder a la de otra familia.'),
('T2','Encontrar el QR de pago e identificar cómo adjuntar un comprobante.','Localiza el QR, comprende las instrucciones y reconoce el formulario de pago.'),
('T3','Como administrador, cambiar el nombre de una familia de prueba.','Se muestra el nuevo nombre y se conserva después de recargar.'),
('T4','Como administrador, cambiar la familia de un usuario de prueba.','El usuario queda asociado a la familia elegida y ve la información correspondiente.'),
('T5','Como administrador, configurar la repetición semanal de limpieza.','Se conserva Choque, Galarza, Salinas, David y Acha, sin repetir Salinas2 como otro hogar.')],[.08,.44,.48])

heading('Fase 2. Observación y medición de la usabilidad')
para('El participante recibirá la tarea sin una explicación paso a paso. El observador anotará lo que ocurre y evitará indicar qué botón debe pulsar. Se cronometrará desde la lectura de la tarea hasta su finalización o abandono. Un error será una acción que aleje del objetivo; una ayuda será una intervención del observador. La satisfacción se recogerá al terminar con una pregunta abierta sobre las dificultades.')
table('Guía de observación del participante',['Aspecto por observar','Observación real'],[(s,'Pendiente: __________________________________') for s in ['Encuentra dónde comenzar.','Comprende la tarea.','Entiende textos y botones.','Duda o se equivoca.','Necesita ayuda.','Encuentra cómo regresar.','Comprende los errores.','Reconoce que la operación terminó.','Completa la tarea.']],[.48,.52])
table('Registro de tiempos, errores, ayudas y éxito',['Tarea','Tiempo (s)','Errores','Ayudas','¿Completó?'],[(f'T{i}','________','________','________','Sí / No') for i in range(1,6)],note='No se han medido tiempos ni observado participantes. Completar después del ensayo; no sustituir con estimaciones.')
para('La tasa de éxito se calculará como tareas completadas divididas entre tareas intentadas, multiplicado por 100. Los tiempos solo se compararán cuando se empleen tareas equivalentes, condiciones similares y un procedimiento común. No se presentan porcentajes de mejora porque no existe una medición humana previa comparable.')

heading('Fase 3. Casos de prueba funcional')
para('Los casos distinguen entrada, resultado esperado, resultado obtenido y estado. Esta organización facilita la confirmación y la regresión (ISTQB, s. f.-a). La automatización de la interfaz aporta evidencia adicional sobre la navegación y los mensajes (Google, s. f.-a). En las Tablas 5 y 6 se resume la evidencia de la versión 5.1; el detalle reproducible se conserva en sus archivos XML y registros de ejecución (achaalex326-ux, 2026).')
table('Diez casos funcionales y su evidencia técnica',['ID y función','Entrada o pasos','Esperado y obtenido','Estado'],[
('CP-01. Inicio','Instalar la actualización release y abrirla en Android 11.','Esperado: acceso nativo. Obtenido: pantalla de inicio de sesión visible.','Correcto en emulador.'),
('CP-02. Administrador','Abrir Inicio, Gastos, Pagos, Familias y Más.','Esperado: cinco pestañas y opciones sin duplicación. Obtenido: se verifican las pestañas y el menú.','Correcto con datos ficticios.'),
('CP-03. Privacidad de familia','Abrir una vista con rol familia.','Esperado: cuotas propias y sin administración de usuarios. Obtenido: controles administrativos ausentes.','Correcto en UI; acceso real pendiente.'),
('CP-04. Restaurante','Abrir el perfil restaurante de ejemplo.','Esperado: lectura y saldo 60,00 del fixture. Obtenido: saldo visible y sin pestaña Pagos.','Correcto con datos ficticios.'),
('CP-05. Cálculo decimal','Sumar 0,10 + 0,20 y analizar formatos monetarios.','Esperado: 0,30 y conversión decimal correcta. Obtenido: valores esperados mediante BigDecimal.','Correcto en prueba unitaria.'),
('CP-06. Saldo','Deuda 100; pago aprobado aplicado 30; otro pendiente 50.','Esperado: saldo 70. Obtenido: 70; el pendiente no reduce la deuda.','Correcto en prueba unitaria.'),
('CP-07. Nombre familiar','Validar nombres con 0, 1, 80 y 81 caracteres.','Esperado: aceptar 1 y 80; rechazar 0 y 81. Obtenido: límites cumplidos.','Correcto local; guardado real pendiente.'),
('CP-08. Limpieza','Validar hogares únicos, fecha lunes y alias Salinas/Salinas2.','Esperado: cinco hogares y seis identidades contables. Obtenido: alias compartido sin unir la contabilidad.','Correcto en reglas locales.'),
('CP-09. Exportación','Generar el libro de reportes con datos de ejemplo.','Esperado: XML válido, hojas Gastos, Pagos y Familias. Obtenido: se verifican las tres hojas.','Correcto localmente.'),
('CP-10. Turno y preferencias','Abrir el turno propio; activar tema oscuro y texto grande; recrear Activity.','Esperado: turno accesible y preferencias conservadas. Obtenido: acciones y ajustes siguen visibles.','Correcto en interfaz.')],[.19,.26,.37,.18],note='Los diez casos sintetizan verificaciones existentes; no equivalen a diez nuevas operaciones en el servidor. El CP-08 no demuestra por sí solo que el trabajo automático semanal se haya ejecutado.')

heading('Fase 4. Pruebas de validación')
table('Entradas inválidas y resultado observado',['ID','Entrada incorrecta','Resultado esperado','Resultado obtenido'],[
('V1','Importe vacío, texto, cero, negativo, NaN o Infinity.','Rechazar importes no válidos o no positivos.','Correcto: rechazo en pruebas unitarias.'),
('V2','Contraseña de siete caracteres o confirmación diferente.','Rechazar y explicar el problema.','Correcto local; en UI se muestra que las contraseñas no coinciden.'),
('V3','Nombre vacío o de 81 caracteres.','Rechazar el nombre.','Correcto en validación local.'),
('V4','La misma familia en ambas posiciones del encargado.','Rechazar la duplicación.','Correcto en validación local.'),
('V5','Hogar repetido en el ciclo o fecha inicial que no sea lunes.','Rechazar la configuración.','Correcto en validación local.'),
('V6','Pago por QR sin comprobante.','Solicitar el comprobante antes de continuar.','Correcto local; carga real del archivo pendiente.')],[.07,.35,.28,.30])

heading('Fase 5. Pruebas de límites')
para('La Tabla 7 recoge valores concretos de frontera. Un máximo que no está establecido no se inventa. Los resultados se refieren a los formularios locales y no sustituyen la comprobación de las restricciones del servidor.')
table('Fronteras verificadas en los formularios',['Campo','Mínimo válido','Máximo válido','Antes / después','Resultado'],[
('Nombre familiar','1 carácter.','80 caracteres.','0 y 81 caracteres.','1 y 80 aceptados; 0 y 81 rechazados.'),
('Contraseña','8 caracteres.','No evaluado en esta suite.','7 caracteres; 8 con confirmación distinta.','7 rechazados; 8 coincidentes aceptados; diferencia rechazada.'),
('Texto de una tarea','No determinado en estos casos.','160 caracteres.','161 caracteres; el mínimo no se midió.','160 aceptados y 161 rechazados.'),
('Importe positivo','0,01 en los casos ensayados.','No determinado en esta suite.','0, −1, vacío, NaN e Infinity.','0,01 aceptado; entradas inválidas rechazadas.')],[.18,.19,.22,.21,.20],note='La aceptación de 0,01 no constituye una medición de todos los incrementos decimales posibles. El máximo monetario del sistema queda fuera del resultado informado.')

heading('Fase 6. Pruebas de estrés')
para('Se prepara un plan con tres condiciones exigentes. No se han medido todavía tiempos de respuesta, memoria ni estabilidad del APK 5.1 bajo estas cargas. Las antiguas mediciones de la interfaz web 4.3.1 no se reutilizan como resultados de la versión nativa. Los datos se generarán en un entorno de prueba para evitar modificar registros familiares reales.')
table('Plan de estrés y registro pendiente',['Prueba','Procedimiento propuesto','Qué registrar','Estado'],[
('E1. Cantidad de datos','Cargar 100, 500 y 1000 pagos ficticios; repetir tres veces cada cantidad en el mismo dispositivo.','Tiempo de apertura de la lista, bloqueos, memoria y registros visibles.','Pendiente.'),
('E2. Operaciones repetidas','Cambiar 20 veces el nombre de una familia de prueba y consultar el resultado tras cada guardado.','Guardados correctos, errores, duplicados y estado final.','Pendiente.'),
('E3. Acciones rápidas','Pulsar Guardar repetidamente y alternar secciones durante 30 segundos en una cuenta de prueba.','Solicitudes repetidas, mensajes, bloqueos y consistencia final.','Pendiente.')],[.20,.40,.28,.12],note='La prueba del contrato de paginación con 501 registros es funcional. No demuestra que el teléfono mantenga un rendimiento aceptable con esa cantidad.')
para('En cada ensayo se anotarán el modelo del teléfono, versión de Android, memoria disponible, tipo de conexión y tamaño de los datos. Si hay un fallo, se conservarán captura, registro y pasos para reproducirlo. Se repetirá después de corregirlo con las mismas condiciones.')

heading('Fase 7. Pruebas de persistencia')
table('Persistencia de los registros y las preferencias',['Prueba','Resultado esperado','Resultado obtenido','Estado'],[
('P1. Guardar → cerrar → abrir.','El nombre de una familia de prueba sigue actualizado.','No observado en cierre completo del APK.','Pendiente.'),
('P2. Registrar varios → reiniciar → recuperar.','Tres registros de prueba vuelven a mostrarse sin pérdida ni duplicados.','No ejecutado con registros nuevos y reinicio del teléfono.','Pendiente.'),
('P3. Modificar → cerrar → abrir → comprobar.','La familia asignada al usuario y sus permisos corresponden al cambio.','No verificado con autenticación real después de cerrar.','Pendiente.'),
('P4. Preferencias → recrear Activity.','Se conservan el tema oscuro y el tamaño de texto.','Conservación comprobada en el emulador.','Correcto; alcance limitado.')],[.26,.28,.30,.16],note='Recrear una Activity no equivale a terminar el proceso, reiniciar el teléfono ni recuperar una operación guardada en Supabase.')

heading('Fase 8. Detección y clasificación de problemas')
para('Se clasifican los reportes de la evolución del proyecto para conservar su contexto. Los avisos de versiones anteriores no se presentan como fallos reproducidos en 5.1. La relación con calidad de producto orienta el análisis y no constituye una certificación ISO/IEC 25010:2023 (International Organization for Standardization [ISO], 2023).')
table('Hallazgos, clasificación y prioridad',['Hallazgo','Tipo','Prioridad','Evidencia y situación'],[
('Pantalla inicial inmóvil al abrir sin conexión.','Mensaje / disponibilidad.','Alta.','Captura y reporte previo del usuario; posteriormente indicó que funcionó al conectarse. La recuperación sin red en 5.1 debe ensayarse.'),
('Carga de QR con “Bucket not found”.','Funcional / configuración.','Alta.','Captura histórica de Storage. No se ejecutó una carga bancaria real en el APK 5.1.'),
('Dificultad para localizar acciones y exceso de navegación.','Usabilidad / navegación.','Media.','Solicitud del usuario; navegación actual validada con cinco pestañas y menú sin repetición.'),
('Texto desbordado o controles poco claros.','Interfaz.','Media.','Reporte del usuario; nueva interfaz ensayada a 320 × 800 con ampliación de texto.'),
('Salinas y Salinas2 como hogares distintos en limpieza.','Regla de negocio.','Alta.','Aclaración del usuario; alias compartido verificado por pruebas locales sin fusionar cuentas.')],[.28,.18,.13,.41])

heading('Fase 9. Priorización de los problemas')
para('Se considera crítico un defecto que impide usar una función esencial sin alternativa; alto, el que afecta considerablemente la experiencia; medio, el que permite continuar con dificultad; y bajo, el de impacto menor. Antes de presentar, se priorizarán la apertura con conexión, la sesión real y la carga del QR; después, la integridad de nombres, asignaciones y turnos. Las mejoras visuales requieren además observación humana para evaluar si reducen confusión.')

heading('Fase 10. Correcciones y nuevas pruebas')
table('Tres mejoras y la evidencia posterior disponible',['Problema previo','Causa de diseño o regla','Mejora implementada','Comprobación posterior'],[
('Acciones difíciles de ubicar.','Distribución de destinos y repetición de opciones.','Navegación inferior con cinco pestañas y un menú Más sin repetirlas.','Pruebas de permisos, pestañas y navegación aprobadas en 5.1.'),
('Lectura y presentación poco claras.','Jerarquía visual y adaptación al espacio disponible.','Componentes nativos Material 3, botones con iconos y texto, nueva paleta y ajustes de lectura.','Pantallas capturadas; caso de texto grande y tema oscuro aprobado en emulador.'),
('Doble participación del mismo hogar en la limpieza.','Identidad contable confundida con hogar de limpieza.','Alias Salinas/Salinas2 para un mismo hogar, manteniendo cuentas separadas.','Pruebas locales verifican cinco hogares, seis identidades y rechazo de repetidos.')],[.22,.23,.28,.27],note='Estas mejoras pertenecen a la evolución del proyecto; no todas se introdujeron por primera vez en 5.1. En 5.1 se volvió a ejecutar la suite correspondiente.')
table('Comparación de indicadores antes y después',['Indicador','Antes','Después'],[
('Tiempo de las cinco tareas','Sin medición humana comparable.','Pendiente de cronometraje.'),
('Errores del participante','Sin registro previo.','Pendiente de observación.'),
('Ayudas recibidas','Sin registro previo.','Pendiente de observación.'),
('Éxito de las tareas','Sin tasa previa calculada.','Pendiente de ensayo.'),
('Regresión automatizada','No se establece una línea base equivalente en esta ficha.','30 pruebas unitarias y 8 instrumentadas aprobadas en 5.1.')],[.25,.37,.38])
para('La evidencia demuestra que los comportamientos ensayados cumplen las aserciones automatizadas. Todavía no permite afirmar que el usuario tarda menos ni que se equivoca menos. Esa conclusión requiere completar el registro de usabilidad y repetirlo en condiciones comparables.')

heading('Fase 11. Relación con Kotlin')
para('La versión actual utiliza Kotlin de forma directa. Las reglas de dominio, los formularios, el repositorio y las pantallas se encuentran en archivos .kt. La seguridad frente a valores nulos ayuda a tratar entradas ausentes sin asumir que todo dato existe (JetBrains, s. f.).')
table('Conceptos de Kotlin aplicados al proyecto',['Elemento','Aplicación en Casa Familias','Cómo explicarlo'],[
('if / when','Validaciones de campos, permisos y elección de estados.','La condición determina si se permite continuar o se muestra un error.'),
('Funciones y BigDecimal','Análisis de importes y cálculo de saldos.','Se separa el cálculo de la pantalla y se evita el error de suma binaria de decimales.'),
('List y transformaciones','Registros, pestañas y hogares de limpieza.','Se construyen colecciones y se evita repetir destinos u hogares.'),
('find / filter','Selección de filas según familia, estado o acceso.','La selección limita la información que corresponde mostrar.'),
('data class','Modelos como Profile y Snapshot.','Agrupan los datos que la interfaz y las reglas utilizan.'),
('Tipos anulables','Resultado de analizar un importe o encontrar un dato.','Un valor ausente debe tratarse antes de utilizarlo.'),
('Estado de Compose y ViewModel','Pantallas, formularios y actualización del contenido.','La interfaz representa el estado actual; las pruebas verifican cambios y navegación.')],[.24,.38,.38])

heading('Uso de inteligencia artificial como tutor')
para('En este trabajo se utilizó asistencia de IA para revisar el proyecto, implementar cambios solicitados, organizar comprobaciones y preparar la documentación. No se atribuyen al estudiante observaciones, mediciones ni aprendizajes que no haya realizado. Cada integrante debe revisar el código, reproducir los casos elegidos y explicar con sus propias palabras las decisiones. La Tabla 14 diferencia los pedidos reales de los aprendizajes personales pendientes.')
table('Registro de asistencia y revisión del estudiante',['Pedido o tema real','Asistencia utilizada','Qué aprendí / qué verifiqué'],[
('Mejorar colores, botones y legibilidad.','Revisión de la interfaz y preparación de una versión visual actualizada.','Completar con la explicación personal: __________________'),
('Mantener la limpieza semanal y unificar Salinas/Salinas2 como hogar.','Análisis de la regla y comprobaciones de hogares e identidades contables.','Completar después de revisar el modelo: __________________'),
('Preparar el documento en APA.','Organización del informe, formato y distinción de resultados y pendientes.','Completar después de realizar las pruebas: __________________')],[.33,.34,.33])

heading('Productos de entrega y defensa')
para('La entrega comprende el APK de pruebas 5.1, el código fuente, las capturas y los registros técnicos existentes, además de este informe editable. Las versiones anteriores se conservan como antecedentes; la comparación humana antes y después sigue pendiente. Antes de entregar al docente deben completarse las observaciones de un compañero, tres ensayos de estrés y las tres pruebas de persistencia con cuentas de prueba.')
para('Cada integrante deberá explicar qué se probó, qué problema se encontró, por qué pudo ocurrir, qué se modificó y qué concepto de Kotlin intervino. Para demostrar una corrección, deberá mostrar el caso de prueba, el resultado esperado, la evidencia posterior y las limitaciones. Un resultado aprobado en el emulador no debe presentarse como un ensayo realizado por una persona en un teléfono físico.')

heading('Conclusiones')
para('Casa Familias 5.1 cuenta con navegación nativa, validaciones de datos y una separación entre reglas de negocio, acceso a datos y presentación. Los resultados disponibles muestran 30 pruebas unitarias y 8 instrumentadas sin fallos, además de la apertura del APK release en emulador. Android Lint registra cero errores y 13 advertencias; este resultado no sustituye las pruebas de uso.')
para('El informe permite organizar la evaluación sin inventar resultados. La comprobación con usuarios, el rendimiento bajo carga, la carga real del QR y la recuperación de registros tras cerrar el APK deben completarse antes de afirmar que toda la experiencia está validada. La mejora se justificará mediante casos reproducibles y evidencia, no mediante una declaración de que la aplicación es perfecta.')

DOC.add_page_break();heading('Referencias')
refs=[
('achaalex326-ux. (2026). *Casa Familias (versión 5.1)* [Código fuente y resultados de pruebas]. GitHub. ','https://github.com/achaalex326-ux/Casa/tree/ad2d8eaf38a64413d9218c278c6f02e7e82a4337'),
('*Ficha de trabajo 02: Evaluación de una aplicación móvil: usabilidad, funcionalidad, límites y estrés* [Material de clase]. (s. f.).',''),
('Google. (s. f.-a). *Automate UI tests*. Android Developers. ','https://developer.android.com/training/testing/ui-tests'),
('Google. (s. f.-b). *Fundamentals of testing Android apps*. Android Developers. ','https://developer.android.com/training/testing/fundamentals'),
('International Organization for Standardization. (2023). *Systems and software engineering—Systems and software Quality Requirements and Evaluation (SQuaRE)—Product quality model* (ISO/IEC 25010:2023). ','https://www.iso.org/standard/78176.html'),
('International Software Testing Qualifications Board. (s. f.-a). *Certified Tester Foundation Level (CTFL) v4.0 overview*. ','https://istqb.org/certifications/certified-tester-foundation-level-ctfl-v4-0/'),
('International Software Testing Qualifications Board. (s. f.-b). *Certified Tester Usability Testing (CT-UT)*. ','https://istqb.org/certifications/certified-tester-usability-testing-ct-ut/'),
('JetBrains. (s. f.). *Null safety*. Kotlin. ','https://kotlinlang.org/docs/null-safety.html')]
for text,url in refs:
    p=para(text,False);p.paragraph_format.left_indent=Inches(.5);p.paragraph_format.first_line_indent=Inches(-.5)
    if url: p.add_run(url)
DOC.add_page_break();centered('Apéndice A',True);centered('Ubicación y alcance de las evidencias',True)
para('La referencia al repositorio utiliza una revisión fija para conservar la correspondencia entre versión y pruebas. Dentro de entregas/casa-familias-5.1 se encuentran verificacion.json y la carpeta evidencias. NativeRulesTest contiene 21 pruebas; RepositoryContractTest contiene 9. El registro android-tests.txt documenta 8 pruebas instrumentadas. Los archivos de pruebas usan datos ficticios y no representan transferencias ni pagos reales.')
para('Las capturas están en diseno/nativo-v5.1/capturas-android. La ejecución principal de interfaz utilizó Android 11, API 30, resolución 320 × 800 y texto del sistema al 130 %. El caso oscuro añadió texto de la aplicación al 130 %, con escala combinada del 169 %. Se verificó además que el APK release abre la pantalla nativa de acceso. No se registra una prueba de la versión 5.1 en un teléfono físico desde este entorno.')
para('Para terminar la ficha, complete los campos del participante, ejecute T1 a T5 sin orientar los pasos, registre tiempo, errores y ayudas, y añada sus observaciones. Después realice E1 a E3 y P1 a P3 en un entorno de prueba. Conserve capturas propias y resultados antes y después cuando pueda repetir las mismas condiciones.')
DOC.core_properties.title=TITLE
DOC.core_properties.subject='Ficha de Trabajo 02, Casa Familias 5.1. Formato APA 7 estudiantil.'
DOC.core_properties.author=''
DOC.core_properties.keywords='APA 7, Casa Familias, Kotlin, pruebas de usabilidad'
DOC.core_properties.comments='Nombres y fecha en blanco; no se inventan ensayos con usuarios.'
filename=OUT/'Ficha_02_Casa_Familias_APA_7_Actualizada.docx'
DOC.save(filename)
print(json.dumps({'archivo':str(filename),'bytes':filename.stat().st_size,'tablas':len(DOC.tables),'referencias':len(refs)},ensure_ascii=False))
