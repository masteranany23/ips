"""
Direct model prediction test - bypasses API
"""
import joblib
import pandas as pd
import json

# Same config as API
MODEL_PATH = "rf_wifi_model.pkl"
ENCODER_PATH = "label_encoder.pkl"
FEATURE_LIST_PATH = "feature_list_used.csv"
MISSING_RSSI = -110

def normalize_bssid(bssid: str) -> str:
    """Normalize BSSID: lowercase, replace - with :, ADD trailing colon to match training data format"""
    normalized = str(bssid).strip().lower().replace("-", ":")
    # Add trailing colon if not present (training data has it)
    if not normalized.endswith(":"):
        normalized += ":"
    return normalized

# Load model artifacts
print("Loading model artifacts...")
rf_model = joblib.load(MODEL_PATH)
label_encoder = joblib.load(ENCODER_PATH)
feature_list_raw = pd.read_csv(FEATURE_LIST_PATH, header=None)[0].astype(str).tolist()
feature_list = [normalize_bssid(bssid) for bssid in feature_list_raw]

print(f"Loaded {len(feature_list)} features")
print(f"Model expects {rf_model.n_features_in_} features")
print(f"Sample features from list: {feature_list[:5]}")

# Your test scan data
test_data = {
    "scans": [
        {"bssid": "7a:d4:b4:ea:97:69", "rssi": -28},
        {"bssid": "ae:0d:27:65:6f:7a", "rssi": -68},
        {"bssid": "dc:ea:e7:b9:16:00", "rssi": -89},
        {"bssid": "ea:f3:bc:51:91:c5", "rssi": -74},
        {"bssid": "26:ac:78:6c:d0:11", "rssi": -92}
    ]
}

# Build feature vector
print("\n" + "="*50)
print("Building feature vector...")
data = {}
for bssid in feature_list:
    data[bssid] = MISSING_RSSI

# Fill from scan
matched_count = 0
matched_bssids = []
for scan in test_data["scans"]:
    b = normalize_bssid(scan["bssid"])
    r = float(scan["rssi"])
    
    if b in data:
        data[b] = r
        matched_count += 1
        matched_bssids.append(b)
        print(f"  ✓ Matched: {b} -> {r} dBm")
    else:
        print(f"  ✗ Not in training: {b} ({r} dBm)")

print(f"\nMatched {matched_count}/{len(test_data['scans'])} APs to known features")

# Create DataFrame
X = pd.DataFrame([list(data.values())], columns=feature_list)

print(f"\nDataFrame shape: {X.shape}")
print(f"Non-missing features: {(X.iloc[0] != MISSING_RSSI).sum()}/{len(feature_list)}")
print(f"Value range: [{X.iloc[0].min():.1f}, {X.iloc[0].max():.1f}]")

# Predict
print("\n" + "="*50)
print("Making prediction...")
try:
    pred_idx = rf_model.predict(X)[0]
    pred_label = label_encoder.inverse_transform([pred_idx])[0]
    probs = rf_model.predict_proba(X)[0]
    
    # Map to class names
    class_probs = dict(zip(label_encoder.classes_, probs))
    
    # Top-3
    top3 = sorted(class_probs.items(), key=lambda kv: kv[1], reverse=True)[:3]
    
    max_prob = max(probs)
    
    print("\n" + "="*50)
    print("PREDICTION RESULT")
    print("="*50)
    print(f"Predicted Location: {pred_label}")
    print(f"Confidence: {max_prob:.2%}")
    print("\nTop 3 predictions:")
    for i, (loc, prob) in enumerate(top3, 1):
        print(f"  {i}. {loc}: {prob:.2%}")
    
    if max_prob < 0.3:
        print("\n⚠ WARNING: Low confidence - scanned APs may not match training data well")
    
    print("\n" + "="*50)
    
    # Full probability distribution
    print("\nAll class probabilities:")
    sorted_probs = sorted(class_probs.items(), key=lambda kv: kv[1], reverse=True)
    for loc, prob in sorted_probs:
        bar = "█" * int(prob * 50)
        print(f"  {loc:20s} {prob:6.2%} {bar}")
    
except Exception as e:
    print(f"\n❌ ERROR during prediction: {e}")
    import traceback
    traceback.print_exc()
