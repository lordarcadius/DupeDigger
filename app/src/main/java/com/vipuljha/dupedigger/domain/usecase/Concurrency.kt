package com.vipuljha.dupedigger.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope

/** How many files a scan stage reads at once. */
internal const val PARALLELISM = 4

/** Runs [transform] on at most [PARALLELISM] items at once and keeps the non-null results, in no particular order. */
internal suspend fun <T, R : Any> List<T>.mapConcurrently(transform: suspend (T) -> R?): List<R> = coroutineScope {
    val queue = Channel<T>(Channel.UNLIMITED)
    forEach { queue.trySend(it) }
    queue.close()
    List(minOf(PARALLELISM, size)) {
        async {
            val results = ArrayList<R>()
            for (item in queue) transform(item)?.let(results::add)
            results
        }
    }.awaitAll().flatten()
}
