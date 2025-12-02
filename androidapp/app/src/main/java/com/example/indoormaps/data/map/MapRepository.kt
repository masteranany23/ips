package com.example.indoormaps.data.map

import android.content.Context
import android.util.Log

/**
 * Repository for managing floor maps with in-memory caching
 */
class MapRepository(context: Context) {
    
    companion object {
        private const val TAG = "MapRepository"
    }
    
    private val mapLoader = MapLoader(context)
    private val mapsCache = mutableListOf<FloorMap>()
    
    /**
     * Load all maps from assets
     */
    suspend fun loadMaps() {
        mapsCache.clear()
        mapsCache.addAll(mapLoader.loadAllMaps())
        Log.d(TAG, "Loaded ${mapsCache.size} maps into cache")
    }
    
    /**
     * Get all cached maps
     */
    fun allMaps(): List<FloorMap> = mapsCache.toList()
    
    /**
     * Find map by building and floor ID
     */
    fun getFloorMap(buildingId: String, floorId: String): FloorMap? {
        return mapsCache.find { 
            it.buildingId.equals(buildingId, ignoreCase = true) && 
            it.floorId.equals(floorId, ignoreCase = true)
        }
    }
    
    /**
     * Find map and node for a given location ID (forgiving search)
     */
    fun findMapForLocation(locationId: String): Pair<FloorMap, LocationNode>? {
        // Try exact match first
        for (map in mapsCache) {
            val node = map.nodes.find { it.id.equals(locationId, ignoreCase = true) }
            if (node != null) return map to node
        }
        
        // Try suffix match
        for (map in mapsCache) {
            val node = map.nodes.find { it.id.endsWith(locationId, ignoreCase = true) }
            if (node != null) {
                Log.d(TAG, "Found node ${node.id} via suffix match for $locationId")
                return map to node
            }
        }
        
        // Try contains match
        for (map in mapsCache) {
            val node = map.nodes.find { it.id.contains(locationId, ignoreCase = true) }
            if (node != null) {
                Log.d(TAG, "Found node ${node.id} via contains match for $locationId")
                return map to node
            }
        }
        
        Log.w(TAG, "No map found for location: $locationId")
        return null
    }
}
