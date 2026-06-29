package com.dps.evenup.feature.expenseflow.impl.expensesaved

import java.nio.charset.StandardCharsets

internal data class QrCodeMatrix(
    val size: Int,
    private val modules: BooleanArray,
) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    val darkModuleCount: Int = modules.count { module -> module }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QrCodeMatrix

        if (size != other.size) return false
        if (darkModuleCount != other.darkModuleCount) return false
        if (!modules.contentEquals(other.modules)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + darkModuleCount
        result = 31 * result + modules.contentHashCode()
        return result
    }
}

internal object QrCodeGenerator {
    fun encode(text: String): QrCodeMatrix? {
        if (text.isBlank()) {
            return null
        }

        val data = text.toByteArray(StandardCharsets.UTF_8)
        val spec = QrBlockSpec.versionForByteCount(data.size) ?: return null
        val dataCodewords = createDataCodewords(data, spec)
        val allCodewords = addErrorCorrectionAndInterleave(dataCodewords, spec)
        val dataBits = allCodewords.flatMap { codeword -> bitsOf(codeword, bitCount = 8) }

        val baseBuilder = QrMatrixBuilder(spec.version)
        baseBuilder.drawFunctionPatterns()
        baseBuilder.placeData(dataBits)

        val bestBuilder = (0..7)
            .map { mask ->
                baseBuilder.copy().apply {
                    applyMask(mask)
                    drawFormatBits(mask)
                    drawVersionBits()
                }
            }
            .minBy { builder -> builder.penaltyScore() }

        return bestBuilder.toQrCodeMatrix()
    }

    private fun createDataCodewords(data: ByteArray, spec: QrBlockSpec): IntArray {
        val bitBuffer = BitBuffer()
        bitBuffer.appendBits(0b0100, bitCount = 4)
        bitBuffer.appendBits(data.size, bitCount = if (spec.version <= 9) 8 else 16)
        data.forEach { byte -> bitBuffer.appendBits(byte.toInt() and 0xFF, bitCount = 8) }

        val dataCapacityBits = spec.totalDataCodewords * 8
        bitBuffer.appendBits(0, bitCount = minOf(4, dataCapacityBits - bitBuffer.size))
        bitBuffer.padToByte()

        val codewords = bitBuffer.toCodewords().toMutableList()
        var padIndex = 0
        while (codewords.size < spec.totalDataCodewords) {
            codewords += if (padIndex % 2 == 0) 0xEC else 0x11
            padIndex += 1
        }
        return codewords.toIntArray()
    }

    private fun addErrorCorrectionAndInterleave(
        dataCodewords: IntArray,
        spec: QrBlockSpec,
    ): IntArray {
        var offset = 0
        val blocks = spec.dataCodewordsPerBlock.map { blockSize ->
            val blockData = dataCodewords.copyOfRange(offset, offset + blockSize)
            offset += blockSize
            QrDataBlock(
                dataCodewords = blockData,
                errorCorrectionCodewords = ReedSolomon.computeRemainder(blockData, spec.errorCorrectionCodewordsPerBlock),
            )
        }

        val result = mutableListOf<Int>()
        val maxDataBlockSize = blocks.maxOf { block -> block.dataCodewords.size }
        for (index in 0 until maxDataBlockSize) {
            blocks.forEach { block ->
                if (index < block.dataCodewords.size) {
                    result += block.dataCodewords[index]
                }
            }
        }

        for (index in 0 until spec.errorCorrectionCodewordsPerBlock) {
            blocks.forEach { block ->
                result += block.errorCorrectionCodewords[index]
            }
        }

        return result.toIntArray()
    }
}

private data class QrBlockSpec(
    val version: Int,
    val errorCorrectionCodewordsPerBlock: Int,
    val dataCodewordsPerBlock: List<Int>,
) {
    val totalDataCodewords: Int = dataCodewordsPerBlock.sum()

    fun fitsByteCount(byteCount: Int): Boolean {
        val characterCountBits = if (version <= 9) 8 else 16
        return 4 + characterCountBits + (byteCount * 8) <= totalDataCodewords * 8
    }

    companion object {
        private val levelLowSpecs = listOf(
            QrBlockSpec(version = 1, errorCorrectionCodewordsPerBlock = 7, dataCodewordsPerBlock = listOf(19)),
            QrBlockSpec(version = 2, errorCorrectionCodewordsPerBlock = 10, dataCodewordsPerBlock = listOf(34)),
            QrBlockSpec(version = 3, errorCorrectionCodewordsPerBlock = 15, dataCodewordsPerBlock = listOf(55)),
            QrBlockSpec(version = 4, errorCorrectionCodewordsPerBlock = 20, dataCodewordsPerBlock = listOf(80)),
            QrBlockSpec(version = 5, errorCorrectionCodewordsPerBlock = 26, dataCodewordsPerBlock = listOf(108)),
            QrBlockSpec(version = 6, errorCorrectionCodewordsPerBlock = 18, dataCodewordsPerBlock = listOf(68, 68)),
            QrBlockSpec(version = 7, errorCorrectionCodewordsPerBlock = 20, dataCodewordsPerBlock = listOf(78, 78)),
            QrBlockSpec(version = 8, errorCorrectionCodewordsPerBlock = 24, dataCodewordsPerBlock = listOf(97, 97)),
            QrBlockSpec(version = 9, errorCorrectionCodewordsPerBlock = 30, dataCodewordsPerBlock = listOf(116, 116)),
            QrBlockSpec(version = 10, errorCorrectionCodewordsPerBlock = 18, dataCodewordsPerBlock = listOf(68, 68, 69, 69)),
        )

        fun versionForByteCount(byteCount: Int): QrBlockSpec? = levelLowSpecs.firstOrNull { spec ->
            spec.fitsByteCount(byteCount)
        }
    }
}

private data class QrDataBlock(
    val dataCodewords: IntArray,
    val errorCorrectionCodewords: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QrDataBlock

        if (!dataCodewords.contentEquals(other.dataCodewords)) return false
        if (!errorCorrectionCodewords.contentEquals(other.errorCorrectionCodewords)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dataCodewords.contentHashCode()
        result = 31 * result + errorCorrectionCodewords.contentHashCode()
        return result
    }
}

private class BitBuffer {
    private val bits = mutableListOf<Int>()

    val size: Int
        get() = bits.size

    fun appendBits(value: Int, bitCount: Int) {
        if (bitCount <= 0) {
            return
        }
        for (index in bitCount - 1 downTo 0) {
            bits += (value ushr index) and 1
        }
    }

    fun padToByte() {
        while (bits.size % 8 != 0) {
            bits += 0
        }
    }

    fun toCodewords(): List<Int> = bits.chunked(8).map { byteBits ->
        byteBits.fold(0) { value, bit -> (value shl 1) or bit }
    }
}

private class QrMatrixBuilder(
    private val version: Int,
    private val modules: Array<BooleanArray> = Array(17 + version * 4) { BooleanArray(17 + version * 4) },
    private val reserved: Array<BooleanArray> = Array(17 + version * 4) { BooleanArray(17 + version * 4) },
) {
    private val size: Int = 17 + version * 4

    fun copy(): QrMatrixBuilder {
        val modulesCopy = Array(size) { row -> modules[row].clone() }
        val reservedCopy = Array(size) { row -> reserved[row].clone() }
        return QrMatrixBuilder(version, modulesCopy, reservedCopy)
    }

    fun drawFunctionPatterns() {
        drawFinderPattern(left = 0, top = 0)
        drawFinderPattern(left = size - 7, top = 0)
        drawFinderPattern(left = 0, top = size - 7)
        drawAlignmentPatterns()
        drawTimingPatterns()
        reserveFormatBits()
        reserveVersionBits()
        setFunctionModule(8, 4 * version + 9, dark = true)
    }

    fun placeData(bits: List<Int>) {
        var bitIndex = 0
        var upward = true
        var column = size - 1
        while (column > 0) {
            if (column == 6) {
                column -= 1
            }

            for (rowOffset in 0 until size) {
                val row = if (upward) size - 1 - rowOffset else rowOffset
                for (columnOffset in 0..1) {
                    val x = column - columnOffset
                    if (!reserved[row][x]) {
                        modules[row][x] = bitIndex < bits.size && bits[bitIndex] == 1
                        bitIndex += 1
                    }
                }
            }

            upward = !upward
            column -= 2
        }
    }

    fun applyMask(mask: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!reserved[y][x] && maskApplies(mask, x, y)) {
                    modules[y][x] = !modules[y][x]
                }
            }
        }
    }

    fun drawFormatBits(mask: Int) {
        val formatBits = formatBits(errorCorrectionLevelLow = 1, mask)

        for (index in 0..5) {
            setFunctionModule(8, index, bit(formatBits, index))
        }
        setFunctionModule(8, 7, bit(formatBits, 6))
        setFunctionModule(8, 8, bit(formatBits, 7))
        setFunctionModule(7, 8, bit(formatBits, 8))
        for (index in 9..14) {
            setFunctionModule(14 - index, 8, bit(formatBits, index))
        }

        for (index in 0..7) {
            setFunctionModule(size - 1 - index, 8, bit(formatBits, index))
        }
        for (index in 8..14) {
            setFunctionModule(8, size - 15 + index, bit(formatBits, index))
        }
    }

    fun drawVersionBits() {
        if (version < 7) {
            return
        }

        val bits = versionBits(version)
        for (index in 0 until 18) {
            val dark = bit(bits, index)
            val a = size - 11 + index % 3
            val b = index / 3
            setFunctionModule(a, b, dark)
            setFunctionModule(b, a, dark)
        }
    }

    fun penaltyScore(): Int =
        penaltyRuns(horizontal = true) +
            penaltyRuns(horizontal = false) +
            penaltyBlocks() +
            penaltyFinderLikePatterns(horizontal = true) +
            penaltyFinderLikePatterns(horizontal = false) +
            penaltyDarkBalance()

    fun toQrCodeMatrix(): QrCodeMatrix {
        val flatModules = BooleanArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                flatModules[y * size + x] = modules[y][x]
            }
        }
        return QrCodeMatrix(size = size, modules = flatModules)
    }

    private fun drawFinderPattern(left: Int, top: Int) {
        for (dy in -1..7) {
            for (dx in -1..7) {
                val x = left + dx
                val y = top + dy
                if (x !in 0 until size || y !in 0 until size) {
                    continue
                }

                val dark = dx in 0..6 &&
                    dy in 0..6 &&
                    (dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx in 2..4 && dy in 2..4))
                setFunctionModule(x, y, dark)
            }
        }
    }

    private fun drawAlignmentPatterns() {
        val positions = alignmentPatternPositions(version)
        positions.forEach { centerY ->
            positions.forEach { centerX ->
                if (reserved[centerY][centerX]) {
                    return@forEach
                }
                drawAlignmentPattern(centerX, centerY)
            }
        }
    }

    private fun drawAlignmentPattern(centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val distance = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunctionModule(centerX + dx, centerY + dy, distance != 1)
            }
        }
    }

    private fun drawTimingPatterns() {
        for (index in 8 until size - 8) {
            val dark = index % 2 == 0
            setFunctionModule(index, 6, dark)
            setFunctionModule(6, index, dark)
        }
    }

    private fun reserveFormatBits() {
        for (index in 0..5) {
            reserveModule(8, index)
            reserveModule(index, 8)
        }
        reserveModule(8, 7)
        reserveModule(8, 8)
        reserveModule(7, 8)

        for (index in 0..7) {
            reserveModule(size - 1 - index, 8)
        }
        for (index in 0..6) {
            reserveModule(8, size - 1 - index)
        }
    }

    private fun reserveVersionBits() {
        if (version < 7) {
            return
        }

        for (index in 0 until 18) {
            val a = size - 11 + index % 3
            val b = index / 3
            reserveModule(a, b)
            reserveModule(b, a)
        }
    }

    private fun setFunctionModule(x: Int, y: Int, dark: Boolean) {
        modules[y][x] = dark
        reserved[y][x] = true
    }

    private fun reserveModule(x: Int, y: Int) {
        reserved[y][x] = true
    }

    private fun penaltyRuns(horizontal: Boolean): Int {
        var score = 0
        for (primary in 0 until size) {
            var runColor = false
            var runLength = 0
            for (secondary in 0 until size) {
                val dark = if (horizontal) modules[primary][secondary] else modules[secondary][primary]
                if (secondary == 0 || dark != runColor) {
                    if (runLength >= 5) {
                        score += 3 + runLength - 5
                    }
                    runColor = dark
                    runLength = 1
                } else {
                    runLength += 1
                }
            }
            if (runLength >= 5) {
                score += 3 + runLength - 5
            }
        }
        return score
    }

    private fun penaltyBlocks(): Int {
        var score = 0
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val dark = modules[y][x]
                if (modules[y][x + 1] == dark && modules[y + 1][x] == dark && modules[y + 1][x + 1] == dark) {
                    score += 3
                }
            }
        }
        return score
    }

    private fun penaltyFinderLikePatterns(horizontal: Boolean): Int {
        var score = 0
        val pattern = listOf(true, false, true, true, true, false, true, false, false, false, false)
        val inversePattern = pattern.map { value -> !value }
        for (primary in 0 until size) {
            for (secondary in 0..size - pattern.size) {
                val matchesPattern = pattern.indices.all { offset ->
                    val dark = if (horizontal) modules[primary][secondary + offset] else modules[secondary + offset][primary]
                    dark == pattern[offset]
                }
                val matchesInversePattern = inversePattern.indices.all { offset ->
                    val dark = if (horizontal) modules[primary][secondary + offset] else modules[secondary + offset][primary]
                    dark == inversePattern[offset]
                }
                if (matchesPattern || matchesInversePattern) {
                    score += 40
                }
            }
        }
        return score
    }

    private fun penaltyDarkBalance(): Int {
        val darkModules = modules.sumOf { row -> row.count { module -> module } }
        val totalModules = size * size
        val fivePercentSteps = kotlin.math.abs(darkModules * 20 - totalModules * 10) / totalModules
        return fivePercentSteps * 10
    }
}

private object ReedSolomon {
    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var value = 1
        for (index in 0 until 255) {
            exp[index] = value
            log[value] = index
            value = value shl 1
            if (value and 0x100 != 0) {
                value = value xor 0x11D
            }
        }
        for (index in 255 until exp.size) {
            exp[index] = exp[index - 255]
        }
    }

    fun computeRemainder(data: IntArray, degree: Int): IntArray {
        val divisor = computeDivisor(degree)
        val result = IntArray(degree)

        data.forEach { codeword ->
            val factor = codeword xor result.first()
            for (index in 0 until degree - 1) {
                result[index] = result[index + 1]
            }
            result[degree - 1] = 0

            for (index in divisor.indices) {
                result[index] = result[index] xor multiply(divisor[index], factor)
            }
        }

        return result
    }

    private fun computeDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (index in 0 until degree) {
            for (coefficient in result.indices) {
                result[coefficient] = multiply(result[coefficient], root)
                if (coefficient + 1 < result.size) {
                    result[coefficient] = result[coefficient] xor result[coefficient + 1]
                }
            }
            root = multiply(root, 0x02)
        }
        return result
    }

    private fun multiply(left: Int, right: Int): Int =
        if (left == 0 || right == 0) 0 else exp[log[left] + log[right]]
}

private fun bitsOf(value: Int, bitCount: Int): List<Int> = (bitCount - 1 downTo 0).map { index ->
    (value ushr index) and 1
}

private fun bit(value: Int, index: Int): Boolean = ((value ushr index) and 1) != 0

private fun maskApplies(mask: Int, x: Int, y: Int): Boolean = when (mask) {
    0 -> (x + y) % 2 == 0
    1 -> y % 2 == 0
    2 -> x % 3 == 0
    3 -> (x + y) % 3 == 0
    4 -> (x / 3 + y / 2) % 2 == 0
    5 -> ((x * y) % 2 + (x * y) % 3) == 0
    6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
    7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
    else -> error("Unsupported QR mask $mask")
}

private fun formatBits(errorCorrectionLevelLow: Int, mask: Int): Int {
    val data = (errorCorrectionLevelLow shl 3) or mask
    var remainder = data
    for (index in 0 until 10) {
        remainder = (remainder shl 1) xor if ((remainder ushr 9) != 0) 0x537 else 0
    }
    return ((data shl 10) or remainder) xor 0x5412
}

private fun versionBits(version: Int): Int {
    var remainder = version
    for (index in 0 until 12) {
        remainder = (remainder shl 1) xor if ((remainder ushr 11) != 0) 0x1F25 else 0
    }
    return (version shl 12) or remainder
}

private fun alignmentPatternPositions(version: Int): List<Int> = when (version) {
    1 -> emptyList()
    2 -> listOf(6, 18)
    3 -> listOf(6, 22)
    4 -> listOf(6, 26)
    5 -> listOf(6, 30)
    6 -> listOf(6, 34)
    7 -> listOf(6, 22, 38)
    8 -> listOf(6, 24, 42)
    9 -> listOf(6, 26, 46)
    10 -> listOf(6, 28, 50)
    else -> error("Unsupported QR version $version")
}
