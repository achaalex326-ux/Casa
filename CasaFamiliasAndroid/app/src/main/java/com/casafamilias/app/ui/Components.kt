package com.casafamilias.app.ui

import androidx.compose.foundation.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role as SemanticRole
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import com.casafamilias.app.R
import com.casafamilias.app.domain.*
import java.math.BigDecimal

val CasaBlue=Color(0xFF155EEF)
val CasaTeal=Color(0xFF067982)
val Navy=Color(0xFF14233D)
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Inter=FontFamily(Font(R.font.inter,FontWeight.Normal,variationSettings=FontVariation.Settings(FontVariation.weight(400))),Font(R.font.inter,FontWeight.Medium,variationSettings=FontVariation.Settings(FontVariation.weight(500))),Font(R.font.inter,FontWeight.SemiBold,variationSettings=FontVariation.Settings(FontVariation.weight(600))),Font(R.font.inter,FontWeight.Bold,variationSettings=FontVariation.Settings(FontVariation.weight(700))))
@Composable fun CasaTheme(dark: Boolean,content: @Composable ()->Unit) {
    val palette=if(dark)darkColorScheme(
        primary=Color(0xFF9AC1FF),onPrimary=Color(0xFF072D63),primaryContainer=Color(0xFF163B68),onPrimaryContainer=Color(0xFFDCEAFF),
        secondary=Color(0xFF82DDD6),onSecondary=Color(0xFF003B3D),secondaryContainer=Color(0xFF133D45),onSecondaryContainer=Color(0xFFC1F4EE),
        background=Color(0xFF0D1728),surface=Color(0xFF17243A),onSurface=Color(0xFFF2F6FF),onBackground=Color(0xFFF2F6FF),onSurfaceVariant=Color(0xFFBAC9DF),surfaceVariant=Color(0xFF223650),outline=Color(0xFF8497B4),outlineVariant=Color(0xFF344A65))
    else lightColorScheme(
        primary=CasaBlue,onPrimary=Color.White,primaryContainer=Color(0xFFE8F0FF),onPrimaryContainer=Color(0xFF10489F),
        secondary=CasaTeal,onSecondary=Color.White,secondaryContainer=Color(0xFFE8F0FF),onSecondaryContainer=Color(0xFF10489F),
        background=Color(0xFFF3F7FF),surface=Color.White,onSurface=Navy,onBackground=Navy,onSurfaceVariant=Color(0xFF5C6B82),surfaceVariant=Color(0xFFEAF0FC),outline=Color(0xFF778AA5),outlineVariant=Color(0xFFDCE5F4),error=Color(0xFFB3261E))
    val baseline=Typography()
    MaterialTheme(colorScheme=palette,typography=Typography(
        displaySmall=baseline.displaySmall.copy(fontFamily=Inter,fontWeight=FontWeight.Bold),
        headlineLarge=baseline.headlineLarge.copy(fontFamily=Inter,fontWeight=FontWeight.Bold,fontSize=28.sp),
        headlineMedium=baseline.headlineMedium.copy(fontFamily=Inter,fontWeight=FontWeight.Bold,fontSize=24.sp),
        titleLarge=baseline.titleLarge.copy(fontFamily=Inter,fontWeight=FontWeight.SemiBold,fontSize=21.sp),
        titleMedium=baseline.titleMedium.copy(fontFamily=Inter,fontWeight=FontWeight.SemiBold),
        titleSmall=baseline.titleSmall.copy(fontFamily=Inter,fontWeight=FontWeight.SemiBold),
        bodyLarge=baseline.bodyLarge.copy(fontFamily=Inter),bodyMedium=baseline.bodyMedium.copy(fontFamily=Inter),bodySmall=baseline.bodySmall.copy(fontFamily=Inter),
        labelLarge=baseline.labelLarge.copy(fontFamily=Inter,fontWeight=FontWeight.SemiBold),labelMedium=baseline.labelMedium.copy(fontFamily=Inter),labelSmall=baseline.labelSmall.copy(fontFamily=Inter)
    ),shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp)),content=content)
}
private val paths=mapOf(
 "home" to "M3 10L12 3L21 10V21H15V14H9V21H3Z", "expenses" to "M6 3H18V21L15 19L12 21L9 19L6 21Z M9 8H15 M9 12H15",
 "payments" to "M3 5H21V19H3Z M3 10H21 M7 15H11", "families" to "M3 21V18C3 15 6 14 8 14C11 14 13 16 13 18V21 M8 3A4 4 0 1 0 8 11A4 4 0 0 0 8 3 M16 14C19 14 21 16 21 18V21 M17 3A4 4 0 0 1 17 11",
 "more" to "M4 12H5 M11 12H12 M18 12H19", "notifications" to "M6 9A6 6 0 0 1 18 9V15L21 18H3L6 15Z M10 21H14",
 "cleaning" to "M15 3L11 12 M8 11L16 14L12 22L3 19Z", "users" to "M3 21V18C3 15 6 14 8 14C11 14 13 16 13 18V21 M8 3A4 4 0 1 0 8 11A4 4 0 0 0 8 3 M18 7V13 M15 10H21",
 "reports" to "M4 3V21H21 M9 17V12 M14 17V8 M19 17V4", "admin" to "M12 3L4 6V12C4 17 12 21 12 21C12 21 20 17 20 12V6Z M8 12L11 15L16 9",
 "back" to "M19 12H5 M11 6L5 12L11 18", "next" to "M9 5L16 12L9 19", "add" to "M12 4V20 M4 12H20",
 "check" to "M4 12L9 17L20 6", "close" to "M5 5L19 19 M19 5L5 19", "search" to "M10 3A7 7 0 1 0 10 17A7 7 0 0 0 10 3 M15 15L22 22",
 "profile" to "M4 21V18C4 14 20 14 20 18V21 M12 3A4 4 0 1 0 12 11A4 4 0 0 0 12 3", "notices" to "M4 10V15H8L19 19V6L8 10Z M8 15L10 21",
 "proposals" to "M8 16C2 11 5 3 12 3C19 3 22 11 16 16V19H8Z M9 22H15", "date" to "M4 5H20V21H4Z M8 3V7 M16 3V7 M4 10H20",
 "image" to "M3 3H21V21H3Z M3 17L8 12L12 16L16 11L21 17 M8 6A2 2 0 1 0 8 10A2 2 0 0 0 8 6", "lock" to "M5 10H19V21H5Z M8 10V6A4 4 0 0 1 16 6V10",
 "download" to "M12 3V16 M7 11L12 16L17 11 M4 16V21H20V16", "refresh" to "M20 8A8 8 0 1 0 20 16 M20 3V8H15", "qr" to "M3 3H9V9H3Z M15 3H21V9H15Z M3 15H9V21H3Z M15 15H18V18H21V21H15Z",
 "logout" to "M9 3H4V21H9 M9 12H21 M17 8L21 12L17 16", "eye" to "M2 12C6 3 18 3 22 12C18 21 6 21 2 12Z M12 9A3 3 0 1 0 12 15A3 3 0 0 0 12 9"
)
@Composable fun Glyph(name: String,description: String?=null,modifier: Modifier=Modifier.size(24.dp),tint: Color=LocalContentColor.current) {
    val vector=remember(name){ImageVector.Builder(name,24.dp,24.dp,24f,24f).addPath(pathData=PathParser().parsePathString(paths[name] ?: paths.getValue("expenses")).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round).build()}
    Icon(vector,description,modifier,tint=tint)
}
@Composable fun BrandMark(size: Dp=40.dp) {
    Box(Modifier.size(size).background(Brush.linearGradient(listOf(CasaBlue,CasaTeal)),RoundedCornerShape(size*.3f)),contentAlignment=Alignment.Center) {
        Glyph("home",modifier=Modifier.size(size*.62f),tint=Color.White)
        Box(Modifier.align(Alignment.TopEnd).padding(size*.1f).size(size*.14f).background(Color(0xFFFF9D79),CircleShape))
    }
}
@Composable fun Tile(icon: String,modifier: Modifier=Modifier) {
    val dark=MaterialTheme.colorScheme.background.luminance()<.4f
    val warm=icon in setOf("cleaning","notices","date","proposals")
    val teal=icon in setOf("families","users","profile","reports")
    val ink=when {warm->if(dark)Color(0xFFFFB49E) else Color(0xFFB93C24);teal->if(dark)Color(0xFF82DDD6) else Color(0xFF066F73);else->MaterialTheme.colorScheme.primary}
    val background=if(dark)ink.copy(alpha=.14f) else when {warm->Color(0xFFFFF0E8);teal->Color(0xFFE4F8F5);else->MaterialTheme.colorScheme.primaryContainer}
    Box(modifier.size(46.dp).background(background,RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){Glyph(icon,tint=ink)}
}
@Composable fun Panel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),shadowElevation=1.dp){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
@Composable fun MetricButton(icon: String,label: String,value: String,action: String,modifier: Modifier=Modifier,onClick: ()->Unit) {
    Surface(onClick=onClick,modifier=modifier.semantics {role=SemanticRole.Button},shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Tile(icon);Text(label,style=MaterialTheme.typography.labelLarge);Text(value,style=MaterialTheme.typography.headlineMedium)
            Text(action,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
        }
    }
}
val PageHeadingInBar=staticCompositionLocalOf {false}
@Composable fun Title(text: String,subtitle: String?=null) {Column(verticalArrangement=Arrangement.spacedBy(5.dp)){if(!PageHeadingInBar.current)Text(text,style=MaterialTheme.typography.headlineMedium);if(subtitle!=null)Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}}
@Composable fun Hero(label: String,amount: BigDecimal,detail: String="") {
    Surface(shape=RoundedCornerShape(26.dp),color=CasaBlue,contentColor=Color.White,modifier=Modifier.fillMaxWidth(),shadowElevation=3.dp) {
        Box(Modifier.background(Brush.linearGradient(listOf(CasaBlue,Color(0xFF1458CB),CasaTeal)))) {
            Box(Modifier.align(Alignment.TopEnd).offset(x=35.dp,y=(-45).dp).size(150.dp).background(Color.White.copy(alpha=.06f),CircleShape))
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){Glyph(if(label.contains("Gastos"))"expenses" else "payments",modifier=Modifier.size(22.dp));Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)}
                Text(Amounts.format(amount),style=MaterialTheme.typography.headlineLarge)
                if(detail.isNotBlank())Text(detail,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
private fun actionIcon(text: String): String {
    val value=text.lowercase()
    return when {
        "sesión" in value->"logout";"rechazar" in value || "eliminar" in value->"close"
        "guardar" in value || "aprobar" in value || "completar" in value || "aceptar" in value->"check"
        "imagen" in value || "comprobante" in value || "foto" in value->"image"
        "descargar" in value || "exportar" in value->"download"
        "pago" in value || "cuota" in value->"payments";"gasto" in value->"add"
        "usuario" in value || "cuenta" in value || "familia" in value->"profile"
        "actualizar" in value || "reintentar" in value || "intercambio" in value->"refresh"
        "turno" in value || "limpieza" in value->"cleaning";"contraseña" in value->"lock"
        "anterior" in value->"back";"publicar" in value->"add";else->"next"
    }
}
@Composable fun Action(text: String,enabled: Boolean=true,onClick: ()->Unit) {
    Button(onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=16.dp)) {
        Glyph(actionIcon(text),modifier=Modifier.size(22.dp));Spacer(Modifier.width(10.dp));Text(text,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge)
    }
}
@Composable fun Secondary(text: String,enabled: Boolean=true,onClick: ()->Unit) {
    val destructive=listOf("rechazar","eliminar","cerrar sesión").any {text.lowercase().contains(it)}
    FilledTonalButton(onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=14.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(destructive)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,contentColor=if(destructive)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)) {
        Glyph(actionIcon(text),modifier=Modifier.size(20.dp));Spacer(Modifier.width(10.dp));Text(text,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelLarge)
    }
}
@Composable fun NavRow(icon: String,title: String,subtitle: String="",onClick: ()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().semantics {role=SemanticRole.Button}) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Tile(icon)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,style=MaterialTheme.typography.titleSmall);if(subtitle.isNotBlank())Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Box(Modifier.background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(10.dp)).padding(horizontal=10.dp,vertical=8.dp)){Text("Ver",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)}
        }
    }
}
@Composable fun Empty(message: String,detail: String="Los nuevos registros aparecerán aquí.") {Panel {Glyph("expenses",modifier=Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary);Text(message,style=MaterialTheme.typography.titleMedium);if(detail.isNotBlank())Text(detail,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable fun DataLine(label: String,value: String) {Column(verticalArrangement=Arrangement.spacedBy(3.dp)){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value.ifBlank { "—" },style=MaterialTheme.typography.bodyLarge)}}
@Composable fun Badge(status: String) {val label=mapOf("review" to "En revisión","partial" to "Pago parcial","overdue" to "Vencido","pending" to "Pendiente","approved" to "Aprobado","rejected" to "Rechazado","done" to "Completado","paid" to "Pagado","open" to "Abierta","accepted" to "Aceptado","closed" to "Cerrado")[status] ?: status
    val color=when(status){"approved","done","paid","accepted"->Color(0xFF1D7B4A);"rejected","overdue"->Color(0xFFB83232);else->Color(0xFF9C5A00)}
    Surface(shape=RoundedCornerShape(10.dp),color=color.copy(alpha=.12f)){Text(label,Modifier.padding(horizontal=10.dp,vertical=5.dp),color=if(MaterialTheme.colorScheme.background.luminance()<.4f)color.copy(red=(color.red+.3f).coerceAtMost(1f),green=(color.green+.3f).coerceAtMost(1f),blue=(color.blue+.3f).coerceAtMost(1f)) else color,style=MaterialTheme.typography.labelMedium)}
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun Filters(options: List<Choice>,value: String,onSelect: (String)->Unit) {FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){options.forEach { c -> FilterChip(selected=c.value==value,onClick={onSelect(c.value)},label={Text(c.label)}) }}}
@Composable fun Confirm(title: String,message: String,confirm: String="Confirmar",onDismiss: ()->Unit,onConfirm: ()->Unit) {AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(message)},confirmButton={TextButton(onClick=onConfirm){Text(confirm)}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})}
fun routeTitle(route: String)=mapOf("home" to "Inicio","expenses" to "Gastos","payments" to "Pagos","families" to "Familias","more" to "Más","dues" to "Mis cuotas","cleaning" to "Limpieza","users" to "Usuarios","reports" to "Reportes","admin" to "Administración","profile" to "Mi perfil","audit" to "Auditoría","periods" to "Cierre y reapertura","templates" to "Plantillas de gastos","notifications" to "Notificaciones","notices" to "Avisos","proposals" to "Propuestas","restaurant-contributions" to "Aportes del restaurante")[route] ?: "Detalle"

@Composable fun BottomLabel(label: String,slotWidth: Dp) {
    val measurer=rememberTextMeasurer();val density=LocalDensity.current
    val style=MaterialTheme.typography.labelMedium
    val lines=label.replace(" ","\n")
    val size=remember(lines,slotWidth,density.fontScale,density.density,style) {
        val width=with(density){slotWidth.toPx()}
        var size=11f
        while(size>3f && measurer.measure(AnnotatedString(lines),style.copy(fontSize=size.sp),softWrap=false,density=density).size.width>width-2)size-=.25f
        size.sp
    }
    Text(lines,Modifier.requiredWidth(slotWidth),style=style.copy(fontSize=size),maxLines=if(lines.contains("\n"))2 else 1,softWrap=false,textAlign=TextAlign.Center)
}
