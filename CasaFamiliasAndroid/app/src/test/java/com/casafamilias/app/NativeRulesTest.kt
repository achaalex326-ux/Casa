package com.casafamilias.app

import com.casafamilias.app.domain.*
import com.casafamilias.app.data.ExportFiles
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.math.BigDecimal
import java.util.zip.ZipInputStream

class NativeRulesTest {
    private fun row(vararg p: Pair<String,Any?>)=Record(json(*p))
    private fun data(role: Role=Role.ADMIN)=Snapshot(Profile("u","Test","test@example.test",role,1,2),mapOf(
        "families" to (1..6).map{row("id" to it,"name" to "Familia $it")},
        "cleaning_family_aliases" to listOf(row("family_id" to 2,"household_id" to 1)),
        "expense_shares" to listOf(row("id" to "s","family_id" to 1,"amount_due" to "100.00")),
        "payments" to listOf(row("id" to "p1","share_id" to "s","family_id" to 1,"amount" to "40.00","applied_amount" to "30.00","status" to "approved"),row("id" to "p2","share_id" to "s","family_id" to 1,"amount" to "50.00","applied_amount" to "50.00","status" to "pending")),
        "family_debts" to listOf(row("share_id" to "s","balance" to "70.00"))))
    @Test fun decimalMoneyDoesNotAccumulateFloatingError(){assertEquals(BigDecimal("0.30"),Amounts.parse("0,10")!!+Amounts.parse("0,20")!!)}
    @Test fun localeMoneyInputsAreParsed(){assertEquals(BigDecimal("1234.56"),Amounts.parse("1.234,56"));assertEquals(BigDecimal("1234.56"),Amounts.parse("1,234.56"))}
    @Test fun invalidAndNonPositiveAmountsAreRejected(){listOf("","NaN","Infinity","-1","0","abc").forEach{assertFalse(it,Amounts.valid(it))};assertTrue(Amounts.valid("0.01"))}
    @Test fun onlyAppliedApprovedMoneyReducesBalance(){assertEquals(0,BigDecimal("70.00").compareTo(data().balance(1)))}
    @Test fun familyDebtUsesServerPortal(){assertEquals(0,BigDecimal("70.00").compareTo(data(Role.FAMILY).balance(1)))}
    @Test fun cleaningAliasesNeverMergeAccountingFamilies(){val s=data(Role.FAMILY);assertEquals(1L,s.household(2));assertEquals(5,s.cleaningFamilies().size);assertEquals(6,s.rows("families").size);assertTrue(s.ownsCleaning(2));assertFalse(s.ownsCleaning(3))}
    @Test fun roleRoutesFailClosedForPrivateSections(){listOf("admin","users","family","families","reports","expenses","qr","assignment","family-name","services").forEach{assertFalse(it,Access.allows(Role.FAMILY,it))};assertTrue(Access.allows(Role.FAMILY,"proposals"))}
    @Test fun householdCanReadTurnButCannotCreateAssignments(){assertTrue(Access.allows(Role.FAMILY,"turn"));assertTrue(Access.allows(Role.PAYER,"turn"));assertFalse(Access.allows(Role.FAMILY,"new-turn"));assertTrue(Access.allows(Role.ADMIN,"new-turn"))}
    @Test fun restaurantCannotOpenFinancialAdministration(){assertTrue(Access.allows(Role.RESTAURANT,"home"));assertFalse(Access.allows(Role.RESTAURANT,"payments"));assertFalse(Access.allows(Role.RESTAURANT,"form"))}
    @Test fun navigationHasAtMostFiveUniqueTabs(){Role.entries.forEach{val tabs=Access.tabs(it);assertTrue(tabs.size<=5);assertEquals(tabs.size,tabs.distinct().size)};assertEquals(listOf("home","expenses","payments","families","more"),Access.tabs(Role.ADMIN))}
    @Test fun moreDoesNotRepeatMainDestinations(){Role.entries.forEach{r->assertTrue(Access.more(r).none{it in Access.tabs(r)})};assertFalse("profile" in Access.more(Role.ADMIN))}
    @Test fun unknownRoleIsRejected(){assertThrows(IllegalStateException::class.java){Role.from("superadmin")}}
    @Test fun partialPaymentRequiresReceiptOnlyForQR(){val form=Forms.build("payment","",data(Role.FAMILY));val values=mapOf("family" to "1","mode" to "all","share" to "s","amount" to "1.00","date" to "2026-10-15","method" to "qr");assertTrue(form.error(values)!!.contains("comprobante"));assertNull(form.error(values+("method" to "cash")))}
    @Test fun shortAndMismatchedPasswordsFail(){val form=Forms.build("password","",data());assertNotNull(form.error(mapOf("password" to "1234567","confirmation" to "1234567")));assertNotNull(form.error(mapOf("password" to "12345678","confirmation" to "87654321")));assertNull(form.error(mapOf("password" to "12345678","confirmation" to "12345678")))}
    @Test fun familyNameBoundaries(){val f=Forms.build("family-name","1",data());assertNotNull(f.error(mapOf("name" to "")));assertNull(f.error(mapOf("name" to "A")));assertNull(f.error(mapOf("name" to "A".repeat(80))));assertNotNull(f.error(mapOf("name" to "A".repeat(81))))}
    @Test fun payerMustHaveDifferentFamilies(){val f=Forms.build("payer-families","",data());assertNotNull(f.error(mapOf("first" to "1","second" to "1")));assertNull(f.error(mapOf("first" to "1","second" to "2")))}
    @Test fun rotationRejectsDuplicateHouseholdsAndNonMonday(){val f=Forms.build("rotation","",data());val values=f.fields.associate{it.key to it.initial}.toMutableMap();values["anchor"]="2026-10-12";assertNull(f.error(values));values["order_1"]=values["order_0"]!!;assertNotNull(f.error(values));values["order_1"]="3";values["anchor"]="2026-10-13";assertNotNull(f.error(values))}
    @Test fun cleaningTaskBoundaries(){val f=Forms.build("turn","",data());val base=mapOf("family" to "1","date" to "2026-10-15");assertNull(f.error(base+("tasks" to "A".repeat(160))));assertNotNull(f.error(base+("tasks" to "A".repeat(161))));assertNotNull(f.error(base+("tasks" to (1..41).joinToString("\n"){"Tarea $it"} )))}
    @Test fun longFormsUseMultipleSteps(){assertEquals(3,Forms.build("services","",data()).pages);assertEquals(2,Forms.build("user","",data()).pages)}
    @Test fun xlsxUsesLiteralStringsAndValidXml(){val bytes=ExportFiles.xlsx(listOf(listOf("Concepto","Monto"),listOf("=HYPERLINK(\"mal\") & <x>","12.34")));val files=mutableMapOf<String,String>();ZipInputStream(bytes.inputStream()).use{z->while(true){val e=z.nextEntry ?: break;files[e.name]=z.readBytes().toString(Charsets.UTF_8)}};assertEquals(5,files.size);assertTrue(files.getValue("xl/worksheets/sheet1.xml").contains("t=\"inlineStr\""));assertFalse(files.values.any{it.contains("<f>")});files.values.forEach{javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it.byteInputStream())}}
    @Test fun workbookPreservesAllReportSheets() {
        val bytes=ExportFiles.reportXlsx(data(),listOf(listOf("Concepto","Total")),"2026-10")
        val files=mutableMapOf<String,String>();ZipInputStream(bytes.inputStream()).use {z->while(true){val e=z.nextEntry ?: break;files[e.name]=z.readBytes().toString(Charsets.UTF_8)}}
        assertEquals(7,files.size);listOf("Gastos","Pagos","Familias").forEach {assertTrue(files.getValue("xl/workbook.xml").contains("name=\"$it\""))}
        files.values.forEach {javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it.byteInputStream())}
        assertTrue(files.getValue("xl/worksheets/sheet3.xml").contains("Saldo a favor"))
    }

    @Test fun payerBillCoverageKeepsBothFamiliesAtZeroWithoutErasingOtherDebt() {
        val shares=(1..3).map {row("id" to "s$it","family_id" to it,"amount_due" to "100.00")}
        val coverage=(1..2).map {row("share_id" to "s$it","family_id" to it,"applied_amount" to "100.00","kind" to "auto","status" to "approved")}
        val snapshot=data(Role.PAYER).copy(tables=mapOf("expense_shares" to shares,"payments" to coverage))
        assertEquals(0,BigDecimal.ZERO.compareTo(snapshot.balance(1)))
        assertEquals(0,BigDecimal.ZERO.compareTo(snapshot.balance(2)))
        assertEquals(0,BigDecimal("100.00").compareTo(snapshot.balance(3)))
        val withExtra=snapshot.copy(tables=snapshot.tables+("expense_shares" to (shares+row("id" to "extra","family_id" to 1,"amount_due" to "10.00"))))
        assertEquals(0,BigDecimal("10.00").compareTo(withExtra.balance(1)))
    }
    @Test fun automaticCoverageIsNotShownAsAnotherQrOrCashPayment() {
        assertEquals("Cuota cubierta automáticamente",row("kind" to "auto").paymentMethodLabel())
        assertEquals("Aplicación de saldo a favor",row("kind" to "credit").paymentMethodLabel())
    }
    @Test fun legacyMethodsUseStoredNotesAndNeverGuessQr() {
        assertEquals("Efectivo",row("kind" to "manual","note" to "Método: Efectivo · Entregado").paymentMethodLabel())
        assertEquals("QR",row("kind" to "manual","note" to "Método: QR · Comprobante").paymentMethodLabel())
        assertEquals("No especificada",row("kind" to "manual").paymentMethodLabel())
    }

    @Test fun administratorVisibilityDoesNotMeanHouseholdParticipation() {
        val admin=data().copy(profile=data().profile.copy(familyId=null,secondFamilyId=null))
        assertTrue(admin.ownsCleaning(1));assertFalse(admin.representsCleaning(1));assertFalse(admin.representsCleaning(null))
        assertFalse(admin.canAnswerSwap(row("status" to "pending","target_family_id" to 1,"created_by" to "other")))
    }
    @Test fun swapReplyRequiresRecipientHouseholdAndDifferentUser() {
        val family=data(Role.FAMILY)
        val request=row("status" to "pending","target_family_id" to 2,"created_by" to "other")
        assertTrue(family.canAnswerSwap(request))
        assertFalse(family.canAnswerSwap(row("status" to "pending","target_family_id" to 3,"created_by" to "other")))
        assertFalse(family.canAnswerSwap(row("status" to "pending","target_family_id" to 2,"created_by" to "u")))
        assertFalse(family.canAnswerSwap(row("status" to "accepted","target_family_id" to 2,"created_by" to "other")))
        assertFalse(data(Role.RESTAURANT).canAnswerSwap(request))
    }
    @Test fun payerCanExchangeOnlyRepresentedHouseholds() {
        val payer=data(Role.PAYER).copy(profile=data(Role.PAYER).profile.copy(secondFamilyId=3))
        assertTrue(payer.representsCleaning(2));assertTrue(payer.representsCleaning(3));assertFalse(payer.representsCleaning(4))
    }
    @Test fun weekAtMonthBoundaryAppearsInBothMonthsAndOnSunday() {
        val s=data().copy(tables=mapOf("cleaning_turns" to listOf(
            row("id" to "a","turn_date" to "2026-09-28"),row("id" to "b","turn_date" to "2026-10-05"))))
        assertEquals(listOf("a"),s.cleaningMonth("2026-09").map{it.id})
        assertEquals(listOf("a","b"),s.cleaningMonth("2026-10").map{it.id})
        assertEquals(listOf("a"),s.cleaningWeek("2026-10-04").map{it.id})
        assertEquals(listOf("b"),s.cleaningWeek("2026-10-05").map{it.id})
    }
    @Test fun weekLabelsIncludeBothDatesAndYearBoundary() {
        val label=Dates.weekLabel("2027-01-01")
        assertTrue(label.contains("2026"));assertTrue(label.contains("2027"));assertTrue(label.contains(" al "))
    }

}
