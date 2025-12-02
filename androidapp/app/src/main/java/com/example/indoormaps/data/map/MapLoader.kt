package com.example.indoormaps.data.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads and validates floor maps from assets
 */
class MapLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "MapLoader"
        private const val MAPS_DIR = "maps"
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Load all floor maps from assets/maps directory
     */
    suspend fun loadAllMaps(): List<FloorMap> = withContext(Dispatchers.IO) {
        try {
            val mapFiles = context.assets.list(MAPS_DIR) ?: emptyArray()
            Log.d(TAG, "Found ${mapFiles.size} map files")
            
            mapFiles.mapNotNull { fileName ->
                try {
                    loadMap("$MAPS_DIR/$fileName")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load $fileName: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list maps directory: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Load a single map file
     */
    private fun loadMap(path: String): FloorMap {
        val jsonString = context.assets.open(path).bufferedReader().use { it.readText() }
        val map = json.decodeFromString<FloorMap>(jsonString)
        
        validateMap(map)
        Log.d(TAG, "Loaded map: ${map.buildingId}-${map.floorId} with ${map.nodes.size} nodes")
        
        return map
    }
    
    /**
     * Validate map integrity
     */
    private fun validateMap(map: FloorMap) {
        // Check for duplicate node IDs
        val nodeIds = map.nodes.map { it.id }
        val duplicates = nodeIds.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            throw IllegalStateException("Duplicate node IDs found: ${duplicates.keys}")
        }
        
        // Check that all edges reference existing nodes
        val nodeIdSet = nodeIds.toSet()
        map.edges.forEach { edge ->
            if (edge.from !in nodeIdSet) {
                throw IllegalStateException("Edge references unknown node: ${edge.from}")
            }
            if (edge.to !in nodeIdSet) {
                throw IllegalStateException("Edge references unknown node: ${edge.to}")
            }
        }
    }
}
