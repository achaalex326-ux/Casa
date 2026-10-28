package com.casafamilias.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

fun json(vararg pairs: Pair<String, Any?>) =
    JSONObject().apply { pairs.forEach { put(it.first, it.second ?: JSONObject.NULL) } }

fun JSONArray.records(): List<Record> =
    (0 until length()).mapNotNull { optJSONObject(it)?.let(::Record) }

data class Record(val json: JSONObject) {
    val id
        get() = text("id")

    fun text(key: String): String = if (json.isNull(key)) "" else json.optString(key, "")

    fun money(key: String): BigDecimal = text(key).toBigDecimalOrNull() ?: BigDecimal.ZERO

    fun number(key: String) = text(key).toLongOrNull()

    fun flag(key: String) = json.optBoolean(key, false)

    fun list(key: String): List<String> =
        json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }
            ?: emptyList()

    fun paymentMethodLabel(): String =
        when {
            text("kind") == "auto" -> "Cuota cubierta automáticamente"
            text("kind") == "credit" -> "Aplicación de saldo a favor"
            text("method") == "cash" ||
                text("payment_method") == "cash" ||
                text("note").startsWith("Método: Efectivo") -> "Efectivo"
            text("method") == "qr" ||
                text("payment_method") == "qr" ||
                text("note").startsWith("Método: QR") -> "QR"
            else -> "No especificada"
        }
}

enum class Role(val code: String, val label: String) {
    FAMILY("family", "Familia"),
    PAYER("payer", "Encargado de pagos"),
    ADMIN("sysadmin", "Administrador"),
    RESTAURANT("restaurant", "Restaurante");

    val staff
        get() = this == ADMIN || this == PAYER

    companion object {
        fun from(code: String) =
            entries.firstOrNull { it.code == code }
                ?: error("El perfil no tiene un rol compatible.")
    }
}

data class Profile(
    val id: String,
    val name: String,
    val email: String,
    val role: Role,
    val familyId: Long?,
    val secondFamilyId: Long?,
) {
    val familyIds
        get() = listOfNotNull(familyId, secondFamilyId.takeIf { role == Role.PAYER })

    companion object {
        fun from(r: Record) =
            Profile(
                r.id,
                r.text("display_name").ifBlank { "Mi cuenta" },
                r.text("contact_email"),
                Role.from(r.text("role")),
                r.number("family_id"),
                r.number("second_family_id"),
            )
    }
}

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String,
) {
    fun toJson() =
        json(
            "access_token" to accessToken,
            "refresh_token" to refreshToken,
            "expires_at" to expiresAt,
            "user" to json("id" to userId, "email" to email),
        )

    companion object {
        fun from(j: JSONObject): Session {
            val u = j.optJSONObject("user") ?: JSONObject()
            return Session(
                j.getString("access_token"),
                j.getString("refresh_token"),
                j.optLong(
                    "expires_at",
                    System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600),
                ),
                u.optString("id"),
                u.optString("email"),
            )
        }
    }
}

object Dates {
    fun today(): String = LocalDate.now(ZoneId.of("America/La_Paz")).toString()

    fun month(): String = today().take(7)

    fun monday(date: String): String =
        LocalDate.parse(date).let { it.minusDays(it.dayOfWeek.value.toLong() - 1) }.toString()

    fun label(date: String): String =
        runCatching {
                LocalDate.parse(date.take(10))
                    .format(
                        java.time.format.DateTimeFormatter.ofPattern(
                            "d MMM yyyy",
                            Locale("es", "BO"),
                        )
                    )
            }
            .getOrDefault(date)

    fun monthLabel(month: String): String =
        runCatching {
                YearMonth.parse(month)
                    .format(
                        java.time.format.DateTimeFormatter.ofPattern(
                            "MMMM yyyy",
                            Locale("es", "BO"),
                        )
                    )
            }
            .getOrDefault(month)

    fun weekLabel(date: String): String =
        runCatching {
                val start = LocalDate.parse(monday(date))
                "${label(start.toString())} al ${label(start.plusDays(6).toString())}"
            }
            .getOrDefault(date)
}

object Amounts {
    fun parse(input: String): BigDecimal? {
        val s = input.trim().replace(" ", "")
        val normalized =
            when {
                s.contains(',') && s.contains('.') ->
                    if (s.lastIndexOf(',') > s.lastIndexOf('.'))
                        s.replace(".", "").replace(',', '.')
                    else s.replace(",", "")
                else -> s.replace(',', '.')
            }
        return normalized.toBigDecimalOrNull()
    }

    fun format(amount: BigDecimal): String =
        "Bs " +
            NumberFormat.getNumberInstance(Locale("es", "BO"))
                .apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
                .format(amount)

    fun valid(input: String, allowZero: Boolean = false) =
        parse(input)?.let { if (allowZero) it >= BigDecimal.ZERO else it > BigDecimal.ZERO } == true

    fun jsonValue(input: String): BigDecimal =
        (parse(input) ?: error("Escribe un monto válido.")).setScale(2, RoundingMode.HALF_UP)
}

data class Snapshot(
    val profile: Profile,
    val tables: Map<String, List<Record>>,
    val loadedAt: Long = System.currentTimeMillis(),
    val preview: Boolean = false,
) {
    fun rows(table: String) = tables[table].orEmpty()

    fun find(table: String, id: String) = rows(table).firstOrNull { it.id == id }

    fun family(id: Long?) =
        rows("families").firstOrNull { it.number("id") == id }?.text("name") ?: "Familia"

    fun household(id: Long?) =
        rows("cleaning_family_aliases")
            .firstOrNull { it.number("family_id") == id }
            ?.number("household_id") ?: id

    fun ownsCleaning(id: Long?) =
        profile.role == Role.ADMIN || profile.familyIds.any { household(it) == household(id) }

    /** Representing a household is separate from administering its cleaning records. */
    fun representsCleaning(id: Long?) =
        id != null &&
            profile.role != Role.RESTAURANT &&
            profile.familyIds.any { household(it) == household(id) }

    fun canAnswerSwap(request: Record) =
        request.text("status") == "pending" &&
            request.text("created_by") != profile.id &&
            representsCleaning(request.number("target_family_id"))

    fun cleaningWeek(date: String = Dates.today()) =
        rows("cleaning_turns")
            .filter { Dates.monday(it.text("turn_date")) == Dates.monday(date) }
            .sortedBy { it.text("turn_date") }

    fun cleaningMonth(month: String) =
        rows("cleaning_turns")
            .filter { r ->
                val start = LocalDate.parse(Dates.monday(r.text("turn_date")))
                val selected = YearMonth.parse(month)
                !start.isAfter(selected.atEndOfMonth()) &&
                    !start.plusDays(6).isBefore(selected.atDay(1))
            }
            .sortedBy { it.text("turn_date") }

    fun credit(id: Long?) =
        rows("family_credits").firstOrNull { it.number("family_id") == id }?.money("balance")
            ?: BigDecimal.ZERO

    fun shareBalance(share: Record): BigDecimal {
        val paid =
            rows("payments")
                .filter { it.text("share_id") == share.id && it.text("status") == "approved" }
                .fold(BigDecimal.ZERO) { a, p -> a + p.money("applied_amount") }
        return (share.money("amount_due") - paid).max(BigDecimal.ZERO)
    }

    fun balance(id: Long?): BigDecimal =
        if (profile.role == Role.FAMILY)
            rows("family_debts").fold(BigDecimal.ZERO) { a, d -> a + d.money("balance") }
        else
            rows("expense_shares")
                .filter { it.number("family_id") == id }
                .fold(BigDecimal.ZERO) { a, s -> a + shareBalance(s) }

    fun ownPayments() =
        rows("payments").filter { profile.role.staff || it.number("family_id") == profile.familyId }

    fun expenses(month: String) =
        rows("expenses").filter { it.text("expense_date").startsWith(month) }

    fun total(month: String) =
        expenses(month).fold(BigDecimal.ZERO) { a, e -> a + e.money("total_amount") }

    fun cleaningFamilies() =
        rows("families").filter { f ->
            rows("cleaning_family_aliases").none { it.number("family_id") == f.number("id") }
        }
}

object Access {
    val staffRoutes =
        setOf(
            "expenses",
            "expense",
            "services",
            "families",
            "family",
            "reports",
            "templates",
            "restaurant-contributions",
            "credit",
            "comment",
            "notice",
            "external-paid",
        )
    val adminRoutes =
        setOf(
            "admin",
            "users",
            "user",
            "qr",
            "payer-families",
            "periods",
            "audit",
            "rotation",
            "new-turn",
            "family-name",
            "assignment",
            "template",
        )

    fun allows(role: Role, route: String): Boolean =
        when {
            role == Role.RESTAURANT ->
                route in setOf("home", "profile", "password", "notifications")
            route in adminRoutes -> role == Role.ADMIN
            route in staffRoutes -> role.staff
            else -> true
        }

    fun tabs(role: Role): List<String> =
        when (role) {
            Role.RESTAURANT -> listOf("home", "profile")
            Role.FAMILY -> listOf("home", "dues", "payments", "cleaning", "more")
            else -> listOf("home", "expenses", "payments", "families", "more")
        }

    fun more(role: Role) =
        when (role) {
            Role.ADMIN -> listOf("cleaning", "users", "reports", "notices", "proposals", "admin")
            Role.PAYER -> listOf("cleaning", "reports", "notices", "proposals")
            Role.FAMILY -> listOf("notices", "proposals")
            else -> emptyList()
        }
}
