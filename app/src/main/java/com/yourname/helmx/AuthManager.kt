package com.yourname.helmx

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.yourname.helmx.User
import kotlinx.coroutines.tasks.await

class AuthManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun getCurrentUser() : FirebaseUser? {
        return auth.currentUser
    }

    /**
     * Sign up new user with email and password
     *
     * @param email User's email address
     * @param password User's password (min 6 characters required by Firebase)
     * @param fullName User's full name
     * @param phoneNumber User's phone number
     * @return Result<String> - Success with UID or Failure with error message
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String
    ): Result<String> {
        return try {
            // Step 1: Create authentication account
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()

            // Step 2: Get the newly created user's UID
            val uid = authResult.user?.uid ?: throw Exception("User creation failed")

            // Step 3: Create user document in Firestore
            val user = User(
                id = uid,
                fullname = fullName,
                email = email,
                phone = phoneNumber,
                createdAt = System.currentTimeMillis()
            )

            // Step 4: Save to Firestore "users" collection
            firestore.collection("users")
                .document(uid)
                .set(user)
                .await()

            // Step 5: Return success with UID
            Result.success(uid)

        } catch (e: Exception) {
            // If anything fails, return error message
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<String> {
        return try {
            // Authenticate user
            val authResult = auth.signInWithEmailAndPassword(email, password).await()

            // Get UID
            val uid = authResult.user?.uid ?: throw Exception("Log in failed")

            // Return success
            Result.success(uid)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user data from Firestore
     *
     * @param uid User's unique identifier
     * @return Result<User> - Success with User object or Failure
     */
    suspend fun getUserData(uid: String): Result<User> {
        return try {
            // Fetch user document from Firestore
            val document = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            // Convert Firestore document to User object
            val user = document.toObject(User::class.java)
                ?: throw Exception("User data not found")

            Result.success(user)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Check if user is currently logged in
     */
    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    suspend fun reauthenticate(password: String): Result<Boolean> {
        return try {
            val user = auth.currentUser ?: throw Exception("Not logged in")
            val email = user.email ?: throw Exception("User has no email")
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEmail(newEmail: String): Result<Boolean> {
        return try {
            val user = auth.currentUser ?: throw Exception("Not logged in")
            user.verifyBeforeUpdateEmail(newEmail).await() // Using modern API for Firebase
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePassword(newPassword: String): Result<Boolean> {
        return try {
            val user = auth.currentUser ?: throw Exception("Not logged in")
            user.updatePassword(newPassword).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Boolean> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}