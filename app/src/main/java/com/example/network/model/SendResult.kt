package com.example.network.model

/**
 * Result of a real socket outputStream.write() and flush() operation.
 *
 * Rules:
 * - Only reports Success when bytes have actually been written and flushed to the TCP socket.
 * - Coroutine launch is NEVER considered a send success.
 */
sealed class SendResult {
    data class Success(
        val messageType: String,
        val bytesWritten: Int,
        val timestamp: Long = System.currentTimeMillis()
    ) : SendResult()

    data class Failure(
        val reason: String,
        val cause: Throwable? = null
    ) : SendResult() {
        val isClosedSocket: Boolean
            get() = cause is java.net.SocketException || reason.contains("closed", ignoreCase = true)
    }
}
