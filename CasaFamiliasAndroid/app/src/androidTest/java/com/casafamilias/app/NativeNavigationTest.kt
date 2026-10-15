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
    private fun launch(role: String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val intent=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("design_preview",true).putExtra("preview_role",role)
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
        compose.onAllNodesWithText("Mis cuotas").onFirst().assertExists()
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
        compose.onNode(isToggleable()).performScrollTo().performClick()
        scroll("Extra grande").performClick()
        screenshot("12-perfil-oscuro-texto-grande")
        scenario!!.recreate()
        scroll("Extra grande").assertIsSelected()
        compose.onNode(isToggleable()).assertIsOn()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onAllNodesWithText("Mis cuotas").onFirst().assertExists()
        compose.onNodeWithText("Limpieza").performSemanticsAction(SemanticsActions.GetTextLayoutResult){action->val layouts=mutableListOf<TextLayoutResult>();assertTrue(action(layouts));assertEquals(1,layouts.single().lineCount);assertFalse(layouts.single().hasVisualOverflow)}
        screenshot("13-familia-oscuro-texto-grande")
    }

}
