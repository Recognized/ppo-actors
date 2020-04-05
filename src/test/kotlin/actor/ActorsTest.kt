package actor

import actors.*
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.time.Duration

class MockSlowYandexSearch : MockYandexSearch() {
    override suspend fun doSearch(query: String): List<String> {
        delay(5000)
        return super.doSearch(query)
    }
}

class MockSlowBingSearch : MockBingSearch() {
    override suspend fun doSearch(query: String): List<String> {
        delay(5000)
        return super.doSearch(query)
    }
}

class ActorsTest {

    @Test
    fun `test all providers fast`() {
        doTest(setOf(MockYandexSearch::class.java, MockGoogleSearch::class.java, MockBingSearch::class.java)) {

            within(Duration.ofSeconds(3)) {
                val result: TotalResult = expectMsgAnyClassOf(TotalResult::class.java)
                assert(result.results.size == 3)

                expectNoMessage()
            }
        }
    }

    @Test
    fun `test not enough providers`() {
        doTest(setOf(MockGoogleSearch::class.java, MockBingSearch::class.java)) {

            within(Duration.ofSeconds(3)) {
                val result: TotalResult = expectMsgAnyClassOf(TotalResult::class.java)
                assert(result.results.size == 2)

                expectNoMessage()
            }
        }
    }

    @Test
    fun `test some providers are slow`() {
        doTest(setOf(MockGoogleSearch::class.java, MockSlowBingSearch::class.java, MockSlowYandexSearch::class.java)) {
            within(Duration.ofSeconds(3)) {
                val result: TotalResult = expectMsgAnyClassOf(TotalResult::class.java)
                assert(result.results.size == 1)

                expectNoMessage()
            }
        }
    }

    @Test
    fun `test all providers are slow`() {
        doTest(setOf(MockSlowBingSearch::class.java, MockSlowYandexSearch::class.java)) {
            within(Duration.ofSeconds(3)) {
                val result: TotalResult = expectMsgAnyClassOf(TotalResult::class.java)
                assert(result.results.isEmpty())

                expectNoMessage()
            }
        }
    }

    private fun doTest(classes: Set<Class<out AbstractSearch>>, block: TestKit.() -> Unit) {
        with(TestKit(system)) {
            val searcher = Props.create(SearchOrganizer::class.java, classes)
            val actor = childActorOf(searcher)
            actor.tell(SearchRequest("test"), ref)
            block()
        }
    }

    companion object {
        private val system = ActorSystem.create()

        @AfterAll
        fun tearDown() {
            TestKit.shutdownActorSystem(system)
        }
    }
}