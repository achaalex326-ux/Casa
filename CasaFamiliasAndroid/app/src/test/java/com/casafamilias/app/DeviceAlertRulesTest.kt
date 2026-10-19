package com.casafamilias.app

import com.casafamilias.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DeviceAlertRulesTest {
    private val today=LocalDate.parse("2026-10-19")
    private val enabled=Instant.parse("2026-10-19T08:00:00Z")
    private fun row(vararg p: Pair<String,Any?>)=Record(json(*p))
    private fun input(role: Role=Role.FAMILY,turns: List<Record> = emptyList(),debts: List<Record> = emptyList(),notices: List<Record> = emptyList())=Snapshot(Profile("u","User","test@example.test",role,2,if(role==Role.PAYER)1 else null),mapOf("families" to listOf(row("id" to 1,"name" to "Familia Uno"),row("id" to 3,"name" to "Familia Otra")),"cleaning_family_aliases" to listOf(row("family_id" to 2,"household_id" to 1)),"cleaning_turns" to turns,"alert_debts" to debts,"notifications" to notices))
    private fun debt(balance: String="40.50",due: String="2026-10-21")=row("share_id" to "s","concept" to "Agua","balance" to balance,"due_date" to due)
    private fun turn(id: String="t",family: Int=1,date: String="2026-10-19",status: String="pending")=row("id" to id,"family_id" to family,"turn_date" to date,"status" to status)
    @Test fun onlyOwnCurrentCleaningWeekAndAliasAreIncluded() {
        val s=input(turns=listOf(turn(),turn("other",3),turn("future",1,"2026-10-26"),turn("past",1,"2026-10-12")))
        val plan=DeviceAlertRules.plan(s,emptySet(),enabled,today)
        assertEquals(1,plan.alerts.size);assertTrue(plan.alerts.single().body.contains("Familia Uno"))
    }
    @Test fun sharedPayerFamiliesDoNotDuplicateTheSameTurn() {
        val plan=DeviceAlertRules.plan(input(Role.PAYER,turns=listOf(turn())),emptySet(),enabled,today)
        assertEquals(1,plan.alerts.size)
    }
    @Test fun completedCleaningHasNoReminderAndNoActiveTag() {
        val plan=DeviceAlertRules.plan(input(turns=listOf(turn(status="done"))),emptySet(),enabled,today)
        assertTrue(plan.alerts.isEmpty());assertTrue(plan.activeTags.isEmpty())
    }
    @Test fun newDebtContainsItsActualBalanceAndDoesNotRepeatEveryPoll() {
        val s=input(debts=listOf(debt()))
        val first=DeviceAlertRules.plan(s,emptySet(),enabled,today).alerts.single()
        assertEquals("Se registró una deuda",first.title);assertTrue(first.body.contains("40,50"))
        assertTrue(DeviceAlertRules.plan(s,first.remember,enabled,today).alerts.isEmpty())
    }
    @Test fun nextDayDueReminderUsesUpdatedBalance() {
        val first=DeviceAlertRules.plan(input(debts=listOf(debt())),emptySet(),enabled,today).alerts.single()
        val next=DeviceAlertRules.plan(input(debts=listOf(debt("10.25"))),first.remember,enabled,today.plusDays(1)).alerts.single()
        assertEquals("Tu pago está por vencer",next.title);assertTrue(next.body.contains("10,25"))
    }
    @Test fun paidOrWaivedDebtNeverProducesAnAlertEvenWithAnOldServerReminder() {
        val n=row("id" to "old","kind" to "due","title" to "Pago vencido","body" to "Debe 999","created_at" to "2026-10-19T10:00:00Z")
        val plan=DeviceAlertRules.plan(input(debts=listOf(debt("0")),notices=listOf(n)),emptySet(),enabled,today)
        assertTrue(plan.alerts.isEmpty());assertTrue(plan.activeTags.isEmpty())
    }
    @Test fun historicalAndAlreadyReadPublicationsAreNotReplayed() {
        val ns=listOf(row("id" to "old","kind" to "expense","title" to "Anterior","created_at" to "2026-10-18T10:00:00Z"),row("id" to "read","kind" to "expense","title" to "Leído","created_at" to "2026-10-19T10:00:00Z","read_at" to "2026-10-19T10:01:00Z"))
        assertTrue(DeviceAlertRules.plan(input(notices=ns),emptySet(),enabled,today).alerts.isEmpty())
    }
    @Test fun newServicePublicationIsDeliveredOnceEvenWhenThereIsNoDebt() {
        val s=input(notices=listOf(row("id" to "new","kind" to "expense","title" to "Servicios registrados","body" to "Octubre","created_at" to "2026-10-19T10:00:00Z")))
        val alert=DeviceAlertRules.plan(s,emptySet(),enabled,today).alerts.single()
        assertEquals("Servicios registrados",alert.title)
        assertTrue(DeviceAlertRules.plan(s,alert.remember,enabled,today).alerts.isEmpty())
    }
    @Test fun reminderForFutureWeeksIsNotSentAsThisWeeksCleaning() {
        val n=row("id" to "future-notice","kind" to "cleaning","title" to "Turno semanal","created_at" to "2026-10-19T10:00:00Z")
        assertTrue(DeviceAlertRules.plan(input(turns=listOf(turn(date="2026-12-07")),notices=listOf(n)),emptySet(),enabled,today).alerts.isEmpty())
    }
    @Test fun overdueRemindersAreDailyAndBatchesAreBounded() {
        val debts=(1..20).map {row("share_id" to "s$it","balance" to 10,"due_date" to "2026-10-18")}
        val plan=DeviceAlertRules.plan(input(debts=debts),emptySet(),enabled,today)
        assertEquals(8,plan.alerts.size)
        val next=DeviceAlertRules.plan(input(debts=listOf(debt(due="2026-10-18"))),setOf("new:debt:s"),enabled,today)
        assertEquals("Tienes un pago vencido",next.alerts.single().title)
    }
}
