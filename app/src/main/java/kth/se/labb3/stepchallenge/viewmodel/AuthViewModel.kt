package kth.se.labb3.stepchallenge.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kth.se.labb3.stepchallenge.data.model.User
import kth.se.labb3.stepchallenge.data.repository.AuthRepository
import kth.se.labb3.stepchallenge.data.repository.AuthResult
import kth.se.labb3.stepchallenge.data.repository.LeaderboardRepository

/**
 * UI state for authentication screens.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    val error: String? = null,
    val registrationSuccess: Boolean = false
)

/**
 * ViewModel for handling authentication.
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val leaderboardRepository: LeaderboardRepository = LeaderboardRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()

        viewModelScope.launch {
            authRepository.authStateFlow.collect { firebaseUser ->
                if (firebaseUser != null) {
                    val user = authRepository.firebaseUserToUser(firebaseUser)
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = true,
                        user = user,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoggedIn = false,
                        user = null,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun checkAuthState() {
        val currentUser = authRepository.currentUser
        if (currentUser != null) {
            val user = authRepository.firebaseUserToUser(currentUser)
            _uiState.value = _uiState.value.copy(
                isLoggedIn = true,
                user = user
            )
        }
    }

    /**
     * Register a new user.
     */
    fun register(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = authRepository.register(email, password, displayName)) {
                is AuthResult.Success -> {
                    val user = authRepository.firebaseUserToUser(result.data)

                    // Create user in Firestore
                    leaderboardRepository.createOrUpdateUser(user)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        user = user,
                        registrationSuccess = true
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is AuthResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    /**
     * Login with email and password.
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = authRepository.login(email, password)) {
                is AuthResult.Success -> {
                    val user = authRepository.firebaseUserToUser(result.data)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        user = user
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is AuthResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    /**
     * Logout the current user.
     */
    fun logout() {
        authRepository.logout()
        _uiState.value = AuthUiState()
    }

    /**
     * Clear any error messages.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Send password reset email.
     */
    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = authRepository.sendPasswordResetEmail(email)) {
                is AuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = null
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
                is AuthResult.Loading -> {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel() as T
            }
        }
    }
}