package kth.se.labb3.stepchallenge.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kth.se.labb3.stepchallenge.data.model

/**
 * Result wrapper for authentication operations.
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
    data object Loading : AuthResult<Nothing>()
}

/**
 * Repository for handling Firebase Authentication.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    /**
     * Get the current Firebase user.
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Check if user is logged in.
     */
    val isLoggedIn: Boolean
        get() = currentUser != null

    /**
     * Observe auth state changes.
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Register a new user with email and password.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): AuthResult<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user ?: return AuthResult.Error("Registration failed")

            // Update display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            user.updateProfile(profileUpdates).await()

            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Registration failed")
        }
    }

    /**
     * Sign in with email and password.
     */
    suspend fun login(email: String, password: String): AuthResult<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return AuthResult.Error("Login failed")
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed")
        }
    }

    /**
     * Sign out the current user.
     */
    fun logout() {
        auth.signOut()
    }

    /**
     * Send password reset email.
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Failed to send reset email")
        }
    }

    /**
     * Update user display name.
     */
    suspend fun updateDisplayName(displayName: String): AuthResult<Unit> {
        return try {
            val user = currentUser ?: return AuthResult.Error("No user logged in")
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            user.updateProfile(profileUpdates).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Failed to update display name")
        }
    }

    /**
     * Convert FirebaseUser to our User model.
     */
    fun firebaseUserToUser(firebaseUser: FirebaseUser): User {
        return User(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            displayName = firebaseUser.displayName ?: "User",
            photoUrl = firebaseUser.photoUrl?.toString()
        )
    }
}