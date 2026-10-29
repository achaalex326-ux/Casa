package com.casafamilias.app.data

import android.net.Uri
import com.casafamilias.app.domain.*
import org.json.JSONObject

/** Read-only design fixtures, enabled only by an explicit intent on a debuggable APK. */
class PreviewRepository(
    private val role: Role,
    private val accountDemo: Boolean = false,
    private val servicesDemo: Boolean = false,
    private val paidDemo: Boolean = false,
) : CasaDataSource {
    override val session =
        Session("design-only", "design-only", Long.MAX_VALUE, "preview", "vista@example.test")

    override suspend fun restore(legacy: String?) {}

    override suspend fun login(email: String, password: String) {}

    override suspend fun load(): Snapshot {
        fun row(vararg p: Pair<String, Any?>) = Record(json(*p))
        val month = Dates.month()
        val monday = Dates.monday(Dates.today())
        val profile =
            Profile(
                "preview",
                if (role == Role.FAMILY) "Familia Salinas"
                else if (role == Role.PAYER) "Encargado de pagos"
                else if (role == Role.RESTAURANT) "Restaurante" else "Alex",
                "vista@example.test",
                role,
                if (role in listOf(Role.FAMILY, Role.PAYER)) 1L else null,
                if (role == Role.PAYER) 2L else null,
            )
        val families =
            listOf("Salinas", "Salinas2", "Acha", "Choque", "Galarza", "David").mapIndexed { i, n ->
                row("id" to i + 1, "name" to "Familia $n")
            }
        val expenses =
            listOf(
                    "Agua" to 450,
                    "Luz" to 820,
                    "Gas" to 380,
                    "Internet" to 400,
                    "Arreglo de cocina" to 400,
                )
                .mapIndexed { i, (n, a) ->
                    row(
                        "id" to "e$i",
                        "concept" to n,
                        "total_amount" to a,
                        "expense_date" to "$month-05",
                        "due_date" to "$month-20",
                        "category" to if (i == 4) "external" else "basic",
                        "justification" to "Ejemplo de diseño",
                    )
                }
        val payments =
            listOf(4, 1, 3).mapIndexed { i, f ->
                row(
                    "id" to "p$i",
                    "family_id" to f,
                    "share_id" to "s$i",
                    "amount" to listOf(85, 120, 160)[i],
                    "applied_amount" to 0,
                    "credit_amount" to 0,
                    "status" to "pending",
                    "paid_at" to "$month-10",
                    "created_at" to "$month-10T10:00:00Z",
                    "note" to "Pago de servicios",
                    "method" to "qr",
                )
            }
        val shares =
            listOf(4, 1, 3).mapIndexed { i, f ->
                row(
                    "id" to "s$i",
                    "family_id" to f,
                    "expense_id" to "e$i",
                    "amount_due" to listOf(85, 120, 160)[i],
                )
            }
        val cleaning =
            listOf(4, 5, 1, 6, 3).mapIndexed { i, f ->
                row(
                    "id" to "t$i",
                    "family_id" to f,
                    "turn_date" to
                        java.time.LocalDate.parse(monday).plusWeeks(i.toLong()).toString(),
                    "status" to "pending",
                    "checklist" to org.json.JSONArray(listOf("Baño", "Cocina", "Áreas comunes")),
                )
            }
        val demoDebts =
            (1..3).map { m ->
                row(
                    "share_id" to "demo-share-$m",
                    "expense_id" to "demo-expense-$m",
                    "concept" to "Servicios del mes",
                    "balance" to 80,
                    "amount_due" to 80,
                    "amount_paid" to 0,
                    "month_start" to "2030-0$m-01",
                    "expense_date" to "2030-0$m-01",
                    "due_date" to "2030-0$m-20",
                )
            }
        val allocation =
            org.json.JSONArray(
                demoDebts.mapIndexed { i, d ->
                    json(
                        "share_id" to d.text("share_id"),
                        "concept" to d.text("concept"),
                        "month_start" to d.text("month_start"),
                        "expense_date" to d.text("expense_date"),
                        "amount" to if (i == 2) 40 else 80,
                    )
                }
            )
        val demoBatch =
            row(
                "id" to "demo-deposit",
                "family_id" to 1,
                "amount" to 200,
                "status" to "pending",
                "method" to "qr",
                "paid_at" to Dates.today(),
                "created_at" to Dates.today(),
                "planned_allocation" to allocation,
                "planned_credit" to 0,
                "created_by" to "preview",
            )
        val demoAnchor =
            row(
                "id" to "demo-deposit",
                "batch_id" to "demo-deposit",
                "share_id" to "demo-share-1",
                "family_id" to 1,
                "amount" to 200,
                "status" to "pending",
                "paid_at" to Dates.today(),
                "created_at" to Dates.today(),
            )
        val swaps =
            listOf(
                row(
                    "id" to "swap-incoming",
                    "requester_family_id" to 4,
                    "target_family_id" to 1,
                    "requester_turn_id" to "t0",
                    "target_turn_id" to "t2",
                    "status" to "pending",
                    "created_by" to "another-user",
                    "created_at" to Dates.today(),
                ),
                row(
                    "id" to "swap-outgoing",
                    "requester_family_id" to 1,
                    "target_family_id" to 5,
                    "requester_turn_id" to "t2",
                    "target_turn_id" to "t1",
                    "status" to "pending",
                    "created_by" to "preview",
                    "created_at" to Dates.today(),
                ),
            )
        val tables =
            mapOf(
                "payment_batches" to listOf(demoBatch),
                "cleaning_swap_requests" to swaps,
                "families" to families,
                "profiles" to
                    listOf(
                        row(
                            "id" to "preview",
                            "display_name" to profile.name,
                            "role" to role.code,
                            "contact_email" to profile.email,
                            "family_id" to profile.familyId,
                        ),
                        row(
                            "id" to "u1",
                            "display_name" to "Cuenta de prueba",
                            "role" to "family",
                            "family_id" to 4,
                            "contact_email" to "familia@example.test",
                        ),
                    ),
                "expenses" to expenses,
                "payments" to
                    ((if (role == Role.FAMILY) payments.filter { it.number("family_id") == 1L }
                    else payments) + demoAnchor),
                "expense_shares" to shares,
                "family_debts" to demoDebts,
                "cleaning_turns" to
                    if (role == Role.FAMILY) cleaning.filter { it.number("family_id") == 1L }
                    else cleaning,
                "cleaning_family_aliases" to listOf(row("family_id" to 2, "household_id" to 1)),
                "cleaning_rotation_settings" to
                    listOf(
                        row(
                            "enabled" to true,
                            "family_order" to org.json.JSONArray(listOf(4, 5, 1, 6, 3)),
                            "anchor_monday" to monday,
                            "default_checklist" to
                                org.json.JSONArray(listOf("Baño", "Cocina", "Áreas comunes")),
                        )
                    ),
                "notices" to
                    listOf(
                        row(
                            "id" to "n1",
                            "title" to "Organización de la limpieza",
                            "body" to "Revisa el turno de tu familia en el calendario.",
                            "created_at" to Dates.today(),
                        )
                    ),
                "external_utility_debts" to
                    listOf(
                        row(
                            "id" to "d1",
                            "service" to "electricity",
                            "amount_due" to 100,
                            "amount_paid" to 40,
                            "month_start" to "$month-01",
                        )
                    ),
            )
        if (servicesDemo) {
            val sheet =
                listOf(
                    listOf("36", "211.50"),
                    listOf("24", "165.60"),
                    listOf("32", "145.20"),
                    listOf("36", "243.60"),
                    listOf("36.50", "198"),
                    listOf("36.50", "191.60"),
                )
            val bills =
                sheet.mapIndexed { i, values ->
                    val amounts =
                        mapOf(
                            "water" to values[0].toBigDecimal(),
                            "electricity" to values[1].toBigDecimal(),
                            "gas" to java.math.BigDecimal.ZERO,
                            "wifi" to "149".toBigDecimal(),
                        )
                    val total = ServiceCalculation.calculate(amounts)
                    row(
                        "id" to "sheet-$i",
                        "month_start" to "2026-0${i+1}-01",
                        "due_date" to java.time.YearMonth.of(2026, i + 1).atEndOfMonth().toString(),
                        "water_amount" to values[0],
                        "electricity_amount" to values[1],
                        "gas_amount" to 0,
                        "wifi_amount" to 149,
                        "family_total_amount" to total.toShare,
                        "calculation_method" to "bill_total",
                        "family_count" to 6,
                        "expense_id" to "sheet-expense-$i",
                    )
                }
            val sheetShares =
                bills.flatMapIndexed { i, bill ->
                    val total =
                        ServiceCalculation.calculate(
                            mapOf(
                                "water" to bill.money("water_amount"),
                                "electricity" to bill.money("electricity_amount"),
                                "wifi" to bill.money("wifi_amount"),
                            )
                        )
                    total.shares.mapIndexed { j, amount ->
                        row(
                            "id" to "sheet-share-$i-$j",
                            "expense_id" to "sheet-expense-$i",
                            "family_id" to j + 1,
                            "amount_due" to amount,
                        )
                    }
                }
            val sheetExpenses =
                bills.mapIndexed { i, bill ->
                    row(
                        "id" to "sheet-expense-$i",
                        "concept" to "Servicios básicos 2026-0${i+1}",
                        "category" to "basic",
                        "expense_date" to bill.text("month_start"),
                        "total_amount" to bill.money("family_total_amount"),
                    )
                }
            return Snapshot(
                profile,
                tables +
                    mapOf(
                        "monthly_basic_services" to bills,
                        "expense_shares" to sheetShares,
                        "expenses" to sheetExpenses,
                        "payments" to emptyList(),
                        "payment_batches" to emptyList(),
                    ),
                preview = true,
            )
        }
        if (accountDemo) {
            val accountRows =
                listOf(
                    row(
                        "share_id" to "demo-share-1",
                        "expense_id" to "demo-expense-1",
                        "concept" to "Servicios de la casa",
                        "month_start" to "2026-08-01",
                        "expense_date" to "2026-08-01",
                        "amount_due" to 80,
                        "amount_paid" to 80,
                        "balance" to 0,
                    ),
                    row(
                        "share_id" to "demo-share-2",
                        "expense_id" to "demo-expense-2",
                        "concept" to "Servicios de la casa",
                        "month_start" to "2026-09-01",
                        "expense_date" to "2026-09-01",
                        "amount_due" to 80,
                        "amount_paid" to 40,
                        "balance" to 40,
                    ),
                    row(
                        "share_id" to "demo-share-3",
                        "expense_id" to "demo-expense-3",
                        "concept" to "Servicios de la casa",
                        "month_start" to "2026-10-01",
                        "expense_date" to "2026-10-01",
                        "amount_due" to 80,
                        "amount_paid" to 0,
                        "balance" to 80,
                    ),
                )
            return Snapshot(
                profile,
                tables +
                    ("family_debts" to
                        if (paidDemo)
                            accountRows.map { row ->
                                Record(
                                    org.json
                                        .JSONObject(row.json.toString())
                                        .put("amount_paid", row.money("amount_due"))
                                        .put("balance", 0)
                                )
                            }
                        else accountRows),
                preview = true,
            )
        }
        return Snapshot(profile, tables, preview = true)
    }

    private fun blocked(): Nothing = error("Vista de diseño: no se guardan datos.")

    override suspend fun rpc(name: String, body: JSONObject): Any? = blocked()

    override suspend fun change(table: String, method: String, body: JSONObject?, id: String?) =
        blocked()

    override suspend fun notifyPayment(paymentId: String) = blocked()

    override suspend fun createUser(body: JSONObject) = blocked()

    override suspend fun signedImage(path: String, qr: Boolean): String = blocked()

    override suspend fun upload(uri: Uri, qr: Boolean): String = blocked()

    override suspend fun logout() = blocked()

    override suspend fun recover(email: String) = blocked()

    override suspend fun password(value: String) = blocked()

    override suspend fun importRecovery(fragment: String) = blocked()
}
