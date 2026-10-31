package com.vyora.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LocalInferenceEngine
 *
 * Real, offline, JNI-backed inference engine wrapping llama.cpp (libai-chat.so).
 * Executes on background dispatcher, streaming tokens via Kotlin Flow, supporting
 * cancellation, context management, thread tuning, and clean native unloading.
 */
class LocalInferenceEngine private constructor() {

    companion object {
        private const val TAG = "LocalInferenceEngine"

        @Volatile
        private var instance: LocalInferenceEngine? = null

        fun getInstance(): LocalInferenceEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalInferenceEngine().also { instance = it }
            }
        }

        private var nativeLibraryLoaded = false

        init {
            try {
                System.loadLibrary("ai-chat")
                nativeLibraryLoaded = true
                Log.i(TAG, "libai-chat.so loaded successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load libai-chat.so: ${t.message}", t)
            }
        }
    }

    sealed interface EngineState {
        object Unloaded : EngineState
        data class Loading(val modelPath: String) : EngineState
        data class Ready(val modelPath: String) : EngineState
        data class Generating(val prompt: String) : EngineState
        data class Error(val message: String) : EngineState
    }

    private val _engineState = MutableStateFlow<EngineState>(EngineState.Unloaded)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val mutex = Mutex()
    private val isCancelled = AtomicBoolean(false)
    private var isBackendInitialized = false
    private var currentModelPath: String? = null

    // Native JNI functions declared in ai_chat.cpp
    private external fun init(nativeLibDir: String)
    private external fun load(modelPath: String): Int
    private external fun prepare(): Int
    private external fun systemInfo(): String
    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun processUserPrompt(userPrompt: String, nPredict: Int): Int
    private external fun generateNextToken(): String?
    private external fun unload()
    private external fun shutdown()

    fun isNativeReady(): Boolean = nativeLibraryLoaded

    /**
     * Initializes the backend if not already initialized.
     * nativeLibDir is usually context.applicationInfo.nativeLibraryDir.
     */
    fun ensureBackendInitialized(context: Context) {
        if (!nativeLibraryLoaded) {
            Log.w(TAG, "Native library not loaded; skipping backend init")
            return
        }
        if (!isBackendInitialized) {
            synchronized(this) {
                if (!isBackendInitialized) {
                    val libDir = context.applicationInfo.nativeLibraryDir
                    try {
                        init(libDir)
                        isBackendInitialized = true
                        Log.i(TAG, "llama.cpp backend initialized from $libDir")
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error initializing llama.cpp backend", t)
                    }
                }
            }
        }
    }

    /**
     * Loads a GGUF model from the specified file path.
     * Keeps execution strictly off the main thread.
     */
    suspend fun loadModel(context: Context, modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        if (!nativeLibraryLoaded) {
            val error = "Native llama.cpp library (libai-chat.so) is not available"
            _engineState.value = EngineState.Error(error)
            return@withContext Result.failure(IllegalStateException(error))
        }

        if (!modelFile.exists() || !modelFile.canRead()) {
            val error = "Model file not found or unreadable: ${modelFile.absolutePath}"
            _engineState.value = EngineState.Error(error)
            return@withContext Result.failure(IllegalArgumentException(error))
        }

        mutex.withLock {
            if (_engineState.value is EngineState.Ready && currentModelPath == modelFile.absolutePath) {
                Log.d(TAG, "Model already loaded: ${modelFile.absolutePath}")
                return@withContext Result.success(Unit)
            }

            try {
                // Unload previous model if any
                if (_engineState.value is EngineState.Ready) {
                    unloadInternal()
                }

                _engineState.value = EngineState.Loading(modelFile.absolutePath)
                ensureBackendInitialized(context)

                Log.i(TAG, "Loading model from ${modelFile.absolutePath}...")
                val loadCode = load(modelFile.absolutePath)
                if (loadCode != 0) {
                    val msg = "Native model load failed with code: $loadCode"
                    _engineState.value = EngineState.Error(msg)
                    return@withContext Result.failure(RuntimeException(msg))
                }

                val prepCode = prepare()
                if (prepCode != 0) {
                    val msg = "Native model prepare failed with code: $prepCode"
                    unload()
                    _engineState.value = EngineState.Error(msg)
                    return@withContext Result.failure(RuntimeException(msg))
                }

                currentModelPath = modelFile.absolutePath
                _engineState.value = EngineState.Ready(modelFile.absolutePath)
                Log.i(TAG, "Model successfully loaded and prepared: ${modelFile.name}")
                Result.success(Unit)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load model", t)
                _engineState.value = EngineState.Error(t.message ?: "Unknown model load error")
                Result.failure(t)
            }
        }
    }

    /**
     * Sets or updates system instructions in the model's KV context.
     */
    suspend fun setSystemPrompt(systemPrompt: String): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_engineState.value !is EngineState.Ready) {
                return@withContext Result.failure(IllegalStateException("Model is not loaded"))
            }
            try {
                val code = processSystemPrompt(systemPrompt)
                if (code == 0) Result.success(Unit) else Result.failure(RuntimeException("processSystemPrompt error $code"))
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    /**
     * Streams generated tokens for the user prompt using Kotlin Flow.
     * Generation can be cancelled at any time via [cancelGeneration] or coroutine cancellation.
     */
    fun generate(
        userPrompt: String,
        maxTokens: Int = 512,
        systemPrompt: String? = null
    ): Flow<String> = flow {
        if (!nativeLibraryLoaded) {
            emit("Error: Native inference engine not available on this platform.")
            return@flow
        }

        mutex.lock()
        try {
            val currentState = _engineState.value
            if (currentState !is EngineState.Ready) {
                emit("Error: Model is not loaded. Current state: $currentState")
                return@flow
            }

            _engineState.value = EngineState.Generating(userPrompt)
            isCancelled.set(false)

            if (!systemPrompt.isNullOrBlank()) {
                val sysRes = processSystemPrompt(systemPrompt)
                if (sysRes != 0) {
                    Log.w(TAG, "Failed to set custom system prompt: $sysRes")
                }
            }

            val userRes = processUserPrompt(userPrompt, maxTokens)
            if (userRes != 0) {
                emit("Error: Failed to process prompt in model context (code $userRes)")
                return@flow
            }

            var tokenCount = 0
            while (!isCancelled.get() && tokenCount < maxTokens) {
                val tokenPiece = generateNextToken() ?: break // null indicates EOG or stop
                if (tokenPiece.isNotEmpty()) {
                    emit(tokenPiece)
                }
                tokenCount++
            }

            if (isCancelled.get()) {
                Log.d(TAG, "Generation cancelled by user after $tokenCount tokens.")
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Generation coroutine cancelled.")
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Error during generation", t)
            emit("\n[Inference Error: ${t.localizedMessage ?: "Unknown error"}]")
        } finally {
            currentModelPath?.let { _engineState.value = EngineState.Ready(it) } ?: run {
                _engineState.value = EngineState.Unloaded
            }
            mutex.unlock()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Signals the ongoing generation loop to halt immediately.
     */
    fun cancelGeneration() {
        Log.i(TAG, "cancelGeneration() requested")
        isCancelled.set(true)
    }

    /**
     * Unloads the currently loaded model and frees native memory.
     */
    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        mutex.withLock {
            unloadInternal()
        }
    }

    private fun unloadInternal() {
        if (nativeLibraryLoaded && currentModelPath != null) {
            try {
                unload()
                Log.i(TAG, "Native model unloaded successfully.")
            } catch (t: Throwable) {
                Log.e(TAG, "Error unloading native model", t)
            }
        }
        currentModelPath = null
        _engineState.value = EngineState.Unloaded
    }

    /**
     * Returns true if model is loaded and ready for inference.
     */
    fun isModelReady(): Boolean = _engineState.value is EngineState.Ready
}
