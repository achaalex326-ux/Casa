package com.casafamilias.app.domain

import java.time.LocalDate

enum class InputType {
    TEXT,
    EMAIL,
    PASSWORD,
    MONEY,
    DATE,
    MONTH,
    CHOICE,
    MULTILINE,
    IMAGE,
    CHECK,
}

data class Choice(val value: String, val label: String)

data class Field(
    val key: String,
    val label: String,
    val type: InputType = InputType.TEXT,
    val initial: String = "",
    val required: Boolean = false,
    val choices: List<Choice> = emptyList(),
    val max: Int = 0,
    val page: Int = 0,
    val allowZero: Boolean = false,
    val showIf: Pair<String, String>? = null,
)

data class Form(
    val kind: String,
    val title: String,
    val fields: List<Field>,
    val recordId: String = "",
    val help: String = "",
) {
    val pages
        get() = (fields.maxOfOrNull { it.page } ?: 0) + 1

    fun visible(values: Map<String, String>) =
        fields.filter { it.showIf == null || values[it.showIf.first] == it.showIf.second }

    fun error(values: Map<String, String>, page: Int? = null): String? {
        for (f in visible(values).filter { page == null || it.page == page }) {
            val v = values[f.key].orEmpty().trim()
            if (f.required && v.isEmpty()) return "Completa ${f.label.lowercase()}."
            if (v.isBlank()) continue
            if (f.max > 0 && v.length > f.max) return "${f.label}: máximo ${f.max} caracteres."
            if (f.type == InputType.MONEY && !Amounts.valid(v, f.allowZero))
                return "Escribe un monto ${if(f.allowZero) "igual o mayor a cero" else "mayor a cero"} en ${f.label.lowercase()}."
            if (f.type == InputType.MONTH && runCatching { java.time.YearMonth.parse(v) }.isFailure)
                return "Selecciona un mes válido."
            if (f.type == InputType.DATE && runCatching { LocalDate.parse(v) }.isFailure)
                return "Revisa la fecha de ${f.label.lowercase()}."
            if (f.type == InputType.EMAIL && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(v))
                return "Escribe un correo válido."
            if (f.type == InputType.CHOICE && f.choices.none { it.value == v })
                return "Selecciona una opción válida en ${f.label.lowercase()}."
        }
        if (page != null) return null
        if (kind == "payment" && values["method"] == "qr" && values["receipt"].isNullOrBlank())
            return "Para un pago por QR, adjunta la foto del comprobante."
        if (
            kind in setOf("user", "assignment") &&
                values["role"] == "payer" &&
                values[if (kind == "user") "payer_family" else "family"] == values["second"]
        )
            return "El encargado necesita dos familias diferentes."
        if (kind == "payer-families" && values["first"] == values["second"])
            return "Las dos familias deben ser diferentes."
        if (kind in setOf("user", "password") && values["password"].orEmpty().length < 8)
            return "La contraseña debe tener al menos 8 caracteres."
        if (kind == "password" && values["password"] != values["confirmation"])
            return "Las contraseñas no coinciden."
        if (kind in setOf("turn", "rotation")) {
            val tasks =
                values["tasks"].orEmpty().lines().map { it.trim() }.filter { it.isNotBlank() }
            if (tasks.size > 40 || tasks.any { it.length > 160 })
                return "Usa hasta 40 tareas, de 1 a 160 caracteres por línea."
        }
        if (kind == "rotation") {
            val order = fields.filter { it.key.startsWith("order_") }.map { values[it.key] }
            if (order.size != order.toSet().size) return "Cada familia debe aparecer una sola vez."
            if (runCatching { LocalDate.parse(values["anchor"]).dayOfWeek.value }.getOrNull() != 1)
                return "La fecha inicial debe ser un lunes."
        }
        if (
            kind in setOf("expense", "proposal") &&
                values["justification"].orEmpty().trim().length < 3
        )
            return "Explica el motivo con al menos 3 caracteres."
        return null
    }
}

object Forms {
    fun build(kind: String, id: String, s: Snapshot, targets: List<Record> = emptyList()): Form {
        val families = s.rows("families").map { Choice(it.id, it.text("name")) }
        fun f(
            key: String,
            label: String,
            type: InputType = InputType.TEXT,
            required: Boolean = false,
            initial: String = "",
            page: Int = 0,
            max: Int = 0,
            choices: List<Choice> = emptyList(),
            zero: Boolean = false,
            show: Pair<String, String>? = null,
        ) = Field(key, label, type, initial, required, choices, max, page, zero, show)
        val date = f("date", "Fecha", InputType.DATE, true, Dates.today())
        val amount = f("amount", "Monto (Bs)", InputType.MONEY, true)
        val receipt = f("receipt", "Foto del comprobante", InputType.IMAGE)
        val services =
            listOf(
                Choice("water", "Agua"),
                Choice("electricity", "Luz"),
                Choice("gas", "Gas"),
                Choice("wifi", "Internet"),
            )
        val fields: List<Field>
        val title: String
        var help = ""
        when (kind) {
            "payment" -> {
                title = "Registrar pago"
                val selected =
                    s.rows("expense_shares").firstOrNull { it.id == id }?.number("family_id")
                        ?: s.profile.familyId
                val debts =
                    if (s.profile.role == Role.FAMILY) s.paymentDebts(s.profile.familyId)
                    else s.rows("families").flatMap { s.paymentDebts(it.number("id")) }
                val opts =
                    debts.map { d ->
                        Choice(d.shareId, d.label + " · " + Amounts.format(d.balance))
                    }
                fields =
                    listOf(
                        f(
                            "family",
                            "Familia del depósito",
                            InputType.CHOICE,
                            true,
                            selected?.toString().orEmpty(),
                            choices = families,
                        ),
                        f(
                            "mode",
                            "Aplicar el pago a",
                            InputType.CHOICE,
                            true,
                            if (id.isBlank()) "all" else "single",
                            choices =
                                listOf(
                                    Choice("all", "Mis deudas más antiguas"),
                                    Choice("single", "Una deuda específica"),
                                ),
                        ),
                        f(
                            "share",
                            "Deuda específica",
                            InputType.CHOICE,
                            true,
                            id,
                            choices = opts,
                            show = "mode" to "single",
                        ),
                        amount.copy(label = "Total depositado (Bs)"),
                        date,
                        f(
                            "method",
                            "Forma de pago",
                            InputType.CHOICE,
                            true,
                            "qr",
                            choices =
                                listOf(
                                    Choice("qr", "QR o transferencia"),
                                    Choice("cash", "Efectivo"),
                                ),
                        ),
                        receipt,
                        f("note", "Observación", InputType.MULTILINE, max = 1000),
                    )
                help =
                    "Adjunta un solo comprobante por el total depositado. El reparto comienza por los meses más antiguos; puede cubrir varios meses o una parte del siguiente. El saldo se descuenta al aprobar el depósito."
            }
            "expense" -> {
                title = "Registrar gasto extraordinario"
                val proposal = s.find("proposals", id)
                fields =
                    listOf(
                        f(
                            "template",
                            "Plantilla (opcional)",
                            InputType.CHOICE,
                            choices =
                                s.rows("recurring_expense_templates").map {
                                    Choice(it.id, it.text("concept"))
                                },
                        ),
                        f(
                            "concept",
                            "Concepto",
                            required = true,
                            initial = proposal?.text("concept").orEmpty(),
                            max = 160,
                        ),
                        amount.copy(initial = proposal?.text("estimated_amount").orEmpty()),
                        date,
                        f("due", "Fecha límite", InputType.DATE, true, Dates.today()),
                        f(
                            "paid_by",
                            "Familia que adelantó el pago",
                            InputType.CHOICE,
                            true,
                            choices = families,
                        ),
                        f(
                            "justification",
                            "Justificación",
                            InputType.MULTILINE,
                            true,
                            proposal?.text("justification").orEmpty(),
                            max = 2000,
                        ),
                        receipt,
                    )
                help =
                    "Se utilizará el reparto del servidor entre las seis identidades contables. Revisa los datos antes de guardar."
            }
            "credit" -> {
                title = "Descontar de un servicio"
                fields =
                    listOf(
                        f("service", "Servicio", InputType.CHOICE, true, choices = services),
                        amount,
                        f("note", "Detalle", InputType.MULTILINE, true, max = 1000),
                        receipt,
                    )
                help =
                    "Este monto se aplica como descuento al servicio, sin dividirlo como un gasto extraordinario."
            }
            "services" -> {
                title = "Registrar servicios del mes"
                val list =
                    mutableListOf(
                        f("month", "Mes de las facturas", InputType.MONTH, true, Dates.month()),
                        f(
                            "due",
                            "Pagar hasta",
                            InputType.DATE,
                            true,
                            java.time.YearMonth.parse(Dates.month()).atEndOfMonth().toString(),
                        ),
                        f(
                            "reference",
                            "Guardar solo como referencia, sin crear deudas",
                            InputType.CHECK,
                            initial = "false",
                        ),
                    )
                services.forEach { v ->
                    list +=
                        f(v.value, v.label + " (Bs)", InputType.MONEY, true, "0", 1, zero = true)
                    list +=
                        f(
                            v.value + "_receipt",
                            "Factura de " + v.label.lowercase(),
                            InputType.IMAGE,
                            page = 1,
                        )
                }
                list +=
                    f(
                        "other_water",
                        "Descuento adicional de agua (Bs)",
                        InputType.MONEY,
                        initial = "0",
                        page = 2,
                        zero = true,
                    )
                list +=
                    f(
                        "other_electricity",
                        "Descuento adicional de luz (Bs)",
                        InputType.MONEY,
                        initial = "0",
                        page = 2,
                        zero = true,
                    )
                fields = list
                help =
                    "Completa el mes, las facturas y revisa la suma. El total se reparte entre las seis cuentas. Gas queda en 0 si no hay factura."
            }
            "family-name" -> {
                title = "Nombre de la familia"
                fields =
                    listOf(
                        f(
                            "name",
                            "Nombre",
                            required = true,
                            initial = s.find("families", id)?.text("name").orEmpty(),
                            max = 80,
                        )
                    )
            }
            "assignment" -> {
                title = "Asignar familia"
                val u = s.find("profiles", id) ?: error("Cuenta no disponible.")
                val payer = u.text("role") == "payer"
                fields =
                    listOf(
                        f("role", "Rol", initial = u.text("role")),
                        f(
                            "family",
                            "Familia",
                            InputType.CHOICE,
                            true,
                            u.text("family_id"),
                            choices = families,
                        ),
                    ) +
                        if (payer)
                            listOf(
                                f(
                                    "second",
                                    "Segunda familia",
                                    InputType.CHOICE,
                                    true,
                                    u.text("second_family_id"),
                                    choices = families,
                                )
                            )
                        else emptyList()
                help =
                    "Cuenta: ${u.text("display_name")}. Solo cambia la asignación; los pagos anteriores mantienen su familia."
            }
            "user" -> {
                title = "Crear usuario"
                fields =
                    listOf(
                        f("name", "Nombre completo", required = true, max = 160),
                        f("username", "Nombre de usuario", required = true, max = 80),
                        f("email", "Correo", InputType.EMAIL, true),
                        f("password", "Contraseña inicial", InputType.PASSWORD, true),
                        f(
                            "role",
                            "Rol",
                            InputType.CHOICE,
                            true,
                            "family",
                            page = 1,
                            choices = Role.entries.map { Choice(it.code, it.label) },
                        ),
                        f(
                            "family",
                            "Familia",
                            InputType.CHOICE,
                            true,
                            page = 1,
                            choices = families,
                            show = "role" to "family",
                        ),
                        f(
                            "payer_family",
                            "Primera familia del encargado",
                            InputType.CHOICE,
                            true,
                            page = 1,
                            choices = families,
                            show = "role" to "payer",
                        ),
                        f(
                            "second",
                            "Segunda familia del encargado",
                            InputType.CHOICE,
                            true,
                            page = 1,
                            choices = families,
                            show = "role" to "payer",
                        ),
                    )
                help =
                    "La creación usa el servicio de administración existente. No compartas la contraseña en capturas."
            }
            "qr" -> {
                title = "QR de pago"
                val current = s.rows("payment_qr_settings").firstOrNull()
                fields =
                    listOf(
                        f(
                            "title",
                            "Título",
                            initial = current?.text("qr_title").orEmpty().ifBlank { "Pago por QR" },
                            max = 120,
                        ),
                        f(
                            "note",
                            "Instrucciones",
                            InputType.MULTILINE,
                            initial = current?.text("qr_note").orEmpty(),
                            max = 2000,
                        ),
                        f(
                            "receipt",
                            "Imagen del QR",
                            InputType.IMAGE,
                            required = current?.text("qr_image_path").isNullOrBlank(),
                        ),
                    )
                help =
                    "PNG, JPG o WebP de hasta 5 MB. Si no seleccionas otra imagen se conserva el QR actual."
            }
            "payer-families" -> {
                title = "Familias del encargado"
                val payer = s.rows("profiles").firstOrNull { it.text("role") == "payer" }
                fields =
                    listOf(
                        f(
                            "first",
                            "Primera familia",
                            InputType.CHOICE,
                            true,
                            payer?.text("family_id").orEmpty(),
                            choices = families,
                        ),
                        f(
                            "second",
                            "Segunda familia",
                            InputType.CHOICE,
                            true,
                            payer?.text("second_family_id").orEmpty(),
                            choices = families,
                        ),
                    )
                help =
                    "Son dos familias contables diferentes. Esta configuración no cambia los turnos compartidos de limpieza."
            }
            "turn" -> {
                title = "Asignar turno"
                fields =
                    listOf(
                        f(
                            "family",
                            "Familia",
                            InputType.CHOICE,
                            true,
                            choices = s.cleaningFamilies().map { Choice(it.id, it.text("name")) },
                        ),
                        date,
                        f(
                            "tasks",
                            "Tareas: una por línea",
                            InputType.MULTILINE,
                            initial = "Baño\nCocina\nGradas\nBasura",
                        ),
                        f("note", "Observación", InputType.MULTILINE, max = 2000),
                    )
            }
            "complete" -> {
                title = "Completar limpieza"
                fields = listOf(receipt, f("note", "Observación", InputType.MULTILINE, max = 2000))
                help = "Confirma únicamente cuando se hayan realizado todas las tareas del turno."
            }
            "swap" -> {
                title = "Solicitar intercambio"
                fields =
                    listOf(
                        f(
                            "target",
                            "Turno de otra familia",
                            InputType.CHOICE,
                            true,
                            choices =
                                targets.map {
                                    Choice(
                                        it.id,
                                        it.text("family_name").ifBlank {
                                            s.family(it.number("family_id"))
                                        } + " · " + Dates.weekLabel(it.text("turn_date")),
                                    )
                                },
                        )
                    )
                help =
                    "Otra persona de la familia receptora debe aceptar el intercambio. Hasta entonces, cada familia conserva su semana. El administrador no aprueba cambios entre otras familias."
            }
            "rotation" -> {
                title = "Organizar repetición semanal"
                val cfg = s.rows("cleaning_rotation_settings").firstOrNull()
                val canonical = s.cleaningFamilies()
                val order =
                    cfg?.json?.optJSONArray("family_order")?.let { a ->
                        (0 until a.length()).map { a.optLong(it).toString() }
                    } ?: canonical.map { it.id }
                val opts = canonical.map { Choice(it.id, it.text("name")) }
                fields =
                    listOf(
                        f(
                            "enabled",
                            "Repetición semanal activa",
                            InputType.CHECK,
                            initial = (cfg?.flag("enabled") ?: false).toString(),
                        )
                    ) +
                        order.mapIndexed { i, v ->
                            f(
                                "order_$i",
                                "Posición ${i+1}",
                                InputType.CHOICE,
                                true,
                                v,
                                choices = opts,
                            )
                        } +
                        listOf(
                            f(
                                "anchor",
                                "Lunes inicial",
                                InputType.DATE,
                                true,
                                cfg?.text("anchor_monday").orEmpty().ifBlank {
                                    Dates.monday(Dates.today())
                                },
                            ),
                            f(
                                "tasks",
                                "Tareas: una por línea",
                                InputType.MULTILINE,
                                initial =
                                    cfg?.list("default_checklist")?.joinToString("\n")
                                        ?: "Baño\nCocina\nGradas\nBasura",
                            ),
                        )
                help =
                    "Las cuentas de un mismo hogar comparten posición. Se conservan las semanas ya asignadas. Pausar impide crear semanas nuevas, sin borrar turnos."
            }
            "notice" -> {
                title = "Publicar aviso"
                fields =
                    listOf(
                        f("title", "Título", required = true, max = 160),
                        f("body", "Mensaje", InputType.MULTILINE, true, max = 4000),
                        f("until", "Visible hasta (opcional)", InputType.DATE),
                    )
            }
            "proposal" -> {
                title = "Publicar propuesta"
                fields =
                    listOf(
                        f("concept", "Concepto", required = true, max = 160),
                        amount,
                        f("justification", "Justificación", InputType.MULTILINE, true, max = 4000),
                        receipt,
                    )
                help =
                    "Una propuesta no registra un gasto automáticamente. Conserva la votación por identidad contable."
            }
            "comment" -> {
                title = "Comentar gasto"
                fields = listOf(f("body", "Comentario", InputType.MULTILINE, true, max = 2000))
            }
            "template" -> {
                title = "Crear plantilla"
                fields =
                    listOf(
                        f("concept", "Concepto", required = true, max = 160),
                        f(
                            "category",
                            "Categoría",
                            InputType.CHOICE,
                            true,
                            "external",
                            choices =
                                listOf(
                                    Choice("external", "Extraordinario"),
                                    Choice("basic", "Servicio básico"),
                                ),
                        ),
                        f("day", "Día de vencimiento", required = true, initial = "20"),
                        f("justification", "Justificación", InputType.MULTILINE, max = 2000),
                    )
            }
            "external-paid" -> {
                title = "Registrar aporte del restaurante"
                val debt = s.find("external_utility_debts", id)
                fields =
                    listOf(
                        amount.copy(
                            initial =
                                debt
                                    ?.let {
                                        (it.money("amount_due") - it.money("amount_paid"))
                                            .max(java.math.BigDecimal.ZERO)
                                            .toPlainString()
                                    }
                                    .orEmpty()
                        ),
                        f("received_by", "Recibido por", required = true, max = 160),
                    )
            }
            "password" -> {
                title = "Cambiar contraseña"
                fields =
                    listOf(
                        f("password", "Nueva contraseña", InputType.PASSWORD, true),
                        f("confirmation", "Confirma la contraseña", InputType.PASSWORD, true),
                    )
            }
            else -> error("Formulario no disponible.")
        }
        return Form(kind, title, fields, id, help)
    }
}
