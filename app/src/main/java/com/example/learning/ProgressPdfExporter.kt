package com.example.learning

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

internal object ProgressPdfExporter {
    fun write(output: OutputStream, lines: List<String>) {
        require(lines.isNotEmpty()) { "Không có nội dung để xuất báo cáo." }

        val document = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 12f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            var pageNumber = 1
            var page = newPage(document, pageNumber)
            var y = 48f

            for (line in lines) {
                var remaining = line
                if (remaining.isEmpty()) {
                    if (y > 790f) {
                        document.finishPage(page)
                        page = newPage(document, ++pageNumber)
                        y = 48f
                    }
                    y += 20f
                    continue
                }
                while (remaining.isNotEmpty()) {
                    if (y > 790f) {
                        document.finishPage(page)
                        page = newPage(document, ++pageNumber)
                        y = 48f
                    }
                    val length = paint.breakText(remaining, true, 515f, null).coerceAtLeast(1)
                    page.canvas.drawText(remaining.take(length), 40f, y, paint)
                    y += 20f
                    remaining = remaining.drop(length)
                }
            }

            document.finishPage(page)
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun newPage(document: PdfDocument, pageNumber: Int): PdfDocument.Page =
        document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
}
