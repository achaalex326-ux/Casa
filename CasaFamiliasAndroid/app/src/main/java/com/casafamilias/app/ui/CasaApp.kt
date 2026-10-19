package com.casafamilias.app.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.casafamilias.app.domain.*
import com.casafamilias.app.data.ExportFiles
import com.casafamilias.app.data.BiometricAccess
import com.casafamilias.app.data.DeviceAlerts
import kotlinx.coroutines.*

val LocalBiometricAccess=staticCompositionLocalOf<BiometricAccess?> {null}
val LocalDeviceAlerts=staticCompositionLocalOf<DeviceAlerts?> {null}

@Composable fun CasaApp(vm: CasaViewModel,biometrics: BiometricAccess?=null,alerts: DeviceAlerts?=null) {
    val state by vm.state.collectAsStateWithLifecycle()
    val density=LocalDensity.current
    val activity=LocalContext.current as? Activity
    SideEffect {activity?.window?.let {window->
        if(state.biometricEnabled)window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }}
    SideEffect {activity?.window?.let {window->val bars=WindowCompat.getInsetsController(window,window.decorView);bars.isAppearanceLightStatusBars=!state.dark;bars.isAppearanceLightNavigationBars=!state.dark;window.navigationBarColor=if(state.dark)0xFF0D1728.toInt() else 0xFFF3F7FF.toInt()}}
    CasaTheme(state.dark) {
        CompositionLocalProvider(LocalDensity provides Density(density.density,density.fontScale*state.textScale),LocalBiometricAccess provides biometrics,LocalDeviceAlerts provides alerts) {
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                when {
                    state.starting -> Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){BrandMark(72.dp);Spacer(Modifier.height(20.dp));Text("Casa Familias",style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(18.dp));CircularProgressIndicator();Spacer(Modifier.height(18.dp));Text("Conectando con tu hogar…",textAlign=TextAlign.Center);Text("Necesitas conexión a internet.",style=MaterialTheme.typography.bodySmall)}
                    state.recovery -> RecoveryScreen(vm,state)
                    state.locked -> if(state.passwordEntry)LoginScreen(vm,state) else BiometricLockScreen(vm,state)
                    state.data==null -> LoginScreen(vm,state)
                    else -> key(state.data!!.profile.id,state.data!!.profile.role,state.data!!.profile.familyId,state.data!!.profile.secondFamilyId){SignedApp(vm,state.data!!)}
                }
            }
        }
    }
}
@Composable fun BiometricLockScreen(vm: CasaViewModel,state: CasaState) {
    val biometrics=LocalBiometricAccess.current
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp));BrandMark(80.dp)
        Text("Tu hogar, protegido",style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center)
        Text("Confirma tu identidad para abrir Casa Familias.",textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(biometrics?.available()==true)Action(if(state.biometricPrompting)"Esperando tu huella…" else "Entrar con huella",!state.biometricPrompting){biometrics.authenticate()}
        else Text(biometrics?.explanation() ?: "Usa tu correo y contraseña para ingresar.",textAlign=TextAlign.Center)
        state.info?.let {Text(it,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.primary)}
        Secondary("Usar correo y contraseña",!state.biometricPrompting){vm.usePassword()}
        Text("La huella se verifica en este teléfono.",style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
    }
}
@Composable fun LoginScreen(vm: CasaViewModel,state: CasaState) {
    var email by rememberSaveable {mutableStateOf("")};var password by remember {mutableStateOf("")};var reveal by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=26.dp,vertical=38.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(28.dp));BrandMark(72.dp)
        Text("Casa Familias",style=MaterialTheme.typography.titleLarge)
        Text(if(state.locked)"Ingresa con tu contraseña" else "Bienvenido a casa.",style=MaterialTheme.typography.headlineLarge)
        Text("Pagos claros y un hogar organizado, entre todos.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(email,{email=it},label={Text("Correo electrónico")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=MaterialTheme.shapes.medium,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),enabled=!state.busy)
        OutlinedTextField(password,{password=it},label={Text("Contraseña")},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=MaterialTheme.shapes.medium,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),visualTransformation=if(reveal)VisualTransformation.None else PasswordVisualTransformation(),enabled=!state.busy,trailingIcon={IconButton(onClick={reveal=!reveal}){Glyph("eye",if(reveal)"Ocultar contraseña" else "Mostrar contraseña")}})
        if(state.info!=null)Text(state.info,color=MaterialTheme.colorScheme.primary)
        if(state.error!=null)Text(state.error,color=MaterialTheme.colorScheme.error)
        Action(if(state.busy)"Conectando…" else "Entrar a mi cuenta",!state.busy){vm.login(email,password)}
        TextButton(onClick={vm.recover(email)},enabled=!state.busy){Text("Olvidé mi contraseña")}
        if(state.locked)Secondary("Volver al acceso con huella",!state.busy){vm.returnToBiometric()}
        if(state.error!=null)Secondary("Reintentar conexión",!state.busy){vm.start()}
        Text("¿Necesitas una cuenta? Habla con el administrador.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun RecoveryScreen(vm: CasaViewModel,state: CasaState) {
    var password by remember {mutableStateOf("")};var confirm by remember {mutableStateOf("")}
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Title("Nueva contraseña","Elige una contraseña de al menos 8 caracteres.")
        Input(Field("password","Nueva contraseña",InputType.PASSWORD),password,!state.busy){password=it}
        Input(Field("confirm","Confirma la contraseña",InputType.PASSWORD),confirm,!state.busy){confirm=it}
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Action(if(state.busy)"Guardando…" else "Guardar contraseña",!state.busy){vm.resetPassword(password,confirm)}
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SignedApp(vm: CasaViewModel,s: Snapshot) {
    val state by vm.state.collectAsStateWithLifecycle();val lifecycle=LocalLifecycleOwner.current.lifecycle;val nav=rememberNavController();val context=LocalContext.current;val scope=rememberCoroutineScope()
    val snackbar=remember {SnackbarHostState()};var viewing by remember {mutableStateOf<Pair<String,Boolean>?>(null)}
    var export by remember {mutableStateOf<Triple<String,List<List<String>>,String>?>(null)};var exporting by remember {mutableStateOf(false)}
    val saveFile=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        val uri=result.data?.data;val pending=export
        if(result.resultCode==Activity.RESULT_OK && uri!=null && pending!=null)scope.launch {
            exporting=true
            try {withContext(Dispatchers.IO){val bytes=when(pending.first){"pdf"->ExportFiles.pdf(pending.second);"xlsx"->ExportFiles.reportXlsx(s,pending.second,pending.third);else->ExportFiles.backup(s)};context.contentResolver.openOutputStream(uri)?.use {it.write(bytes)} ?: error("No se pudo abrir el archivo.")};vm.inform("Archivo guardado.")}
            catch(e: Exception){vm.inform("No se pudo guardar el archivo. Inténtalo de nuevo.")}
            finally{exporting=false;export=null}
        } else export=null
    }
    fun download(format: String,rows: List<List<String>> = emptyList(),period: String = Dates.month()) {
        export=Triple(format,rows,period)
        val mime=when(format){"pdf"->"application/pdf";"xlsx"->"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";else->"application/json"}
        saveFile.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,"CasaFamilias-${Dates.today()}.$format"))
    }
    LaunchedEffect(vm){vm.messages.collect {snackbar.showSnackbar(it)}}
    LaunchedEffect(vm,s.profile.id){while(isActive){delay(45000);if(!s.preview && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))vm.refresh()}}
    val entry by nav.currentBackStackEntryAsState();val route=entry?.destination?.route.orEmpty();val root=route.substringBefore('/')
    val tabs=Access.tabs(s.profile.role)
    val showBottom=root in tabs || root in setOf("cleaning","users","reports","admin","notices","proposals","swaps","templates","periods","audit")
    val active=if(root in tabs)root else "more"
    fun go(path: String){if(path.substringBefore('/') in tabs){nav.navigate(path){popUpTo("home"){saveState=true};launchSingleTop=true;restoreState=true}} else nav.navigate(path){launchSingleTop=true}}
    fun back(){vm.clearError();if(!nav.popBackStack())go("home")}
    CompositionLocalProvider(ReceiptViewer provides {path,qr->viewing=path to qr},PageHeadingInBar provides (root in setOf("cleaning","users","reports","admin","notices","notifications","proposals","profile","audit","periods","templates","swaps"))) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,snackbarHost={SnackbarHost(snackbar)},topBar={
            Column {
                TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),title={if(root in tabs || root.isBlank())Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){BrandMark(32.dp);Text("Casa Familias",style=MaterialTheme.typography.titleMedium)}else Text(when(root){"form"->"Formulario";"payments-family"->"Historial";else->routeTitle(root)},style=MaterialTheme.typography.titleMedium)},navigationIcon={if(root !in tabs && root.isNotBlank())IconButton(onClick={back()}){Glyph("back","Volver")}},actions={
                    if(s.profile.role!=Role.RESTAURANT)IconButton(onClick={go("notifications")}){BadgedBox(badge={if(s.rows("notifications").any {it.text("read_at").isBlank()})Badge()}){Glyph("notifications","Abrir notificaciones")}}
                    if(root!="profile" && root!="form")IconButton(onClick={go("profile")},modifier=Modifier.semantics{contentDescription="Abrir mi perfil"}){Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(36.dp)){Box(contentAlignment=Alignment.Center){Text(s.profile.name.split(' ').take(2).mapNotNull{it.firstOrNull()}.joinToString("").uppercase(),style=MaterialTheme.typography.labelLarge)}}}
                })
                if(s.preview)Surface(color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth()){Text("Vista de diseño · Datos de ejemplo",Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center)}
                if(state.busy || exporting)LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },bottomBar={if(showBottom)NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp){tabs.forEach {tab->NavigationBarItem(selected=active==tab,onClick={go(tab)},icon={Glyph(if(tab=="dues")"expenses" else if(tab=="profile")"profile" else tab)},colors=NavigationBarItemDefaults.colors(selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary,indicatorColor=MaterialTheme.colorScheme.primaryContainer),label={BottomLabel(if(s.profile.role==Role.RESTAURANT && tab=="home")"Servicios" else routeTitle(tab),LocalConfiguration.current.screenWidthDp.dp/tabs.size-6.dp)})}}}) {padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(state.error!=null)Surface(color=MaterialTheme.colorScheme.errorContainer,modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(state.error!!,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.clearError()}){Text("Cerrar")}}}
                NavHost(navController=nav,startDestination="home",modifier=Modifier.weight(1f)) {
                    val roots=listOf("home","expenses","payments","families","more","dues","cleaning","swaps","notices","notifications","proposals","users","reports","admin","profile","periods","templates","audit")
                    roots.forEach {screen->composable(screen){
                        if(!Access.allows(s.profile.role,screen)){Empty("Tu cuenta no tiene acceso a esta sección");return@composable}
                        when(screen){
                            "home"->HomeScreen(s,::go);"expenses"->ExpensesScreen(s,::go);"payments"->PaymentsScreen(s,::go);"families"->FamiliesScreen(s,::go);"dues"->DuesScreen(s,::go);"cleaning"->CleaningScreen(s,::go);"swaps"->SwapsScreen(s,vm)
                            "notices","notifications"->NoticesScreen(s,::go,screen=="notifications",vm);"proposals"->ProposalsScreen(s,::go,vm);"users"->UsersScreen(s,::go);"reports"->ReportsScreen(s,::download)
                            "admin"->AdministrationScreen(::go){download("json")};"profile"->ProfileScreen(s,vm,::go);"periods"->PeriodsScreen(s,vm);"templates"->TemplatesScreen(s,vm,::go);"audit"->AuditScreen(s)
                            "more"->ContentList{item{Title("Más","Organiza el resto, sin complicaciones")};items(Access.more(s.profile.role)){item->NavRow(item,routeTitle(item),when(item){"cleaning"->"Turnos y tareas";"users"->"Cuentas, roles y familias";"reports"->"Resúmenes y exportaciones";"admin"->"Configuración y control";"notices"->"Comunicados del hogar";else->"Ideas y votaciones"}){go(item)}}}
                        }
                    }}
                    listOf("payment","family","turn","user","expense-detail","payments-family").forEach {screen->composable("$screen/{id}"){e->val id=e.arguments?.getString("id").orEmpty()
                        val allowed=Access.allows(s.profile.role,when(screen){"expense-detail"->"expenses";"payments-family"->"families";else->screen})
                        if(!allowed){Empty("Tu cuenta no tiene acceso a esta sección");return@composable}
                        when(screen){"payment"->PaymentDetail(s,id,vm);"family"->DuesScreen(s,::go,id.toLongOrNull());"turn"->TurnDetail(s,id,::go,vm);"user"->UserDetail(s,id,::go);"expense-detail"->ExpenseDetail(s,id,::go,vm);"payments-family"->PaymentsScreen(s,::go,id)}
                    }}
                    composable("form/{kind}?id={id}",arguments=listOf(navArgument("kind"){type=NavType.StringType},navArgument("id"){type=NavType.StringType;defaultValue=""})){e->
                        val kind=e.arguments?.getString("kind").orEmpty();val id=e.arguments?.getString("id").orEmpty()
                        val allowed=Access.allows(s.profile.role,if(kind=="turn")"new-turn" else kind) && (kind !in setOf("complete","swap") || s.find("cleaning_turns",id)?.let {s.ownsCleaning(it.number("family_id"))}==true)
                        if(!allowed){Empty("Tu cuenta no tiene acceso a esta acción");return@composable}
                        LaunchedEffect(kind,id){vm.clearError();if(kind=="swap")vm.targets(id)}
                        val form=runCatching {Forms.build(kind,id,s,state.extras["swap:$id"].orEmpty())}.getOrNull()
                        if(form==null)Empty("Formulario no disponible") else FormScreen(vm,s,form,::back)
                    }
                }
            }
        }
        viewing?.let {item->ModalBottomSheet(onDismissRequest={viewing=null}){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(if(item.second)"QR de pago" else "Comprobante",style=MaterialTheme.typography.titleLarge);ImageReceipt(vm,item.first,item.second);Secondary("Cerrar"){viewing=null};Spacer(Modifier.height(20.dp))}}}
    }
}
