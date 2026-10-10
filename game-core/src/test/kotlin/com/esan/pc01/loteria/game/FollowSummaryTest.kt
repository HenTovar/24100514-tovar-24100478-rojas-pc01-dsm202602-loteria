package com.esan.pc01.loteria.game

import org.junit.Assert.*
import org.junit.Test

class FollowSummaryTest {
    private val a=Jugada("demo","A",listOf(5,12,18,23,34,41))
    private val b=a.copy(uid="B",numeros=listOf(5,12,18,23,34,42))
    private val balls=a.numeros.mapIndexed { i,n -> Bolilla(i+1,n) }
    private fun snapshot(status:String="finalizado", published:List<Bolilla> = balls)=FollowState(
        header=Sorteo("demo","Demo","10/10/2026",status),balls=published,headerLoading=false,ballsLoading=false)
    @Test fun demoAWinsAndBHasFiveHits() {
        assertEquals(FollowSummary(6,a.numeros.toSet(),"GANADOR"),summarizeFollow(a,snapshot()))
        assertEquals(FollowSummary(6,setOf(5,12,18,23,34),"SIGUE INTENTANDO"),summarizeFollow(b,snapshot()))
    }
    @Test fun eachPublishedBallUpdatesCountsWithoutPrematureResult() {
        for(i in 0..6) {
            val s=summarizeFollow(a,snapshot("en_curso",balls.take(i)))
            assertEquals(i,s.published);assertEquals(i,s.matched.size);assertNull(s.finalResult)
        }
    }
    @Test fun finalStateWithOnlyFiveBallsDoesNotProduceResult() {
        assertNull(summarizeFollow(a,snapshot(published=balls.take(5))).finalResult)
    }
    @Test fun sixBallsInOpenStateDoNotProduceResult() {
        assertNull(summarizeFollow(a,snapshot("abierto")).finalResult)
    }
    @Test fun outcomeRequiresBothStreamsRegardlessOfCallbackOrder() {
        val initial=FollowState(); val headerFirst=initial.copy(header=snapshot().header,headerLoading=false)
        assertNull(summarizeFollow(a,headerFirst).finalResult)
        assertEquals("GANADOR",summarizeFollow(a,headerFirst.copy(balls=balls,ballsLoading=false)).finalResult)
        val ballsFirst=initial.copy(balls=balls,ballsLoading=false)
        assertNull(summarizeFollow(a,ballsFirst).finalResult)
        assertEquals("GANADOR",summarizeFollow(a,ballsFirst.copy(header=snapshot().header,headerLoading=false)).finalResult)
    }
    @Test fun errorsLoadingAndForeignHeaderBlockTheFinalResult() {
        val s=snapshot()
        for(state in listOf(s.copy(headerError="error"),s.copy(ballsError="error"),s.copy(headerLoading=true),
            s.copy(ballsLoading=true),s.copy(header=null),s.copy(header=s.header!!.copy(id="otro"))))
            assertNull(summarizeFollow(a,state).finalResult)
    }
    @Test fun malformedOrDuplicateBallsCannotMakeAWinner() {
        for(list in listOf(balls.dropLast(1)+Bolilla(6,34),balls.dropLast(1)+Bolilla(5,41),
            balls.dropLast(1)+Bolilla(6,46),balls+Bolilla(7,42))) {
            val s=summarizeFollow(a,snapshot(published=list)); assertNull(s.finalResult);assertEquals(0,s.published)
        }
    }
    @Test fun zeroHitsAtCompletedDrawIsTryAgain() {
        val p=a.copy(numeros=listOf(1,2,3,4,6,7));val s=summarizeFollow(p,snapshot())
        assertEquals(0,s.matched.size);assertEquals("SIGUE INTENTANDO",s.finalResult)
    }
    @Test fun reopensFromStoredPlayAndCurrentSnapshotsNotStoredResults() {
        // Simulate immutable Firestore documents; PlayController performs the real restoration path.
        val stored=mapOf("A" to a,"B" to b)
        val gateway=object:PlayGateway {
            override fun draws(done:(Result<List<Sorteo>>)->Unit){done(Result.success(listOf(snapshot().header!!)))}
            override fun ownPlay(sorteoId:String,uid:String,done:(Result<Jugada?>)->Unit){done(Result.success(stored[uid]))}
            override fun create(play:Jugada,done:(Result<Jugada>)->Unit){fail("Reopening must not write a play")}
        }
        var removed=0
        val live=object:FollowGateway {
            override fun header(sorteoId:String,done:(Result<Sorteo>)->Unit):FollowSubscription {
                done(Result.success(snapshot().header!!));return FollowSubscription { removed++ }
            }
            override fun balls(sorteoId:String,done:(Result<List<Bolilla>>)->Unit):FollowSubscription {
                done(Result.success(balls));return FollowSubscription { removed++ }
            }
        }
        for(uid in listOf("A","B")) repeat(2) {
            val playController=PlayController(uid,gateway);playController.loadDraws();playController.select(playController.state.value.draws.single())
            val restored=playController.state.value.play!!; val follow=FollowController(restored,live);follow.start()
            assertEquals(if(uid=="A")"GANADOR" else "SIGUE INTENTANDO",summarizeFollow(restored,follow.state.value).finalResult)
            assertEquals(if(uid=="A")6 else 5,summarizeFollow(restored,follow.state.value).matched.size)
            follow.close();playController.close()
        }
        assertEquals(8,removed)
    }
}
