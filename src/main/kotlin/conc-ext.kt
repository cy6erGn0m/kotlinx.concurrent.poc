package kotlinx.concurrent

/**
 * @author Sergey Mashkov
 */

trait Maybe<T>
    data class Missing<T> : Maybe<T>
    data class Present<T>(val value : T) : Maybe<T>