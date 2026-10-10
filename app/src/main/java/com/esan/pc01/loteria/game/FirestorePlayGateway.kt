package com.esan.pc01.loteria.game

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.text.SimpleDateFormat
import java.util.Locale

class FirestorePlayGateway(private val db: FirebaseFirestore, private val auth: FirebaseAuth) : PlayGateway {
    override fun draws(done: (Result<List<Sorteo>>) -> Unit) {
        db.collection("sorteos").get(Source.SERVER).addOnCompleteListener { task ->
            if (!task.isSuccessful) done(Result.failure(task.exception ?: Exception()))
            else done(runCatching { task.result.documents.map(::parseDraw) })
        }
    }

    override fun ownPlay(sorteoId: String, uid: String, done: (Result<Jugada?>) -> Unit) {
        if (auth.currentUser?.uid != uid) { done(Result.failure(PlayRejected("La sesión cambió. Vuelve a iniciar sesión."))); return }
        // Lectura directa del UID propio, nunca listado de todas las jugadas.
        db.collection("sorteos").document(sorteoId).collection("jugadas").document(uid)
            .get(Source.SERVER).addOnCompleteListener { task ->
                if (!task.isSuccessful) done(Result.failure(task.exception ?: Exception()))
                else done(runCatching { if (task.result.exists()) parsePlay(task.result, sorteoId, uid) else null })
            }
    }

    override fun create(play: Jugada, done: (Result<Jugada>) -> Unit) {
        if (auth.currentUser?.uid != play.uid || !validNumbers(play.numeros)) {
            done(Result.failure(PlayRejected("Sesión o números inválidos."))); return
        }
        val draw = db.collection("sorteos").document(play.sorteoId)
        val own = draw.collection("jugadas").document(play.uid)
        db.runTransaction { tx ->
            // Todas las lecturas antes de la escritura. Un cambio concurrente fuerza reintento.
            val header = tx.get(draw)
            val previous = tx.get(own)
            if (!header.exists() || header.getString("estado") != "abierto")
                throw PlayRejected("El sorteo ya no está abierto. No se guardó la jugada.")
            if (previous.exists()) throw PlayRejected("Ya tienes una jugada. Recarga el sorteo para verla.")
            tx.set(own, mapOf("sorteoId" to play.sorteoId, "uid" to play.uid, "numeros" to play.numeros))
            play
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) done(Result.success(task.result))
            else {
                val error = task.exception
                val cause = generateSequence(error as Throwable?) { it.cause }.filterIsInstance<PlayRejected>().firstOrNull()
                done(Result.failure(cause ?: error ?: Exception()))
            }
        }
    }

    private fun parseDraw(doc: DocumentSnapshot): Sorteo {
        val nombre = doc.getString("nombre")?.takeIf { it.isNotBlank() } ?: throw PlayRejected("Falta nombre del sorteo en Firestore.")
        val fecha = doc.getTimestamp("fecha") ?: throw PlayRejected("Falta fecha Timestamp del sorteo en Firestore.")
        val estado = doc.getString("estado")?.takeIf { it in listOf("abierto", "en_curso", "finalizado") }
            ?: throw PlayRejected("Estado del sorteo inválido.")
        return Sorteo(doc.id, nombre, SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE")).format(fecha.toDate()), estado)
    }

    private fun parsePlay(doc: DocumentSnapshot, sorteoId: String, uid: String): Jugada {
        val numbers = (doc.get("numeros") as? List<*>)?.map {
            val number = it as? Long ?: throw PlayRejected("La jugada tiene números no enteros.")
            if (number !in 1L..45L) throw PlayRejected("La jugada tiene números fuera de rango.")
            number.toInt()
        } ?: throw PlayRejected("La jugada no tiene números válidos.")
        if (doc.getString("sorteoId") != sorteoId || doc.getString("uid") != uid || !validNumbers(numbers))
            throw PlayRejected("La jugada almacenada no coincide con esta sesión o sorteo.")
        return Jugada(sorteoId, uid, numbers)
    }
}
