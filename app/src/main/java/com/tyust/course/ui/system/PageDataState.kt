package com.tyust.course.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel

/** Large, non-sensitive results stay in memory; small UI state uses saved state. */
class PageDataState {
    private val values = mutableMapOf<String, MutableState<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> state(key: String, initial: () -> T): MutableState<T> =
        values.getOrPut(key) { mutableStateOf(initial()) } as MutableState<T>
}

class PageDataViewModel : ViewModel() {
    private var account: String? = null
    private var data = PageDataState()

    fun forAccount(key: String): PageDataState {
        if (account != key) {
            account = key
            data = PageDataState()
        }
        return data
    }
}

val LocalPageDataState = staticCompositionLocalOf<PageDataState?> { null }

@Composable
fun <T> rememberPageData(key: String, initial: () -> T): MutableState<T> {
    val store = LocalPageDataState.current
    return remember(store, key) { store?.state(key, initial) ?: mutableStateOf(initial()) }
}
