#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// WiFi credentials
const char* ssid = "daredevil";
const char* password = "qsig9884";

// API endpoint
//const char* serverUrl = "https://YOUR_APP.onrender.com/predict";
// For local testing:
 const char* serverUrl = "http://10.98.28.84:8000/predict";

// Scan interval (milliseconds)
const unsigned long scanInterval = 3000;  // Scan every 3 seconds
unsigned long lastScanTime = 0;

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n=== WiFi Indoor Positioning ESP32 ===");
  
  // Connect to WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  
  Serial.println("\n✓ Connected to WiFi");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());
  Serial.println("Starting continuous WiFi scanning...\n");
}

void loop() {
  if (millis() - lastScanTime >= scanInterval) {
    lastScanTime = millis();
    scanAndSend();
  }
}

void scanAndSend() {
  Serial.println("--- Scanning WiFi networks ---");
  
  int n = WiFi.scanNetworks();
  
  if (n == 0) {
    Serial.println("No networks found");
    return;
  }
  
  Serial.printf("Found %d networks\n", n);
  
  // Create JSON document
  DynamicJsonDocument doc(4096);
  JsonArray scans = doc.createNestedArray("scans");
  
  // Add all discovered networks
  for (int i = 0; i < n; i++) {
    JsonObject scan = scans.createNestedObject();
    scan["bssid"] = WiFi.BSSIDstr(i);
    scan["rssi"] = WiFi.RSSI(i);
    
    Serial.printf("  %d: %s (%d dBm)\n", i+1, WiFi.BSSIDstr(i).c_str(), WiFi.RSSI(i));
  }
  
  // Serialize JSON
  String jsonString;
  serializeJson(doc, jsonString);
  
  // Send to API
  sendToAPI(jsonString);
  
  // Clear scan results
  WiFi.scanDelete();
}

void sendToAPI(String jsonData) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("❌ WiFi not connected");
    return;
  }
  
  HTTPClient http;
  http.begin(serverUrl);
  http.addHeader("Content-Type", "application/json");
  
  Serial.println("\n📤 Sending to API...");
  int httpResponseCode = http.POST(jsonData);
  
  if (httpResponseCode > 0) {
    String response = http.getString();
    Serial.printf("✓ Response code: %d\n", httpResponseCode);
    
    // Parse response
    DynamicJsonDocument responseDoc(1024);
    deserializeJson(responseDoc, response);
    
    String location = responseDoc["location"];
    float confidence = responseDoc["confidence"];
    int matchedAps = responseDoc["matched_aps"];
    
    Serial.println("=================================");
    Serial.printf("📍 LOCATION: %s\n", location.c_str());
    Serial.printf("   Confidence: %.1f%%\n", confidence * 100);
    Serial.printf("   Matched APs: %d\n", matchedAps);
    Serial.println("=================================\n");
  } else {
    Serial.printf("❌ Error code: %d\n", httpResponseCode);
    Serial.printf("   Error: %s\n", http.errorToString(httpResponseCode).c_str());
  }
  
  http.end();
}
