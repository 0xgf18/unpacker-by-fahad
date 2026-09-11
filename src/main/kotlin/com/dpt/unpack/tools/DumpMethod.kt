package com.dpt.unpack.tools

import com.dpt.unpack.dex.DexParser
import java.io.File

/**
 * Debug aid: resolve class+method by name in a dex and print its raw insns.
 * usage: DumpMethod <dex> <Lcls/Name;> <methodName> [payload|patched|truth]
 */
object DumpMethod {
    fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

    fun readUleb(d: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var c = start
        do {
            val b = d[c].toInt() and 0xFF
            c++
            result = result or ((b and 0x7F).toLong() shl shift)
            shift += 7
            if (b and 0x80 == 0) break
        } while (true)
        return result to c
    }

    fun strings(d: ByteArray): List<String> {
        val sidOff = u32(d, 0x3C).toInt(); val sidSize = u32(d, 0x38).toInt()
        val out = ArrayList<String>(sidSize)
        for (i in 0 until sidSize) {
            val off = u32(d, sidOff + i * 4).toInt()
            var p = readUleb(d, off).second
            p = readUleb(d, p).second
            val sb = StringBuilder()
            while (true) {
                val v = d[p].toInt() and 0xFF
                if (v == 0) break
                when {
                    v < 0x80 -> { sb.append(v.toChar()); p++ }
                    v in 0xC0..0xDF -> { sb.append((((v and 0x1F) shl 6) or (d[p + 1].toInt() and 0x3F)).toChar()); p += 2 }
                    v in 0xE0..0xEF -> { sb.append((((v and 0x0F) shl 12) or ((d[p + 1].toInt() and 0x3F) shl 6) or (d[p + 2].toInt() and 0x3F)).toChar()); p += 3 }
                    else -> p++
                }
            }
            out.add(sb.toString())
        }
        return out
    }

    fun types(d: ByteArray): List<String> {
        val st = strings(d)
        val tSize = u32(d, 0x40).toInt(); val tOff = u32(d, 0x44).toInt()
        return (0 until tSize).map { st[u32(d, tOff + it * 4).toInt()] }
    }

    fun findMethodIdx(d: ByteArray, cls: String, name: String): Int? {
        val st = strings(d); val ty = types(d)
        val clsIdx = ty.indexOf(cls)
        if (clsIdx < 0) return null
        val mOff = u32(d, 0x5C).toInt(); val mSize = u32(d, 0x58).toInt()
        for (i in 0 until mSize) {
            val cIdx = u32(d, mOff + i * 8).toInt()
            val nIdx = u32(d, mOff + i * 8 + 4).toInt()
            if (cIdx == clsIdx && st[nIdx] == name) return i
        }
        return null
    }

    fun dump(dex: ByteArray, cls: String, name: String): String? {
        val midx = findMethodIdx(dex, cls, name) ?: return null
        val methods = DexParser.parseMethods(dex)
        val m = methods.firstOrNull { it.methodIdx.toInt() == midx } ?: return null
        val co = m.codeOff.toInt()
        val insns = m.insnsByteSize
        val sb = StringBuilder()
        sb.append(String.format("idx=%d codeOff=%d insnsSize=%d regs=%d outs=%d\n", midx, co, insns, u16(dex, co), u16(dex, co + 4)))
        val end = minOf(co + 16 + insns, co + 16 + 96)
        for (j in co + 16 until end) sb.append(String.format("%02x ", dex[j]))
        return sb.toString().trim()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val dex = File(args[0]).readBytes()
        if (args.size > 3 && args[3] == "counts") {
            val st = strings(dex)
            val ty = types(dex)
            println("strings=${st.size} types=${ty.size} methods=${u32(dex, 0x58)} classes=${u32(dex, 0x60)}")
            println("first strings: " + st.take(24))
            val cls = args.getOrElse(1) { "?" }
            println("has class '$cls': " + ty.contains(cls))
            if (args.size > 4 && args[4] == "hex") {
                val sidOff = u32(dex, 0x3C).toInt(); val sidSize = u32(dex, 0x38).toInt()
                println("string_ids_off=$sidOff size=$sidSize")
                for (i in 0 until 3) {
                    val a = u32(dex, sidOff + i * 4).toInt()
                    val sb = StringBuilder()
                    for (j in 0 until 48) if (a + j < dex.size) sb.append(String.format("%02x ", dex[a + j]))
                    println("str[$i] at $a: " + sb)
                }
            }
            return
        }
        val cls = args[1]; val name = args[2]
        println(args.getOrElse(3) { "?" } + ": " + dump(dex, cls, name))
    }
}