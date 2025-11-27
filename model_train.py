"""
model_train_fixed.py

- Loads wifi_training_wide_per_scan.csv (one row per scan)
- Uses AP columns as features (excludes Timestamp and Device_ID)
- Trains RandomForestClassifier with safe stratify / CV logic
- Evaluates and saves model, encoder, and feature list
"""

import pandas as pd
import numpy as np
import joblib
import os
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.preprocessing import LabelEncoder

# ---------------- CONFIG ----------------
INPUT_FILE = "wifi_training_wide_per_scan.csv"   # generated earlier
MODEL_FILE = "rf_wifi_model.pkl"
ENCODER_FILE = "label_encoder.pkl"
FEATURE_LIST_FILE = "feature_list_used.csv"     # saves AP column order

TEST_SIZE = 0.20
N_ESTIMATORS = 300
RANDOM_STATE = 42
# ----------------------------------------

def load_data():
    print(f"Loading {INPUT_FILE} ...")
    if not os.path.exists(INPUT_FILE):
        raise FileNotFoundError(f"File not found: {INPUT_FILE}")

    df = pd.read_csv(INPUT_FILE)
    # Decide which columns are metadata (not features)
    # As you requested, exclude Timestamp and Device_ID from training.
    meta_cols = {"Location_Label", "Burst_ID", "Scan_Index"}  # required metadata to keep
    # Validate presence
    missing_meta = meta_cols - set(df.columns)
    if missing_meta:
        raise ValueError(f"Missing expected metadata columns in input CSV: {missing_meta}")

    # Feature columns = all columns except meta ones
    feature_cols = [c for c in df.columns if c not in meta_cols]

    # BUT ensure we don't accidentally include strings (defensive)
    # Convert feature cols to numeric where possible; if some columns are non-numeric drop them with a warning.
    non_numeric = []
    for c in feature_cols:
        # attempt conversion
        try:
            pd.to_numeric(df[c])
        except Exception:
            non_numeric.append(c)

    if non_numeric:
        # If non-numeric columns are present, remove them from features (they're probably metadata like Timestamp / Device_ID)
        print("Warning: Found non-numeric columns in feature candidate list. Removing them from features:", non_numeric)
        feature_cols = [c for c in feature_cols if c not in non_numeric]

    X = df[feature_cols].astype(float)
    y = df["Location_Label"].astype(str)

    print(f"Loaded {len(df)} samples, feature columns: {len(feature_cols)}")
    return X, y, feature_cols

def safe_stratify_split(X, y, test_size=TEST_SIZE, random_state=RANDOM_STATE):
    # Check class counts
    vc = y.value_counts()
    min_count = int(vc.min())
    print("\nLabel distribution (counts):")
    print(vc.to_string())

    # Determine whether stratify is safe: for stratify, every class should have at least 2 samples
    if min_count < 2:
        print("\nWarning: At least one class has <2 samples. Skipping stratified split to avoid errors.")
        stratify = None
    else:
        stratify = y

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state, stratify=stratify
    )
    return X_train, X_test, y_train, y_test

def encode_labels(y_train, y_test):
    encoder = LabelEncoder()
    y_all = pd.concat([y_train, y_test])
    encoder.fit(y_all)
    y_train_enc = encoder.transform(y_train)
    y_test_enc = encoder.transform(y_test)
    print("\nLabels encoded. Classes:")
    print(list(encoder.classes_))
    return y_train_enc, y_test_enc, encoder

def train_rf(X_train, y_train):
    print("\nTraining RandomForest...")
    rf = RandomForestClassifier(
        n_estimators=N_ESTIMATORS,
        n_jobs=-1,
        random_state=RANDOM_STATE
    )
    rf.fit(X_train, y_train)
    print("Training finished.")
    return rf

def safe_cross_val(rf, X, y_enc):
    # Determine minimum samples per class to pick a safe cv
    import math
    vc = pd.Series(y_enc).value_counts()
    min_count = int(vc.min())
    if min_count < 2:
        print("\nSkipping cross-validation: some classes have <2 samples.")
        return None
    # Choose cv = min(5, min_count)
    cv = min(5, min_count)
    if cv < 2:
        print("\nSkipping cross-validation: insufficient class samples.")
        return None
    print(f"\nRunning cross-validation with cv={cv} (safe value based on min class count={min_count}) ...")
    scores = cross_val_score(rf, X, y_enc, cv=cv, n_jobs=-1)
    print("CV scores:", scores)
    print(f"CV mean accuracy: {scores.mean():.4f}")
    return scores

def evaluate(rf, X_test, y_test_enc, encoder):
    print("\nEvaluating on test set...")
    y_pred = rf.predict(X_test)
    # Convert numeric labels back to strings for human-readable report
    y_true_labels = encoder.inverse_transform(y_test_enc)
    y_pred_labels = encoder.inverse_transform(y_pred)
    print("\nClassification Report:")
    print(classification_report(y_true_labels, y_pred_labels))
    print("\nConfusion Matrix (rows=true, cols=pred):")
    cm = confusion_matrix(y_true_labels, y_pred_labels, labels=encoder.classes_)
    cm_df = pd.DataFrame(cm, index=encoder.classes_, columns=encoder.classes_)
    print(cm_df.to_string())

def save_artifacts(rf, encoder, feature_cols):
    joblib.dump(rf, MODEL_FILE)
    joblib.dump(encoder, ENCODER_FILE)
    # Save feature order so inference uses same order
    pd.Series(feature_cols).to_csv(FEATURE_LIST_FILE, index=False, header=False)
    print(f"\nSaved model -> {MODEL_FILE}")
    print(f"Saved label encoder -> {ENCODER_FILE}")
    print(f"Saved feature list -> {FEATURE_LIST_FILE}")

def main():
    X, y, feature_cols = load_data()

    X_train, X_test, y_train, y_test = safe_stratify_split(X, y)

    # Encode labels
    y_train_enc, y_test_enc, encoder = encode_labels(y_train, y_test)

    # Train RF
    rf = train_rf(X_train, y_train_enc)

    # Cross-validation (safe)
    safe_cross_val(rf, X, encoder.transform(pd.concat([y_train, y_test])))

    # Evaluate on test
    evaluate(rf, X_test, y_test_enc, encoder)

    # Save model + encoder + feature list
    save_artifacts(rf, encoder, feature_cols)

if __name__ == "__main__":
    main()
