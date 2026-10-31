package com.vyora.ai

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * LocalModelManager
 *
 * Facade coordinating SAFORA model storage, integrity, download progress,
 * and file lifecycle in app-private storage.
 */
class LocalModelManager(private val context: Context) {

    private val downloadManager = ModelDownloadManager.getInstance(context)

    val downloadState: StateFlow<ModelDownloadState> = downloadManager.state

    fun getModelFile(): File = downloadManager.modelFile

    fun isModelInstalled(): Boolean = downloadManager.isModelInstalled()

    fun getInstalledModelSize(): Long = downloadManager.getInstalledModelSize()

    fun startDownload() {
        downloadManager.startDownload()
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    fun deleteModel(): Boolean {
        return downloadManager.deleteModel()
    }
}
