package com.dpt.unpack.rebuild

import java.io.File

/**
 * Invokes the Android build-tools (zipalign + apksigner) with a generated
 * throw-away keystore. Produces a final, signed, aligned APK.
 */
object Signer {

    const val STORE_PASS = "dptunpack"
    const val KEY_PASS = "dptunpack"
    const val ALIAS = "dpt-dumper"

    fun findBuildTools(): File? {
        val candidates = listOf(
            System.getenv("DPT_BUILD_TOOLS"),
            System.getenv("ANDROID_HOME")?.let { File(it, "build-tools")?.let { b ->
                b.listFiles()?.sortedByDescending { it.name }?.firstOrNull { File(it, "lib/apksigner.jar").isFile }?.absolutePath
            } },
        ).filterNotNull()
        for (c in candidates) {
            val d = File(c)
            if (File(d, "lib/apksigner.jar").isFile) return d
        }
        return null
    }

    /** Resolve an executable name with the platform-appropriate suffix ("" vs ".exe"). */
    private fun bin(dir: File, name: String): File? {
        if (dir == null || !dir.isDirectory) return null
        val win = System.getProperty("os.name").lowercase().contains("win")
        val exe = if (win) ".exe" else ""
        val f = File(dir, name + exe)
        return if (f.isFile) f else null
    }

    fun align(buildTools: File, input: File, outputAlign: File): List<String> {
        val zipalign = bin(buildTools, "zipalign")
        if (zipalign == null) {
            // zipalign is optional (install quality only); apksigner.jar alone can sign.
            input.copyTo(outputAlign, overwrite = true)
            return listOf("    zipalign: not found (skipped - signing without alignment)")
        }
        runProcess(
            zipalign.absolutePath,
            "-f", "-v", "4", input.absolutePath, outputAlign.absolutePath,
        )
        return listOf("    zipalign: ${outputAlign.absolutePath}")
    }

    fun ensureKeystore(outDir: File): File {
        val ks = File(outDir, "dpt-dumper.jks")
        if (ks.isFile) return ks
        val keytool = bin(File(System.getProperty("java.home"), "bin"), "keytool")
            ?: throw IllegalStateException("keytool not found in ${System.getProperty("java.home")}/bin")
        runProcess(
            keytool.absolutePath,
            "-genkeypair", "-keystore", ks.absolutePath,
            "-storepass", STORE_PASS, "-keypass", KEY_PASS,
            "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=DPT-Dumper, OU=Tools, O=DPT-Dumper, L=NA, S=NA, C=NA",
            "-noprompt",
        )
        return ks
    }

    fun sign(buildTools: File, keystore: File, apk: File): List<String> {
        val java = bin(File(System.getProperty("java.home"), "bin"), "java")
            ?: throw IllegalStateException("java not found in ${System.getProperty("java.home")}/bin")
        val apksigner = File(buildTools, "lib/apksigner.jar").absolutePath
        runProcess(
            java.absolutePath, "-jar", apksigner,
            "sign", "--ks", keystore.absolutePath,
            "--ks-key-alias", ALIAS,
            "--ks-pass", "pass:$STORE_PASS",
            "--key-pass", "pass:$KEY_PASS",
            "--v1-signing-enabled", "true",
            "--v2-signing-enabled", "true",
            apk.absolutePath,
        )
        val out = runProcess(
            java.absolutePath, "-jar", apksigner,
            "verify", "--verbose", apk.absolutePath,
        )
        return out.lines().map { "    signer: $it" }.toMutableList()
    }

    /** Finds a tool executable (apksigner / zipalign) directly on $PATH. */
    fun findPathBinary(name: String): File? {
        val path = System.getenv("PATH") ?: return null
        val win = System.getProperty("os.name").lowercase().contains("win")
        val exts = if (win) listOf(".exe", ".bat", ".cmd", "") else listOf("")
        for (dir in path.split(File.pathSeparator)) {
            if (dir.isBlank()) continue
            for (e in exts) {
                val f = File(dir, name + e)
                if (f.isFile) return f
            }
        }
        return null
    }

    /** Signs with a plain `apksigner` binary found on $PATH (Termux `apt install apksigner`). */
    fun signWithPathBinary(apksigner: File, keystore: File, apk: File): List<String> {
        runProcess(
            apksigner.absolutePath,
            "sign", "--ks", keystore.absolutePath,
            "--ks-key-alias", ALIAS,
            "--ks-pass", "pass:$STORE_PASS",
            "--key-pass", "pass:$KEY_PASS",
            "--v1-signing-enabled", "true",
            "--v2-signing-enabled", "true",
            apk.absolutePath,
        )
        val out = runProcess(apksigner.absolutePath, "verify", "--verbose", apk.absolutePath)
        return out.lines().map { "    signer: $it" }.toMutableList()
    }

    /** 4-alignment via zipalign found on $PATH (best effort). */
    fun alignWithPathBinary(zipalign: File?, input: File, outputAlign: File): List<String> {
        if (zipalign == null) {
            input.copyTo(outputAlign, overwrite = true)
            return listOf("    zipalign: not on PATH (skipped - signing without alignment)")
        }
        runProcess(zipalign.absolutePath, "-f", "-v", "4", input.absolutePath, outputAlign.absolutePath)
        return listOf("    zipalign: ${outputAlign.absolutePath}")
    }

    private fun runProcess(vararg cmd: String): String {
        val pb = ProcessBuilder(*cmd)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val output = proc.inputStream.readBytes().toString(Charsets.UTF_8)
        val code = proc.waitFor()
        if (code != 0) {
            throw IllegalStateException("command failed (exit $code): ${cmd.joinToString(" ")}\n$output")
        }
        return output
    }
}