package com.example.network.service

import com.example.network.model.AckMsg
import com.example.network.model.NumberResultMsg
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe request-response registry that correlates outgoing requests
 * with matching incoming ACK and NUMBER_RESULT packets using the same unique requestId.
 */
class NetworkRequestRegistry {

    private val pendingAcks = ConcurrentHashMap<String, CompletableDeferred<AckMsg>>()
    private val pendingResults = ConcurrentHashMap<String, CompletableDeferred<NumberResultMsg>>()

    fun registerPendingAck(requestId: String): CompletableDeferred<AckMsg> {
        val deferred = CompletableDeferred<AckMsg>()
        pendingAcks[requestId] = deferred
        return deferred
    }

    fun registerPendingResult(requestId: String): CompletableDeferred<NumberResultMsg> {
        val deferred = CompletableDeferred<NumberResultMsg>()
        pendingResults[requestId] = deferred
        return deferred
    }

    fun dispatchAck(ack: AckMsg): Boolean {
        val deferred = pendingAcks.remove(ack.requestId)
        return deferred?.complete(ack) == true
    }

    fun dispatchResult(result: NumberResultMsg): Boolean {
        val deferred = pendingResults.remove(result.requestId)
        return deferred?.complete(result) == true
    }

    fun remove(requestId: String) {
        pendingAcks.remove(requestId)
        pendingResults.remove(requestId)
    }

    fun cancelAll(reason: String = "Connection reset") {
        val cancellation = java.util.concurrent.CancellationException(reason)
        pendingAcks.values.forEach { it.cancel(cancellation) }
        pendingAcks.clear()
        pendingResults.values.forEach { it.cancel(cancellation) }
        pendingResults.clear()
    }
}
