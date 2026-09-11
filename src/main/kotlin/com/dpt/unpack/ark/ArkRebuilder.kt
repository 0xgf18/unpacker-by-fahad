package com.dpt.unpack.ark

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Rebuilds an unpacked 360-Jiagu APK:
 *  - drops the shell's stub classes*.dex (the only thing that shipped);
 *  - installs the recovered payload dexes;
 *  - drops old signing blocks;
 *  - writes the manifest with the real <application android:name>;
 *  - transparently keeps every resource / asset / native lib (shell remnants
 *    such as assets/libjiagu_mips.a are harmless once the stub dex is gone).
 */
object ArkRebuilder {

    private val DEX_NAME = Regex("classes\\d+\\.dex")

    fun rebuild(apk: File, patchedDir: File, manifest: ByteArray, out: File) {
        out.parentFile?.mkdirs()
        val dropSig = { name: String ->
            name.startsWith("META-INF/") && (name.endsWith(".MF") ||
                name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA") ||
                name.endsWith(".EC") || name.contains("CERT"))
        }

        ZipFile(apk).use { zip ->
            ZipOutputStream(out.outputStream()).use { zos ->
                zos.setLevel(9)

                putDeflated(zos, "AndroidManifest.xml", manifest)

                val entries = zip.entries().toList()
                for (e in entries) {
                    val name = e.name
                    if (name == "AndroidManifest.xml") continue
                    if (name == "META-INF/MANIFEST.MF") continue
                    if (name.startsWith("classes") && name.endsWith(".dex")) continue // old shell stub
                    if (dropSig(name)) continue
                    val bytes = zip.getInputStream(e).readBytes()
                    if (mustStore(name)) {
                        putStored(zos, name, bytes)
                    } else {
                        putDeflated(zos, name, bytes)
                    }
                }

                val dexFiles = patchedDir.listFiles { f ->
                    f.isFile && (f.name == "classes.dex" || f.name.matches(DEX_NAME))
                }?.sortedBy { f ->
                    if (f.name == "classes.dex") 0
                    else f.name.removePrefix("classes").removeSuffix(".dex").toInt()
                } ?: emptyList()
                for (f in dexFiles) {
                    putDeflated(zos, f.name, f.readBytes())
                }
            }
        }
    }

    private fun mustStore(name: String): Boolean =
        name == "resources.arsc" ||
            name.startsWith("res/raw/") ||
            name.startsWith("assets/") ||
            (name.startsWith("META-INF/") && name.endsWith(".version"))

    private fun putStored(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        entry.time = 0L
        entry.method = ZipEntry.STORED
        val crc = CRC32()
        crc.update(bytes)
        entry.size = bytes.size.toLong()
        entry.compressedSize = bytes.size.toLong()
        entry.crc = crc.value
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun putDeflated(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        entry.time = 0L
        entry.method = ZipEntry.DEFLATED
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }
}