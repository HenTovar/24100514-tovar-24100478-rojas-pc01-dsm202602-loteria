package com.esan.pc01.loteria.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionUser(val uid: String, val email: String?)
enum class AuthMode { LOGIN, REGISTER }
enum class AuthFailure { INVALID_EMAIL, INVALID_CREDENTIALS, EMAIL_IN_USE, WEAK_PASSWORD, NETWORK, TOO_MANY_REQUESTS, DISABLED, UNKNOWN }

data class AuthState(
    val mode: AuthMode = AuthMode.LOGIN,
    val user: SessionUser? = null,
    val busy: Boolean = false,
    val message: String? = null
)

// Firebase es el único proveedor de identidad. El controlador no inventa sesiones.
interface AuthGateway {
    fun observeSession(onChange: (SessionUser?) -> Unit): () -> Unit
    fun signIn(email: String, password: String, done: (AuthFailure?) -> Unit)
    fun register(email: String, password: String, done: (AuthFailure?) -> Unit)
    fun signOut()
}

fun failureFromCode(code: String?): AuthFailure = when (code) {
    "ERROR_INVALID_EMAIL" -> AuthFailure.INVALID_EMAIL
    "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> AuthFailure.INVALID_CREDENTIALS
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthFailure.EMAIL_IN_USE
    "ERROR_WEAK_PASSWORD" -> AuthFailure.WEAK_PASSWORD
    "ERROR_USER_DISABLED" -> AuthFailure.DISABLED
    "ERROR_TOO_MANY_REQUESTS" -> AuthFailure.TOO_MANY_REQUESTS
    else -> AuthFailure.UNKNOWN
}

fun AuthFailure.message(): String = when (this) {
    AuthFailure.INVALID_EMAIL -> "Revisa el formato del correo."
    AuthFailure.INVALID_CREDENTIALS -> "No se pudo iniciar sesión. Revisa correo y contraseña."
    AuthFailure.EMAIL_IN_USE -> "Ese correo ya tiene una cuenta. Inicia sesión."
    AuthFailure.WEAK_PASSWORD -> "La contraseña no cumple la política de Firebase. Usa al menos seis caracteres."
    AuthFailure.NETWORK -> "No hay conexión. Revisa tu red e inténtalo de nuevo."
    AuthFailure.TOO_MANY_REQUESTS -> "Demasiados intentos. Espera y vuelve a intentar."
    AuthFailure.DISABLED -> "La cuenta está deshabilitada."
    AuthFailure.UNKNOWN -> "No se pudo completar la operación. Inténtalo de nuevo."
}

class AuthController(private val gateway: AuthGateway) : AutoCloseable {
    private val mutable = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = mutable.asStateFlow()
    private var removeListener: (() -> Unit)? = null
    private var started = false
    private var closed = false

    fun start() {
        if (started || closed) return
        started = true
        removeListener = gateway.observeSession { user ->
            if (!closed) mutable.value = mutable.value.copy(user = user, message = null)
        }
    }

    fun switchMode() {
        if (closed || state.value.busy || state.value.user != null) return
        mutable.value = state.value.copy(
            mode = if (state.value.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN,
            message = null
        )
    }

    fun submit(email: String, password: String, confirmation: String = "") {
        if (closed || !started || state.value.busy || state.value.user != null) return
        val cleanEmail = email.trim()
        val problem = when {
            cleanEmail.isBlank() || password.isEmpty() -> "Completa correo y contraseña."
            !cleanEmail.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) -> "Revisa el formato del correo."
            state.value.mode == AuthMode.REGISTER && password.length < 6 -> "Usa al menos seis caracteres en la contraseña."
            state.value.mode == AuthMode.REGISTER && password != confirmation -> "Las contraseñas no coinciden."
            else -> null
        }
        if (problem != null) { mutable.value = state.value.copy(message = problem); return }
        mutable.value = state.value.copy(busy = true, message = null)
        val done: (AuthFailure?) -> Unit = { failure ->
            if (!closed) mutable.value = state.value.copy(busy = false, message = failure?.message())
        }
        try {
            if (state.value.mode == AuthMode.REGISTER) gateway.register(cleanEmail, password, done)
            else gateway.signIn(cleanEmail, password, done)
        } catch (_: Exception) { done(AuthFailure.UNKNOWN) }
    }

    fun signOut() {
        if (closed || state.value.busy) return
        try { gateway.signOut() }
        catch (_: Exception) { mutable.value = state.value.copy(message = AuthFailure.UNKNOWN.message()) }
    }

    override fun close() {
        if (closed) return
        closed = true
        removeListener?.invoke()
        removeListener = null
    }
}
