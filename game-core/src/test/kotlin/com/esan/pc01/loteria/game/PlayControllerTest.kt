package com.esan.pc01.loteria.game

import org.junit.Assert.*
import org.junit.Test

class PlayControllerTest {
    private val open = Sorteo("prueba", "Lotería", "10/10/2026", "abierto")
    private val nums = listOf(5,12,18,23,34,41)
    private class Fake : PlayGateway {
        var catalog: List<Sorteo> = emptyList()
        var stored: Jugada? = null
        var reads = 0
        var writes = 0
        var readUid = ""
        var sent: Jugada? = null
        var deferred = false
        var readCallback: ((Result<Jugada?>) -> Unit)? = null
        var saveCallback: ((Result<Jugada>) -> Unit)? = null
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        override fun draws(done: (Result<List<Sorteo>>) -> Unit) = done(Result.success(catalog))
        override fun ownPlay(sorteoId: String, uid: String, done: (Result<Jugada?>) -> Unit) {
            reads++; readUid = uid; readCallback = done
            if (!deferred) done(readFailure?.let { Result.failure(it) } ?: Result.success(stored))
        }
        override fun create(play: Jugada, done: (Result<Jugada>) -> Unit) {
            writes++; sent = play; saveCallback = done
            if (!deferred) {
                if (writeFailure != null) done(Result.failure(writeFailure!!))
                else { stored = play; done(Result.success(play)) }
            }
        }
    }
    private fun ready(g: Fake = Fake(), draw: Sorteo = open): Pair<PlayController, Fake> {
        g.catalog = listOf(draw)
        val c = PlayController("alice", g); c.loadDraws(); c.select(draw); return c to g
    }
    @Test fun sixDifferentIntegersWithinRangeAccepted() { assertEquals(nums, parseNumbers("5, 12,18,23,34,41")) }
    @Test fun inclusiveLimitsAccepted() { assertTrue(validNumbers(listOf(1,2,3,4,5,45))) }
    @Test fun duplicateRejected() { assertNull(parseNumbers("1,2,3,4,5,5")) }
    @Test fun wrongCountRejected() { for (text in listOf("1,2,3,4,5", "1,2,3,4,5,6,7", "")) assertNull(parseNumbers(text)) }
    @Test fun rangeRejected() { for(text in listOf("0,2,3,4,5,6", "1,2,3,4,5,46", "-1,2,3,4,5,6")) assertNull(parseNumbers(text)) }
    @Test fun decimalTextAndOverflowRejected() { for(text in listOf("1,2,3,4,5,6.0", "1,2,3,4,5,x", "1,2,3,4,5,999999999999")) assertNull(parseNumbers(text)) }
    @Test fun missingTokenRejected() { assertNull(parseNumbers("1,2,3,4,5,")) }
    @Test fun ownPlayReadUsesSessionUid() { val (_,g)=ready(); assertEquals("alice",g.readUid);assertEquals(1,g.reads) }
    @Test fun saveCarriesDrawIdUidAndNumbers() {
        val (c,g)=ready();c.submit("5,12,18,23,34,41")
        assertEquals(Jugada("prueba","alice",nums),g.sent);assertEquals(g.sent,c.state.value.play);assertEquals(1,g.writes)
    }
    @Test fun savedPlayCannotBeOverwrittenBySecondSubmit() {
        val (c,g)=ready();c.submit("5,12,18,23,34,41");c.submit("1,2,3,4,5,6");assertEquals(1,g.writes)
    }
    @Test fun closedStatesCannotCreate() {
        for(status in listOf("en_curso","finalizado","desconocido")) {
            val (c,g)=ready(draw=open.copy(estado=status));c.submit("1,2,3,4,5,6");assertEquals(0,g.writes)
        }
    }
    @Test fun persistedPlayRestoredAndNotEditable() {
        val g=Fake();g.stored=Jugada("prueba","alice",nums);val (c,_)=ready(g)
        assertEquals(g.stored,c.state.value.play);c.submit("1,2,3,4,5,6");assertEquals(0,g.writes)
    }
    @Test fun closedDrawStillRecoversExistingPlay() {
        val g=Fake();g.stored=Jugada("prueba","alice",nums);val (c,_)=ready(g,open.copy(estado="finalizado"))
        assertEquals(g.stored,c.state.value.play)
    }
    @Test fun foreignOrMalformedStoredPayloadRejected() {
        for(play in listOf(Jugada("prueba","bob",nums),Jugada("otro","alice",nums),Jugada("prueba","alice",listOf(1,2)))) {
            val g=Fake();g.stored=play;val (c,_)=ready(g);assertNull(c.state.value.play);assertFalse(c.state.value.ownPlayLoaded)
            c.submit("1,2,3,4,5,6");assertEquals(0,g.writes)
        }
    }
    @Test fun failedReadCannotLeadToCreate() {
        val g=Fake();g.readFailure=Exception("private error");val (c,_)=ready(g);c.submit("1,2,3,4,5,6")
        assertEquals(0,g.writes);assertFalse(c.state.value.ownPlayLoaded)
    }
    @Test fun invalidInputDoesNotWrite() { val(c,g)=ready();c.submit("1,1,2,3,4,5");assertEquals(0,g.writes);assertNotNull(c.state.value.message) }
    @Test fun loadingAndDuplicateRequestsCannotWrite() {
        val g=Fake();g.deferred=true;val(c,_)=ready(g);c.submit("1,2,3,4,5,6");assertEquals(0,g.writes)
        g.readCallback!!(Result.success(null));c.submit("1,2,3,4,5,6");c.submit("1,2,3,4,5,6");assertEquals(1,g.writes)
    }
    @Test fun transactionCloseOrExistingRaceIsReportedWithoutSavedPlay() {
        for(message in listOf("El sorteo ya no está abierto.","Ya tienes una jugada.")) {
            val g=Fake();g.writeFailure=PlayRejected(message);val(c,_)=ready(g);c.submit("1,2,3,4,5,6")
            assertNull(c.state.value.play);assertFalse(c.state.value.busy);assertEquals(message,c.state.value.message)
        }
    }
    @Test fun disposeIgnoresLateReadAndSaveCallbacks() {
        val g=Fake();g.deferred=true;val(c,_)=ready(g);c.close();val old=c.state.value
        g.readCallback!!(Result.success(Jugada("prueba","alice",nums)));assertEquals(old,c.state.value)
        val h=Fake();val(d,_)=ready(h);h.deferred=true;d.submit("1,2,3,4,5,6");d.close();val prior=d.state.value
        h.saveCallback!!(Result.success(h.sent!!));assertEquals(prior,d.state.value)
    }
    @Test fun backClearsPreviousUserPlayAndSelection() {
        val g=Fake();g.stored=Jugada("prueba","alice",nums);val(c,_)=ready(g);c.back()
        assertNull(c.state.value.selected);assertNull(c.state.value.play);assertFalse(c.state.value.ownPlayLoaded)
    }
    @Test fun mismatchedSaveResultIsNotTrusted() {
        val g=Fake();val(c,_)=ready(g);g.deferred=true;c.submit("1,2,3,4,5,6")
        g.saveCallback!!(Result.success(Jugada("otro","bob",nums)));assertNull(c.state.value.play);assertNotNull(c.state.value.message)
    }
}
