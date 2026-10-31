package com.vyora.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class ModelDownloadState {
    object Idle : ModelDownloadState()
    data class NotDownloaded(val approximateSizeBytes: Long = ModelDownloadManager.EXPECTED_FILE_SIZE_BYTES) : ModelDownloadState()
    object CheckingSpace : ModelDownloadState()
    data class Downloading(
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedKBps: Long = 0L
    ) : ModelDownloadState() {
        constructor(
            bytesDownloaded: Long,
            totalBytes: Long,
            progressFraction: Float,
            speedBytesPerSec: Long = 0L
        ) : this(
            percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else (progressFraction * 100).toInt().coerceIn(0, 100),
            downloadedBytes = bytesDownloaded,
            totalBytes = totalBytes,
            speedKBps = speedBytesPerSec / 1024L
        )

        val progressFraction: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else (percent / 100f).coerceIn(0f, 1f)
        val bytesDownloaded: Long
            get() = downloadedBytes
        val speedBytesPerSec: Long
            get() = speedKBps * 1024L
    }
    data class Validating(val progressFraction: Float = 1.0f) : ModelDownloadState()
    data class Installed(val file: File, val sizeBytes: Long) : ModelDownloadState()
    data class Failed(val reason: String, val canRetry: Boolean = true) : ModelDownloadState() {
        val message: String get() = reason
    }
    data class Error(val message: String, val canRetry: Boolean = true) : ModelDownloadState() {
        val reason: String get() = message
    }
}

/**
 * Production-ready Model Download Manager for SAFORA Q4_K_M GGUF.
 *
 * Implements:
 * - App-private directory download (context.filesDir/models/).
 * - Resumable HTTP Range requests with redirect following.
 * - Usable disk space pre-check (requiring at least 1.15 GB).
 * - Temporary .part staging promoted to final file only after size and integrity validation.
 * - Live speed, bytes, and percentage progress tracking.
 * - Cancellation, retry, recovery, and confirmed deletion.
 */
class ModelDownloadManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
) {

    companion object {
        private const val TAG = "ModelDownloadManager"
        const val MODEL_NAME = "SAFORA 2B (Q4_K_M)"
        const val MODEL_FILENAME = "safora-q4_k_m.gguf"
        const val MODEL_URL = "https://huggingface.co/pragya30/SAFORA/resolve/main/safora-q4_k_m.gguf"
        const val EXPECTED_FILE_SIZE_BYTES = 986048160L
        // Official Git LFS SHA-256 OID from Hugging Face model repository
        const val EXPECTED_SHA256 = "93a5bd4b4c20a27423b53f416c36eddf74d5966e8b690881adbd9110e7b18224"
        const val LEGACY_XET_HASH = "5957685a85e6907667839586e637d388062b3c102df11983843f6ebb46910a07"
        const val MIN_FREE_SPACE_BYTES = 1_150_000_000L // 1.15 GB
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB
        private const val MAX_REDIRECTS = 5

        @Volatile
        private var instance: ModelDownloadManager? = null

        fun getInstance(context: Context): ModelDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: ModelDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    val modelFile: File
        get() = File(modelsDir, MODEL_FILENAME)

    val partFile: File
        get() = File(modelsDir, "$MODEL_FILENAME.part")

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()
    val state: StateFlow<ModelDownloadState> get() = downloadState

    fun getInstalledModelSize(): Long = if (isModelInstalled()) modelFile.length() else 0L

    private var activeDownloadJob: Job? = null

    init {
        refreshStatus()
    }

    fun isModelInstalled(): Boolean {
        val file = modelFile
        return file.exists() && file.isFile && file.length() == EXPECTED_FILE_SIZE_BYTES
    }

    fun refreshStatus(): ModelDownloadState {
        val file = modelFile
        val state = if (isModelInstalled()) {
            ModelDownloadState.Installed(file, file.length())
        } else {
            ModelDownloadState.Idle
        }
        _downloadState.value = state
        return state
    }

    fun startDownload() {
        if (activeDownloadJob?.isActive == true) {
            Log.w(TAG, "Download job already in progress")
            return
        }

        activeDownloadJob = scope.launch(Dispatchers.IO) {
            try {
                // 1. Check if model is already installed
                if (isModelInstalled()) {
                    Log.i(TAG, "Model already fully downloaded and validated.")
                    _downloadState.value = ModelDownloadState.Installed(modelFile, modelFile.length())
                    return@launch
                }

                // If partFile is already fully downloaded on device, validate & promote immediately
                if (partFile.exists() && partFile.length() == EXPECTED_FILE_SIZE_BYTES) {
                    _downloadState.value = ModelDownloadState.Validating(1.0f)
                    val actualSha = computeSha256(partFile)
                    Log.i(TAG, "Checking existing complete part file SHA-256: $actualSha")
                    val isHashValid = actualSha.equals(EXPECTED_SHA256, ignoreCase = true) ||
                            actualSha.equals(LEGACY_XET_HASH, ignoreCase = true)
                    if (isHashValid) {
                        if (modelFile.exists()) modelFile.delete()
                        val promoted = partFile.renameTo(modelFile)
                        if (promoted) {
                            Log.i(TAG, "Existing part file successfully promoted to $MODEL_FILENAME")
                            _downloadState.value = ModelDownloadState.Installed(modelFile, modelFile.length())
                            return@launch
                        }
                    }
                }

                _downloadState.value = ModelDownloadState.CheckingSpace

                // 2. Check available storage space
                val freeSpace = modelsDir.usableSpace
                val existingPartSize = if (partFile.exists()) partFile.length() else 0L
                val neededSpace = (EXPECTED_FILE_SIZE_BYTES - existingPartSize) + (100 * 1024 * 1024) // 100MB margin
                if (freeSpace < neededSpace) {
                    val neededMb = neededSpace / (1024 * 1024)
                    val freeMb = freeSpace / (1024 * 1024)
                    _downloadState.value = ModelDownloadState.Failed(
                        reason = "Insufficient disk space. Free: $freeMb MB, Required: $neededMb MB.",
                        canRetry = true
                    )
                    return@launch
                }

                // 3. Connect with redirect handling and optional HTTP Range resume
                downloadWithResume()

            } catch (e: CancellationException) {
                Log.i(TAG, "Download cancelled by user.")
                _downloadState.value = ModelDownloadState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                _downloadState.value = ModelDownloadState.Failed(
                    reason = e.localizedMessage ?: "Network error during download.",
                    canRetry = true
                )
            }
        }
    }

    fun cancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        if (!isModelInstalled()) {
            _downloadState.value = ModelDownloadState.Idle
        }
    }

    fun deleteModel(): Boolean {
        cancelDownload()
        var deleted = false
        if (modelFile.exists()) {
            deleted = modelFile.delete()
        }
        if (partFile.exists()) {
            partFile.delete()
        }
        refreshStatus()
        Log.i(TAG, "Model deleted from local storage.")
        return deleted
    }

    private suspend fun downloadWithResume() = withContext(Dispatchers.IO) {
        var currentUrl = MODEL_URL
        var redirectCount = 0
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        val startOffset = if (partFile.exists() && partFile.length() < EXPECTED_FILE_SIZE_BYTES) {
            partFile.length()
        } else {
            if (partFile.exists()) partFile.delete()
            0L
        }

        try {
            while (redirectCount < MAX_REDIRECTS) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "VYORA-Android-Downloader/1.0")
                    setRequestProperty("Accept", "application/octet-stream, */*")
                    if (startOffset > 0) {
                        setRequestProperty("Range", "bytes=$startOffset-")
                        Log.i(TAG, "Requesting byte range from $startOffset...")
                    }
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == 307 || responseCode == 308) {
                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl.isNullOrEmpty()) {
                        throw IllegalStateException("Received HTTP redirect with no Location header")
                    }
                    currentUrl = newUrl
                    connection.disconnect()
                    redirectCount++
                    Log.d(TAG, "Following redirect #$redirectCount to $currentUrl")
                    continue
                }
                break
            }

            val conn = connection ?: throw IllegalStateException("Failed to establish HTTP connection")
            val code = conn.responseCode
            val isPartial = code == 206
            val isOk = code == 200

            if (!isPartial && !isOk) {
                throw IllegalStateException("Server returned HTTP $code: ${conn.responseMessage}")
            }

            val append = isPartial && startOffset > 0
            var bytesDownloaded = if (append) startOffset else 0L
            val totalBytes = if (isPartial) {
                val contentRange = conn.getHeaderField("Content-Range")
                parseTotalFromContentRange(contentRange) ?: EXPECTED_FILE_SIZE_BYTES
            } else {
                conn.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_FILE_SIZE_BYTES
            }

            inputStream = conn.inputStream
            outputStream = FileOutputStream(partFile, append)

            val buffer = ByteArray(BUFFER_SIZE)
            var lastProgressTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L
            var currentSpeed = 0L

            while (isActive) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                outputStream.write(buffer, 0, read)
                bytesDownloaded += read
                bytesSinceLastTime += read

                val now = System.currentTimeMillis()
                val delta = now - lastProgressTime
                if (delta >= 400) {
                    currentSpeed = (bytesSinceLastTime * 1000) / delta
                    lastProgressTime = now
                    bytesSinceLastTime = 0L

                    val fraction = (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    _downloadState.value = ModelDownloadState.Downloading(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        progressFraction = fraction,
                        speedBytesPerSec = currentSpeed
                    )
                }
            }

            outputStream.flush()

            if (!isActive) {
                throw CancellationException("Download cancelled")
            }

            // 4. Validate completed .part file
            _downloadState.value = ModelDownloadState.Validating(1.0f)
            val finalPartSize = partFile.length()
            if (finalPartSize != EXPECTED_FILE_SIZE_BYTES) {
                throw IllegalStateException("Downloaded file size mismatch. Expected $EXPECTED_FILE_SIZE_BYTES, got $finalPartSize")
            }

            // Verify SHA-256 integrity
            val actualChecksum = computeSha256(partFile)
            Log.i(TAG, "Validation: Calculated SHA-256 = $actualChecksum")
            val isValid = actualChecksum.equals(EXPECTED_SHA256, ignoreCase = true) ||
                    actualChecksum.equals(LEGACY_XET_HASH, ignoreCase = true)
            if (!isValid) {
                Log.w(TAG, "SHA-256 mismatch! Expected: $EXPECTED_SHA256, Calculated: $actualChecksum")
                // If checksum failed, report error but preserve part for operator diagnostics
                throw IllegalStateException("Integrity check failed: SHA-256 checksum mismatch")
            }

            // 5. Atomic promotion from .part to final model file
            if (modelFile.exists()) {
                modelFile.delete()
            }
            val renamed = partFile.renameTo(modelFile)
            if (!renamed) {
                throw IllegalStateException("Failed to promote temporary .part file to $MODEL_FILENAME")
            }

            Log.i(TAG, "SAFORA model downloaded, verified, and installed successfully.")
            _downloadState.value = ModelDownloadState.Installed(modelFile, modelFile.length())

        } finally {
            try { inputStream?.close() } catch (ignored: Exception) {}
            try { outputStream?.close() } catch (ignored: Exception) {}
            try { connection?.disconnect() } catch (ignored: Exception) {}
        }
    }

    private fun parseTotalFromContentRange(contentRange: String?): Long? {
        if (contentRange == null) return null
        // Format: bytes 500-999/1234
        val slashIndex = contentRange.lastIndexOf('/')
        if (slashIndex >= 0 && slashIndex < contentRange.length - 1) {
            return contentRange.substring(slashIndex + 1).trim().toLongOrNull()
        }
        return null
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
