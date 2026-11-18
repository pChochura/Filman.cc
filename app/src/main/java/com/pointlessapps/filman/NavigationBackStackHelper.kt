package com.pointlessapps.filman

import androidx.compose.runtime.snapshots.SnapshotStateList

internal fun SnapshotStateList<Route>.navigateToRoute(route: Route?) {
    if (route == null) {
        if (size > 1) {
            removeLastOrNull()
        }
        return
    }

    var routeToAdd = route
    if (routeToAdd is Route.Login) {
        val currentRoute = lastOrNull()
        if (currentRoute is Route.Login) {
            return
        }
        if (currentRoute != null) {
            routeToAdd = routeToAdd.copy(
                returnRoute = routeToAdd.returnRoute ?: currentRoute,
            )
            if (routeToAdd.replaceCurrentRoute) {
                removeLastOrNull()
            }
        }
    } else if (lastOrNull() is Route.Login) {
        val loginRoute = last() as Route.Login
        removeLastOrNull()

        if (!loginRoute.replaceCurrentRoute && lastOrNull() == routeToAdd) {
            return
        }
    }

    add(routeToAdd)
}

internal fun SnapshotStateList<Route>.navigateToTabRoute(route: Route) {
    removeAll { it.showNavigationBar }
    add(route)
}
