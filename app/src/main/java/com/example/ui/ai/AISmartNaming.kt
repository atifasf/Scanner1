package com.example.ui.ai

import com.google.mlkit.vision.text.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object AISmartNaming {

    /**
     * Analyzes OCR text content and generates a context-aware document name.
     * Examples: Invoice_July_2026, National_ID, Passport, Meeting_Notes, Receipt_25Jul
     */
    fun generateSmartFileName(extractedText: String?): String {
        val dateFormat = SimpleDateFormat("ddMMM", Locale.US)
        val todayStr = dateFormat.format(Date())

        if (extractedText.isNullOrBlank()) {
            return "Scan_$todayStr"
        }

        val safeText = extractedText
        val textLower = safeText.lowercase(Locale.ROOT)

        // 1. Invoice detection
        if (textLower.contains("invoice") || textLower.contains("bill to") || textLower.contains("tax invoice") || textLower.contains("amount due")) {
            val invNumMatcher = Pattern.compile("(#|no|invoice|num)[:\\s]*([a-z0-9-]+)", Pattern.CASE_INSENSITIVE).matcher(safeText)
            val invNum = if (invNumMatcher.find()) invNumMatcher.group(2) else ""
            val monthFormat = SimpleDateFormat("MMM_yyyy", Locale.US).format(Date())
            return if (invNum != null && invNum.isNotBlank() && invNum.length in 3..12) "Invoice_$invNum" else "Invoice_$monthFormat"
        }

        // 2. Passport detection
        if (textLower.contains("passport") || textLower.contains("republic") || textLower.contains("type/type p") || textLower.contains("surname")) {
            return "Passport_Doc"
        }

        // 3. National ID / Driving License
        if (textLower.contains("identity card") || textLower.contains("national id") || textLower.contains("driver license") || textLower.contains("driving licence") || textLower.contains("aadhaar")) {
            return "National_ID"
        }

        // 4. Receipt
        if (textLower.contains("receipt") || textLower.contains("merchant") || textLower.contains("total paid") || textLower.contains("cashier")) {
            return "Receipt_$todayStr"
        }

        // 5. Meeting Notes / Minutes
        if (textLower.contains("meeting") || textLower.contains("agenda") || textLower.contains("attendees") || textLower.contains("minutes")) {
            return "Meeting_Notes_$todayStr"
        }

        // 6. Contract / Agreement
        if (textLower.contains("agreement") || textLower.contains("contract") || textLower.contains("terms and conditions") || textLower.contains("signed")) {
            return "Contract_Agreement"
        }

        // 7. Extract first line clean title
        val lines = safeText.split("\n")
            .map { it.trim() }
            .filter { it.length in 3..30 && !it.contains("http") }

        if (lines.isNotEmpty()) {
            val cleanHeader = lines.first().replace(Regex("[^a-zA-Z0-9_]"), "_").take(20)
            if (cleanHeader.isNotBlank()) {
                return cleanHeader
            }
        }

        return "Document_$todayStr"
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
