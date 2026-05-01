package com.npusensei.app.circuit

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json

class BlueprintRepository(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {
    fun listBlueprintIds(): List<String> {
        return runCatching {
            context.assets.list(ASSET_DIR)?.toList().orEmpty()
        }.getOrDefault(emptyList())
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }
    }

    fun load(id: String): CircuitBlueprint? = runCatching {
        context.assets.open("$ASSET_DIR/$id.json").use { stream ->
            json.decodeFromString(CircuitBlueprint.serializer(), stream.bufferedReader().readText())
        }
    }.onFailure { Log.e(TAG, "Failed to load blueprint $id", it) }
        .getOrNull()

    fun loadAll(): List<CircuitBlueprint> = listBlueprintIds().mapNotNull(::load)

    companion object {
        private const val TAG = "BlueprintRepository"
        private const val ASSET_DIR = "blueprints"
    }
}
