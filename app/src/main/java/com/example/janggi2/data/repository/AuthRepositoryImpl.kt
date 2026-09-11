package com.example.janggi2.data.repository

import com.example.janggi2.domain.model.AuthUser
import com.example.janggi2.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Implementation of AuthRepository using Firebase Authentication (Google provider).
 */
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = firebaseAuth.signInWithCredential(credential).await().user?.toAuthUser()
        if (user != null) {
            Result.success(user)
        } else {
            Result.failure(IllegalStateException("Firebase sign-in returned no user"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    override fun getCurrentUser(): AuthUser? = firebaseAuth.currentUser?.toAuthUser()

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        displayName = displayName,
        email = email,
        photoUrl = photoUrl?.toString()
    )
}
