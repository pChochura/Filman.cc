package com.pointlessapps.filman.ui.login

import app.cash.turbine.test
import com.pointlessapps.filman.data.api.FilmanRepository
import com.pointlessapps.filman.data.local.SessionManager
import com.pointlessapps.filman.data.model.PageResult
import com.pointlessapps.filman.data.sync.SyncManager
import com.pointlessapps.filman.data.sync.api.SyncApiClient
import com.pointlessapps.filman.data.sync.model.SyncAuthResponse
import com.pointlessapps.filman.data.sync.model.SyncUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: FilmanRepository = mockk(relaxed = true)
    private val sessionManager: SessionManager = mockk(relaxed = true)
    private val syncApiClient: SyncApiClient = mockk(relaxed = true)
    private val syncManager: SyncManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { sessionManager.usernameFlow } returns MutableStateFlow("savedUser")
        coEvery { repository.getFeedPage(any(), any()) } returns PageResult(
            featuredItems = emptyList(),
            movies = emptyList(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onLoginClicked with blank credentials displays error`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(repository, sessionManager, syncApiClient, syncManager)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LoginEvent.OnLoginClicked("", "pass"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.authErrorMessage)
        assertFalse(viewModel.state.value.isLoginLoading)
    }

    @Test
    fun `onLoginClicked with valid credentials logs in, saves auth token, triggers sync, and navigates back`() = runTest(testDispatcher) {
        coEvery { syncApiClient.login(any()) } returns Result.success(
            SyncAuthResponse(token = "test_token", user = SyncUser(id = "1", username = "tester"), success = true),
        )

        val viewModel = LoginViewModel(repository, sessionManager, syncApiClient, syncManager)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LoginEvent.OnLoginClicked("tester", "password123", isRegister = false))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { sessionManager.saveAuthToken("test_token", "1", "tester") }
            coVerify { syncManager.triggerSync() }

            val effect = awaitItem()
            assertEquals(LoginEffect.NavigateBack, effect)
            assertFalse(viewModel.state.value.isLoginLoading)
            assertNull(viewModel.state.value.authErrorMessage)
        }
    }

    @Test
    fun `onLoginClicked with register mode calls register API`() = runTest(testDispatcher) {
        coEvery { syncApiClient.register(any()) } returns Result.success(
            SyncAuthResponse(token = "reg_token", user = SyncUser(id = "2", username = "newuser"), success = true),
        )

        val viewModel = LoginViewModel(repository, sessionManager, syncApiClient, syncManager)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(LoginEvent.OnLoginClicked("newuser", "password123", isRegister = true))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { syncApiClient.register(match { it.username == "newuser" }) }
            coVerify { sessionManager.saveAuthToken("reg_token", "2", "newuser") }
            val effect = awaitItem()
            assertEquals(LoginEffect.NavigateBack, effect)
        }
    }

    @Test
    fun `onLoginClicked failure updates state with error message`() = runTest(testDispatcher) {
        coEvery { syncApiClient.login(any()) } returns Result.failure(IOException("Server error occurred"))

        val viewModel = LoginViewModel(repository, sessionManager, syncApiClient, syncManager)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LoginEvent.OnLoginClicked("tester", "password123", isRegister = false))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoginLoading)
        assertNotNull(viewModel.state.value.authErrorMessage)
    }

    @Test
    fun `onModeChanged updates isRegisterMode and clears error`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel(repository, sessionManager, syncApiClient, syncManager)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(LoginEvent.OnModeChanged(isRegisterMode = true))
        assertTrue(viewModel.state.value.isRegisterMode)

        viewModel.onEvent(LoginEvent.OnModeChanged(isRegisterMode = false))
        assertFalse(viewModel.state.value.isRegisterMode)
    }
}
