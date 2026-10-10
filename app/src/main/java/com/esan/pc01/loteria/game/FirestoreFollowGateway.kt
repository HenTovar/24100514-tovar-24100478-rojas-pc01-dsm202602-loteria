package com.esan.pc01.loteria.game

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/** Sólo lecturas. Firestore administra la conexión; no hay temporizador ni polling. */
class FirestoreFollowGateway(private val db: FirebaseFirestore, auth: FirebaseAuth) : FollowGateway {
    private val parser = FirestorePlayGateway(db, auth)
    override fun header(sorteoId: String, done: (Result<Sorteo>) -> Unit): FollowSubscription {
        val registration = db.collection("sorteos").document(sorteoId).addSnapshotListener { snapshot, error ->
            if (error != null) done(Result.failure(error))
            else done(runCatching {
                val doc = snapshot?.takeIf { it.exists() } ?: throw PlayRejected("No existe la cabecera.")
                parser.parseDraw(doc)
            })
        }
        return FollowSubscription { registration.remove() }
    }
    override fun balls(sorteoId: String, done: (Result<List<Bolilla>>) -> Unit): FollowSubscription {
        val registration = db.collection("sorteos").document(sorteoId).collection("bolillas")
            .orderBy("orden", Query.Direction.ASCENDING).addSnapshotListener { snapshot, error ->
                if (error != null) done(Result.failure(error))
                else done(runCatching {
                    val docs = snapshot?.documents ?: throw PlayRejected("Sin snapshot de bolillas.")
                    val balls = docs.map { doc ->
                        val order = doc.get("orden") as? Long ?: throw PlayRejected("Orden no entero.")
                        val number = doc.get("numero") as? Long ?: throw PlayRejected("Número no entero.")
                        if (order !in 1L..6L || number !in 1L..45L) throw PlayRejected("Bolilla fuera de rango.")
                        Bolilla(order.toInt(), number.toInt())
                    }
                    if (!validBalls(balls)) throw PlayRejected("Bolillas duplicadas o inválidas.")
                    balls
                })
            }
        return FollowSubscription { registration.remove() }
    }
}
