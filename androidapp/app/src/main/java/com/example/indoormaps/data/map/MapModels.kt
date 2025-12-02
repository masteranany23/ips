package com.example.indoormaps.data.map

import kotlinx.serialization.Serializable

/**
 * Represents a single node (room, corridor, corner) on the floor map
 */
@Serializable
data class LocationNode(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val meta: Map<String, String>? = null
)

/**
 * Represents a connection between two nodes
 */
@Serializable
data class Edge(
    val from: String,
    val to: String,
    val weight: Float? = null
)

/**
 * Complete floor map with nodes and edges
 */
@Serializable
data class FloorMap(
    val buildingId: String,
    val floorId: String,
    val width: Float,
    val height: Float,
    val backgroundImage: String? = null,
    val nodes: List<LocationNode>,
    val edges: List<Edge>
) {
    /**
     * Find node by ID
     */
    fun findNode(id: String): LocationNode? = nodes.find { it.id == id }
    
    /**
     * Get all edges connected to a node
     */
    fun getConnectedEdges(nodeId: String): List<Edge> = 
        edges.filter { it.from == nodeId || it.to == nodeId }
}
