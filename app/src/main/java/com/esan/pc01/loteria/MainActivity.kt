package com.esan.pc01.loteria

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.esan.pc01.loteria.auth.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gateway = if (FirebaseApp.getApps(this).isEmpty()) null
                      else FirebaseAuthGateway(FirebaseAuth.getInstance())
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (gateway == null) ConfigurationRequired() else Authentication(gateway)
                }
            }
        }
    }
}

@Composable
private fun ConfigurationRequired() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Lotería PC01", style = MaterialTheme.typography.headlineLarge)
        Text("Falta configurar Firebase")
        Text("Añade app/google-services.json de tu proyecto y habilita correo/contraseña en Firebase Authentication. Luego recompila la app.")
        Text("No se puede iniciar sesión sin esa configuración.")
    }
}

@Composable
private fun Authentication(gateway: AuthGateway) {
    val controller = remember(gateway) { AuthController(gateway) }
    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.close() }
    }
    val state by controller.state.collectAsState()
    var email by remember { mutableStateOf("") }
    // No rememberSaveable: no persistir contraseñas en el estado guardado de Android.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    LaunchedEffect(state.mode, state.user?.uid) { password = ""; confirmation = "" }
    BackHandler(enabled = state.user == null && state.mode == AuthMode.REGISTER) {
        if (!state.busy) controller.switchMode()
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Lotería PC01", style = MaterialTheme.typography.headlineLarge)
        val user = state.user
        if (user != null) {
            Text("Sesión iniciada", style = MaterialTheme.typography.titleLarge)
            Text("Correo: ${user.email ?: "No disponible"}")
            Text("La siguiente entrega habilitará el registro de tu jugada y su seguimiento.")
            Button(onClick = { controller.signOut() }, enabled = !state.busy) { Text("Cerrar sesión") }
        } else {
            val registration = state.mode == AuthMode.REGISTER
            Text(if (registration) "Crear cuenta" else "Iniciar sesión", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo") },
                singleLine = true, enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña") },
                singleLine = true, enabled = !state.busy, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            if (registration) OutlinedTextField(value = confirmation, onValueChange = { confirmation = it },
                label = { Text("Repetir contraseña") }, singleLine = true, enabled = !state.busy,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Button(onClick = { controller.submit(email, password, confirmation) }, enabled = !state.busy) {
                Text(if (registration) "Registrarme" else "Entrar")
            }
            TextButton(onClick = { controller.switchMode() }, enabled = !state.busy) {
                Text(if (registration) "Volver al login" else "Crear una cuenta")
            }
        }
        if (state.busy) { CircularProgressIndicator(); Text("Conectando…") }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
