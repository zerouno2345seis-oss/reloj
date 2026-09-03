package com.quran.watch8.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * Runs [block] only while the activity is resumed, restarting it on the next resume.
 *
 * A plain `LaunchedEffect { while (true) { delay(...) } }` is scoped to the
 * *composition*, not the lifecycle. This app declares `category.HOME`, so its
 * activity is effectively never destroyed — the composition survives every
 * screen-off, and a bare ticker keeps waking the CPU forever behind a dark
 * screen. Scoping to RESUMED cancels the coroutine at onPause and starts a
 * fresh one when the user looks at the watch again.
 */
@Composable
fun RepeatOnResumed(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, *keys) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            block()
        }
    }
}
