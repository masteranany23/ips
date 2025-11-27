# prediction_api_fixed.py
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Dict, Any
import joblib
import pandas as pd
import numpy as np
import os

# -----------------------------
MODEL_PATH = "rf_wifi_model.pkl"
ENCODER_PATH = "label_encoder.pkl"
FEATURE_LIST_PATH = "feature_list_used.csv"
MISSING_RSSI = -110
# -----------------------------

# Load artifacts
if not os.path.exists(MODEL_PATH) or not os.path.exists(ENCODER_PATH) or not os.path.exists(FEATURE_LIST_PATH):
    raise RuntimeError("Model, encoder or feature list not found. Make sure rf_wifi_model.pkl, label_encoder.pkl and feature_list_used.csv exist.")

def normalize_bssid(bssid: str) -> str:
    """Normalize BSSID: lowercase, replace - with :, ADD trailing colon to match training data format"""
    normalized = str(bssid).strip().lower().replace("-", ":")
    # Add trailing colon if not present (training data has it)
    if not normalized.endswith(":"):
        normalized += ":"
    return normalized

rf_model = joblib.load(MODEL_PATH)
label_encoder = joblib.load(ENCODER_PATH)

# Load feature list and normalize BSSIDs (remove trailing colons, lowercase, etc.)
feature_list_raw = pd.read_csv(FEATURE_LIST_PATH, header=None)[0].astype(str).tolist()
feature_list = [normalize_bssid(bssid) for bssid in feature_list_raw]
print(f"[INFO] Loaded model with {len(feature_list)} features.")
print(f"[INFO] Sample features: {feature_list[:3]}")

app = FastAPI(title="WiFi Indoor Positioning API (fixed)")

class ScanItem(BaseModel):
    bssid: str
    rssi: float

class ScanRequest(BaseModel):
    # accept either "scans" (preferred) or "access_points" (some sketches used that)
    scans: List[ScanItem] = None
    access_points: List[ScanItem] = None

def build_feature_df(scan_items: List[Dict[str, Any]]):
    """
    Build a 1-row DataFrame with columns exactly = feature_list and values filled with MISSING_RSSI
    """
    # Initialize with missing sentinel - USE DICT TO PRESERVE ORDER
    data = {}
    for bssid in feature_list:
        data[bssid] = MISSING_RSSI
    
    # Fill from scan
    matched_count = 0
    matched_bssids = []
    for it in scan_items:
        b = normalize_bssid(it.get("bssid",""))
        try:
            r = float(it.get("rssi"))
        except Exception:
            continue
        if b in data:
            data[b] = r
            matched_count += 1
            matched_bssids.append(b)
    
    # Create DataFrame with columns in exact order from feature_list
    df = pd.DataFrame([list(data.values())], columns=feature_list)
    
    # Log feature statistics for debugging
    non_missing = (df.iloc[0] != MISSING_RSSI).sum()
    print(f"[DEBUG] Matched {matched_count} APs from scan to known features")
    print(f"[DEBUG] Matched BSSIDs: {matched_bssids[:3]}...")
    print(f"[DEBUG] Non-missing features: {non_missing}/{len(feature_list)}")
    print(f"[DEBUG] Feature value range: [{df.iloc[0].min():.1f}, {df.iloc[0].max():.1f}]")
    
    # Verify DataFrame shape
    print(f"[DEBUG] DataFrame shape: {df.shape}")
    print(f"[DEBUG] DataFrame dtypes: {df.dtypes.unique()}")
    
    return df

@app.post("/predict")
def predict(req: ScanRequest):
    # pick whichever key is provided
    raw_list = None
    if req.scans:
        raw_list = req.scans
    elif req.access_points:
        raw_list = req.access_points
    else:
        raise HTTPException(status_code=400, detail="No scans provided. Use JSON key 'scans' with list of {bssid,rssi}.")

    # Convert Pydantic objects to dicts
    scan_items = [{"bssid": s.bssid, "rssi": s.rssi} for s in raw_list]

    # Build DataFrame with correct feature names
    X = build_feature_df(scan_items)
    
    # Additional validation
    if X.shape[1] != len(feature_list):
        raise HTTPException(status_code=500, detail=f"Feature mismatch: got {X.shape[1]}, expected {len(feature_list)}")
    
    # Verify column names match exactly
    if not all(X.columns == feature_list):
        print("[ERROR] Column order mismatch detected!")
        raise HTTPException(status_code=500, detail="Feature column order mismatch")

    # Predict with detailed error handling
    try:
        # Check if model expects the right number of features
        print(f"[DEBUG] Model expects {rf_model.n_features_in_} features, providing {X.shape[1]}")
        
        pred_idx = rf_model.predict(X)[0]
        pred_label = label_encoder.inverse_transform([pred_idx])[0]
        probs = rf_model.predict_proba(X)[0]
        # map labels to probabilities
        class_probs = dict(zip(label_encoder.classes_, probs))
        # top-3
        top3 = sorted(class_probs.items(), key=lambda kv: kv[1], reverse=True)[:3]
        
        # Log confidence - low confidence indicates poor feature quality
        max_prob = max(probs)
        print(f"[DEBUG] Prediction confidence: {max_prob:.3f}")
        if max_prob < 0.3:
            print("[WARNING] Low confidence prediction - check if scanned APs match training data")
            
    except ValueError as e:
        print(f"[ERROR] ValueError during prediction: {e}")
        print(f"[ERROR] X shape: {X.shape}, X dtypes: {X.dtypes.unique()}")
        print(f"[ERROR] First few values: {X.iloc[0, :5].tolist()}")
        raise HTTPException(status_code=500, detail=f"Model prediction failed (ValueError): {str(e)}")
    except Exception as e:
        print(f"[ERROR] Unexpected error during prediction: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Model prediction failed: {e}")

    # Log to terminal
    print("\n===============================")
    print(" NEW PREDICTION RECEIVED")
    print("===============================")
    print("Scanned APs (raw):", len(scan_items))
    print("Scanned BSSIDs:", [normalize_bssid(s["bssid"]) for s in scan_items[:5]], "...")
    print("Predicted Location:", pred_label)
    print(f"Confidence: {max(probs):.2%}")
    print("Top-3:", [(loc, f"{prob:.2%}") for loc, prob in top3])
    print("===============================\n")

    return {"location": pred_label, "confidence": float(max(probs)), "top3": top3, "probs": class_probs}
    
if __name__ == "__main__":
    uvicorn.run("prediction_api:app", host="0.0.0.0", port=8000, reload=True)
