package com.sfdex.gmbioreactor.data.root

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object SafeBase64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else -1
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else -1

            val c0 = b0 ushr 2
            val c1 = ((b0 and 0x03) shl 4) or (if (b1 >= 0) (b1 ushr 4) else 0)
            val c2 = if (b1 >= 0) (((b1 and 0x0F) shl 2) or (if (b2 >= 0) (b2 ushr 6) else 0)) else -1
            val c3 = if (b2 >= 0) (b2 and 0x3F) else -1

            sb.append(ALPHABET[c0])
            sb.append(ALPHABET[c1])
            sb.append(if (c2 >= 0) ALPHABET[c2] else '=')
            sb.append(if (c3 >= 0) ALPHABET[c3] else '=')

            i += 3
        }
        return sb.toString()
    }
}

object RootEngine {

    const val CONFIG_DIR = "/data/adb/gmbioreactor"
    const val CONFIG_FILE = "$CONFIG_DIR/config.json"
    const val CONFIG_TMP_FILE = "$CONFIG_DIR/config.json.tmp"

    val MODULE_DIRS = listOf(
        "/data/adb/modules/s26ultra_ithome",
        "/data/adb/modules/s26spoof",
        "/data/adb/modules/gmbioreactor"
    )

    private var cachedRootAvailable: Boolean? = null

    /**
     * Executes a command via su shell.
     */
    fun executeSu(command: String, timeoutSeconds: Long = 10): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutThread = Thread {
                try {
                    process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (stdoutBuilder.isNotEmpty()) stdoutBuilder.append("\n")
                            stdoutBuilder.append(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            val stderrThread = Thread {
                try {
                    process.errorStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (stderrBuilder.isNotEmpty()) stderrBuilder.append("\n")
                            stderrBuilder.append(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            stdoutThread.start()
            stderrThread.start()

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ShellResult(-1, stdoutBuilder.toString(), "Execution timed out after $timeoutSeconds seconds")
            }

            stdoutThread.join(1000)
            stderrThread.join(1000)

            ShellResult(process.exitValue(), stdoutBuilder.toString(), stderrBuilder.toString())
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Failed to execute su command")
        }
    }

    /**
     * Checks if Root (su) permission is available and working.
     */
    fun isRootAvailable(forceCheck: Boolean = false): Boolean {
        if (!forceCheck && cachedRootAvailable != null) {
            return cachedRootAvailable!!
        }

        val result = executeSu("id")
        val isRoot = result.isSuccess && (result.stdout.contains("uid=0") || result.stdout.contains("root"))
        cachedRootAvailable = isRoot
        return isRoot
    }

    /**
     * Resets the cached root availability state.
     */
    fun resetRootCache() {
        cachedRootAvailable = null
    }

    /**
     * Encodes string into Base64 for safe shell passing.
     */
    fun encodeBase64(content: String): String {
        return SafeBase64.encode(content.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Atomically writes configuration JSON into /data/adb/gmbioreactor/config.json.
     * Uses a temporary file, sets chmod 0644, and performs atomic mv.
     */
    fun writeConfigFile(jsonContent: String): Boolean {
        val base64Data = encodeBase64(jsonContent)
        val command = "mkdir -p $CONFIG_DIR && echo '$base64Data' | base64 -d > $CONFIG_TMP_FILE && chmod 0644 $CONFIG_TMP_FILE && mv -f $CONFIG_TMP_FILE $CONFIG_FILE && chmod 0644 $CONFIG_FILE"
        val result = executeSu(command)
        return result.isSuccess
    }

    /**
     * Reads configuration JSON from /data/adb/gmbioreactor/config.json.
     */
    fun readConfigFile(): String? {
        val result = executeSu("cat $CONFIG_FILE 2>/dev/null")
        if (result.isSuccess && result.stdout.isNotBlank()) {
            return result.stdout
        }
        return null
    }

    /**
     * Force stops an application via am force-stop.
     */
    fun forceStopApp(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val sanitizedPkg = packageName.trim().replace("'", "").replace(";", "").replace(" ", "")
        val result = executeSu("am force-stop '$sanitizedPkg'")
        return result.isSuccess
    }

    /**
     * Force stops multiple applications via am force-stop.
     */
    fun forceStopApps(packages: List<String>): Boolean {
        if (packages.isEmpty()) return true
        val validPkgs = packages.map { it.trim().replace("'", "").replace(";", "").replace(" ", "") }
            .filter { it.isNotEmpty() }
        if (validPkgs.isEmpty()) return true

        val commands = validPkgs.joinToString("; ") { "am force-stop '$it'" }
        val result = executeSu(commands)
        return result.isSuccess
    }

    /**
     * Checks whether the Zygisk module directory exists in /data/adb/modules/.
     */
    fun isZygiskModuleInstalled(): Boolean {
        val checkConditions = MODULE_DIRS.joinToString(" || ") { "[ -d '$it' ]" }
        val command = "$checkConditions"
        val result = executeSu(command)
        return result.isSuccess
    }
}
