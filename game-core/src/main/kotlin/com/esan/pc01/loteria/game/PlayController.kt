package com.esan.pc01.loteria.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Sorteo(val id: String, val nombre: String, val fecha: String, val estado: String)
data class Jugada(val sorteoId: String, val uid: String, val numeros: List<Int>)
class PlayRejected(message: String) : Exception(message)

fun validNumbers(numbers: List<Int>): Boolean =
    numbers.size == 6 && numbers.toSet().size == 6 && numbers.all { it in 1..45 }

fun parseNumbers(input: String): List<Int>? {
    val parts = input.split(',').map { it.trim() }
    val values = parts.map { it.toIntOrNull() ?: return null }
    return values.takeIf(::validNumbers)
}

interface PlayGateway {
    fun draws(done: (Result<List<Sorteo>>) -> Unit)
    fun ownPlay(sorteoId: String, uid: String, done: (Result<Jugada?>) -> Unit)
    fun create(play: Jugada, done: (Result<Jugada>) -> Unit)
}

data class PlayState(
    val draws: List<Sorteo> = emptyList(), val selected: Sorteo? = null,
    val play: Jugada? = null, val busy: Boolean = false,
    val ownPlayLoaded: Boolean = false, val message: String? = null
)

class PlayController(private val uid: String, private val gateway: PlayGateway) : AutoCloseable {
    init { require(uid.isNotBlank()) }
    private val mutable = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = mutable.asStateFlow()
    private var generation = 0
    private var closed = false

    fun loadDraws() {
        if (closed || state.value.busy) return
        val current = ++generation
        mutable.value = PlayState(busy = true)
        gateway.draws { result ->
            if (!closed && current == generation) result.fold(
                onSuccess = { draws -> mutable.value = PlayState(draws = draws) },
                onFailure = { mutable.value = PlayState(message = readable(it)) }
            )
        }
    }

    fun select(draw: Sorteo) {
        if (closed || state.value.busy || draw !in state.value.draws) return
        val current = ++generation
        mutable.value = state.value.copy(selected = draw, play = null, busy = true, ownPlayLoaded = false, message = null)
        gateway.ownPlay(draw.id, uid) { result ->
            if (!closed && current == generation) result.fold(
                onSuccess = { play ->
                    if (play != null && !matches(play, draw.id)) {
                        mutable.value = state.value.copy(busy = false, message = "La jugada almacenada no coincide con esta sesión o es inválida.")
                    } else mutable.value = state.value.copy(play = play, busy = false, ownPlayLoaded = true)
                },
                onFailure = { mutable.value = state.value.copy(busy = false, message = readable(it)) }
            )
        }
    }

    fun back() {
        if (closed || state.value.busy) return
        generation++
        mutable.value = state.value.copy(selected = null, play = null, ownPlayLoaded = false, message = null)
    }

    fun submit(input: String) {
        if (closed || state.value.busy) return
        val draw = state.value.selected ?: return
        val error = when {
            !state.value.ownPlayLoaded -> "Primero carga tu jugada guardada."
            state.value.play != null -> "Ya tienes una jugada para este sorteo."
            draw.estado != "abierto" -> "El sorteo no está abierto. No se pueden registrar jugadas."
            else -> null
        }
        if (error != null) { mutable.value = state.value.copy(message = error); return }
        val numbers = parseNumbers(input)
        if (numbers == null) {
            mutable.value = state.value.copy(message = "Ingresa seis enteros distintos del 1 al 45 separados por comas."); return
        }
        val current = ++generation
        val play = Jugada(draw.id, uid, numbers)
        mutable.value = state.value.copy(busy = true, message = null)
        gateway.create(play) { result ->
            if (!closed && current == generation) result.fold(
                onSuccess = { saved ->
                    if (saved != play) mutable.value = state.value.copy(busy = false, message = "No se pudo confirmar la jugada guardada. Recarga el sorteo.")
                    else mutable.value = state.value.copy(play = saved, busy = false, message = "Jugada guardada.")
                },
                onFailure = { mutable.value = state.value.copy(busy = false, message = readable(it)) }
            )
        }
    }

    private fun matches(play: Jugada, drawId: String) =
        play.uid == uid && play.sorteoId == drawId && validNumbers(play.numeros)
    private fun readable(error: Throwable) = if (error is PlayRejected) error.message else "No se pudo completar la operación. Revisa tu conexión, sesión y reglas de Firestore."
    override fun close() { closed = true; generation++ }
}
