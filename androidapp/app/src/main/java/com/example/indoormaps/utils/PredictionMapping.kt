package com.example.indoormaps.utils

/**
 * Manual mapping between ML prediction IDs and map node IDs
 * 
 * TODO: Update this mapping table to match your actual prediction labels
 * with the node IDs in tri01_f1.json
 */
object PredictionMapping {
    
    /**
     * Map prediction label to node ID
     * 
     * Example mappings (UPDATE THESE):
     *  - "p1405" (prediction) -> "TRI01F1_ROOM_103" (map node)
     *  - "p1407" (prediction) -> "TRI01F1_ROOM_104" (map node)
     */
    val predictionToNode = mapOf(
        // TODO: Add your actual mappings here
        "p1405" to "TRI01F1_ROOM_103",
        "p1407" to "TRI01F1_ROOM_104",
        "messdh" to "TRI01F1_ROOM_105",
        "mini118" to "TRI01F1_ROOM_118",
        "mini122" to "TRI01F1_ROOM_122",
        "oatfront1" to "TRI01F1_OAT",
        
        // Add more mappings as needed
        // "your_prediction_id" to "TRI01F1_ROOM_XXX",
    )
    
    /**
     * Get mapped node ID for a prediction
     */
    fun mapPredictionToNode(predictionId: String): String? {
        return predictionToNode[predictionId.lowercase()]
    }
}
