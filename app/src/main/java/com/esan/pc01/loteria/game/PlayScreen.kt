package com.esan.pc01.loteria.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun PlayScreen(uid: String, email: String?, onSignOut: () -> Unit) {
    val controller = remember(uid) {
        PlayController(uid, FirestorePlayGateway(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()))
    }
    DisposableEffect(controller) { controller.loadDraws(); onDispose { controller.close() } }
    val state by controller.state.collectAsState()
    var following by remember(state.selected?.id) { mutableStateOf(false) }
    if (following && state.play != null) {
        FollowScreen(state.play!!, onBack = { following = false }, onSignOut = onSignOut)
        return
    }
    var input by remember(state.selected?.id) { mutableStateOf("") }
    BackHandler(enabled = state.selected != null) { if (!state.busy) controller.back() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Lotería PC01", style = MaterialTheme.typography.headlineLarge)
        Text("Correo: ${email ?: "No disponible"}")
        TextButton(onClick = onSignOut, enabled = !state.busy) { Text("Cerrar sesión") }
        val draw = state.selected
        if (draw == null) {
            Text("Elige un sorteo", style = MaterialTheme.typography.titleLarge)
            if (!state.busy && state.draws.isEmpty()) Text("No hay sorteos. Crea la cabecera en la consola de Firebase y actualiza.")
            state.draws.forEach { item ->
                OutlinedButton(onClick = { controller.select(item) }, enabled = !state.busy) {
                    Text("${item.nombre} · ${item.fecha} · ${item.estado}")
                }
            }
            Button(onClick = { controller.loadDraws() }, enabled = !state.busy) { Text("Actualizar sorteos") }
        } else {
            Text(draw.nombre, style = MaterialTheme.typography.titleLarge)
            Text("Fecha: ${draw.fecha}")
            Text("Estado: ${draw.estado}")
            val play = state.play
            if (play != null) {
                Text("Tu jugada: ${play.numeros.joinToString(", ")}")
                Text("Esta jugada ya está guardada y no puede modificarse.")
                Button(onClick = { following = true }) { Text("Seguir sorteo en tiempo real") }
            } else if (state.ownPlayLoaded && draw.estado == "abierto") {
                OutlinedTextField(value = input, onValueChange = { input = it }, enabled = !state.busy,
                    label = { Text("Seis números separados por comas") },
                    supportingText = { Text("Ejemplo: 5, 12, 18, 23, 34, 41") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { controller.submit(input) }, enabled = !state.busy) { Text("Guardar jugada") }
            } else if (state.ownPlayLoaded) Text("El sorteo no está abierto. No puedes registrar una jugada.")
            TextButton(onClick = { controller.back() }, enabled = !state.busy) { Text("Volver a sorteos") }
        }
        if (state.busy) { CircularProgressIndicator(); Text("Consultando Firestore…") }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
