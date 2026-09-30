package com.technomancer.pangu.app

import kotlin.reflect.KClass

/**
 * The only holder of app-wide state. Populated once from mainApp; everything else
 * receives its dependencies from here instead of declaring its own singleton.
 * Registering a type twice or resolving a missing type throws.
 */
object ServiceRegistry {
    private val services = HashMap<KClass<*>, Any>()

    fun <T : Any> register(type: KClass<T>, instance: T) {
        synchronized(services) {
            check(!services.containsKey(type)) { "Service already registered: ${type.qualifiedName}" }
            services[type] = instance
        }
    }

    inline fun <reified T : Any> register(instance: T) = register(T::class, instance)

    fun <T : Any> tryResolve(type: KClass<T>): T? = synchronized(services) {
        @Suppress("UNCHECKED_CAST")
        services[type] as T?
    }

    fun <T : Any> resolve(type: KClass<T>): T =
        tryResolve(type) ?: throw IllegalStateException("Service not registered: ${type.qualifiedName}")

    inline fun <reified T : Any> resolve(): T = resolve(T::class)

    inline fun <reified T : Any> tryResolve(): T? = tryResolve(T::class)

    fun clear() {
        synchronized(services) { services.clear() }
    }
}
