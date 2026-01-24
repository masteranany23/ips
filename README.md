# 📍 WiFi Indoor Positioning System (IPS)

<div align="center">

![Status](https://img.shields.io/badge/Status-Production-success)
![Accuracy](https://img.shields.io/badge/Accuracy-100%25-brightgreen)
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20ESP32%20%7C%20Web-blue)
![License](https://img.shields.io/badge/License-MIT-orange)

**Highly accurate indoor location tracking using WiFi fingerprinting and machine learning**

[Features](#-features) • [Demo](#-demo) • [Architecture](#-architecture) • [Quick Start](#-quick-start) • [API](#-api-reference)

</div>

---

## 🎯 Overview

A **production-ready indoor positioning system** that achieves **100% test accuracy** and **96% cross-validation accuracy** by analyzing WiFi signal patterns. The system supports multiple deployment modes: Android app with offline predictions, ESP32/ESP8266 IoT devices, and cloud-based API service.

### Why This Project?

- 🎯 **Pinpoint Accuracy**: 100% test accuracy across 10 indoor locations
- 📱 **Offline-First**: TFLite model runs on-device without internet
- ⚡ **Real-Time**: 3-5 second scan intervals with adaptive throttling
- 🗺️ **Visual Maps**: Interactive floor plan with live position highlighting
- 🔌 **IoT Ready**: Compatible with ESP32, ESP8266, and Android devices
- ☁️ **Cloud Backup**: Fallback to remote API when offline model unavailable

---

## ✨ Features

### 🤖 Machine Learning Engine
- **Random Forest Classifier** with 300 estimators
- **251 WiFi fingerprint features** from BSSID signal strengths
- **Knowledge Distillation** for Neural Network conversion
- **INT8 Quantization** for mobile deployment (4x smaller model)
- **10 Location Classes** with perfect separation

### 📱 Android Application
- **Jetpack Compose UI** with Material3 design
- **Dual Prediction System**: Local TFLite + Remote API fallback
- **Floor Map Visualization** with animated position markers
- **Continuous Background Scanning** (3-5 second adaptive intervals)
- **Navigation System**: Home screen (predictions) ↔ Map screen (visual)
- **Offline-capable** with on-device model inference

### 🌐 Cloud API (FastAPI)
- RESTful endpoint deployed on [Render](https://ips-u8u0.onrender.com)
- Aggregates multiple scans for improved accuracy
- Returns predictions with confidence scores
- Health check endpoint for monitoring

### 🛠️ IoT Support
- **ESP32/ESP8266 Scanner**: Continuous WiFi scanning firmware
- Sends scan data to cloud API every 3 seconds
- Handles WiFi throttling and connection recovery
- JSON-formatted payload with RSSI values

---

## 🎬 Demo

### Android App Flow

```
┌─────────────────┐      ┌─────────────────┐
│   Home Screen   │◄────►│   Map Screen    │
│                 │      │                 │
│ Local:  min122  │      │ ┌─────────────┐ │
│ Remote: min122  │      │ │ Floor Plan  │ │
│ Conf:   62%     │      │ │   ● YOU     │ │
│                 │      │ │  ARE HERE   │ │
│ [View Map] ────►│      │ └─────────────┘ │
└─────────────────┘      └─────────────────┘
```

### Prediction Example

**Input**: WiFi scan with 15 access points
```json
{
  "scans": [{
    "00:11:22:33:44:55": -45,
    "aa:bb:cc:dd:ee:ff": -67,
    ...
  }]
}
```

**Output**: Location prediction with confidence
```json
{
  "location": "min122",
  "confidence": 0.62,
  "all_probabilities": {
    "min122": 0.62,
    "mini125": 0.18,
    "min118": 0.12,
    ...
  }
}
```

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    Data Collection                        │
│  ESP32/Android → WiFi Scan (BSSID + RSSI) → CSV Storage  │
└─────────────────┬────────────────────────────────────────┘
                  │
┌─────────────────▼────────────────────────────────────────┐
│                 Machine Learning Pipeline                 │
│  1. Preprocess: Long→Wide format (251 features/scan)     │
│  2. Train: Random Forest (300 trees, 96% CV accuracy)    │
│  3. Convert: Knowledge Distillation → Neural Network     │
│  4. Quantize: INT8 TFLite (compatible with Android)      │
└─────────────────┬────────────────────────────────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
┌───────▼────────┐  ┌───────▼────────┐
│  Android App   │  │   Cloud API    │
│                │  │                │
│ TFLite Model   │  │ FastAPI Server │
│ (Offline)      │  │ (Render.com)   │
│                │  │                │
│ ┌────────────┐ │  │ ┌────────────┐ │
│ │ Prediction │ │  │ │ RF Model   │ │
│ │  Service   │ │  │ │ Inference  │ │
│ └──────┬─────┘ │  │ └────────────┘ │
│        │       │  │                │
│ ┌──────▼─────┐ │  └────────────────┘
│ │  Map View  │ │          ▲
│ │ Highlight  │ │          │
│ └────────────┘ │     HTTP Request
└────────────────┘
```

---

## 🚀 Quick Start

### 1️⃣ Data Collection

**Option A: Android Device**
```bash
# Use the Android app to collect WiFi scans
# Export scans to wifi_training_data.csv
```

**Option B: ESP32/ESP8266**
```bash
# Flash esp32_example.ino to your device
# Collect scans at each location
# Save output to wifi_training_data.csv
```

### 2️⃣ Train the Model

```bash
# Install dependencies
pip install -r requirements.txt

# Convert long→wide format
python preprocess_data.py

# Train Random Forest
python model_train.py

# Output: rf_wifi_model.pkl, label_encoder.pkl, feature_list_used.csv
```

**Expected Results**:
```
Model Accuracy: 100.00%
Cross-Validation (5-fold): 96.00% (+/- 0.08)

Classification Report:
              precision    recall  f1-score   support
     min118       1.00      1.00      1.00        10
     min122       1.00      1.00      1.00        10
    mini104       1.00      1.00      1.00        10
    mini125       1.00      1.00      1.00        10
minireception    1.00      1.00      1.00        10
   oatback       1.00      1.00      1.00        10
  oatfront       1.00      1.00      1.00        10
    pha405       1.00      1.00      1.00        10
    pha407       1.00      1.00      1.00        10
    pha410       1.00      1.00      1.00        10
```

### 3️⃣ Convert to TFLite (for Android)

```bash
# Run in Google Colab (TensorFlow 2.19+)
python convert_colab.py

# Output: wifi_positioning.tflite, model_metadata.json
# Model size: ~100KB (INT8 quantized)
```

### 4️⃣ Deploy Cloud API

```bash
# Option A: Local testing
uvicorn prediction_api:app --reload

# Option B: Deploy to Render
git push origin main
# Render auto-deploys from render.yaml
```

**API Endpoint**: `https://ips-u8u0.onrender.com/predict`

### 5️⃣ Build Android App

```bash
cd androidapp

# Copy TFLite model
cp ../wifi_positioning.tflite app/src/main/assets/
cp ../model_metadata.json app/src/main/assets/

# Build APK
./gradlew assembleDebug

# Install: app/build/outputs/apk/debug/app-debug.apk
```

---

## 📊 Dataset Format

### Input: Long Format (`wifi_training_data.csv`)
```csv
Location,Burst,Scan,BSSID,RSSI
min118,1,1,00:11:22:33:44:55,-45
min118,1,1,aa:bb:cc:dd:ee:ff,-67
min118,1,2,00:11:22:33:44:55,-47
...
```

### Processed: Wide Format (`wifi_training_wide_per_scan.csv`)
```csv
Location_Label,Burst_ID,Scan_Index,00:11:22:33:44:55,aa:bb:cc:dd:ee:ff,...
min118,1,1,-45,-67,...
min118,1,2,-47,-69,...
...
```

**Key Stats**:
- **100 total scans** (10 locations × 10 scans each)
- **251 unique BSSIDs** (WiFi access points detected)
- **Missing values filled** with -110 dBm (no signal)

---

## 🔌 API Reference

### Health Check
```http
GET /health
```

**Response**:
```json
{
  "status": "healthy",
  "model_loaded": true,
  "num_features": 251,
  "locations": ["min118", "min122", ...]
}
```

### Predict Location
```http
POST /predict
Content-Type: application/json
```

**Request Body**:
```json
{
  "scans": [
    {
      "00:11:22:33:44:55": -45,
      "aa:bb:cc:dd:ee:ff": -67
    },
    {
      "00:11:22:33:44:55": -47,
      "aa:bb:cc:dd:ee:ff": -69
    }
  ]
}
```

**Response**:
```json
{
  "location": "min122",
  "confidence": 0.62,
  "all_probabilities": {
    "min122": 0.62,
    "mini125": 0.18,
    "min118": 0.12,
    "minireception": 0.04,
    ...
  },
  "metadata": {
    "scans_processed": 2,
    "features_used": 251
  }
}
```

---

## 🧪 Technical Details

### Machine Learning Pipeline

**1. Data Preprocessing**
- Pivot long format to wide format (one row per scan)
- Extract 251 BSSID features from signal strength patterns
- Fill missing values with -110 dBm (below detection threshold)
- Encode location labels to integers

**2. Random Forest Training**
```python
RandomForestClassifier(
    n_estimators=300,
    max_depth=15,
    min_samples_split=2,
    random_state=42
)
```

**3. Knowledge Distillation**
- Train neural network on RF soft labels (predict_proba)
- Architecture: Input(251) → Dense(256) → Dense(128) → Dense(64) → Output(10)
- Uses teacher-student learning to transfer RF knowledge

**4. TFLite Conversion**
```python
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
# INT8 quantization for Android TFLite 2.16+ compatibility
```

### Android Architecture

**Components**:
- `ModelInference`: TFLite interpreter wrapper
- `WifiScanner`: Continuous background scanning
- `LocationViewModel`: Prediction orchestrator
- `PredictionService`: Debounced prediction stream (300ms)
- `MapViewModel`: Floor map node mapping
- `FloorMapView`: Canvas-based rendering

**Navigation Flow**:
```kotlin
NavHost {
    composable("home") { HomeScreen(navController) }
    composable("map") { MapScreen(navController) }
}
```

**Prediction Mapping**:
```kotlin
"min118" → "TRI01F1_ROOM_118"
"oatfront" → "TRI01F1_OAT"
// Highlights corresponding node on floor map
```

---

## 📦 Project Structure

```
ips/
├── 🐍 Python Backend
│   ├── model_train.py              # Random Forest training
│   ├── preprocess_data.py          # Long→Wide format conversion
│   ├── convert_colab.py            # TFLite conversion (Colab)
│   ├── prediction_api.py           # FastAPI server
│   ├── verify_training_data.py     # Data validation
│   └── test_direct_prediction.py   # Model testing
│
├── 📱 Android App
│   └── androidapp/
│       ├── app/src/main/
│       │   ├── java/com/example/indoormaps/
│       │   │   ├── ui/              # Compose screens
│       │   │   ├── viewmodels/      # LocationViewModel, MapViewModel
│       │   │   ├── data/            # Repositories, services
│       │   │   └── ml/              # ModelInference (TFLite)
│       │   └── assets/
│       │       ├── wifi_positioning.tflite
│       │       ├── model_metadata.json
│       │       └── tri01_f1.json    # Floor map data
│       └── build.gradle.kts
│
├── 🔌 IoT Firmware
│   ├── esp32_example.ino           # ESP32 scanner
│   └── esp8266_scanner.ino         # ESP8266 scanner
│
├── 📊 Data & Models
│   ├── wifi_training_data.csv      # Raw scans (long format)
│   ├── wifi_training_wide_per_scan.csv  # Processed (wide)
│   ├── rf_wifi_model.pkl           # Trained Random Forest
│   ├── label_encoder.pkl           # Location label encoder
│   ├── feature_list_used.csv       # 251 BSSID features
│   ├── wifi_positioning.tflite     # Mobile model (INT8)
│   └── model_metadata.json         # TFLite metadata
│
├── ⚙️ Configuration
│   ├── requirements.txt            # Python dependencies
│   ├── render.yaml                 # Cloud deployment config
│   └── runtime.txt                 # Python version
│
└── 📖 Documentation
    ├── README.md                   # This file
    ├── DEPLOYMENT_GUIDE.md         # Render deployment
    └── README_DEPLOYMENT.md        # API deployment details
```

---

## 🛠️ Tech Stack

**Machine Learning**
- scikit-learn 1.3.0 (Random Forest)
- TensorFlow 2.19 (Neural Network, TFLite)
- Pandas, NumPy (Data processing)

**Backend**
- FastAPI (REST API)
- Uvicorn (ASGI server)
- Joblib (Model serialization)

**Mobile**
- Kotlin 1.9.10
- Jetpack Compose (UI)
- TensorFlow Lite 2.16.1 (On-device inference)
- Material3 (Design system)
- Navigation Compose 2.7.7

**IoT**
- ESP32/ESP8266 (WiFi scanning)
- ArduinoJson (Payload formatting)

**Infrastructure**
- Render.com (Cloud hosting)
- Git/GitHub (Version control)
- Gradle 8.x (Android build)

---

## 📈 Performance Metrics

| Metric | Value |
|--------|-------|
| **Test Accuracy** | 100% |
| **Cross-Validation (5-fold)** | 96% ± 8% |
| **Inference Time (Android)** | <50ms |
| **Model Size (TFLite)** | ~100KB |
| **Scan Interval** | 3-5 seconds |
| **Features** | 251 BSSIDs |
| **Locations** | 10 classes |
| **Training Samples** | 100 scans |

---

## 🔧 Troubleshooting

### TFLite Loading Error: "FULLY_CONNECTED version 12 not supported"

**Solution**: Use INT8 quantization in `convert_colab.py`
```python
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
def representative_dataset():
    for i in range(min(100, len(X_train))):
        yield [X_train[i:i+1].astype(np.float32)]
converter.representative_dataset = representative_dataset
```

### WiFi Throttling on Android

The app handles throttling automatically with adaptive intervals (3-5s). Check logcat:
```
🔍 WiFi scan completed: 15 APs detected
⏱️ Throttled! Waiting 5000ms...
```

### Prediction Mapping Errors

Ensure all location labels are mapped in `PredictionMapping.kt`:
```kotlin
"min118" to "TRI01F1_ROOM_118",
"oatfront" to "TRI01F1_OAT",
// Add all 10 locations
```

---

## 🎓 Learning Resources

**WiFi Fingerprinting**
- [Indoor Localization Techniques](https://en.wikipedia.org/wiki/Indoor_positioning_system)
- [RSSI-based Positioning](https://www.mdpi.com/1424-8220/19/1/84)

**Machine Learning**
- [Random Forest Classification](https://scikit-learn.org/stable/modules/ensemble.html#forests-of-randomized-trees)
- [Knowledge Distillation](https://arxiv.org/abs/1503.02531)

**TensorFlow Lite**
- [Model Optimization](https://www.tensorflow.org/lite/performance/model_optimization)
- [INT8 Quantization](https://www.tensorflow.org/lite/performance/post_training_quantization)

---

## 🤝 Contributing

Contributions welcome! Here's how:

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** changes (`git commit -m 'Add amazing feature'`)
4. **Push** to branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

**Areas for improvement**:
- Support for multi-floor buildings
- BLE beacon integration
- Real-time collaborative mapping
- iOS app development

---

## 📄 License

This project is licensed under the MIT License - see LICENSE file for details.

---

## 👨‍💻 Author

Created with ❤️ by a passionate developer

**Connect**:
- GitHub: [Anany Mishra](https://github.com/masteranany23)
- LinkedIn: [Anany Mishra](www.linkedin.com/in/mishra-anany)

---

## 🙏 Acknowledgments

- TensorFlow team for TFLite optimization tools
- scikit-learn for robust ML algorithms
- Jetpack Compose for modern Android UI
- Render.com for free cloud hosting

---

<div align="center">

**⭐ Star this repo if you found it helpful!**

Made with ☕ and 🧠

</div>
