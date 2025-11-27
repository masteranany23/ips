#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

// CONFIG
const char* WIFI_SSID = "daredevil";
const char* WIFI_PASS = "qsig9884";
const char* SERVER_URL = "https://ips-u8u0.onrender.com/predict";
const unsigned long SEND_INTERVAL_MS = 3000UL;

WiFiClientSecure client;
unsigned long lastSend = 0;

void connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) return;
  
  Serial.printf("\nConnecting to WiFi: %s\n", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
    if (millis() - start > 20000) {
      Serial.println("\nWiFi timeout. Will retry.");
      return;
    }
  }
  
  Serial.println("\nWiFi Connected");
  Serial.print("IP: "); Serial.println(WiFi.localIP());
  Serial.print("Gateway: "); Serial.println(WiFi.gatewayIP());
  Serial.print("Server: "); Serial.println(SERVER_URL);
  Serial.println("-----------------------------------");
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n\nWiFi Indoor Positioning - ESP8266");
  Serial.println("===================================");
  
  // Disable SSL certificate validation (for ease of use)
  // In production, you should use proper certificate validation
  client.setInsecure();
  
  connectWiFi();
}

void loop() {
  if (millis() - lastSend < SEND_INTERVAL_MS) return;
  lastSend = millis();

  // Ensure WiFi
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi disconnected. Reconnecting...");
    connectWiFi();
    if (WiFi.status() != WL_CONNECTED) {
      Serial.println("Skip this cycle.");
      return;
    }
  }

  Serial.println("\n--- WiFi Scan Start ---");
  int n = WiFi.scanNetworks(false, true);
  
  if (n == 0) {
    Serial.println("No networks found");
    return;
  }
  
  Serial.printf("Found %d networks\n", n);
  for (int i = 0; i < min(n, 5); ++i) {
    String bssid = WiFi.BSSIDstr(i);
    bssid.toLowerCase();
    Serial.printf("  %d: %s  %d dBm\n", i+1, bssid.c_str(), WiFi.RSSI(i));
  }

  // Build JSON
  DynamicJsonDocument doc(8192);
  JsonArray arr = doc.createNestedArray("scans");
  
  for (int i = 0; i < n; ++i) {
    JsonObject o = arr.createNestedObject();
    String bssid = WiFi.BSSIDstr(i);
    bssid.toLowerCase();
    o["bssid"] = bssid;
    o["rssi"] = WiFi.RSSI(i);
  }
  
  String payload;
  serializeJson(doc, payload);
  
  Serial.printf("Payload size: %d bytes\n", payload.length());

  // HTTP POST with HTTPS
  HTTPClient http;
  http.begin(client, SERVER_URL);  // Use WiFiClientSecure
  http.addHeader("Content-Type", "application/json");
  http.setTimeout(15000);

  Serial.println("Sending POST...");
  int code = http.POST(payload);

  if (code > 0) {
    Serial.printf("HTTP %d\n", code);
    
    if (code == 200) {
      String resp = http.getString();
      DynamicJsonDocument respDoc(1024);
      DeserializationError error = deserializeJson(respDoc, resp);
      
      if (!error) {
        String location = respDoc["location"] | "Unknown";
        float confidence = respDoc["confidence"] | 0.0;
        int matched = respDoc["matched_aps"] | 0;
        int total = respDoc["total_aps"] | 0;
        
        Serial.println("===================================");
        Serial.printf("LOCATION: %s\n", location.c_str());
        Serial.printf("Confidence: %.1f%%\n", confidence * 100);
        Serial.printf("Matched: %d/%d APs\n", matched, total);
        
        JsonArray top3 = respDoc["top3"];
        if (top3.size() > 0) {
          Serial.println("Top 3:");
          for (int i = 0; i < min(3, (int)top3.size()); i++) {
            String loc = top3[i][0];
            float prob = top3[i][1];
            Serial.printf("  %d. %s: %.1f%%\n", i+1, loc.c_str(), prob * 100);
          }
        }
        Serial.println("===================================");
        
      } else {
        Serial.println("JSON parse error");
        Serial.println(resp);
      }
    } else {
      Serial.println(http.getString());
    }
    
  } else {
    Serial.printf("POST failed: %d\n", code);
    Serial.println("\nDiagnostics:");
    Serial.printf("  WiFi: %d\n", WiFi.status());
    Serial.printf("  Gateway: %s\n", WiFi.gatewayIP().toString().c_str());
    Serial.printf("  RSSI: %d dBm\n", WiFi.RSSI());
  }
  
  http.end();
  WiFi.scanDelete();
  
  Serial.printf("\nNext scan in %lu sec\n", SEND_INTERVAL_MS / 1000);
  Serial.println("-----------------------------------");
}
