# Deploying to Render

## Prerequisites
- GitHub account
- Render account (free tier available at render.com)
- Your model files: `rf_wifi_model.pkl`, `label_encoder.pkl`, `feature_list_used.csv`

## Deployment Steps

### 1. Prepare Your Repository
```bash
# Initialize git repository (if not already done)
git init

# Add all files
git add app.py requirements.txt render.yaml .gitignore
git add rf_wifi_model.pkl label_encoder.pkl feature_list_used.csv

# Commit
git commit -m "Initial commit - WiFi positioning API"

# Create GitHub repository and push
git remote add origin https://github.com/YOUR_USERNAME/wifi-positioning-api.git
git branch -M main
git push -u origin main
```

### 2. Deploy on Render
1. Go to https://render.com and sign in
2. Click "New +" → "Web Service"
3. Connect your GitHub repository
4. Render will auto-detect the `render.yaml` configuration
5. Click "Create Web Service"

### 3. Configuration
Render will automatically:
- Install dependencies from `requirements.txt`
- Start the server with `uvicorn app:app`
- Assign a URL like: `https://wifi-positioning-api.onrender.com`

### 4. Test Your API
```bash
# Health check
curl https://YOUR_APP.onrender.com/health

# Predict location
curl -X POST https://YOUR_APP.onrender.com/predict \
  -H "Content-Type: application/json" \
  -d '{
    "scans": [
      {"bssid": "70:e4:22:c0:1a:08", "rssi": -45},
      {"bssid": "dc:ea:e7:b9:16:00", "rssi": -67}
    ]
  }'
```

## ESP32/Arduino Integration
Update your ESP32 code to use the Render URL:
```cpp
const char* serverUrl = "https://YOUR_APP.onrender.com/predict";
```

## Frontend Integration
```javascript
const API_URL = "https://YOUR_APP.onrender.com";

async function predictLocation(scans) {
  const response = await fetch(`${API_URL}/predict`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ scans })
  });
  return await response.json();
}
```

## System Architecture

### Overview
The system is designed to provide real-time WiFi positioning using a combination of machine learning and web technologies. The ESP32/Arduino devices collect WiFi signals and send them to the server, which predicts the device's location.

### Components
1. **ESP32/Arduino**: Collects WiFi signals and sends HTTP requests to the server.
2. **Render**: Hosts the FastAPI application and serves the machine learning model.
3. **FastAPI**: Handles incoming requests, processes data, and returns predictions.

### Data Flow
1. ESP32/Arduino devices scan for WiFi signals and measure RSSI values.
2. The devices send the collected data to the FastAPI server as JSON.
3. FastAPI receives the data, runs the machine learning model, and predicts the location.
4. The server responds with the predicted location, which is then used by the ESP32/Arduino or any frontend application.

### WebSocket Integration (Optional)
For real-time bidirectional communication, you can integrate WebSockets:
- **Frontend**: Connects to the WebSocket server and listens for location updates.
- **Backend**: Sends real-time location updates to connected clients.

## Notes
- **Free tier**: Service sleeps after 15 min of inactivity (first request may be slow)
- **Upgrade**: For production, consider paid tier for always-on service
- **CORS**: Currently allows all origins. Update `allow_origins` in `app.py` for production
