package com.esan.pc01.loteria.game

import org.junit.Assert.*
import org.junit.Test

class FollowControllerTest {
    private val play = Jugada("sorteo-prueba", "uid-prueba", listOf(5, 12, 18, 23, 34, 41))
    private fun draw(id: String = play.sorteoId, status: String = "abierto") = Sorteo(id, "Prueba", "10/10/2026", status)
    private class Fake : FollowGateway {
        var headers = 0; var ballLists = 0; var removals = 0
        var throwHeader = false; var throwBalls = false; var throwRemoval = false
        val paths = mutableListOf<String>()
        lateinit var headerCallback: (Result<Sorteo>) -> Unit
        lateinit var ballsCallback: (Result<List<Bolilla>>) -> Unit
        override fun header(sorteoId: String, done: (Result<Sorteo>) -> Unit): FollowSubscription {
            headers++; paths.add(sorteoId); headerCallback = done
            if (throwHeader) throw IllegalStateException("register error")
            return FollowSubscription { removals++; if (throwRemoval) throw IllegalStateException() }
        }
        override fun balls(sorteoId: String, done: (Result<List<Bolilla>>) -> Unit): FollowSubscription {
            ballLists++; paths.add(sorteoId); ballsCallback = done
            if (throwBalls) throw IllegalStateException("register error")
            return FollowSubscription { removals++ }
        }
    }
    @Test fun startCreatesExactlyTwoSubscriptionsEvenWhenCalledTwice() {
        val g=Fake(); val c=FollowController(play,g); c.start(); c.start()
        assertEquals(1,g.headers); assertEquals(1,g.ballLists); assertEquals(listOf(play.sorteoId,play.sorteoId),g.paths)
    }
    @Test fun closeRemovesBothExactlyOnce() {
        val g=Fake(); val c=FollowController(play,g); c.start(); c.close(); c.close(); c.start()
        assertEquals(2,g.removals); assertEquals(1,g.headers)
    }
    @Test fun closeBeforeStartCreatesNoSubscriptions() {
        val g=Fake(); val c=FollowController(play,g); c.close(); c.start(); assertEquals(0,g.headers)
    }
    @Test fun lateCallbacksAfterLeavingCannotChangeState() {
        val g=Fake(); val c=FollowController(play,g); c.start(); c.close(); val before=c.state.value
        g.headerCallback(Result.success(draw())); g.ballsCallback(Result.success(listOf(Bolilla(1,5))))
        assertEquals(before,c.state.value); assertTrue(c.matchedNumbers().isEmpty())
    }
    @Test fun reopenCreatesOnePairAndRemovesPreviousPair() {
        val g=Fake(); FollowController(play,g).also { it.start(); it.close() }
        val second=FollowController(play,g); second.start()
        assertEquals(2,g.headers); assertEquals(2,g.ballLists); assertEquals(2,g.removals)
        second.close(); assertEquals(4,g.removals)
    }
    @Test fun listIsSortedByOrderNotByNumber() {
        val g=Fake();val c=FollowController(play,g);c.start()
        g.ballsCallback(Result.success(listOf(Bolilla(3,5),Bolilla(1,41),Bolilla(2,12))))
        assertEquals(listOf(41,12,5),c.state.value.balls.map { it.numero })
    }
    @Test fun repeatedSnapshotReplacesInsteadOfAppending() {
        val g=Fake();val c=FollowController(play,g);c.start();val balls=listOf(Bolilla(1,5))
        repeat(3) { g.ballsCallback(Result.success(balls)) }; assertEquals(balls,c.state.value.balls)
        g.ballsCallback(Result.success(emptyList()));assertTrue(c.state.value.balls.isEmpty())
    }
    @Test fun matchedHighlightOnlyIntersectsTheUsersNumbers() {
        val g=Fake();val c=FollowController(play,g);c.start()
        g.ballsCallback(Result.success(listOf(Bolilla(1,5),Bolilla(2,42),Bolilla(3,12))))
        assertEquals(setOf(5,12),c.matchedNumbers()); assertEquals(play.numeros,c.play.numeros)
    }
    @Test fun allSixBallsHaveNoStoredResultOrExtraSubscription() {
        val g=Fake();val c=FollowController(play,g);c.start()
        g.ballsCallback(Result.success(play.numeros.mapIndexed { i,n -> Bolilla(i+1,n) }))
        g.headerCallback(Result.success(draw(status="finalizado")))
        assertEquals(play.numeros.toSet(),c.matchedNumbers());assertEquals(1,g.headers);assertEquals(1,g.ballLists)
    }
    @Test fun headerStatesUpdateIndependentlyOfBalls() {
        val g=Fake();val c=FollowController(play,g);c.start()
        for(s in listOf("abierto","en_curso","finalizado")) {
            g.headerCallback(Result.success(draw(status=s))); assertEquals(s,c.state.value.header?.estado)
        }
        assertTrue(c.state.value.ballsLoading);assertFalse(c.state.value.headerLoading)
    }
    @Test fun foreignHeaderIsRejected() {
        val g=Fake();val c=FollowController(play,g);c.start();g.headerCallback(Result.success(draw(id="ajeno")))
        assertNull(c.state.value.header);assertNotNull(c.state.value.headerError)
    }
    @Test fun invalidHeaderStatusIsRejected() {
        val g=Fake();val c=FollowController(play,g);c.start();g.headerCallback(Result.success(draw(status="invalido")))
        assertNotNull(c.state.value.headerError)
    }
    @Test fun duplicateOrderOrNumberIsRejectedWithoutHighlights() {
        val g=Fake();val c=FollowController(play,g);c.start()
        for(b in listOf(listOf(Bolilla(1,5),Bolilla(1,12)),listOf(Bolilla(1,5),Bolilla(2,5)))) {
            g.ballsCallback(Result.success(b));assertTrue(c.state.value.balls.isEmpty());assertNotNull(c.state.value.ballsError)
        }
    }
    @Test fun invalidBallRangesAndMoreThanSixAreRejected() {
        val g=Fake();val c=FollowController(play,g);c.start()
        for(b in listOf(listOf(Bolilla(0,5)),listOf(Bolilla(7,5)),listOf(Bolilla(1,0)),listOf(Bolilla(1,46)),(1..7).map{Bolilla(it,it)})) {
            g.ballsCallback(Result.success(b));assertNotNull(c.state.value.ballsError)
        }
    }
    @Test fun listenerErrorClearsStaleDataThenValidSnapshotRecovers() {
        val g=Fake();val c=FollowController(play,g);c.start();g.headerCallback(Result.success(draw()));g.ballsCallback(Result.success(listOf(Bolilla(1,5))))
        g.headerCallback(Result.failure(Exception("raw secret")));g.ballsCallback(Result.failure(Exception("raw secret")))
        assertNull(c.state.value.header);assertTrue(c.state.value.balls.isEmpty());assertFalse(c.state.value.headerError!!.contains("raw secret"))
        g.headerCallback(Result.success(draw()));g.ballsCallback(Result.success(emptyList()))
        assertNull(c.state.value.headerError);assertNull(c.state.value.ballsError)
    }
    @Test fun headerRegistrationFailureDoesNotCrashOrLoseOtherHandle() {
        val g=Fake();g.throwHeader=true;val c=FollowController(play,g);c.start()
        assertNotNull(c.state.value.headerError);c.close();assertEquals(1,g.removals)
    }
    @Test fun ballRegistrationFailureDoesNotCrashOrLoseHeaderHandle() {
        val g=Fake();g.throwBalls=true;val c=FollowController(play,g);c.start()
        assertNotNull(c.state.value.ballsError);c.close();assertEquals(1,g.removals)
    }
    @Test fun failingRemoveDoesNotPreventSecondRemove() {
        val g=Fake();g.throwRemoval=true;val c=FollowController(play,g);c.start();c.close();assertEquals(2,g.removals)
    }
    @Test fun invalidOwnPlayIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { FollowController(play.copy(numeros=listOf(1,1,2,3,4,5)),Fake()) }
        assertThrows(IllegalArgumentException::class.java) { FollowController(play.copy(uid=""),Fake()) }
    }
}
