package com.casafamilias.app.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.casafamilias.app.BuildConfig
import com.casafamilias.app.domain.Snapshot
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

object ExportFiles {
    fun backup(s: Snapshot) =
        JSONObject()
            .apply {
                put("exported_at", java.time.Instant.now().toString())
                put("application", "Casa Familias ${BuildConfig.VERSION_NAME}")
                put(
                    "description",
                    "Registros y referencias de imágenes; no incluye los archivos binarios de Storage.",
                )
                s.tables.forEach { (name, rows) -> put(name, JSONArray(rows.map { it.json })) }
            }
            .toString(2)
            .toByteArray()

    fun pdf(rows: List<List<String>>): ByteArray {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val head =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 17f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
        var page: PdfDocument.Page? = null
        var y = 80f
        var index = 0
        fun newPage() {
            page?.let(doc::finishPage)
            index++
            page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, index).create())
            page!!.canvas.drawText("Casa Familias · Reporte", 36f, 42f, head)
            page!!
                .canvas
                .drawText("Generado: ${java.time.LocalDate.now()} · Página $index", 36f, 62f, paint)
            y = 90f
        }
        fun lines(text: String, width: Float): List<String> {
            val result = mutableListOf<String>()
            var current = ""
            for (word in text.replace('\n', ' ').split(' ')) {
                val next = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(next) > width && current.isNotBlank()) {
                    result += current
                    current = word
                } else current = next
                while (paint.measureText(current) > width && current.length > 1) {
                    val n = paint.breakText(current, true, width, null).coerceAtLeast(1)
                    result += current.take(n)
                    current = current.drop(n)
                }
            }
            if (current.isNotEmpty()) result += current
            return result.ifEmpty { listOf("") }
        }
        newPage()
        val n = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val width = 540f / n
        for (row in rows) {
            val cells = (0 until n).map { lines(row.getOrElse(it) { "" }, width - 10) }
            val height = cells.maxOf { it.size } * 14f + 12
            if (y + height > 748) newPage()
            row.forEachIndexed { col, _ ->
                cells[col].forEachIndexed { line, text ->
                    page!!.canvas.drawText(text, 36 + col * width, y + line * 14, paint)
                }
            }
            y += height
            page!!
                .canvas
                .drawLine(36f, y - 9, 576f, y - 9, Paint().apply { color = 0xFFE0E0E0.toInt() })
        }
        page?.let(doc::finishPage)
        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    private fun xml(s: String) =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }

    fun reportXlsx(s: Snapshot, rows: List<List<String>>, period: String): ByteArray {
        val payments =
            listOf(listOf("Fecha", "Familia", "Monto", "Aplicado", "Crédito", "Estado", "Nota")) +
                s.rows("payments")
                    .filter { it.text("paid_at").startsWith(period) }
                    .map {
                        listOf(
                            it.text("paid_at"),
                            s.family(it.number("family_id")),
                            it.money("amount").toPlainString(),
                            it.money("applied_amount").toPlainString(),
                            it.money("credit_amount").toPlainString(),
                            it.text("status"),
                            it.text("note"),
                        )
                    }
        val expenseIds =
            s.rows("expenses")
                .filter { it.text("expense_date").startsWith(period) }
                .map { it.id }
                .toSet()
        val families =
            listOf(listOf("Familia", "Asignado", "Pagado", "Pendiente", "Saldo a favor")) +
                s.rows("families").map { f ->
                    val shares =
                        s.rows("expense_shares").filter {
                            it.number("family_id") == f.number("id") &&
                                it.text("expense_id") in expenseIds
                        }
                    val due = shares.fold(BigDecimal.ZERO) { a, r -> a + r.money("amount_due") }
                    val pending = shares.fold(BigDecimal.ZERO) { a, r -> a + s.shareBalance(r) }
                    listOf(
                        f.text("name"),
                        due.toPlainString(),
                        (due - pending).toPlainString(),
                        pending.toPlainString(),
                        s.credit(f.number("id")).toPlainString(),
                    )
                }
        return xlsx(linkedMapOf("Gastos" to rows, "Pagos" to payments, "Familias" to families))
    }

    fun xlsx(rows: List<List<String>>): ByteArray = xlsx(linkedMapOf("Movimientos" to rows))

    fun xlsx(sheets: Map<String, List<List<String>>>): ByteArray {
        require(
            sheets.isNotEmpty() &&
                sheets.keys.all { it.length in 1..31 && it.none { c -> c in "[]:*?/\\" } }
        )
        val files = linkedMapOf<String, String>()
        files["[Content_Types].xml"] =
            """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                sheets.keys.indices.joinToString("") {
                    """<Override PartName="/xl/worksheets/sheet${it+1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
                } +
                "</Types>"
        files["_rels/.rels"] =
            """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        files["xl/workbook.xml"] =
            """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                sheets.keys
                    .mapIndexed { i, name ->
                        """<sheet name="${xml(name)}" sheetId="${i+1}" r:id="rId${i+1}"/>"""
                    }
                    .joinToString("") +
                "</sheets></workbook>"
        files["xl/_rels/workbook.xml.rels"] =
            """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                sheets.keys.indices.joinToString("") {
                    """<Relationship Id="rId${it+1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it+1}.xml"/>"""
                } +
                "</Relationships>"
        sheets.values.forEachIndexed { i, rows ->
            files["xl/worksheets/sheet${i+1}.xml"] = buildString {
                append(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                )
                rows.forEachIndexed { r, row ->
                    append("<row r=\"${r+1}\">")
                    row.forEachIndexed { c, text ->
                        var number = c + 1
                        var column = ""
                        while (number > 0) {
                            number--
                            column = ('A'.code + number % 26).toChar() + column
                            number /= 26
                        }
                        val ref = column + (r + 1)
                        if (text.toBigDecimalOrNull() != null && r > 0)
                            append("<c r=\"$ref\"><v>${xml(text)}</v></c>")
                        else
                            append(
                                "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(text)}</t></is></c>"
                            )
                    }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
