package actors

import akka.actor.*
import kotlinx.coroutines.*
import java.time.Duration
import kotlin.coroutines.CoroutineContext

data class SearchRequest(val query: String)
data class SearchResult(val provider: String, val pages: List<String>)
data class TotalResult(val results: List<SearchResult>)

class SearchOrganizer(private val searchers: Set<Class<AbstractSearch>>) : UntypedAbstractActor() {
    private val results = mutableListOf<SearchResult>()

    override fun supervisorStrategy(): SupervisorStrategy {
        return OneForOneStrategy.stoppingStrategy()
    }

    override fun onReceive(message: Any) {
        when (message) {
            is SearchRequest -> {
                searchers.map {
                    context.actorOf(Props.create(it), it.simpleName)
                }.forEach {
                    it.tell(message, self)
                }
                context.receiveTimeout = Duration.ofSeconds(1)
            }
            is SearchResult -> {
                results += message
                if (results.size == 3) {
                    context.parent.tell(TotalResult(results.toList()), self)
                    context.stop(self)
                }
            }
            is ReceiveTimeout -> {
                context.parent.tell(TotalResult(results.toList()), self)
                context.stop(self)
            }
        }
    }
}

abstract class AbstractSearch : UntypedAbstractActor() {

    abstract suspend fun doSearch(query: String): List<String>

    override fun onReceive(message: Any?) {
        when (message) {
            is SearchRequest -> {
                context.actorOf(Props.create(CoroutineActor::class.java) {
                    CoroutineActor {
                        SearchResult(this::class.java.simpleName, doSearch(message.query))
                    }
                }).apply {
                    tell(Unit, sender)
                }
            }
        }
    }
}

private fun AbstractSearch.fakeSearch(): List<String> {
    return (1..10).map {
        "${this::class.java.simpleName}-$it"
    }
}

open class MockGoogleSearch : AbstractSearch() {
    override suspend fun doSearch(query: String): List<String> = fakeSearch()
}

open class MockYandexSearch : AbstractSearch() {
    override suspend fun doSearch(query: String): List<String> = fakeSearch()
}

open class MockBingSearch : AbstractSearch() {
    override suspend fun doSearch(query: String): List<String> = fakeSearch()
}

class CoroutineActor<T>(
    context: CoroutineContext = Dispatchers.IO,
    private val fn: suspend () -> T
) : UntypedAbstractActor(), CoroutineScope {
    private val job = Job()

    override val coroutineContext: CoroutineContext = job + context

    override fun postStop() {
        job.completeExceptionally(CancellationException("Actor stopped"))
    }

    override fun onReceive(message: Any?) {
        val sender = sender()
        launch {
            try {
                sender.tell(fn(), self)
            } catch (ex: CancellationException) {
                // ignore
            } catch (ex: Throwable) {
                sender.tell(ex, self)
            }
        }
    }
}

class SearchResultPrinter(private val searchers: Set<Class<AbstractSearch>>) : UntypedAbstractActor() {
    override fun onReceive(message: Any) {
        when (message) {
            is TotalResult -> {
                println(message)
            }
            else -> {
                context.actorOf(Props.create(SearchOrganizer::class.java, searchers)).tell(message, self)
            }
        }
    }
}

fun main() {
    ActorSystem.create("MySystem").apply {
        actorOf(
            Props.create(
                SearchResultPrinter::class.java,
                setOf(
                    MockGoogleSearch::class.java,
                    MockYandexSearch::class.java,
                    MockBingSearch::class.java
                )
            )
        ).apply {
            tell(SearchRequest("search"), ActorRef.noSender())
        }
    }
}