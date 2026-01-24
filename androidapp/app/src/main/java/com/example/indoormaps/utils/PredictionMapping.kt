package com.example.indoormaps.utils

/**
 * Mapping between ML prediction labels and map node IDs
 * 
 * Your trained model has 10 locations (from model_metadata.json):
 * min118, min122, mini104, mini125, minireception, oatback, oatfront, pha405, pha407, pha410
 */
object PredictionMapping {
    
    val predictionToNode = mapOf(
        // Mini building rooms
        "min118" to "TRI01F1_ROOM_118",    // ✅ Added this missing mapping
        "min122" to "TRI01F1_ROOM_122",
        "mini104" to "TRI01F1_ROOM_104",
        "mini125" to "TRI01F1_ROOM_125",
        "minireception" to "TRI01F1_ROOM_103",
        
        // Open Air Theatre locations
        "oatback" to "TRI01F1_OAT",
        "oatfront" to "TRI01F1_OAT",
        
        // PHA building rooms (map to available rooms)
        "pha405" to "TRI01F1_ROOM_109",
        "pha407" to "TRI01F1_ROOM_110",
        "pha410" to "TRI01F1_ROOM_111"
    )
    
    /**
     * Get mapped node ID for a prediction label
     */
    fun mapPredictionToNode(predictionId: String): String? {
        return predictionToNode[predictionId.lowercase()]
    }
    
    /**
     * Get all supported prediction labels
     */
    fun getSupportedLocations(): List<String> {
        return predictionToNode.keys.toList()
    }
    
    /**
     * Check if a prediction can be mapped
     */
    fun canMap(predictionId: String): Boolean {
        return predictionToNode.containsKey(predictionId.lowercase())
    }
}
