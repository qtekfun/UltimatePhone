package com.qtekfun.ultimatephone

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.internal.MainDispatcherFactory

/**
 * Unit tests run on the JVM, where Android's `Dispatchers.Main` cannot start ("Looper not mocked"). Tests that need a main
 * dispatcher call `Dispatchers.setMain(...)` and `resetMain()`. A coroutine left over from an earlier test (for example the
 * 5-second `WhileSubscribed` timeout of a ViewModel) can resume after `resetMain()`, touch the real main dispatcher and fail
 * an unrelated test, depending only on timing. This factory, picked ahead of Android's by `kotlinx-coroutines-test`, makes
 * the fallback main dispatcher a harmless one that runs work immediately.
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class SafeMainDispatcherFactory : MainDispatcherFactory {
    override val loadPriority: Int = Int.MAX_VALUE - 1

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>): MainCoroutineDispatcher = SafeMain

    private object SafeMain : MainCoroutineDispatcher() {
        override val immediate: MainCoroutineDispatcher get() = this

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = false

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            (Dispatchers.Unconfined as CoroutineDispatcher).dispatch(context, block)
        }
    }
}
