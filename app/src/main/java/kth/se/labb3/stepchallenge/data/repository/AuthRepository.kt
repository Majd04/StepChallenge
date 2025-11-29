package kth.se.labb3.stepchallenge.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kth.se.labb3.stepchallenge.data.model.User

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
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    /**
     * Get the current logged in user.
     */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * Flow that emits auth state changes.
     */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val authStateListener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser)
        }
        firebaseAuth.addAuthStateListener(authStateListener)
        awaitClose {
            firebaseAuth.removeAuthStateListener(authStateListener)
        }
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
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user

            // Update display name
            user?.updateProfile(
                UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
            )?.await()

            if (user != null) {
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Registration failed: User is null")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Registration failed")
        }
    }

    /**
     * Login with email and password.
     */
    suspend fun login(email: String, password: String): AuthResult<FirebaseUser> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user

            if (user != null) {
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Login failed: User is null")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed")
        }
    }

    /**
     * Logout the current user.
     */
    fun logout() {
        firebaseAuth.signOut()
    }

    /**
     * Send password reset email.
     */
    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Failed to send password reset email")
        }
    }

    /**
     * Convert FirebaseUser to app User model.
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