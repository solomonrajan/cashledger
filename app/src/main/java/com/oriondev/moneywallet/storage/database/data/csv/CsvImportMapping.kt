package com.oriondev.moneywallet.storage.database.data.csv

import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVWriter
import com.opencsv.ICSVParser
import com.oriondev.moneywallet.storage.database.data.Constants
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.Serializable
import java.io.StringWriter
import java.math.BigDecimal
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * How to read a CSV file this app did not write: which column holds each field, and how the
 * dates and amounts in it are written. Plain Java, so every helper here runs in a JVM test.
 */
class CsvImportMapping : Serializable {

    @JvmField
    var separator: Char = ','

    @JvmField
    var date: Int = NONE

    @JvmField
    var amount: Int = NONE

    @JvmField
    var description: Int = NONE

    @JvmField
    var note: Int = NONE

    @JvmField
    var category: Int = NONE

    @JvmField
    var datePattern: String? = null

    @JvmField
    var decimalComma: Boolean = false

    @JvmField
    var spendingPositive: Boolean = false

    @JvmField
    var walletId: Long = 0L

    /** The first line of a file, as read to decide how to import it. */
    class Header internal constructor(
        @JvmField val separator: Char,
        @JvmField val cells: Array<String>?,
        @JvmField val nativeHeader: Boolean
    )

    /**
     * The settings worth remembering for a header. The wallet is left out on purpose. Wallet row
     * ids are handed out again when a backup is restored, so a remembered id could quietly name a
     * different wallet from the one first picked.
     */
    fun encode(): String {
        return writeLine(
            arrayOf(
                date.toString(),
                amount.toString(),
                description.toString(),
                note.toString(),
                category.toString(),
                datePattern ?: "",
                decimalComma.toString(),
                spendingPositive.toString()
            )
        )
    }

    companion object {
        const val NONE: Int = -1

        @JvmField
        val DATE_PATTERNS = arrayOf(
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd/MM/yy",
            "MM/dd/yyyy",
            "MM/dd/yy",
            "dd.MM.yyyy",
            "dd.MM.yy"
        )

        /** The digits each entry of [DATE_PATTERNS] allows, in the same order. */
        private val DATE_DIGITS = arrayOf(
            "\\d{4}-\\d{1,2}-\\d{1,2}",
            "\\d{1,2}/\\d{1,2}/\\d{4}",
            "\\d{1,2}/\\d{1,2}/\\d{2}",
            "\\d{1,2}/\\d{1,2}/\\d{4}",
            "\\d{1,2}/\\d{1,2}/\\d{2}",
            "\\d{1,2}\\.\\d{1,2}\\.\\d{4}",
            "\\d{1,2}\\.\\d{1,2}\\.\\d{2}"
        )

        private const val TIME_DIGITS = "( \\d{1,2}:\\d{2}(:\\d{2})?)?"

        private const val SPACE = "[ \\u00A0\\u202F]"

        // a grouped amount never starts with 0, so 0,500 is refused and not read as 500
        private val DOT_AMOUNT = Pattern.compile(
            "^[+-]?(\\d+|[1-9]\\d{0,2}(,\\d{3})+|[1-9]\\d{0,2}($SPACE\\d{3})+)(\\.\\d+)?$"
        )
        private val COMMA_AMOUNT = Pattern.compile(
            "^[+-]?(\\d+|[1-9]\\d{0,2}(\\.\\d{3})+|[1-9]\\d{0,2}($SPACE\\d{3})+)(,\\d+)?$"
        )

        private val LEADING_SYMBOL = Pattern.compile("^([+-]?)\\p{Sc}$SPACE?(.*)$")
        private val TRAILING_SYMBOL = Pattern.compile("^(.*?)$SPACE?\\p{Sc}$")

        private val NATIVE_COLUMNS = arrayOf(
            Constants.COLUMN_WALLET,
            Constants.COLUMN_CURRENCY,
            Constants.COLUMN_CATEGORY,
            Constants.COLUMN_DATETIME,
            Constants.COLUMN_MONEY
        )

        /**
         * The separator a header line uses: whichever of comma, semicolon and tab appears most often
         * outside double quotes. A tie, or none of them at all, is a comma.
         */
        @JvmStatic
        fun detectSeparator(headerLine: String): Char {
            var commas = 0
            var semicolons = 0
            var tabs = 0
            var quoted = false
            for (i in 0 until headerLine.length) {
                val c = headerLine[i]
                if (c == '"') {
                    quoted = !quoted
                } else if (!quoted) {
                    if (c == ',') {
                        commas++
                    } else if (c == ';') {
                        semicolons++
                    } else if (c == '\t') {
                        tabs++
                    }
                }
            }
            if (semicolons > commas && semicolons > tabs) {
                return ';'
            }
            if (tabs > commas && tabs > semicolons) {
                return '\t'
            }
            return ','
        }

        /**
         * Reads the header of a file through the same opening the importer uses, so a byte order mark
         * is dropped here exactly as it is there, and a file with no characters in it is refused with
         * the same message.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun readHeader(file: File): Header {
            BufferedReader(CSVDataImporter.openFile(file)).use { reader ->
                val line = reader.readLine()
                val separator = detectSeparator(line)
                val cells: Array<String>?
                try {
                    cells = parser(separator).parseLine(line)
                } catch (e: IOException) {
                    throw IOException(
                        "Line 1: a quote in this row is not closed on the same line, and every row has to fit on one line",
                        e
                    )
                }
                val nativeHeader: Boolean
                try {
                    nativeHeader = isNativeHeader(line)
                } catch (notReadableThatWay: IOException) {
                    nativeHeader = false
                }
                return Header(separator, cells, nativeHeader)
            }
        }

        /**
         * The parser for the header and the rows of a mapped file. A backslash is an ordinary
         * character here, as CSV escapes a quote by doubling it. With the opencsv default a backslash
         * is dropped, and one just before a closing quote joins the rows that follow into one.
         */
        @JvmStatic
        fun parser(separator: Char): CSVParser {
            return CSVParserBuilder()
                .withSeparator(separator)
                .withEscapeChar(ICSVParser.NULL_CHARACTER)
                .build()
        }

        /**
         * Whether a header line names every column this app's own import needs, read with a comma
         * the way that import reads it. Such a file goes through that import untouched.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun isNativeHeader(headerLine: String): Boolean {
            val cells = CSVParserBuilder().build().parseLine(headerLine)?.toList() ?: return false
            return cells.containsAll(NATIVE_COLUMNS.toList())
        }

        /**
         * What a remembered mapping is stored under. Each cell is trimmed and lowercased, then the
         * cells are written as one quoted CSV line, so a cell holding a comma or a quote cannot make
         * two different headers read the same.
         */
        @JvmStatic
        fun signature(headers: Array<String?>): String {
            val normalized = Array(headers.size) { i ->
                val h = headers[i]
                h?.trim()?.lowercase(Locale.ROOT) ?: ""
            }
            return writeLine(normalized)
        }

        /** The settings [encode] wrote, or null when the text is not something it wrote. */
        @JvmStatic
        fun decode(encoded: String?): CsvImportMapping? {
            if (encoded == null) {
                return null
            }
            return try {
                val cells = CSVParserBuilder().build().parseLine(encoded)
                if (cells == null || cells.size != 8) {
                    return null
                }
                val mapping = CsvImportMapping()
                mapping.date = cells[0].toInt()
                mapping.amount = cells[1].toInt()
                mapping.description = cells[2].toInt()
                mapping.note = cells[3].toInt()
                mapping.category = cells[4].toInt()
                mapping.datePattern = cells[5]
                if (mapping.date < 0 || mapping.amount < 0 || mapping.description < NONE ||
                    mapping.note < NONE || mapping.category < NONE ||
                    !DATE_PATTERNS.contains(mapping.datePattern)
                ) {
                    return null
                }
                mapping.decimalComma = parseBoolean(cells[6])
                mapping.spendingPositive = parseBoolean(cells[7])
                mapping
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                null
            }
        }

        private fun parseBoolean(value: String): Boolean {
            if ("true" == value) {
                return true
            }
            if ("false" == value) {
                return false
            }
            throw IllegalArgumentException(value)
        }

        private fun writeLine(cells: Array<String>): String {
            val out = StringWriter()
            try {
                CSVWriter(out, ',', '"', '"', "").use { writer ->
                    writer.writeNext(cells, true)
                }
            } catch (e: IOException) {
                throw IllegalStateException(e)
            }
            return out.toString()
        }

        /**
         * An amount, read strictly. Grouping is accepted only in threes, because a loose read of
         * "12,50" in a file that uses a dot for decimals drops the comma and imports 1250, and nothing
         * shows the amount for review before it is saved. A space groups exactly where the chosen
         * style's mark would, and one amount groups with one or the other, never both. Anything that
         * is not clearly one number in the chosen style is refused.
         *
         * Bank exports also write a negative in parentheses, as (12.50), or with a trailing minus, as
         * 12.50-, and put one currency symbol before or after the number, with at most one space
         * between. Each of these is recognized as a whole form and read for what it means, never
         * stripped as loose characters, because a parenthesized negative that lost its parentheses
         * would import as income. A negative written that way cannot also carry its own sign, since
         * (-5) or -5- has no one clear reading.
         */
        @JvmStatic
        fun parseAmount(cell: String, decimalComma: Boolean): BigDecimal {
            var number = cell
            var negative = false
            if (number.length >= 2 && number.startsWith("(") && number.endsWith(")")) {
                negative = true
                number = number.substring(1, number.length - 1)
            } else if (number.endsWith("-")) {
                negative = true
                number = number.substring(0, number.length - 1)
            }
            var symbol = LEADING_SYMBOL.matcher(number)
            if (symbol.matches()) {
                val g1 = symbol.group(1) ?: ""
                val g2 = symbol.group(2) ?: ""
                number = g1 + g2
            } else {
                symbol = TRAILING_SYMBOL.matcher(number)
                if (symbol.matches()) {
                    number = symbol.group(1) ?: ""
                }
            }
            var amount: BigDecimal? = null
            if (!(negative && (number.startsWith("+") || number.startsWith("-")))) {
                val compact = number.replace(Regex(SPACE), "")
                if (decimalComma && COMMA_AMOUNT.matcher(number).matches()) {
                    amount = BigDecimal(compact.replace(".", "").replace(',', '.'))
                } else if (!decimalComma && DOT_AMOUNT.matcher(number).matches()) {
                    amount = BigDecimal(compact.replace(",", ""))
                }
            }
            if (amount == null) {
                throw RuntimeException(
                    "the amount \"$cell\" is not a number written as " +
                        if (decimalComma) "1.234,56" else "1,234.56"
                )
            }
            return if (negative) amount.negate() else amount
        }

        /**
         * A date in one of [DATE_PATTERNS], alone or followed by a space and a time with or
         * without seconds, read strictly. The digit counts are checked first because the parse on
         * its own accepts a two digit year where four are asked for, and a lenient parse rolls a day
         * past the end of its month into the next one. A two digit year takes its century from
         * SimpleDateFormat's own window, which places it within 80 years before today and 20 after.
         */
        @JvmStatic
        fun parseDate(cell: String, datePattern: String): Date {
            val index = DATE_PATTERNS.indexOf(datePattern)
            if (index < 0) {
                throw IllegalArgumentException("unknown date format $datePattern")
            }
            if (cell.matches(Regex(DATE_DIGITS[index] + TIME_DIGITS))) {
                var pattern = datePattern
                val colons = cell.length - cell.replace(":", "").length
                if (colons == 1) {
                    pattern += " HH:mm"
                } else if (colons == 2) {
                    pattern += " HH:mm:ss"
                }
                val format = SimpleDateFormat(pattern, Locale.ENGLISH)
                format.isLenient = false
                val position = ParsePosition(0)
                val parsed = format.parse(cell, position)
                if (parsed != null && position.index == cell.length) {
                    return parsed
                }
            }
            throw RuntimeException(
                "the date \"$cell\" is not a real date written as $datePattern, with or without a time"
            )
        }
    }
}
