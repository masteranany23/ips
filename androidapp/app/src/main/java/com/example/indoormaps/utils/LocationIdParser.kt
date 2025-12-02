package com.example.indoormaps.utils

/**
 * Parse location ID into components (building, floor, room)
 * Supports formats: TRI01F1_ROOM_103, TRI01F1ROOM103, BLD01F2RM103
 */
fun parseLocationId(locationId: String): Triple<String?, String?, String?>? {
    // Pattern: building (letters+digits) + F + floor (digit) + separator? + ROOM/RM + room number
    val pattern1 = """([A-Z]+\d+)F(\d+)[_]?ROOM[_]?(\d+)""".toRegex(RegexOption.IGNORE_CASE)
    val pattern2 = """([A-Z]+\d+)F(\d+)[_]?RM[_]?(\d+)""".toRegex(RegexOption.IGNORE_CASE)
    
    pattern1.find(locationId)?.let {
        val (building, floor, room) = it.destructured
        return Triple(building, floor, room)
    }
    
    pattern2.find(locationId)?.let {
        val (building, floor, room) = it.destructured
        return Triple(building, floor, room)
    }
    
    return null
}
