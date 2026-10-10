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
fun FollowScreen(play: Jugada, onBack: () -> Unit, onSignOut: () -> Unit) {
    val controller = remember(play) {
        FollowController(play, FirestoreFollowGateway(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()))
    }
    DisposableEffect(controller) { controller.start(); onDispose { controller.close() } }
    val state by controller.state.collectAsState()
    val matched = controller.matchedNumbers()
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Seguimiento", style = MaterialTheme.typography.headlineLarge)
        state.header?.let { header ->
            Text(header.nombre, style = MaterialTheme.typography.titleLarge)
            Text("Fecha: ${header.fecha}")
            Text(when (header.estado) {
                "abierto" -> "En espera del inicio del sorteo"
                "en_curso" -> "Sorteo en curso"
                else -> "Sorteo finalizado"
            })
        }
        if (state.headerLoading || state.ballsLoading) CircularProgressIndicator()
        state.headerError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.ballsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Tus seis números", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            play.numeros.forEach { number ->
                Surface(color = if (number in matched) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Text(number.toString(), modifier = Modifier.padding(8.dp))
                }
            }
        }
        Text("Los números resaltados ya salieron.")
        Text("Bolillas en orden de salida", style = MaterialTheme.typography.titleMedium)
        if (!state.ballsLoading && state.balls.isEmpty() && state.ballsError == null) Text("Todavía no hay bolillas.")
        state.balls.forEach { ball ->
            Text("${ball.orden}. ${ball.numero}" + if (ball.numero in matched) " · acertado" else "")
        }
        TextButton(onClick = onBack) { Text("Volver a mi jugada") }
        TextButton(onClick = onSignOut) { Text("Cerrar sesión") }
    }
}
