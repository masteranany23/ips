package com.example.indoormaps.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class ApiClient {
    
    companion object {
        private const val TAG = "ApiClient"
        private const val API_URL = "https://ips-u8u0.onrender.com/predict"
        private const val TIMEOUT = 15000
    }
    
    suspend fun sendScanToServer(accessPoints: List<AccessPoint>): PredictionResult? {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                Log.d(TAG, "Sending ${accessPoints.size} APs to server")
                
                val url = URL(API_URL)
                connection = url.openConnection() as HttpsURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                    doInput = true
                    connectTimeout = TIMEOUT
                    readTimeout = TIMEOUT
                }
                
                val jsonPayload = buildJsonPayload(accessPoints)
                Log.d(TAG, "Payload size: ${jsonPayload.length()} chars")
                
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonPayload.toString())
                    writer.flush()
                }
                
                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(
                        InputStreamReader(connection.inputStream)
                    ).use { it.readText() }
                    
                    Log.d(TAG, "Response received: ${response.take(100)}...")
                    parseResponse(response)
                } else {
                    val errorResponse = try {
                        BufferedReader(
                            InputStreamReader(connection.errorStream)
                        ).use { it.readText() }
                    } catch (e: Exception) {
                        "No error details"
                    }
                    Log.e(TAG, "Server error $responseCode: $errorResponse")
                    null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "API call failed", e)
                null
            } finally {
                connection?.disconnect()
            }
        }
    }
    
    private fun buildJsonPayload(accessPoints: List<AccessPoint>): JSONObject {
        return JSONObject().apply {
            val scansArray = JSONArray()
            accessPoints.forEach { ap ->
                scansArray.put(JSONObject().apply {
                    put("bssid", ap.bssid)
                    put("rssi", ap.rssi)
                })
            }
            put("scans", scansArray)
        }
    }
    
    private fun parseResponse(jsonString: String): PredictionResult? {
        return try {
            Log.d(TAG, "Parsing response: ${jsonString.take(200)}")
            
            val json = JSONObject(jsonString)
            
            val location = json.optString("location", "Unknown")
            val confidence = json.optDouble("confidence", 0.0).toFloat()
            
            // matchedAPs and totalAPs might not exist in response
            val matchedAPs = json.optInt("matched_aps", 0)
            val totalAPs = json.optInt("total_aps", 0)
            
            // Parse top3 array
            val top3 = mutableListOf<Pair<String, Float>>()
            if (json.has("top3")) {
                val top3Array = json.getJSONArray("top3")
                for (i in 0 until minOf(top3Array.length(), 3)) {
                    val item = top3Array.getJSONArray(i)
                    if (item.length() >= 2) {
                        val loc = item.getString(0)
                        val prob = item.getDouble(1).toFloat()
                        top3.add(loc to prob)
                    }
                }
            }
            
            Log.d(TAG, "Parsed: location=$location, confidence=$confidence, top3=${top3.size}")
            
            PredictionResult(
                location = location,
                confidence = confidence,
                top3 = top3,
                matchedAPs = matchedAPs,
                totalAPs = totalAPs,
                source = PredictionSource.REMOTE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: ${e.message}", e)
            Log.e(TAG, "Raw response was: $jsonString")
            null
        }
    }
    
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val healthUrl = API_URL.replace("/predict", "/health")
                val url = URL(healthUrl)
                connection = url.openConnection() as HttpsURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                val responseCode = connection.responseCode
                responseCode == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                Log.e(TAG, "Connection test failed", e)
                false
            } finally {
                connection?.disconnect()
            }
        }
    }
}
