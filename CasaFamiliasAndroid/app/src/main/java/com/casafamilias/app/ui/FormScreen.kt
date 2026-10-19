package com.casafamilias.app.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.casafamilias.app.domain.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth

@Composable fun FormScreen(vm: CasaViewModel,s: Snapshot,form: Form,onBack: ()->Unit) {
    val state by vm.state.collectAsState();val context=LocalContext.current;val scope=rememberCoroutineScope()
    val saver=mapSaver<Map<String,String>>(save={ it.filterKeys { key -> key !in setOf("password","confirmation") } },restore={ it.mapValues { v->v.value.toString() } })
    var values by rememberSaveable(form.kind,form.recordId,stateSaver=saver){mutableStateOf(form.fields.associate { it.key to it.initial } + if(form.kind=="payment")mapOf("request_id" to java.util.UUID.randomUUID().toString()) else emptyMap())}
    var page by rememberSaveable(form.kind,form.recordId){mutableIntStateOf(0)}
    var localError by remember {mutableStateOf<String?>(null)}
    var imageKey by remember {mutableStateOf("receipt")};var reading by remember {mutableStateOf(false)}
    var confirm by remember {mutableStateOf(false)}
    fun update(key: String,value: String){values=values+(key to value);localError=null
        if(form.kind=="payment"){
            values=values+("request_id" to java.util.UUID.randomUUID().toString())
            if(key=="family")values=values+("share" to "")
        }
        if(key=="template")s.find("recurring_expense_templates",value)?.let { t -> values=values+("concept" to t.text("concept"))+("justification" to t.text("justification")) }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){ uri ->
        if(uri!=null){runCatching {context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};update(imageKey,uri.toString())}
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Title(form.title)
        if(form.help.isNotBlank())Text(form.help,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(form.pages>1){Text("Paso ${page+1} de ${form.pages}",style=MaterialTheme.typography.labelLarge);LinearProgressIndicator(progress={ (page+1f)/form.pages },modifier=Modifier.fillMaxWidth())}
        if(form.kind=="qr" && values["receipt"].isNullOrBlank())s.rows("payment_qr_settings").firstOrNull()?.text("qr_image_path")?.takeIf {it.isNotBlank()}?.let {path->Panel{Text("QR actual",style=MaterialTheme.typography.titleMedium);ImageReceipt(vm,path,true)}}
        if(form.kind=="payment") {
            val qr=s.rows("payment_qr_settings").firstOrNull()
            if(qr!=null && qr.text("qr_image_path").isNotBlank() && values["method"]!="cash")Panel {Text(qr.text("qr_title").ifBlank {"Pago por QR"},style=MaterialTheme.typography.titleMedium);Text(qr.text("qr_note"));ImageReceipt(vm,qr.text("qr_image_path"),true)}
        }
        form.visible(values).filter { it.page==page && !(form.kind=="assignment" && it.key=="role") && !(form.kind=="payment" && it.key=="family" && !s.profile.role.staff) }.forEach { field ->
            val value=values[field.key].orEmpty()
            if(field.type==InputType.IMAGE) {
                Panel {
                    Text(field.label,style=MaterialTheme.typography.titleSmall)
                    if(value.isNotBlank()){
                        AsyncImage(Uri.parse(value),field.label,Modifier.fillMaxWidth().heightIn(min=120.dp,max=220.dp))
                        TextButton(onClick={update(field.key,"")},enabled=!state.busy){Text("Quitar imagen")}
                    }
                    Secondary(if(value.isBlank()) "Seleccionar imagen" else "Cambiar imagen",!state.busy && !reading){imageKey=field.key;picker.launch(arrayOf("image/png","image/jpeg","image/webp"))}
                    if(value.isNotBlank() && form.kind in setOf("payment","expense","services","credit"))Secondary(if(reading)"Leyendo imagen…" else "Leer importe de la foto",!reading && !state.busy){
                        scope.launch {reading=true
                            val reader=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            try {val recognized=reader.process(InputImage.fromFilePath(context,Uri.parse(value))).await();val lines=recognized.text.lines();val preferred=lines.filter { Regex("total|importe|monto|pagar|cancelar",RegexOption.IGNORE_CASE).containsMatchIn(it) }.ifEmpty {lines.takeLast(12)}
                                val nums=preferred.flatMap {line->Regex("[0-9]+(?:[.,][0-9]{1,3})*").findAll(line).mapNotNull {Amounts.parse(it.value)}.toList()}.filter {it>java.math.BigDecimal.ZERO && it<java.math.BigDecimal(1000000) && it.toInt() !in 2020..2035}
                                val amount=nums.maxOrNull();if(amount!=null)update(if(form.kind=="services")field.key.removeSuffix("_receipt") else "amount",amount.toPlainString())
                                update("ocr_text",recognized.text);vm.inform(if(amount!=null)"Lectura terminada. Revisa el importe antes de guardar." else "No se identificó el importe. Escríbelo manualmente.")
                            }catch(e: Exception){vm.inform("No se pudo leer la imagen. Puedes completar los datos manualmente.")}
                            finally{reader.close();reading=false}
                        }
                    }
                }
            } else {
                val actual=if(form.kind=="payment" && field.key=="share")field.copy(choices=s.paymentDebts(values["family"]?.toLongOrNull()).map {Choice(it.shareId,it.label+" · "+Amounts.format(it.balance))}) else field
                Input(actual,value,!state.busy){update(field.key,it)}
            }
        }
        if(form.kind=="rotation")Panel {Text("Vista previa del orden",style=MaterialTheme.typography.titleSmall);form.fields.filter {it.key.startsWith("order_")}.forEachIndexed {i,f->Text("${i+1}. "+f.choices.firstOrNull {it.value==values[f.key]}?.label.orEmpty())};Text("Los turnos ya asignados se conservan.",style=MaterialTheme.typography.bodySmall)}
        if(form.kind=="payment"){
            val debts=s.paymentDebts(values["family"]?.toLongOrNull())
            val amount=Amounts.parse(values["amount"].orEmpty())
            if(amount!=null && amount>java.math.BigDecimal.ZERO && (values["mode"]!="single" || !values["share"].isNullOrBlank())){
                val plan=PaymentPlans.preview(debts,amount,values["share"].takeIf {values["mode"]=="single"})
                Panel {
                    Text("Así se repartirá tu depósito",style=MaterialTheme.typography.titleMedium)
                    plan.allocations.forEach {a->DataLine(a.debt.label,Amounts.format(a.amount))}
                    DataLine("Total depositado",Amounts.format(plan.amount))
                    DataLine("Deuda que quedaría",Amounts.format(plan.remainingDebt))
                    if(plan.credit>java.math.BigDecimal.ZERO)DataLine("Saldo a favor que se generaría",Amounts.format(plan.credit))
                    Text("Es una vista previa. Al aprobar, se recalcula con los pagos ya revisados. Un solo comprobante cubre todo el depósito.",style=MaterialTheme.typography.bodySmall)
                    if(plan.allocations.isEmpty())Text("No hay deudas pendientes para esta selección.",color=MaterialTheme.colorScheme.error)
                }
            }
        }
        val error=localError ?: state.error
        if(error!=null)Text(error,color=MaterialTheme.colorScheme.error)
        if(page>0)Secondary("Paso anterior",!state.busy){page--}
        Action(if(state.busy)"Guardando…" else if(page<form.pages-1)"Continuar" else if(form.kind=="payment")if(s.profile.role.staff)"Registrar depósito" else "Enviar depósito a revisión" else "Guardar",!state.busy && !reading){
            localError=form.error(values,if(page<form.pages-1)page else null)
            if(localError==null){if(page<form.pages-1)page++ else if(form.kind in setOf("services","expense","credit","external-paid","rotation","complete","payment"))confirm=true else vm.submit(form,values,onBack)}
        }
        Spacer(Modifier.height(12.dp))
    }
    if(confirm)Confirm("Confirmar ${form.title.lowercase()}",when(form.kind){"payment"->"Registrar ${Amounts.format(Amounts.parse(values["amount"].orEmpty()) ?: java.math.BigDecimal.ZERO)} para ${s.family(values["family"]?.toLongOrNull())} con un solo comprobante. ${if(s.profile.role.staff)"Se aprobará y aplicará a las deudas." else "El encargado debe aprobarlo antes de descontar el saldo."}";"rotation"->"Se guardará el orden elegido. Las semanas existentes se conservan.";"complete"->"¿Confirmas que se realizaron las tareas del turno?";else->"Revisa los importes y la familia seleccionada. ¿Quieres guardar estos datos?"},onDismiss={confirm=false}){confirm=false;vm.submit(form,values,onBack)}
}
@Composable fun Input(field: Field,value: String,enabled: Boolean=true,onChange: (String)->Unit) {
    val context=LocalContext.current;var choosing by remember {mutableStateOf(false)};var reveal by remember {mutableStateOf(false)}
    when(field.type) {
        InputType.CHECK -> Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Switch(value=="true",{onChange(it.toString())},enabled=enabled);Spacer(Modifier.width(12.dp));Text(field.label,Modifier.weight(1f))}
        InputType.CHOICE -> {OutlinedButton(onClick={choosing=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=58.dp),shape=MaterialTheme.shapes.medium){Column(Modifier.fillMaxWidth()){Text(field.label,style=MaterialTheme.typography.labelMedium);Text(field.choices.firstOrNull {it.value==value}?.label ?: "Seleccionar",style=MaterialTheme.typography.bodyLarge)}}
            if(choosing)AlertDialog(onDismissRequest={choosing=false},title={Text(field.label)},text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())){if(!field.required)TextButton(onClick={onChange("");choosing=false}){Text("Ninguna")};field.choices.forEach {c->TextButton(onClick={onChange(c.value);choosing=false},modifier=Modifier.fillMaxWidth()){Text(c.label,Modifier.fillMaxWidth())}};if(field.choices.isEmpty())Text("No hay opciones disponibles.")}},confirmButton={TextButton(onClick={choosing=false}){Text("Cerrar")}})
        }
        InputType.DATE -> {OutlinedButton(onClick={val d=runCatching {LocalDate.parse(value)}.getOrElse {LocalDate.now()};DatePickerDialog(context,{_,y,m,day->onChange(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Glyph("date");Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(field.label,style=MaterialTheme.typography.labelMedium);Text(if(value.isBlank())"Elegir fecha" else Dates.label(value))}};if(value.isNotBlank() && !field.required)TextButton(onClick={onChange("")}){Text("Quitar fecha")}}
        InputType.MONTH -> MonthPicker(value,onChange,enabled)
        else -> OutlinedTextField(value,onChange,modifier=Modifier.fillMaxWidth(),enabled=enabled,label={Text(field.label)},shape=MaterialTheme.shapes.medium,singleLine=field.type!=InputType.MULTILINE,minLines=if(field.type==InputType.MULTILINE)3 else 1,visualTransformation=if(field.type==InputType.PASSWORD && !reveal)PasswordVisualTransformation() else VisualTransformation.None,keyboardOptions=KeyboardOptions(keyboardType=when(field.type){InputType.PASSWORD->KeyboardType.Password;InputType.EMAIL->KeyboardType.Email;InputType.MONEY->KeyboardType.Decimal;else->KeyboardType.Text}),trailingIcon=if(field.type==InputType.PASSWORD){{IconButton(onClick={reveal=!reveal}){Glyph("eye",if(reveal)"Ocultar contraseña" else "Mostrar contraseña")}}} else null,supportingText=if(field.max>0){{Text("${value.length}/${field.max}")}} else null)
    }
}
@Composable fun MonthPicker(value: String,onChange: (String)->Unit,enabled: Boolean=true) {
    var open by remember {mutableStateOf(false)}
    OutlinedButton(onClick={open=true},enabled=enabled,shape=MaterialTheme.shapes.medium){Glyph("date");Spacer(Modifier.width(8.dp));Text(Dates.monthLabel(value))}
    if(open){var year by remember {mutableIntStateOf(value.take(4).toIntOrNull() ?: LocalDate.now().year)}
        AlertDialog(onDismissRequest={open=false},title={Text("Mes de consulta")},text={Column{Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){TextButton(onClick={year--}){Text("Año anterior")};Text(year.toString());TextButton(onClick={year++}){Text("Año siguiente")}};Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){(1..12).forEach {m->val month=YearMonth.of(year,m).toString();TextButton(onClick={onChange(month);open=false},modifier=Modifier.fillMaxWidth()){Text(Dates.monthLabel(month))}}}}},confirmButton={TextButton(onClick={open=false}){Text("Cerrar")}})
    }
}
@Composable fun ImageReceipt(vm: CasaViewModel,path: String,qr: Boolean=false) {
    var url by remember(path,qr){mutableStateOf<String?>(null)}
    LaunchedEffect(path,qr){vm.image(path,qr){url=it}}
    if(url!=null)AsyncImage(url,if(qr)"QR de pago" else "Comprobante",Modifier.fillMaxWidth().heightIn(min=180.dp,max=420.dp))
    else Secondary("Cargar imagen"){vm.image(path,qr){url=it}}
}
