"""
Production FastAPI app for WiFi Indoor Positioning System
with WebSocket support for real-time updates
"""
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
import joblib
import pandas as pd
import os
from datetime import datetime

# -----------------------------
MODEL_PATH = "rf_wifi_model.pkl"
ENCODER_PATH = "label_encoder.pkl"
FEATURE_LIST_PATH = "feature_list_used.csv"
MISSING_RSSI = -110
# -----------------------------

# Load model artifacts
def normalize_bssid(bssid: str) -> str:
    """Normalize BSSID: lowercase, replace - with :, add trailing colon"""
    normalized = str(bssid).strip().lower().replace("-", ":")
    if not normalized.endswith(":"):
        normalized += ":"
    return normalized

print("Loading model artifacts...")
if not os.path.exists(MODEL_PATH) or not os.path.exists(ENCODER_PATH) or not os.path.exists(FEATURE_LIST_PATH):
    raise RuntimeError("Model files not found")

rf_model = joblib.load(MODEL_PATH)
label_encoder = joblib.load(ENCODER_PATH)
feature_list_raw = pd.read_csv(FEATURE_LIST_PATH, header=None)[0].astype(str).tolist()
feature_list = [normalize_bssid(bssid) for bssid in feature_list_raw]

print(f"Loaded model with {len(feature_list)} features")
print(f"Available locations: {list(label_encoder.classes_)}")

# Initialize FastAPI app
app = FastAPI(
    title="WiFi Indoor Positioning API",
    description="API for predicting indoor location based on WiFi signal strength",
    version="1.0.0"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Store latest prediction
latest_prediction = {
    "location": None,
    "confidence": 0.0,
    "top3": [],
    "matched_aps": 0,
    "total_aps": 0,
    "timestamp": None
}

# WebSocket manager
class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
        print(f"WebSocket client connected. Total: {len(self.active_connections)}")

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)
        print(f"WebSocket client disconnected. Total: {len(self.active_connections)}")

    async def broadcast(self, message: dict):
        disconnected = []
        for connection in self.active_connections:
            try:
                await connection.send_json(message)
            except:
                disconnected.append(connection)
        for conn in disconnected:
            if conn in self.active_connections:
                self.active_connections.remove(conn)

manager = ConnectionManager()

# Models
class ScanItem(BaseModel):
    bssid: str
    rssi: float

class ScanRequest(BaseModel):
    scans: Optional[List[ScanItem]] = None
    access_points: Optional[List[ScanItem]] = None

class PredictionResponse(BaseModel):
    location: str
    confidence: float
    top3: List[tuple]
    matched_aps: int
    total_aps: int
    timestamp: str

# Helper function - same as working prediction_api.py
def build_feature_df(scan_items: List[Dict[str, Any]]):
    """Build a 1-row DataFrame with columns exactly = feature_list"""
    data = {}
    for bssid in feature_list:
        data[bssid] = MISSING_RSSI
    
    matched_count = 0
    matched_bssids = []
    for it in scan_items:
        b = normalize_bssid(it.get("bssid", ""))
        try:
            r = float(it.get("rssi"))
        except Exception:
            continue
        if b in data:
            data[b] = r
            matched_count += 1
            matched_bssids.append(b)
    
    df = pd.DataFrame([list(data.values())], columns=feature_list)
    
    non_missing = (df.iloc[0] != MISSING_RSSI).sum()
    print(f"[DEBUG] Matched {matched_count} APs from scan to known features")
    print(f"[DEBUG] Matched BSSIDs: {matched_bssids[:3]}...")
    print(f"[DEBUG] Non-missing features: {non_missing}/{len(feature_list)}")
    
    return df, matched_count

# Endpoints
@app.get("/")
def root():
    return {
        "status": "online",
        "service": "WiFi Indoor Positioning API",
        "version": "1.0.0",
        "features": len(feature_list),
        "locations": list(label_encoder.classes_)
    }

@app.get("/health")
def health_check():
    return {"status": "healthy"}

@app.post("/predict", response_model=PredictionResponse)
async def predict_location(request: ScanRequest):
    """Predict indoor location from WiFi scan data"""
    # Pick whichever key is provided (same as prediction_api.py)
    raw_list = None
    if request.scans:
        raw_list = request.scans
    elif request.access_points:
        raw_list = request.access_points
    else:
        raise HTTPException(status_code=400, detail="No scans provided. Use JSON key 'scans' with list of {bssid,rssi}.")
    
    # Convert Pydantic objects to dicts
    scan_items = [{"bssid": s.bssid, "rssi": s.rssi} for s in raw_list]
    
    # Build DataFrame
    X, matched_count = build_feature_df(scan_items)
    
    # Validate
    if X.shape[1] != len(feature_list):
        raise HTTPException(status_code=500, detail=f"Feature mismatch: got {X.shape[1]}, expected {len(feature_list)}")
    
    if not all(X.columns == feature_list):
        print("[ERROR] Column order mismatch detected!")
        raise HTTPException(status_code=500, detail="Feature column order mismatch")
    
    # Predict
    try:
        print(f"[DEBUG] Model expects {rf_model.n_features_in_} features, providing {X.shape[1]}")
        
        pred_idx = rf_model.predict(X)[0]
        pred_label = label_encoder.inverse_transform([pred_idx])[0]
        probs = rf_model.predict_proba(X)[0]
        class_probs = dict(zip(label_encoder.classes_, probs))
        top3 = sorted(class_probs.items(), key=lambda kv: kv[1], reverse=True)[:3]
        max_prob = max(probs)
        timestamp = datetime.now().isoformat()
        
        print(f"[DEBUG] Prediction confidence: {max_prob:.3f}")
        if max_prob < 0.3:
            print("[WARNING] Low confidence prediction")
        
        # Update latest prediction
        global latest_prediction
        latest_prediction = {
            "location": pred_label,
            "confidence": float(max_prob),
            "top3": [(loc, float(prob)) for loc, prob in top3],
            "matched_aps": matched_count,
            "total_aps": len(scan_items),
            "timestamp": timestamp
        }
        
        # Broadcast to WebSocket clients
        await manager.broadcast(latest_prediction)
        
        # Log to terminal
        print("\n===============================")
        print(" NEW PREDICTION RECEIVED")
        print("===============================")
        print("Scanned APs (raw):", len(scan_items))
        print("Scanned BSSIDs:", [normalize_bssid(s["bssid"]) for s in scan_items[:5]], "...")
        print("Predicted Location:", pred_label)
        print(f"Confidence: {max_prob:.2%}")
        print("Top-3:", [(loc, f"{prob:.2%}") for loc, prob in top3])
        print("===============================\n")
        
        return PredictionResponse(**latest_prediction)
        
    except ValueError as e:
        print(f"[ERROR] ValueError during prediction: {e}")
        print(f"[ERROR] X shape: {X.shape}, X dtypes: {X.dtypes.unique()}")
        raise HTTPException(status_code=500, detail=f"Model prediction failed (ValueError): {str(e)}")
    except Exception as e:
        print(f"[ERROR] Unexpected error during prediction: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Model prediction failed: {str(e)}")

@app.get("/latest")
def get_latest_prediction():
    if latest_prediction["location"] is None:
        return {"message": "No predictions yet", "data": None}
    return latest_prediction

@app.get("/locations")
def get_locations():
    return {
        "locations": list(label_encoder.classes_),
        "count": len(label_encoder.classes_)
    }

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        if latest_prediction["location"] is not None:
            await websocket.send_json(latest_prediction)
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
