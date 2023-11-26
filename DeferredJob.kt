import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun demo(scope: CoroutineScope) {
    scope.launch {
        val job1 = scope.safeAsync {
            "Hello 1"
        }
        val job2 = scope.safeAsync<String> {
            throw Exception("Error occured")
        }
        print(job1.await())
        try {
            print(job2.await())
        } catch (e: Exception) {
            print(e)
        }
    }
}

class DeferredJob<T>(
    private val scope: CoroutineScope,
    private val context: CoroutineContext,
    private val block: suspend CoroutineScope.() -> T,
    val lazyStart: Boolean,
) {
    private var job: Job? = null
    private var exception: Exception? = null
    private var result: T? = null
    private var hasConsumed = false

    var isStarted = false
    init {
        if (!lazyStart) {
            start()
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun isCompleted(): Boolean {
        return job?.isCompleted == true
    }

    fun start() {
        if (isStarted) {
            throw Exception("Already started")
        }
        isStarted = true
        job = scope.launch(context) {
            try {
                result = block()
            } catch (e: Exception) {
                exception = e
                if (e is CancellationException) {
                    throw e
                }
            }
        }
    }

    suspend fun await(): T {
        if (!hasConsumed) {
            if (!isStarted) {
                throw Exception("Not started yet")
            }
            job!!.join()
            hasConsumed = true
        }
        if (exception != null) {
            throw exception!!
        }
        return result!!
    }
}

fun <T> CoroutineScope.safeAsync(
    context: CoroutineContext = EmptyCoroutineContext,
    lazyStart: Boolean = false,
    block: suspend CoroutineScope.() -> T,
): DeferredJob<T> {
    return DeferredJob(
        scope = this,
        context = context,
        block = block,
        lazyStart = lazyStart
    )
}
