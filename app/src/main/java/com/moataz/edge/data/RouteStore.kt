package com.moataz.edge.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RouteConfig(
    val id: String,
    val name: String,
    val source: String,
    val destination: String,
    val worker: String,
    val keyword: String,
    val enabled: Boolean
) {
    fun workerConfigJson(): String = JSONObject()
        .put("route_id", id)
        .put("route_name", name)
        .put("keyword", keyword)
        .toString()
}

class RouteStore(context: Context, private val legacyConfig: ConfigStore = ConfigStore(context)) {
    private val preferences = context.getSharedPreferences("edge_routes", Context.MODE_PRIVATE)

    fun all(): List<RouteConfig> {
        val raw = preferences.getString(KEY_ROUTES, null)
        if (raw.isNullOrBlank()) {
            return listOf(
                RouteConfig(
                    id = UUID.randomUUID().toString(),
                    name = "المسار الرئيسي",
                    source = legacyConfig.sourceChat(),
                    destination = legacyConfig.destinationChat(),
                    worker = "default_worker",
                    keyword = "",
                    enabled = true
                )
            ).also(::saveAll)
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        RouteConfig(
                            id = item.getString("id"),
                            name = item.optString("name", "مسار"),
                            source = item.optString("source"),
                            destination = item.optString("destination"),
                            worker = item.optString("worker", "default_worker"),
                            keyword = item.optString("keyword"),
                            enabled = item.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun upsert(route: RouteConfig) {
        val routes = all().toMutableList()
        val index = routes.indexOfFirst { it.id == route.id }
        if (index >= 0) routes[index] = route else routes += route
        saveAll(routes)
    }

    fun add(name: String, source: String, destination: String, worker: String, keyword: String): RouteConfig {
        return RouteConfig(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "مسار جديد" },
            source = source.trim(),
            destination = destination.trim(),
            worker = worker.ifBlank { "default_worker" },
            keyword = keyword.trim(),
            enabled = true
        ).also(::upsert)
    }

    fun setEnabled(id: String, enabled: Boolean) {
        all().firstOrNull { it.id == id }?.let { upsert(it.copy(enabled = enabled)) }
    }

    fun delete(id: String) = saveAll(all().filterNot { it.id == id })

    private fun saveAll(routes: List<RouteConfig>) {
        val array = JSONArray()
        routes.forEach { route ->
            array.put(
                JSONObject()
                    .put("id", route.id)
                    .put("name", route.name)
                    .put("source", route.source)
                    .put("destination", route.destination)
                    .put("worker", route.worker)
                    .put("keyword", route.keyword)
                    .put("enabled", route.enabled)
            )
        }
        preferences.edit().putString(KEY_ROUTES, array.toString()).apply()
    }

    private companion object {
        const val KEY_ROUTES = "routes_json"
    }
}
