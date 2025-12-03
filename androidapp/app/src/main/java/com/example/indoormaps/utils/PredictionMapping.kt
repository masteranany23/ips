package com.example.indoormaps.utils

/**
 * Mapping between ML prediction labels and map node IDs
 */
object PredictionMapping {
    
    val predictionToNode = mapOf(
        // Map your 9 trained locations to nodes in tri01_f1.json
        "mini104" to "TRI01F1_ROOM_104",
        "mini125" to "TRI01F1_ROOM_125",
        "minireception" to "TRI01F1_ROOM_103",  // Adjust to actual reception location
        "oatback" to "TRI01F1_OAT",
        "oatfront" to "TRI01F1_OAT",
        "pha405" to "TRI01F1_ROOM_109",  // Map to actual room IDs
        "pha407" to "TRI01F1_ROOM_110",
        "pha410" to "TRI01F1_ROOM_111",
        "room118" to "TRI01F1_ROOM_118"
    )
    
    fun mapPredictionToNode(predictionId: String): String? {
        return predictionToNode[predictionId.lowercase()]
    }
}
