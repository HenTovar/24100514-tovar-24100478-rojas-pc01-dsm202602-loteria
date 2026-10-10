package com.esan.pc01.loteria.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException

class FirebaseAuthGateway(private val auth: FirebaseAuth) : AuthGateway {
    override fun observeSession(onChange: (SessionUser?) -> Unit): () -> Unit {
        val listener = FirebaseAuth.AuthStateListener { source ->
            onChange(source.currentUser?.let { SessionUser(it.uid, it.email) })
        }
        auth.addAuthStateListener(listener)
        return { auth.removeAuthStateListener(listener) }
    }

    override fun signIn(email: String, password: String, done: (AuthFailure?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            done(if (task.isSuccessful) null else classify(task.exception))
        }
    }

    override fun register(email: String, password: String, done: (AuthFailure?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            done(if (task.isSuccessful) null else classify(task.exception))
        }
    }

    override fun signOut() = auth.signOut()

    private fun classify(error: Exception?): AuthFailure = when (error) {
        is FirebaseNetworkException -> AuthFailure.NETWORK
        is FirebaseTooManyRequestsException -> AuthFailure.TOO_MANY_REQUESTS
        is FirebaseAuthException -> failureFromCode(error.errorCode)
        else -> AuthFailure.UNKNOWN
    }
}
