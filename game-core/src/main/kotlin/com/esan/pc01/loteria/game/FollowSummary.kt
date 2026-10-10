package com.esan.pc01.loteria.game

/** Valores derivados del snapshot actual: nunca se guardan en Firestore. */
data class FollowSummary(val published: Int, val matched: Set<Int>, val finalResult: String?)

fun summarizeFollow(play: Jugada, state: FollowState): FollowSummary {
    if (!validNumbers(play.numeros) || state.ballsLoading || state.ballsError != null || !validBalls(state.balls))
        return FollowSummary(0, emptySet(), null)
    val matched = play.numeros.toSet().intersect(state.balls.map { it.numero }.toSet())
    val complete = !state.headerLoading && state.headerError == null &&
        state.header?.id == play.sorteoId && state.header?.estado == "finalizado" && state.balls.size == 6
    val result = if (!complete) null else if (matched.size == 6) "GANADOR" else "SIGUE INTENTANDO"
    return FollowSummary(state.balls.size, matched, result)
}
