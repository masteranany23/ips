"""
Verify training data integrity before conversion
"""
import pandas as pd
import joblib
import numpy as np

print("Verifying training data integrity...")

# Load wide format
df = pd.read_csv("wifi_training_wide_per_scan.csv")
print(f"\nOriginal CSV: {len(df)} rows")
print("\nClass distribution in CSV:")
print(df['Location_Label'].value_counts().sort_index())

# Load model
rf_model = joblib.load("rf_wifi_model.pkl")
label_encoder = joblib.load("label_encoder.pkl")
feature_list_raw = pd.read_csv("feature_list_used.csv", header=None)[0].astype(str).tolist()

# Normalize BSSIDs
def normalize_bssid(bssid: str) -> str:
    normalized = str(bssid).strip().lower().replace("-", ":")
    if not normalized.endswith(":"):
        normalized += ":"
    return normalized

feature_list = [normalize_bssid(bssid) for bssid in feature_list_raw]

# Rebuild X exactly as training
meta_cols = ['Location_Label', 'Burst_ID', 'Scan_Index']
feature_cols = [c for c in df.columns if c not in meta_cols]

# Check if feature columns match
print(f"\nFeature columns in CSV: {len(feature_cols)}")
print(f"Feature list from model: {len(feature_list)}")

# Build DataFrame with exact feature order
X_data = []
for _, row in df.iterrows():
    feature_vector = []
    for bssid in feature_list:
        if bssid in df.columns:
            feature_vector.append(row[bssid] if pd.notna(row[bssid]) else -110.0)
        else:
            feature_vector.append(-110.0)
    X_data.append(feature_vector)

X_df = pd.DataFrame(X_data, columns=feature_list)

# Predict with RF
rf_predictions = rf_model.predict(X_df)
rf_labels = label_encoder.inverse_transform(rf_predictions)

print("\nRandom Forest predictions on training data:")
from collections import Counter
pred_counts = Counter(rf_labels)
print(pred_counts)

# Compare with original labels
original_labels = df['Location_Label'].values
mismatches = sum(rf_labels != original_labels)

print(f"\nMismatches: {mismatches}/{len(df)}")
if mismatches > 0:
    print("\nMismatched rows:")
    for i, (orig, pred) in enumerate(zip(original_labels, rf_labels)):
        if orig != pred:
            print(f"  Row {i}: Original={orig}, Predicted={pred}")
