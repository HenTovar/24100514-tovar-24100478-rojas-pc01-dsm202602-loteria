package com.esan.pc01.loteria.auth

import org.junit.Assert.*
import org.junit.Test

class AuthControllerTest {
    private class FakeGateway : AuthGateway {
        var listener: ((SessionUser?) -> Unit)? = null
        var completion: ((AuthFailure?) -> Unit)? = null
        var observed = 0
        var removed = 0
        var calls = 0
        var action = ""
        var email = ""
        var password = ""
        var restored: SessionUser? = null
        var throwRequest = false
        override fun observeSession(onChange: (SessionUser?) -> Unit): () -> Unit {
            observed++; listener = onChange; onChange(restored)
            return { removed++ }
        }
        private fun request(kind: String, e: String, p: String, done: (AuthFailure?) -> Unit) {
            if (throwRequest) throw IllegalStateException("secret exception must not escape")
            calls++; action = kind; email = e; password = p; completion = done
        }
        override fun signIn(email: String, password: String, done: (AuthFailure?) -> Unit) = request("login", email, password, done)
        override fun register(email: String, password: String, done: (AuthFailure?) -> Unit) = request("register", email, password, done)
        override fun signOut() { listener?.invoke(null) }
    }
    private fun ready(g: FakeGateway = FakeGateway()) = AuthController(g).also { it.start() }

    @Test fun blankInputsDoNotCallProvider() {
        val g = FakeGateway(); val c = ready(g); c.submit("", "")
        assertEquals(0, g.calls); assertEquals("Completa correo y contraseña.", c.state.value.message)
    }
    @Test fun malformedEmailDoesNotCallProvider() {
        for (email in listOf("a", "a@", "@host.com", "a b@host.com")) {
            val g = FakeGateway(); val c = ready(g); c.submit(email, "password")
            assertEquals(0, g.calls); assertNotNull(c.state.value.message)
        }
    }
    @Test fun loginTrimsEmailButNeverPassword() {
        val g = FakeGateway(); val c = ready(g); c.submit(" a@host.com ", " password ")
        assertEquals("login", g.action); assertEquals("a@host.com", g.email); assertEquals(" password ", g.password)
    }
    @Test fun shortExistingPasswordCanBeSubmittedForLogin() {
        val g = FakeGateway(); ready(g).submit("a@host.com", "12345"); assertEquals(1, g.calls)
    }
    @Test fun registerRejectsShortPassword() {
        val g = FakeGateway(); val c = ready(g); c.switchMode(); c.submit("a@host.com", "12345", "12345")
        assertEquals(0, g.calls); assertNotNull(c.state.value.message)
    }
    @Test fun registerRejectsDifferentConfirmation() {
        val g = FakeGateway(); val c = ready(g); c.switchMode(); c.submit("a@host.com", "123456", "654321")
        assertEquals(0, g.calls); assertEquals("Las contraseñas no coinciden.", c.state.value.message)
    }
    @Test fun validRegisterCallsRealGatewayContract() {
        val g = FakeGateway(); val c = ready(g); c.switchMode(); c.submit("a@host.com", "123456", "123456")
        assertEquals("register", g.action); assertTrue(c.state.value.busy)
    }
    @Test fun duplicateClicksDoNotDuplicateRequests() {
        val g = FakeGateway(); val c = ready(g); c.submit("a@host.com", "password"); c.submit("a@host.com", "password")
        assertEquals(1, g.calls)
    }
    @Test fun switchingDuringRequestIsBlocked() {
        val g = FakeGateway(); val c = ready(g); c.submit("a@host.com", "password"); c.switchMode()
        assertEquals(AuthMode.LOGIN, c.state.value.mode)
    }
    @Test fun successAloneDoesNotFabricateSession() {
        val g = FakeGateway(); val c = ready(g); c.submit("a@host.com", "password"); g.completion!!(null)
        assertNull(c.state.value.user); assertFalse(c.state.value.busy)
    }
    @Test fun providerSessionSuppliesIdentityAndEmail() {
        val g = FakeGateway(); val c = ready(g); g.listener!!(SessionUser("actual-uid", "actual@host.com"))
        assertEquals(SessionUser("actual-uid", "actual@host.com"), c.state.value.user)
    }
    @Test fun restoredSessionIsVisibleWithoutLogin() {
        val g = FakeGateway(); g.restored = SessionUser("saved", "saved@host.com")
        val c = ready(g); assertEquals("saved@host.com", c.state.value.user?.email); assertEquals(0, g.calls)
    }
    @Test fun signOutClearsSessionViaProvider() {
        val g = FakeGateway(); g.restored = SessionUser("saved", "saved@host.com")
        val c = ready(g); c.signOut(); assertNull(c.state.value.user)
    }
    @Test fun failureIsReadableAndAllowsRetry() {
        val g = FakeGateway(); val c = ready(g); c.submit("a@host.com", "password"); g.completion!!(AuthFailure.NETWORK)
        assertEquals(AuthFailure.NETWORK.message(), c.state.value.message); assertFalse(c.state.value.busy)
        c.submit("a@host.com", "password"); assertEquals(2, g.calls); assertNull(c.state.value.message)
    }
    @Test fun modeSwitchClearsErrorsAndCanReturnToLogin() {
        val c = ready(); c.submit("", ""); c.switchMode(); assertNull(c.state.value.message)
        assertEquals(AuthMode.REGISTER, c.state.value.mode); c.switchMode(); assertEquals(AuthMode.LOGIN, c.state.value.mode)
    }
    @Test fun oneObserverAndOneRemovalEvenWithRepeatedLifecycleCalls() {
        val g = FakeGateway(); val c = ready(g); c.start(); c.close(); c.close(); c.start()
        assertEquals(1, g.observed); assertEquals(1, g.removed)
    }
    @Test fun lateCallbacksCannotChangeDisposedController() {
        val g = FakeGateway(); val c = ready(g); c.submit("a@host.com", "password"); c.close(); val old = c.state.value
        g.listener!!(SessionUser("late", "late@host.com")); g.completion!!(AuthFailure.NETWORK)
        assertEquals(old, c.state.value); c.submit("a@host.com", "password"); assertEquals(1, g.calls)
    }
    @Test fun synchronousProviderFailureDoesNotCrashOrLeakDetails() {
        val g = FakeGateway(); g.throwRequest = true; val c = ready(g); c.submit("a@host.com", "password")
        assertFalse(c.state.value.busy); assertEquals(AuthFailure.UNKNOWN.message(), c.state.value.message)
    }
    @Test fun firebaseErrorsAreMappedWithoutExposingUnknownDetails() {
        assertEquals(AuthFailure.INVALID_CREDENTIALS, failureFromCode("ERROR_INVALID_LOGIN_CREDENTIALS"))
        assertEquals(AuthFailure.INVALID_CREDENTIALS, failureFromCode("ERROR_USER_NOT_FOUND"))
        assertEquals(AuthFailure.EMAIL_IN_USE, failureFromCode("ERROR_EMAIL_ALREADY_IN_USE"))
        assertEquals(AuthFailure.WEAK_PASSWORD, failureFromCode("ERROR_WEAK_PASSWORD"))
        assertEquals(AuthFailure.INVALID_EMAIL, failureFromCode("ERROR_INVALID_EMAIL"))
        assertEquals(AuthFailure.DISABLED, failureFromCode("ERROR_USER_DISABLED"))
        assertEquals(AuthFailure.UNKNOWN, failureFromCode("server returned private details"))
        AuthFailure.entries.forEach { assertTrue(it.message().isNotBlank()) }
    }
}
