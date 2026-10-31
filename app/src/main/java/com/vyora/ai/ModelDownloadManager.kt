package com.vyora.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModelDownloadManager {
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress
    fun startDownload(modelUrl: String) {}
    fun cancelDownload() {}
}
