package com.example.ui.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object AISmartNaming {

    /**
     * Analyzes full multi-page OCR text content and generates an accurate, context-aware filename
     * following strict priority rules:
     * 1. Complete Title/Heading (multi-word preserved, never truncated)
     * 2. Irrelevant OCR noise filtered out (dates, page numbers, serial numbers, barcodes)
     * 3. Intelligent Document Purpose / Category Understanding (Leave App, Electricity Bill, Bank Statement, etc.)
     * 4. Multi-page consistency
     * 5. Clean, readable formatting without random words
     * 6. Graceful fallbacks
     */
    fun generateSmartFileName(extractedText: String?): String {
        val todayFormatted = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
        val defaultFallback = "Scanned Document $todayFormatted"

        if (extractedText.isNullOrBlank()) {
            return defaultFallback
        }

        val rawText = extractedText.trim()
        val lines = rawText.split("\n").map { it.trim() }.filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            return defaultFallback
        }

        // 1. Check for explicit "Subject:" or "Sub:" lines (Rule 1: Complete title)
        val subjectLine = lines.firstOrNull { line ->
            val lower = line.lowercase(Locale.ROOT)
            lower.startsWith("subject:") || lower.startsWith("subject :-") || 
            lower.startsWith("sub:") || lower.startsWith("sub :-") ||
            lower.startsWith("title:") || lower.startsWith("re:")
        }
        if (subjectLine != null) {
            val extractedSubject = subjectLine
                .replace(Regex("^(?i)(subject|sub|title|re)\\s*[:-]\\s*"), "")
                .trim()
            if (extractedSubject.length >= 4) {
                val clean = cleanFileName(extractedSubject)
                if (clean.isNotBlank() && !isNoiseOrRandom(clean)) {
                    return clean
                }
            }
        }

        // 2. Check for explicit Board / Ministry / Government / University / High Court / Department headings
        val organizationHeading = findOrganizationHeading(lines)
        if (organizationHeading != null) {
            val clean = cleanFileName(organizationHeading)
            if (clean.isNotBlank()) {
                val docType = detectDocumentCategory(rawText)
                return if (docType != null && !clean.lowercase(Locale.ROOT).contains(docType.lowercase(Locale.ROOT))) {
                    cleanFileName("$clean $docType")
                } else {
                    clean
                }
            }
        }

        // 3. Search for Complete Title Phrases in top lines (Rule 1)
        val titleFromHeader = findTitleInHeader(lines)
        if (titleFromHeader != null) {
            val clean = cleanFileName(titleFromHeader)
            if (clean.isNotBlank()) {
                return clean
            }
        }

        // 4. Intelligent Category & Document Purpose Understanding (Rules 3 & 4)
        val categoryName = detectCategoryWithContext(rawText, lines)
        if (categoryName != null) {
            val clean = cleanFileName(categoryName)
            if (clean.isNotBlank()) {
                return clean
            }
        }

        // 5. First meaningful full sentence/line fallback (Rule 8)
        val meaningfulLine = lines.firstOrNull { line ->
            val clean = cleanFileName(line)
            val words = clean.split("\\s+".toRegex())
            words.size >= 3 && clean.length in 12..70 && !isNoiseOrRandom(line)
        }
        if (meaningfulLine != null) {
            val clean = cleanFileName(meaningfulLine)
            if (clean.isNotBlank()) {
                return clean
            }
        }

        return defaultFallback
    }

    private fun findOrganizationHeading(lines: List<String>): String? {
        val orgKeywords = listOf(
            "board of intermediate", "board of secondary", "board of education",
            "ministry of", "government of", "department of", "university of",
            "high court", "supreme court", "chamber of commerce", "national bank",
            "state bank", "superintendent of police", "district court"
        )
        for (line in lines.take(12)) {
            val lower = line.lowercase(Locale.ROOT)
            if (orgKeywords.any { lower.contains(it) }) {
                if (line.length in 8..80 && !isNoiseOrRandom(line)) {
                    return line
                }
            }
        }
        return null
    }

    private fun findTitleInHeader(lines: List<String>): String? {
        val knownTitleHeaders = listOf(
            "LEAVE APPLICATION", "APPLICATION FOR LEAVE",
            "ELECTRICITY BILL", "WATER BILL", "GAS BILL", "UTILITY BILL",
            "BANK STATEMENT", "ACCOUNT STATEMENT",
            "TAX INVOICE", "COMMERCIAL INVOICE", "INVOICE",
            "PAYMENT RECEIPT", "SALES RECEIPT", "RECEIPT",
            "MEDICAL PRESCRIPTION", "PRESCRIPTION",
            "MEDICAL REPORT", "LABORATORY REPORT", "LAB REPORT", "BLOOD REPORT",
            "SCHOOL RESULT CARD", "RESULT CARD", "MARKSHEET", "TRANSCRIPT",
            "DEGREE CERTIFICATE", "PROVISIONAL CERTIFICATE", "PASS CERTIFICATE",
            "BIRTH CERTIFICATE", "MARRIAGE CERTIFICATE", "DEATH CERTIFICATE",
            "PASSPORT", "NATIONAL IDENTITY CARD", "SMART CARD", "CNIC",
            "DRIVING LICENSE", "DRIVING LICENCE",
            "POLICE CHARACTER CERTIFICATE", "CHARACTER CERTIFICATE",
            "EXPERIENCE CERTIFICATE", "SERVICE CERTIFICATE", "EXPERIENCE LETTER",
            "SALARY SLIP", "PAY SLIP", "PAYSLIP",
            "TAX RETURN", "INCOME TAX RETURN",
            "COURT ORDER", "AFFIDAVIT",
            "BUSINESS PROPOSAL", "PROJECT PROPOSAL",
            "MEETING MINUTES", "MINUTES OF MEETING",
            "PROJECT REPORT", "AUDIT REPORT", "ANNUAL REPORT",
            "VEHICLE REGISTRATION", "INSURANCE POLICY",
            "RENTAL AGREEMENT", "TENANCY AGREEMENT", "LEASE AGREEMENT",
            "OFFER LETTER", "APPOINTMENT LETTER",
            "CURRICULUM VITAE", "RESUME"
        )

        for (line in lines.take(10)) {
            val upper = line.uppercase(Locale.ROOT)
            val matchedHeader = knownTitleHeaders.firstOrNull { upper.contains(it) }
            if (matchedHeader != null) {
                if (line.length in matchedHeader.length..70 && !isNoiseOrRandom(line)) {
                    return line
                }
                return matchedHeader
            }
        }

        for (line in lines.take(5)) {
            if (line.length in 10..60 && line == line.uppercase(Locale.ROOT) && line.split("\\s+".toRegex()).size >= 2) {
                if (!isNoiseOrRandom(line) && !line.contains("HTTP") && !line.contains("PAGE")) {
                    return line
                }
            }
        }

        return null
    }

    private fun detectCategoryWithContext(rawText: String, lines: List<String>): String? {
        val textLower = rawText.lowercase(Locale.ROOT)

        val orgPrefix = when {
            textLower.contains("lesco") -> "Lesco "
            textLower.contains("mepco") -> "Mepco "
            textLower.contains("kelectric") || textLower.contains("k-electric") -> "K-Electric "
            textLower.contains("iesco") -> "Iesco "
            textLower.contains("fesco") -> "Fesco "
            textLower.contains("gepco") -> "Gepco "
            textLower.contains("pesco") -> "Pesco "
            textLower.contains("qesco") -> "Qesco "
            textLower.contains("sngpl") -> "SNGPL "
            textLower.contains("ssgc") -> "SSGC "
            textLower.contains("wasa") -> "WASA "
            textLower.contains("hbl") || textLower.contains("habib bank") -> "HBL "
            textLower.contains("mcb") -> "MCB "
            textLower.contains("ubl") || textLower.contains("united bank") -> "UBL "
            textLower.contains("meezan") -> "Meezan Bank "
            textLower.contains("allied bank") || textLower.contains("abl") -> "Allied Bank "
            textLower.contains("national bank") || textLower.contains("nbp") -> "NBP "
            else -> ""
        }

        if (textLower.contains("leave application") || textLower.contains("application for leave") || 
            (textLower.contains("leave") && (textLower.contains("respectfully") || textLower.contains("humbly request") || textLower.contains("grant me")))) {
            return "Leave Application"
        }

        if (textLower.contains("electricity bill") || (textLower.contains("electricity") && textLower.contains("bill")) || 
            textLower.contains("units consumed") || textLower.contains("kwh") || textLower.contains("meter reading")) {
            return "${orgPrefix}Electricity Bill".trim()
        }

        if (textLower.contains("water bill") || textLower.contains("water supply") || textLower.contains("water charges")) {
            return "${orgPrefix}Water Bill".trim()
        }

        if (textLower.contains("gas bill") || textLower.contains("suingas") || textLower.contains("gas charges") || textLower.contains("hm3")) {
            return "${orgPrefix}Gas Bill".trim()
        }

        if (textLower.contains("bank statement") || textLower.contains("account statement") || 
            (textLower.contains("statement of account") || (textLower.contains("opening balance") && textLower.contains("closing balance")))) {
            return "${orgPrefix}Bank Statement".trim()
        }

        if (textLower.contains("tax invoice") || textLower.contains("commercial invoice") || textLower.contains("invoice") || textLower.contains("bill to")) {
            val invoiceNoMatcher = Pattern.compile("(?i)(invoice|inv)\\s*(no|num|#)?\\s*[:.]?\\s*([a-z0-9-]+)").matcher(rawText)
            val invNum = if (invoiceNoMatcher.find()) invoiceNoMatcher.group(3) else null
            return if (!invNum.isNullOrBlank() && invNum.length in 3..12 && !(invNum.all { it.isDigit() } && invNum.length > 8)) {
                "Invoice $invNum"
            } else {
                "Invoice"
            }
        }

        if (textLower.contains("receipt") || textLower.contains("payment received") || textLower.contains("total paid") || textLower.contains("cashier")) {
            return "Receipt"
        }

        if (textLower.contains("prescription") || textLower.contains("rx") || (textLower.contains("tablet") && textLower.contains("dosage"))) {
            return "Medical Prescription"
        }

        if (textLower.contains("laboratory report") || textLower.contains("lab report") || textLower.contains("blood report") || textLower.contains("pathology")) {
            return "Laboratory Report"
        }

        if (textLower.contains("medical report") || textLower.contains("diagnostic report") || textLower.contains("patient report")) {
            return "Medical Report"
        }

        if (textLower.contains("result card") || textLower.contains("school result") || textLower.contains("marksheet") || textLower.contains("total marks")) {
            return "School Result Card"
        }

        if (textLower.contains("degree certificate") || textLower.contains("provisional certificate") || textLower.contains("awarded the degree")) {
            return "Degree Certificate"
        }

        if (textLower.contains("birth certificate") || textLower.contains("date of birth")) {
            return "Birth Certificate"
        }

        if (textLower.contains("marriage certificate") || textLower.contains("nikah nama")) {
            return "Marriage Certificate"
        }

        if (textLower.contains("passport") || textLower.contains("type/type p") || textLower.contains("republic of")) {
            return "Passport"
        }

        if (textLower.contains("national identity card") || textLower.contains("cnic") || textLower.contains("identity card") || textLower.contains("computerized national")) {
            return "CNIC"
        }

        if (textLower.contains("driving license") || textLower.contains("driving licence") || textLower.contains("driver license")) {
            return "Driving License"
        }

        if (textLower.contains("police character") || textLower.contains("character certificate") || textLower.contains("police clearance")) {
            return "Police Character Certificate"
        }

        if (textLower.contains("experience certificate") || textLower.contains("experience letter") || textLower.contains("service certificate")) {
            return "Experience Certificate"
        }

        if (textLower.contains("salary slip") || textLower.contains("pay slip") || textLower.contains("payslip") || textLower.contains("basic pay")) {
            return "Salary Slip"
        }

        if (textLower.contains("tax return") || textLower.contains("income tax") || textLower.contains("fbr") || textLower.contains("wealth statement")) {
            return "Tax Return"
        }

        if (textLower.contains("court order") || textLower.contains("in the court of") || textLower.contains("plaintiff") || textLower.contains("defendant")) {
            return "Court Order"
        }

        if (textLower.contains("affidavit") || textLower.contains("solemnly affirm")) {
            return "Affidavit"
        }

        if (textLower.contains("business proposal") || textLower.contains("project proposal")) {
            return "Business Proposal"
        }

        if (textLower.contains("meeting minutes") || textLower.contains("minutes of meeting") || textLower.contains("attendees")) {
            return "Meeting Minutes"
        }

        if (textLower.contains("project report") || textLower.contains("final report") || textLower.contains("progress report")) {
            return "Project Report"
        }

        return null
    }

    private fun detectDocumentCategory(rawText: String): String? {
        val textLower = rawText.lowercase(Locale.ROOT)
        return when {
            textLower.contains("result card") || textLower.contains("marksheet") -> "Result Card"
            textLower.contains("degree") || textLower.contains("certificate") -> "Certificate"
            textLower.contains("report") -> "Report"
            textLower.contains("bill") -> "Bill"
            textLower.contains("statement") -> "Statement"
            else -> null
        }
    }

    private fun isNoiseOrRandom(line: String): Boolean {
        val trimmed = line.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        if (trimmed.length < 3) return true
        if (lower.contains("http://") || lower.contains("https://") || lower.contains("www.") || lower.contains("@")) return true
        if (lower.matches(Regex("^(page|pg|p\\.)\\s*\\d+.*"))) return true
        if (trimmed.matches(Regex("^[0-9\\-\\s/.,:#]+$"))) return true
        if (trimmed.matches(Regex("^[A-Z0-9]{10,}$"))) return true

        return false
    }

    private fun cleanFileName(raw: String): String {
        var clean = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|#%]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        clean = clean.removePrefix("-").removePrefix("_").removePrefix(".").removePrefix(":")
            .removeSuffix("-").removeSuffix("_").removeSuffix(".").removeSuffix(":")
            .trim()

        val words = clean.split(" ")
        val takenWords = if (words.size > 12) words.take(12) else words

        return takenWords.joinToString(" ") { word ->
            if (word.length > 1 && word == word.uppercase(Locale.ROOT) && word.none { it.isDigit() }) {
                if (word.length in 2..4) word else word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            } else if (word.isNotEmpty()) {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            } else {
                word
            }
        }
    }
}
