package kotlinx.concurrent

import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

/**
 * @author Sergey Mashkov
 */

class CompletedFuture<T>(val value : T) : Future<T> {
    override fun cancel(mayInterruptIfRunning: Boolean) = false
    override fun isCancelled() = false
    override fun isDone() = true

    override fun get() = value
    override fun get(timeout: Long, unit: TimeUnit): T = get()
}

class FailedFuture<T>(val error : Throwable) : Future<T> {
    override fun cancel(mayInterruptIfRunning: Boolean) = false
    override fun isCancelled() = false
    override fun isDone() = true

    override fun get(): T = throw ExecutionException(error)
    override fun get(timeout: Long, unit: TimeUnit): T = get()
}

class ExternalFuture<T>(val onCancel : () -> Unit = {}) : Future<T> {
    private val initializedLatch = CountDownLatch(1)
    private var value : AtomicReference<Maybe<T>> = AtomicReference(Missing())

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        initializedLatch.countDown()
        if (isCancelled()) {
            onCancel()
            return true
        }
        return false
    }

    override fun isCancelled() = initializedLatch.getCount() == 0L && value.get() is Missing

    override fun isDone() = initializedLatch.getCount() == 0L && value.get() is Present

    override fun get(): T {
        initializedLatch.await()
        val v = value.get()
        return if (v is Present<T>) v.value else throw CancellationException()
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
        if (!initializedLatch.await(timeout, unit)) {
            throw TimeoutException()
        }
        return get()
    }

    tailRecursive
    fun populate(finalValue : T) {
        val v = value.get()
        if (v is Present) {
            throw IllegalStateException("Value is already present")
        }

        if (!value.compareAndSet(v, Present(finalValue))) {
            populate(finalValue)
        } else {
            initializedLatch.countDown()
        }
    }
}