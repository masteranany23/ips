"""
Google Colab Script - Compatible with Colab's Python 3.10+
Upload: rf_wifi_model.pkl, label_encoder.pkl, feature_list_used.csv, wifi_training_wide_per_scan.csv
"""

# First cell - Install dependencies (use available versions)
!pip install scikit-learn pandas joblib numpy

# Second cell - Convert model
import joblib
import numpy as np
import pandas as pd
import json
import sys

print(f"Python: {sys.version}")

# Import TensorFlow (use whatever version Colab has)
try:
    import tensorflow as tf
    print(f"TensorFlow: {tf.__version__}")
except ImportError:
    !pip install tensorflow
    import tensorflow as tf
    print(f"TensorFlow: {tf.__version__}")

# Load models
print("\nLoading trained models...")
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
print(f"Features: {len(feature_list)}")

# Load training data - BUILD IN EXACT ORDER
print("Loading training data...")
df = pd.read_csv("wifi_training_wide_per_scan.csv")

# BUILD DATAFRAME (not numpy array) to preserve column names
X_data = []
for _, row in df.iterrows():
    feature_vector = []
    for bssid in feature_list:
        if bssid in df.columns:
            feature_vector.append(row[bssid] if pd.notna(row[bssid]) else -110.0)
        else:
            feature_vector.append(-110.0)
    X_data.append(feature_vector)

# CREATE DATAFRAME with column names (not numpy array)
X_df = pd.DataFrame(X_data, columns=feature_list)
X = X_df.values.astype(np.float32)  # Still convert to numpy for neural network
y = label_encoder.transform(df["Location_Label"]).astype(np.int32)

n_features = len(feature_list)
n_classes = len(label_encoder.classes_)

print(f"\nDataset: {len(X)} samples, {n_features} features, {n_classes} classes")

# USE RANDOM FOREST PREDICTIONS AS TRAINING LABELS (Knowledge Distillation)
print("\n" + "="*60)
print("Using Knowledge Distillation (learning from Random Forest)")
print("="*60)

# PASS DATAFRAME TO RANDOM FOREST (not numpy array)
rf_predictions = rf_model.predict_proba(X_df)  # Use X_df instead of X

# Show what RF predicts
rf_hard_labels = rf_model.predict(X_df)  # Use X_df instead of X
rf_label_names = label_encoder.inverse_transform(rf_hard_labels)
print(f"\nRandom Forest predictions on training data:")
from collections import Counter
print(Counter(rf_label_names))

# Build neural network - using Keras (works with any TF version)
try:
    import keras
    print(f"\nUsing Keras: {keras.__version__}")
except:
    keras = tf.keras
    print(f"\nUsing tf.keras")

print("\nBuilding neural network...")
model = keras.Sequential([
    keras.layers.Input(shape=(n_features,), name='input'),
    keras.layers.Dense(128, activation='relu', name='hidden1'),
    keras.layers.Dropout(0.3, name='dropout1'),
    keras.layers.Dense(64, activation='relu', name='hidden2'),
    keras.layers.Dropout(0.2, name='dropout2'),
    keras.layers.Dense(n_classes, activation='softmax', name='output')
], name='wifi_positioning_nn')

model.compile(
    optimizer=keras.optimizers.Adam(0.001),
    loss='categorical_crossentropy',  # Use soft labels from RF
    metrics=['accuracy']
)

model.summary()

# Train to mimic Random Forest
print("\nTraining neural network to mimic Random Forest...")
print("This learns the decision boundaries of the RF model")
history = model.fit(
    X, rf_predictions,  # Use RF probabilities as soft labels
    epochs=200,
    batch_size=4,
    validation_split=0.2,
    verbose=2  # Less verbose output
)

# Test against Random Forest
print("\n" + "="*60)
print("Comparing Neural Network vs Random Forest")
print("="*60)
keras_preds = model.predict(X, verbose=0)
keras_labels = [label_encoder.classes_[np.argmax(p)] for p in keras_preds]
rf_labels_names = label_encoder.inverse_transform(rf_model.predict(X_df))  # Use X_df

matches = sum(k == r for k, r in zip(keras_labels, rf_labels_names))
agreement_pct = matches/len(X)*100

print(f"\nAgreement with RF: {matches}/{len(X)} ({agreement_pct:.1f}%)")
print("\nPer-sample comparison (first 10):")
for i in range(min(10, len(X))):
    match = "✓" if keras_labels[i] == rf_labels_names[i] else "✗"
    print(f"  {i}: NN={keras_labels[i]}, RF={rf_labels_names[i]} {match}")

# Convert to TFLite WITHOUT quantization for maximum compatibility
print("\n" + "="*60)
print("Converting to TFLite (no quantization)")
print("="*60)

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = []  # NO optimization - preserve accuracy
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]

try:
    tflite_model = converter.convert()
    print("✓ Conversion successful!")
except Exception as e:
    print(f"Conversion failed: {e}")
    print("\nTrying with default optimizations...")
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    print("✓ Conversion successful with optimizations!")

# Save
output_file = "wifi_positioning.tflite"
with open(output_file, 'wb') as f:
    f.write(tflite_model)

print(f"\n✓ Model saved: {output_file}")
print(f"  Size: {len(tflite_model)/1024:.2f} KB")

# Test TFLite model
print("\n" + "="*60)
print("Testing TFLite model")
print("="*60)

interpreter = tf.lite.Interpreter(model_path=output_file)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"Input shape: {input_details[0]['shape']}")
print(f"Output shape: {output_details[0]['shape']}")

# Test all samples
tflite_matches_rf = 0
tflite_matches_keras = 0

rf_labels_names_all = label_encoder.inverse_transform(rf_model.predict(X_df))  # Use X_df

for i in range(len(X)):
    test_input = X[i:i+1].astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    tflite_output = interpreter.get_tensor(output_details[0]['index'])
    
    tflite_pred = label_encoder.classes_[np.argmax(tflite_output[0])]
    rf_pred = rf_labels_names_all[i]  # Use corrected RF predictions
    keras_pred = keras_labels[i]
    
    if tflite_pred == rf_pred:
        tflite_matches_rf += 1
    if tflite_pred == keras_pred:
        tflite_matches_keras += 1

print(f"\nTFLite matches Random Forest: {tflite_matches_rf}/{len(X)} ({tflite_matches_rf/len(X)*100:.1f}%)")
print(f"TFLite matches Keras: {tflite_matches_keras}/{len(X)} ({tflite_matches_keras/len(X)*100:.1f}%)")

# Save metadata
metadata = {
    "feature_list": feature_list,
    "classes": label_encoder.classes_.tolist(),
    "n_features": n_features,
    "n_classes": n_classes,
    "tf_version": tf.__version__,
    "agreement_with_rf": f"{tflite_matches_rf/len(X)*100:.1f}%",
    "training_samples": len(X),
    "training_method": "knowledge_distillation"
}

metadata_file = "model_metadata.json"
with open(metadata_file, "w") as f:
    json.dump(metadata, f, indent=2)

print(f"\n✓ Metadata saved: {metadata_file}")

# Final summary
print("\n" + "="*60)
print("CONVERSION COMPLETE!")
print("="*60)
print(f"\n📊 Results:")
print(f"  • Training samples: {len(X)}")
print(f"  • Neural Network agreement with Random Forest: {agreement_pct:.1f}%")
print(f"  • TFLite agreement with Random Forest: {tflite_matches_rf/len(X)*100:.1f}%")
print(f"  • Model size: {len(tflite_model)/1024:.2f} KB")

print(f"\n📥 Download these files:")
print(f"  1. {output_file}")
print(f"  2. {metadata_file}")

print(f"\n📲 Copy to Android:")
print(f"  androidapp/app/src/main/assets/")

if tflite_matches_rf/len(X) > 0.7:
    print("\n✅ Model quality: GOOD (>70% agreement)")
elif tflite_matches_rf/len(X) > 0.5:
    print("\n⚠️  Model quality: ACCEPTABLE (50-70% agreement)")
else:
    print("\n❌ Model quality: POOR (<50% agreement)")
    print("   Consider collecting more training data!")
