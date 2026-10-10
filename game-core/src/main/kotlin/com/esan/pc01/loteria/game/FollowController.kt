package com.esan.pc01.loteria.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Bolilla(val orden: Int, val numero: Int)
fun validBalls(balls: List<Bolilla>) = balls.size <= 6 &&
    balls.all { it.orden in 1..6 && it.numero in 1..45 } &&
    balls.map { it.orden }.toSet().size == balls.size &&
    balls.map { it.numero }.toSet().size == balls.size

fun interface FollowSubscription { fun remove() }
interface FollowGateway {
    fun header(sorteoId: String, done: (Result<Sorteo>) -> Unit): FollowSubscription
    fun balls(sorteoId: String, done: (Result<List<Bolilla>>) -> Unit): FollowSubscription
}
data class FollowState(
    val header: Sorteo? = null, val balls: List<Bolilla> = emptyList(),
    val headerLoading: Boolean = true, val ballsLoading: Boolean = true,
    val headerError: String? = null, val ballsError: String? = null
)

/** Un controlador por pantalla; cada snapshot reemplaza la lista, nunca la agrega. */
class FollowController(val play: Jugada, private val gateway: FollowGateway) : AutoCloseable {
    init { require(play.uid.isNotBlank() && play.sorteoId.isNotBlank() && validNumbers(play.numeros)) }
    private val mutable = MutableStateFlow(FollowState())
    val state: StateFlow<FollowState> = mutable.asStateFlow()
    private var started = false
    private var closed = false
    private val subscriptions = mutableListOf<FollowSubscription>()

    fun start() {
        if (started || closed) return
        started = true
        attach({ done -> gateway.header(play.sorteoId, done) }) { result ->
            if (!closed) result.fold(onSuccess = { header ->
                if (header.id != play.sorteoId || header.nombre.isBlank() || header.fecha.isBlank() ||
                    header.estado !in listOf("abierto", "en_curso", "finalizado")) headerFailure()
                else mutable.value = state.value.copy(header = header, headerLoading = false, headerError = null)
            }, onFailure = { headerFailure() })
        }
        if (!closed) attach({ done -> gateway.balls(play.sorteoId, done) }) { result ->
            if (!closed) result.fold(onSuccess = { balls ->
                if (!validBalls(balls)) ballsFailure()
                else mutable.value = state.value.copy(balls = balls.sortedBy { it.orden }.toList(), ballsLoading = false, ballsError = null)
            }, onFailure = { ballsFailure() })
        }
    }
    private fun <T> attach(register: ((Result<T>) -> Unit) -> FollowSubscription, done: (Result<T>) -> Unit) {
        try {
            val subscription = register(done)
            if (closed) subscription.remove() else subscriptions.add(subscription)
        } catch (error: Exception) { done(Result.failure(error)) }
    }
    private fun headerFailure() {
        mutable.value = state.value.copy(header = null, headerLoading = false,
            headerError = "No se pudo recibir la cabecera. Vuelve y reintenta; revisa conexión y permisos.")
    }
    private fun ballsFailure() {
        mutable.value = state.value.copy(balls = emptyList(), ballsLoading = false,
            ballsError = "No se pudieron recibir bolillas válidas. Vuelve y reintenta; revisa conexión y permisos.")
    }
    fun matchedNumbers(): Set<Int> = play.numeros.toSet().intersect(state.value.balls.map { it.numero }.toSet())
    override fun close() {
        if (closed) return
        closed = true
        subscriptions.forEach { runCatching { it.remove() } }
        subscriptions.clear()
    }
}
