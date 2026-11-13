package com.rayatickets.manager

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TableImage {

    private val shortNames = mapOf(
        "mohamed gamal abodaif mohamed" to "Mohamed Gamal",
        "mina ehab magdy" to "Mina Ehab",
        "abdel baset abdel moaty mohamed ahmed" to "Abdelbaset",
        "ahmed mohamed mohamed abdelaal bekhiet" to "Ahmed Abdelaal",
        "mohammad farid gad mahmoud" to "Mohamed Farid",
        "mahmoud bakry ahmed bakry" to "Mahmoud Bakry",
        "aly mohamed taher abdelnaiem" to "Aly Taher",
        "mohamed ashraf mohamed abd alaziz" to "Mohamed Ashraf",
        "mohamed ashraf mahmoud abd alaziz" to "Mohamed Ashraf",
        "walid salah eldin asem ahmed" to "Walid Salah Eldin",
        "mohamed ahmed mohamed osman" to "Mohamed Osman",
        "mahmoud mamdouh fatouh el sayed" to "Mahmoud Mamdouh"
    )

    private fun formatShortName(ticket: Ticket): String {
        val rawLower = ticket.assignee.trim().lowercase()
        return shortNames[rawLower] ?: ticket.displayAssignee
    }

    private fun parseDateTime(raw: String): LocalDateTime? {
        if (raw.isBlank()) return null
        val cleaned = raw.trim().replace("T", " ").substringBefore(".")
        val patterns = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                return LocalDateTime.parse(cleaned.take(19), DateTimeFormatter.ofPattern(pattern))
            } catch (_: Exception) {
            }
        }
        return try {
            LocalDate.parse(cleaned.take(10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay()
        } catch (_: Exception) {
            null
        }
    }

    fun render(tickets: List<Ticket>): Bitmap {
        val outerPadding = 18f
        val tableWidth = 1244f
        val totalWidth = (tableWidth + outerPadding * 2).toInt()

        val colWidths = floatArrayOf(178f, 115f, 290f, 150f, 392f, 119f)
        val colHeaders = arrayOf("Assignee", "Ticket", "Site Name", "Call Type", "Status", "Start Time")
        val cellPadX = 10f
        val cellPadY = 9f
        val headerHeight = 42f

        val cairoToday = LocalDate.now(ZoneId.of("Africa/Cairo"))

        val sorted = tickets.sortedWith(
            compareBy<Ticket>(
                { it.displayAssignee.lowercase() },
                { it.startTime },
                { it.serial }
            )
        )

        val headerBgPaint = Paint().apply { color = Color.parseColor("#2b5fa8") }
        val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        val normalTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#243447")
            textSize = 13f
            typeface = Typeface.DEFAULT
        }
        val oldTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#12243a")
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val assigneeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2b5fa8")
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val rowBorderPaint = Paint().apply {
            color = Color.parseColor("#e3ebf5")
            strokeWidth = 1f
        }
        val groupBorderPaint = Paint().apply {
            color = Color.parseColor("#9fbbe0")
            strokeWidth = 2f
        }
        val tableBgPaint = Paint().apply { color = Color.WHITE }

        data class PreparedRow(
            val layouts: List<StaticLayout>,
            val rowHeight: Float,
            val isGroupStart: Boolean
        )

        val preparedRows = mutableListOf<PreparedRow>()
        var lastAssigneeKey: String? = null

        for (t in sorted) {
            val assigneeKey = t.displayAssignee.trim().lowercase()
            val isGroupStart = lastAssigneeKey == null || assigneeKey != lastAssigneeKey
            lastAssigneeKey = assigneeKey

            val dt = parseDateTime(t.startTime.ifBlank { t.createdAt })
            val isOld = dt != null && dt.toLocalDate().isBefore(cairoToday)

            val baseStatus = t.fullStatusText.ifBlank { t.status }.replace(Regex("\\s+"), " ").trim()
            val note = t.description.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" / ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val statusCombined = if (note.isNotEmpty() && !baseStatus.contains(note, ignoreCase = true)) {
                "$baseStatus ($note)"
            } else {
                baseStatus
            }

            val timeText = if (dt != null) {
                "${dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}\n${dt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
            } else {
                t.startTime.ifBlank { t.createdAt }
            }

            val cellTexts = listOf(
                if (isGroupStart) formatShortName(t) else "",
                t.serial,
                t.siteName.replace(Regex("\\s+"), " ").trim(),
                t.type.replace(Regex("\\s+"), " ").trim(),
                statusCombined,
                timeText
            )

            val bodyPaint = if (isOld) oldTextPaint else normalTextPaint
            val layouts = cellTexts.mapIndexed { idx, txt ->
                val p = if (idx == 0) assigneeTextPaint else bodyPaint
                val availWidth = (colWidths[idx] - cellPadX * 2).toInt().coerceAtLeast(20)
                StaticLayout.Builder.obtain(txt, 0, txt.length, p, availWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.15f)
                    .setIncludePad(false)
                    .build()
            }

            val maxTextHeight = layouts.maxOfOrNull { it.height.toFloat() } ?: 18f
            val rowHeight = maxTextHeight + cellPadY * 2
            preparedRows.add(PreparedRow(layouts, rowHeight, isGroupStart))
        }

        val bodyHeight = if (preparedRows.isEmpty()) 80f else preparedRows.sumOf { it.rowHeight.toDouble() }.toFloat()
        val totalHeight = (outerPadding * 2 + headerHeight + bodyHeight).toInt().coerceAtLeast(160)

        val bitmap = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#f4f8fd"))
        canvas.drawRect(
            outerPadding,
            outerPadding,
            outerPadding + tableWidth,
            outerPadding + headerHeight + bodyHeight,
            tableBgPaint
        )
        canvas.drawRect(
            outerPadding,
            outerPadding,
            outerPadding + tableWidth,
            outerPadding + headerHeight,
            headerBgPaint
        )

        var curX = outerPadding
        for (i in colHeaders.indices) {
            canvas.drawText(
                colHeaders[i],
                curX + cellPadX,
                outerPadding + 26f,
                headerTextPaint
            )
            curX += colWidths[i]
        }

        var curY = outerPadding + headerHeight

        if (preparedRows.isEmpty()) {
            canvas.drawText(
                "No tickets found for selected filters.",
                outerPadding + 20f,
                curY + 34f,
                normalTextPaint
            )
            return bitmap
        }

        for ((idx, row) in preparedRows.withIndex()) {
            if (row.isGroupStart && idx > 0) {
                canvas.drawLine(outerPadding, curY, outerPadding + tableWidth, curY, groupBorderPaint)
            }

            var cellX = outerPadding
            for (col in row.layouts.indices) {
                canvas.save()
                canvas.translate(cellX + cellPadX, curY + cellPadY)
                row.layouts[col].draw(canvas)
                canvas.restore()
                cellX += colWidths[col]
            }

            curY += row.rowHeight
            canvas.drawLine(outerPadding, curY, outerPadding + tableWidth, curY, rowBorderPaint)
        }

        return bitmap
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String = "tickets_table.png"): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    fun exportToCsv(context: Context, tickets: List<Ticket>): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "raya_tickets_${LocalDate.now()}.csv")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("Ticket,Bank,Site Name,ATM ID,Status,Type,Assignee,Start Time,Created At,Description\n")
            for (t in tickets) {
                val cleanDesc = t.description.replace("\n", " ").replace("\r", " ").replace(",", ";")
                val cells = listOf(
                    t.serial,
                    t.bank,
                    t.siteName,
                    t.atmId,
                    t.status,
                    t.type,
                    t.displayAssignee,
                    t.startTime,
                    t.createdAt,
                    cleanDesc
                )
                val line = cells.joinToString(",") { cell ->
                    val escaped = cell.replace("\"", "\"\"")
                    "\"" + escaped + "\""
                }
                writer.write(line)
                writer.write("\n")
            }
        }
        return file
    }
}
