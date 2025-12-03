"""
Train Random Forest model on WiFi fingerprint data
Input: wifi_training_wide_per_scan.csv (Wide format)
Output: rf_wifi_model.pkl, label_encoder.pkl, feature_list_used.csv
"""

import pandas as pd
import numpy as np
import joblib
import os
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.preprocessing import LabelEncoder

# Configuration
INPUT_FILE = "wifi_training_wide_per_scan.csv"
MODEL_FILE = "rf_wifi_model.pkl"
ENCODER_FILE = "label_encoder.pkl"
FEATURE_LIST_FILE = "feature_list_used.csv"

TEST_SIZE = 0.20
N_ESTIMATORS = 300
RANDOM_STATE = 42

def load_data():
    """Load wide format data and separate features/labels"""
    print(f"Loading {INPUT_FILE}...")
    
    if not os.path.exists(INPUT_FILE):
        raise FileNotFoundError(
            f"{INPUT_FILE} not found! Run preprocess_data.py first."
        )
    
    df = pd.read_csv(INPUT_FILE)
    
    # Validate required columns
    if 'Location_Label' not in df.columns:
        raise ValueError("Missing Location_Label column!")
    
    # Metadata columns (not features)
    meta_cols = {'Location_Label', 'Burst_ID', 'Scan_Index'}
    
    # Feature columns = all BSSID columns
    feature_cols = [c for c in df.columns if c not in meta_cols]
    
    # Ensure feature columns are numeric
    for col in feature_cols:
        df[col] = pd.to_numeric(df[col], errors='coerce')
    
    # Fill any remaining NaN with -110
    df[feature_cols] = df[feature_cols].fillna(-110.0)
    
    X = df[feature_cols].values.astype(float)
    y = df['Location_Label'].values
    
    print(f"✓ Loaded {len(df)} samples")
    print(f"  Features (BSSIDs): {len(feature_cols)}")
    print(f"  Locations: {len(np.unique(y))}")
    
    return X, y, feature_cols

def check_data_quality(X, y):
    """Validate data quality"""
    print("\nData Quality Check:")
    
    # Check for NaN
    nan_count = np.isnan(X).sum()
    if nan_count > 0:
        print(f"⚠️  WARNING: {nan_count} NaN values found!")
        return False
    print("✓ No NaN values")
    
    # Check for inf
    inf_count = np.isinf(X).sum()
    if inf_count > 0:
        print(f"⚠️  WARNING: {inf_count} Inf values found!")
        return False
    print("✓ No Inf values")
    
    # Check class distribution
    unique, counts = np.unique(y, return_counts=True)
    print(f"\n✓ Class distribution:")
    for label, count in zip(unique, counts):
        print(f"  {label}: {count} samples")
    
    min_samples = counts.min()
    if min_samples < 2:
        print(f"⚠️  WARNING: Minimum class has only {min_samples} sample(s)")
        print("   Need at least 2 samples per class for train/test split")
        return False
    
    return True

def encode_labels(y):
    """Encode string labels to integers"""
    print("\nEncoding labels...")
    encoder = LabelEncoder()
    y_encoded = encoder.fit_transform(y)
    
    print(f"✓ Classes: {list(encoder.classes_)}")
    return y_encoded, encoder

def train_model(X_train, y_train):
    """Train Random Forest classifier"""
    print("\nTraining Random Forest...")
    print(f"  Estimators: {N_ESTIMATORS}")
    print(f"  Training samples: {len(X_train)}")
    
    rf = RandomForestClassifier(
        n_estimators=N_ESTIMATORS,
        max_depth=None,
        min_samples_split=2,
        min_samples_leaf=1,
        n_jobs=-1,
        random_state=RANDOM_STATE,
        verbose=1
    )
    
    rf.fit(X_train, y_train)
    print("✓ Training complete")
    
    return rf

def evaluate_model(rf, X_test, y_test, encoder):
    """Evaluate model performance"""
    print("\nEvaluating model...")
    
    # Predictions
    y_pred = rf.predict(X_test)
    
    # Convert back to labels
    y_true_labels = encoder.inverse_transform(y_test)
    y_pred_labels = encoder.inverse_transform(y_pred)
    
    # Classification report
    print("\nClassification Report:")
    print(classification_report(y_true_labels, y_pred_labels))
    
    # Confusion matrix
    print("\nConfusion Matrix:")
    cm = confusion_matrix(y_true_labels, y_pred_labels, labels=encoder.classes_)
    cm_df = pd.DataFrame(cm, index=encoder.classes_, columns=encoder.classes_)
    print(cm_df)
    
    # Accuracy
    accuracy = (y_pred == y_test).mean()
    print(f"\n✓ Test Accuracy: {accuracy:.2%}")
    
    return accuracy

def cross_validate(rf, X, y):
    """Perform cross-validation"""
    print("\nCross-validation...")
    
    # Determine safe CV folds
    unique, counts = np.unique(y, return_counts=True)
    min_count = counts.min()
    cv_folds = min(5, min_count)
    
    if cv_folds < 2:
        print("⚠️  Skipping CV: insufficient samples")
        return None
    
    print(f"  Using {cv_folds}-fold CV")
    scores = cross_val_score(rf, X, y, cv=cv_folds, n_jobs=-1, verbose=1)
    
    print(f"  CV Scores: {scores}")
    print(f"  Mean: {scores.mean():.2%} (+/- {scores.std()*2:.2%})")
    
    return scores

def save_artifacts(rf, encoder, feature_cols):
    """Save model, encoder, and feature list"""
    print("\nSaving artifacts...")
    
    joblib.dump(rf, MODEL_FILE)
    print(f"✓ Saved model: {MODEL_FILE}")
    
    joblib.dump(encoder, ENCODER_FILE)
    print(f"✓ Saved encoder: {ENCODER_FILE}")
    
    pd.Series(feature_cols).to_csv(FEATURE_LIST_FILE, index=False, header=False)
    print(f"✓ Saved features: {FEATURE_LIST_FILE}")

def main():
    print("="*60)
    print("WiFi Indoor Positioning - Model Training")
    print("="*60)
    
    # Load data
    X, y, feature_cols = load_data()
    
    # Quality check
    if not check_data_quality(X, y):
        print("\n❌ Data quality check failed!")
        print("   Fix preprocessing issues and try again.")
        return
    
    # Encode labels
    y_encoded, encoder = encode_labels(y)
    
    # Split data
    print(f"\nSplitting data (test_size={TEST_SIZE})...")
    unique, counts = np.unique(y_encoded, return_counts=True)
    min_count = counts.min()
    
    # Use stratify only if all classes have ≥2 samples
    stratify = y_encoded if min_count >= 2 else None
    
    X_train, X_test, y_train, y_test = train_test_split(
        X, y_encoded,
        test_size=TEST_SIZE,
        random_state=RANDOM_STATE,
        stratify=stratify
    )
    
    print(f"✓ Train: {len(X_train)}, Test: {len(X_test)}")
    
    # Train model
    rf = train_model(X_train, y_train)
    
    # Evaluate
    accuracy = evaluate_model(rf, X_test, y_test, encoder)
    
    # Cross-validation
    cross_validate(rf, X, y_encoded)
    
    # Save
    save_artifacts(rf, encoder, feature_cols)
    
    print("\n" + "="*60)
    print("✅ TRAINING COMPLETE!")
    print("="*60)
    print(f"Test Accuracy: {accuracy:.2%}")
    print(f"Model saved: {MODEL_FILE}")
    print("\nNext step: Convert to TFLite using convert_colab.py")

if __name__ == "__main__":
    main()
