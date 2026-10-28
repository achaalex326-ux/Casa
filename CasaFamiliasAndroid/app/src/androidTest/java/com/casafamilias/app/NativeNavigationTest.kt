package com.casafamilias.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.*
import org.junit.Assert.*
import com.casafamilias.app.data.ExportFiles
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult

class NativeNavigationTest {
    @get:Rule val compose=createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>?=null
    private fun launch(role: String,account: Boolean=false) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("design_preview",true).putExtra("preview_role",role).putExtra("preview_account",account)
        scenario=ActivityScenario.launch(intent)
        compose.waitUntil(90000){compose.onAllNodesWithText("Vista de diseño · Datos de ejemplo").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun scroll(text: String): SemanticsNodeInteraction {
        if(compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text))
            return compose.onNodeWithText(text)
        }
        return compose.onNodeWithText(text).performScrollTo()
    }
    @After fun close(){scenario?.close();InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("casa_preferences",0).edit().clear().commit()}
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=File(instrumentation.targetContext.filesDir,"native-screens").apply {mkdirs()}
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        assertNotNull(bitmap)
        File(directory,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun administratorHasFiveTabsAndUniqueMoreMenu() {
        launch("sysadmin")
        listOf("Inicio","Gastos","Pagos","Familias","Más").forEach {compose.onNodeWithText(it).assertExists()}
        compose.onNodeWithText("Más").performClick()
        scroll("Usuarios").assertExists()
        scroll("Administración").assertExists()
        scroll("Usuarios").performClick()
        scroll("Crear usuario").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Inicio").performClick()
        compose.onNodeWithText("Gastos").performClick()
        scroll("Registrar gasto").performClick()
        compose.onNodeWithText("Servicios del mes").performClick()
        compose.onNodeWithText("Paso 1 de 3").assertExists()
    }
    @Test fun familyCannotSeeOtherFamilyAdministration() {
        launch("family")
        compose.onAllNodesWithText("Mi\ncuenta").onFirst().assertExists()
        compose.onNodeWithText("Familias").assertDoesNotExist()
        compose.onNodeWithText("Más").performClick()
        compose.onNodeWithText("Usuarios").assertDoesNotExist()
        compose.onNodeWithText("Administración").assertDoesNotExist()
        scroll("Propuestas").assertExists()
    }
    @Test fun payerHasFinanceAndNoSystemAdministration() {
        launch("payer");compose.onNodeWithText("Más").performClick()
        scroll("Reportes").assertExists()
        compose.onNodeWithText("Usuarios").assertDoesNotExist()
        compose.onNodeWithText("Administración").assertDoesNotExist()
    }
    @Test fun restaurantRemainsReadOnlyAndBalanceIsSixty() {
        launch("restaurant");compose.onNodeWithText("Servicios").assertExists();compose.onNodeWithText("Pagos").assertDoesNotExist()
        assertTrue(compose.onAllNodes(hasText("60,00",substring=true)).fetchSemanticsNodes().isNotEmpty())
    }
    @Test fun rotationAndProcessRecreationKeepTheNativeScreen() {
        launch("sysadmin");scenario!!.recreate()
        compose.waitUntil(90000){compose.onAllNodesWithText("Casa Familias").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Gastos").assertExists()
    }
    @Test fun passwordConfirmationAndPdfExportAreNative() {
        launch("family");compose.onNodeWithContentDescription("Abrir mi perfil").performClick()
        scroll("Cambiar contraseña").performClick()
        compose.onNodeWithText("Nueva contraseña").performTextInput("12345678")
        compose.onNodeWithText("Confirma la contraseña").performTextInput("87654321")
        scroll("Guardar").performClick()
        compose.onAllNodesWithText("Las contraseñas no coinciden.").onFirst().assertExists()
        val pdf=ExportFiles.pdf(listOf(listOf("Concepto","Monto"),listOf("Agua","100.00")))
        assertEquals("%PDF",pdf.take(4).toByteArray().toString(Charsets.US_ASCII))
    }
    @Test fun adminDesignScreensAreImplementedInNativeAndroid() {
        launch("sysadmin");screenshot("01-inicio")
        listOf("Gastos" to "02-gastos","Pagos" to "03-pagos","Familias" to "04-familias").forEach {(title,file)->compose.onNodeWithText(title).performClick();screenshot(file)}
        compose.onNodeWithText("Más").performClick();screenshot("09-mas")
        listOf("Limpieza" to "05-limpieza","Usuarios" to "06-usuarios","Reportes" to "07-reportes","Administración" to "08-administracion").forEach {(title,file)->
            scroll(title).performClick();screenshot(file);compose.onNodeWithContentDescription("Volver").performClick()
        }
    }
    @Test fun familyCanOpenItsTurnAndLargeTextDarkMode() {
        launch("family");screenshot("10-familia-inicio")
        scroll("Ver turno y tareas").performClick()
        scroll("Solicitar intercambio").assertExists();screenshot("11-familia-turno")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithContentDescription("Abrir mi perfil").performClick()
        scroll("Acceso con huella").assertExists()
        scroll("Modo oscuro").assertExists()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        scroll("Extra grande").performClick()
        screenshot("12-perfil-oscuro-texto-grande")
        scenario!!.recreate()
        scroll("Extra grande").assertIsSelected()
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onAllNodesWithText("Mi\ncuenta").onFirst().assertExists()
        compose.onNodeWithText("Limpieza").performSemanticsAction(SemanticsActions.GetTextLayoutResult){action->val layouts=mutableListOf<TextLayoutResult>();assertTrue(action(layouts));assertEquals(1,layouts.single().lineCount);assertFalse(layouts.single().hasVisualOverflow)}
        screenshot("13-familia-oscuro-texto-grande")
    }

    @Test fun engineerAdministratorSeesCurrentFamilyAndWholeCalendarWithoutSwapAuthority() {
        launch("sysadmin")
        scroll("¿Quién limpia esta semana?").assertExists()
        scroll("Ver tareas de esta semana").performClick()
        compose.onNodeWithText("Familia Choque").assertExists()
        compose.onNodeWithText("Solicitar intercambio").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        scroll("Calendario de todas las familias").performClick()
        scroll("Todas las familias").assertExists()
        screenshot("16-calendario-administrador")
        scroll("Solicitudes de intercambio").performClick()
        compose.onNodeWithText("Aceptar intercambio").assertDoesNotExist()
        compose.onNodeWithText("Rechazar intercambio").assertDoesNotExist()
        screenshot("17-intercambios-consulta-administrador")
    }
    @Test fun payerSeesOtherFamilyWeekButCannotRequestTheirExchange() {
        launch("payer")
        scroll("Ver tareas de esta semana").performClick()
        compose.onNodeWithText("Familia Choque").assertExists()
        compose.onNodeWithText("Completar turno").assertDoesNotExist()
        compose.onNodeWithText("Solicitar intercambio").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        scroll("Calendario de todas las familias").performClick()
        scroll("Todas las familias").assertExists()
        screenshot("18-calendario-encargado")
    }
    @Test fun recipientFamilyCanAnswerIncomingExchangeAndSenderCannotAnswerOutgoing() {
        launch("family")
        compose.onNodeWithText("Limpieza").performClick()
        scroll("Solicitudes de intercambio").performClick()
        scroll("Recibidos").performClick()
        scroll("Aceptar intercambio").performClick()
        compose.onNodeWithText("¿Confirmas esta respuesta?").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        scroll("Rechazar intercambio").assertExists()
        screenshot("19-intercambio-recibido")
        scroll("Enviados").performClick()
        compose.onNodeWithText("Aceptar intercambio").assertDoesNotExist()
        compose.onNodeWithText("Rechazar intercambio").assertDoesNotExist()
        scroll("Esperando respuesta de la familia receptora").assertExists()
        screenshot("20-intercambio-enviado")
    }

    @Test fun familyCanPreviewOneDepositAcrossThreeMonths() {
        launch("family");compose.onNodeWithText("Pagos").performClick()
        scroll("Registrar pago").performClick()
        scroll("Total depositado (Bs)").performTextInput("200")
        scroll("Así se repartirá tu depósito").assertExists()
        scroll("Deuda que quedaría").assertExists()
        compose.onAllNodesWithText("Bs 40,00").onLast().assertExists()
        screenshot("21-vista-previa-pago-varios-meses")
    }
    @Test fun familyDepositHistoryGroupsOneReceiptAndThreeMonths() {
        launch("family");compose.onNodeWithText("Pagos").performClick()
        scroll("Ver reparto del depósito").performClick()
        scroll("Reparto previsto al enviar").assertExists()
        scroll("enero 2030").assertExists();scroll("febrero 2030").assertExists();scroll("marzo 2030").assertExists()
        screenshot("22-deposito-reparto-tres-meses")
        scroll("Los saldos no cambian hasta aprobar. El reparto se recalcula al revisar, por si ya se aprobó otro pago.").assertExists()
        compose.onNodeWithText("Aprobar depósito completo").assertDoesNotExist()
    }
    @Test fun administratorReviewsWholeDepositWithOneDecision() {
        launch("sysadmin");compose.onNodeWithText("Pagos").performClick()
        scroll("Revisar depósito completo").performClick()
        scroll("Aprobar depósito completo").performClick()
        compose.onNodeWithText("¿Confirmas la aprobación de Bs 200,00? Se revisará el depósito completo, con un solo comprobante.").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        scroll("Rechazar depósito completo").assertExists()
        screenshot("23-aprobacion-deposito-completo")
    }

    @Test fun accountMakesPaidPartialAndPendingMonthsUnderstandable() {
        launch("family",account=true)
        compose.onNodeWithText("Mi\ncuenta").performClick()
        scroll("Gastos registrados hasta").assertExists()
        scroll("2 meses con saldo pendiente").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("account-month-2026-09"))
        compose.onNodeWithTag("account-month-2026-09").assertExists()
        compose.onNodeWithText("Pago parcial").assertExists()
        screenshot("24-cuenta-mes-pago-parcial")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("account-month-2026-10"))
        screenshot("25-cuenta-mes-pendiente")
        scroll("Al día").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("account-month-2026-08"))
        compose.onNodeWithTag("account-month-2026-08").assertExists()
        compose.onAllNodesWithText("Al día").onLast().assertExists()
        screenshot("26-cuenta-mes-cubierto")
    }
    @Test fun paymentListIdentifiesMonthsAndLinksToAccount() {
        launch("family");compose.onNodeWithText("Pagos").performClick()
        scroll("¿Qué meses me faltan pagar?").assertExists()
        scroll("2 comprobantes por revisar").assertExists()
        scroll("Registrar pago").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Previsto para: enero 2030, febrero 2030, marzo 2030"))
        compose.onNodeWithText("Previsto para: enero 2030, febrero 2030, marzo 2030").assertExists()
        screenshot("27-pagos-meses-y-revision")
    }

}
