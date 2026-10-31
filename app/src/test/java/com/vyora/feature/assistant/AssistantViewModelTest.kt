package com.vyora.feature.assistant

import com.vyora.ai.LocalInferenceEngine
import com.vyora.ai.ModelDownloadState
import com.vyora.domain.repository.AssistantRepository
import com.vyora.domain.repository.EmergencyGuidance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAssistantRepository : AssistantRepository {
        val fakeDownloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
        val fakeEngineState = MutableStateFlow<LocalInferenceEngine.EngineState>(LocalInferenceEngine.EngineState.Unloaded)
        var modelInstalled = false
        var loadModelCalled = false
        var unloadModelCalled = false
        var cancelGenerationCalled = false
        var deleteModelCalled = false

        override val downloadState = fakeDownloadState
        override val engineState = fakeEngineState
        override val isModelInstalled: Boolean get() = modelInstalled

        override fun startModelDownload() {
            fakeDownloadState.value = ModelDownloadState.Downloading(50, 500_000_000L, 1_000_000_000L, 5000L)
        }

        override fun cancelModelDownload() {
            fakeDownloadState.value = ModelDownloadState.Idle
        }

        override fun deleteModel(): Boolean {
            deleteModelCalled = true
            modelInstalled = false
            fakeEngineState.value = LocalInferenceEngine.EngineState.Unloaded
            return true
        }

        override suspend fun loadModel(): Result<Unit> {
            loadModelCalled = true
            fakeEngineState.value = LocalInferenceEngine.EngineState.Ready("/path/model.gguf")
            return Result.success(Unit)
        }

        override suspend fun unloadModel() {
            unloadModelCalled = true
            fakeEngineState.value = LocalInferenceEngine.EngineState.Unloaded
        }

        override fun cancelGeneration() {
            cancelGenerationCalled = true
            fakeEngineState.value = LocalInferenceEngine.EngineState.Ready("/path/model.gguf")
        }

        override fun streamGuidance(query: String): Flow<String> = flow {
            emit("Step 1: ")
            emit("Remain calm. ")
            emit("Step 2: Seek shelter.")
        }

        override suspend fun queryGuidance(query: String): EmergencyGuidance = EmergencyGuidance(
            title = "Test Protocol",
            category = "General",
            summary = "Test Summary",
            steps = listOf("Step 1", "Step 2")
        )

        override fun getQuickProtocols(): List<EmergencyGuidance> = emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateNotDownloaded() = runTest {
        val repo = FakeAssistantRepository()
        val viewModel = AssistantViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ModelLifecycleStatus.NOT_DOWNLOADED, viewModel.uiState.value.lifecycleStatus)
        assertFalse(viewModel.uiState.value.isModelInstalled)
        assertFalse(viewModel.uiState.value.isModelLoaded)
    }

    @Test
    fun testDownloadingStateTransition() = runTest {
        val repo = FakeAssistantRepository()
        val viewModel = AssistantViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startModelDownload()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ModelLifecycleStatus.DOWNLOADING, viewModel.uiState.value.lifecycleStatus)
        val progress = viewModel.uiState.value.downloadProgress as ModelDownloadState.Downloading
        assertEquals(50, progress.percent)
    }

    @Test
    fun testModelLoadedAndReadyState() = runTest {
        val repo = FakeAssistantRepository()
        repo.modelInstalled = true
        val viewModel = AssistantViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ModelLifecycleStatus.INSTALLED_UNLOADED, viewModel.uiState.value.lifecycleStatus)

        viewModel.loadModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ModelLifecycleStatus.READY, viewModel.uiState.value.lifecycleStatus)
        assertTrue(viewModel.uiState.value.isModelLoaded)
        assertTrue(repo.loadModelCalled)
    }

    @Test
    fun testModelStreamingAndStopGeneration() = runTest {
        val repo = FakeAssistantRepository()
        repo.modelInstalled = true
        repo.fakeEngineState.value = LocalInferenceEngine.EngineState.Ready("/path/model.gguf")
        val viewModel = AssistantViewModel(repo)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.sendQuery("Emergency help needed")
        testDispatcher.scheduler.advanceUntilIdle()

        val messages = viewModel.uiState.value.chatHistory
        val userMsg = messages.find { it.isUser }
        val assistantMsg = messages.find { !it.isUser && it.id != "init" }

        assertEquals("Emergency help needed", userMsg?.text)
        assertEquals("Step 1: Remain calm. Step 2: Seek shelter.", assistantMsg?.text)
        assertFalse(assistantMsg?.isStreaming ?: true)

        viewModel.cancelGeneration()
        assertTrue(repo.cancelGenerationCalled)
    }
}
