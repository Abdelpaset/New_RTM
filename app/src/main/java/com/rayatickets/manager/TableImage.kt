package com.rayatickets.manager

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

object TableImage {
    fun render(tickets: List<Ticket>): Bitmap {
        val rowHeight = 44f
        val headerHeight = 52f
        val padding = 16f
        val totalWidth = 1080f
        val rowCount = tickets.size.coerceAtLeast(1)
        val totalHeight = (headerHeight + (rowCount * rowHeight) + (padding * 2)).coerceAtLeast(180f)

        val bitmap = Bitmap.createBitmap(totalWidth.toInt(), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#0F172A"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
        }

        val colWidths = floatArrayOf(130f, 150f, 320f, 140f, 160f, 148f)
        val colHeaders = arrayOf("Ticket", "Bank", "Site Name", "Status", "Assignee", "Start Time")

        val headerPaint = Paint().apply { color = Color.parseColor("#1E293B") }
        canvas.drawRect(padding, padding, totalWidth - padding, padding + headerHeight, headerPaint)

        paint.color = Color.parseColor("#38BDF8")
        paint.textSize = 15f
        var curX = padding + 8f
        for (i in colHeaders.indices) {
            val rect = RectF(curX, padding, curX + colWidths[i] - 12f, padding + headerHeight)
            canvas.save()
            canvas.clipRect(rect)
            canvas.drawText(colHeaders[i], curX, padding + 32f, paint)
            canvas.restore()
            curX += colWidths[i]
        }

        var curY = padding + headerHeight
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#334155")
            strokeWidth = 1f
        }
        val altRowPaint = Paint().apply { color = Color.parseColor("#141E33") }

        if (tickets.isEmpty()) {
            textPaint.color = Color.parseColor("#94A3B8")
            textPaint.textSize = 16f
            canvas.drawText("No tickets found for selected filters.", padding + 30f, curY + 36f, textPaint)
            return bitmap
        }

        for (idx in tickets.indices) {
            val t = tickets[idx]
            if (idx % 2 == 1) {
                canvas.drawRect(padding, curY, totalWidth - padding, curY + rowHeight, altRowPaint)
            }
            canvas.drawLine(padding, curY + rowHeight, totalWidth - padding, curY + rowHeight, linePaint)

            val timeDisplay = if (t.startTime.length >= 16) t.startTime.substring(11, 16) else t.startTime
            val rowData = arrayOf(
                "#${t.serial}",
                t.bank,
                t.siteName,
                t.status,
                t.displayAssignee,
                timeDisplay
            )

            var cellX = padding + 8f
            for (col in rowData.indices) {
                val clipRect = RectF(cellX, curY, cellX + colWidths[col] - 10f, curY + rowHeight)
                canvas.save()
                canvas.clipRect(clipRect)

                when (col) {
                    0 -> {
                        textPaint.color = Color.parseColor("#38BDF8")
                        textPaint.typeface = Typeface.DEFAULT_BOLD
                    }
                    3 -> {
                        textPaint.typeface = Typeface.DEFAULT_BOLD
                        textPaint.color = when (t.status.lowercase()) {
                            "pending" -> Color.parseColor("#FBBF24")
                            "resolved", "solved by phone" -> Color.parseColor("#4ADE80")
                            "cancelled" -> Color.parseColor("#F87171")
                            else -> Color.parseColor("#E2E8F0")
                        }
                    }
                    else -> {
                        textPaint.typeface = Typeface.DEFAULT
                        textPaint.color = Color.parseColor("#F8FAFC")
                    }
                }

                canvas.drawText(rowData[col], cellX, curY + 28f, textPaint)
                canvas.restore()
                cellX += colWidths[col]
            }

            curY += rowHeight
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
