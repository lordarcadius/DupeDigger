package com.vipuljha.dupedigger.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

fun NavBackStack<NavKey>.open(destination: NavKey) {
    add(destination)
}

/** Pops the top entry but never the last one; returns true if it popped. */
fun NavBackStack<NavKey>.back(): Boolean {
    if (size <= 1) return false
    removeLastOrNull()
    return true
}

/** Pops until the earliest occurrence of [destination] is on top; no-op if it is absent. */
fun NavBackStack<NavKey>.backTo(destination: NavKey) {
    val target = indexOf(destination)
    if (target < 0) return
    while (lastIndex > target) removeLastOrNull()
}
