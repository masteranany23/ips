"""
Google Colab - Convert with full compatibility for Android TFLite 2.16+
"""

# Install SPECIFIC TensorFlow version
#!pip install scikit-learn pandas joblib numpy

# Import libraries
import joblib
import numpy as np
import pandas as pd
import json
import sys
from collections import Counter
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split

print(f"Python: {sys.version}")

# Import TensorFlow
import tensorflow as tf
print(f"TensorFlow: {tf.__version__}")  # Should show 2.14.0

# Load trained artifacts
print("\n" + "="*60)
print("Loading trained Random Forest model...")
print("="*60)

rf_model = joblib.load("rf_wifi_model.pkl")
label_encoder = joblib.load("label_encoder.pkl")
feature_list = pd.read_csv("feature_list_used.csv", header=None)[0].astype(str).tolist()

print(f"✓ Features: {len(feature_list)}")
print(f"✓ Classes: {label_encoder.classes_}")

# Load training data
print("\nLoading training data...")
df = pd.read_csv("wifi_training_wide_per_scan.csv")

# Extract features and labels
meta_cols = ['Location_Label', 'Burst_ID', 'Scan_Index']
feature_cols = [c for c in df.columns if c not in meta_cols]

# Verify feature match
if set(feature_cols) != set(feature_list):
    print("⚠️  WARNING: Feature mismatch between CSV and feature_list")
    print(f"   CSV has {len(feature_cols)} features")
    print(f"   feature_list has {len(feature_list)} features")

# Build X in EXACT order from feature_list
X = df[feature_list].values.astype(np.float32)
y_labels = df['Location_Label'].values
y = label_encoder.transform(y_labels)

print(f"✓ Dataset: {len(X)} samples, {len(feature_list)} features, {len(label_encoder.classes_)} classes")

# Verify RF predictions match training labels
print("\n" + "="*60)
print("Verifying Random Forest accuracy...")
print("="*60)

rf_pred = rf_model.predict(X)
rf_pred_labels = label_encoder.inverse_transform(rf_pred)

matches = sum(rf_pred_labels == y_labels)
accuracy_pct = matches/len(y_labels)*100
print(f"RF accuracy on training data: {matches}/{len(y_labels)} ({accuracy_pct:.1f}%)")

if matches < len(y_labels) * 0.95:
    print("⚠️  WARNING: RF doesn't predict its own training data well!")
    print("   This model may not convert well to TFLite")

print(f"\nRF prediction distribution:")
print(Counter(rf_pred_labels))

# Knowledge Distillation: Train NN to mimic RF
print("\n" + "="*60)
print("Training Neural Network (Knowledge Distillation)")
print("="*60)

# Get soft targets from RF
rf_probs = rf_model.predict_proba(X)

# Build neural network with COMPATIBLE ops
try:
    import keras
except:
    keras = tf.keras

print("\nBuilding neural network (Android-compatible)...")

# Use SIMPLE architecture that works with TFLite
model = keras.Sequential([
    keras.layers.Input(shape=(len(feature_list),), name='input'),
    keras.layers.Dense(256, activation='relu', name='dense1'),
    keras.layers.Dense(128, activation='relu', name='dense2'),
    keras.layers.Dense(64, activation='relu', name='dense3'),
    keras.layers.Dense(len(label_encoder.classes_), activation='softmax', name='output')
])

# Use regular Adam optimizer (Keras 3 compatible)
model.compile(
    optimizer=keras.optimizers.Adam(learning_rate=0.0003),
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

model.summary()

# Stratified split
X_train, X_val, y_train, y_val = train_test_split(
    X, rf_probs,
    test_size=0.15,
    stratify=y_labels,
    random_state=42
)

print(f"\n✓ Training: {len(X_train)}, Validation: {len(X_val)}")

# Train
history = model.fit(
    X_train, y_train,
    validation_data=(X_val, y_val),
    epochs=500,
    batch_size=8,
    verbose=2,
    callbacks=[
        keras.callbacks.EarlyStopping(
            monitor='val_accuracy',
            patience=80,
            restore_best_weights=True,
            mode='max',
            verbose=1
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor='val_accuracy',
            factor=0.3,
            patience=30,
            min_lr=1e-6,
            mode='max',
            verbose=1
        )
    ]
)

# Evaluate NN vs RF
print("\n" + "="*60)
print("Evaluating Neural Network")
print("="*60)

nn_preds = model.predict(X, verbose=0)
nn_pred_labels = [label_encoder.classes_[np.argmax(p)] for p in nn_preds]

nn_matches = sum(n == r for n, r in zip(nn_pred_labels, rf_pred_labels))
nn_accuracy = nn_matches / len(rf_pred_labels) * 100

print(f"\nNN agreement with RF: {nn_matches}/{len(rf_pred_labels)} ({nn_accuracy:.1f}%)")
print(f"\nPer-class comparison:")
print(classification_report(rf_pred_labels, nn_pred_labels, zero_division=0))

# Convert to TFLite with QUANTIZATION (forces compatible ops)
print("\n" + "="*60)
print("Converting to TFLite with QUANTIZATION (Android compatible)")
print("="*60)

converter = tf.lite.TFLiteConverter.from_keras_model(model)

# CRITICAL: Use quantization with representative dataset
# This FORCES older op versions that Android supports
def representative_dataset():
    for i in range(min(100, len(X))):
        yield [X[i:i+1]]

converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.representative_dataset = representative_dataset

# Set to use only compatible ops
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
converter.inference_input_type = tf.float32  # Keep input as float32
converter.inference_output_type = tf.float32  # Keep output as float32

try:
    tflite_model = converter.convert()
    print("✓ Conversion with INT8 quantization successful!")
except Exception as e:
    print(f"INT8 conversion failed: {e}")
    print("\nFalling back to dynamic range quantization...")
    
    # Fallback: simpler quantization
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    
    tflite_model = converter.convert()
    print("✓ Conversion with dynamic quantization successful!")

output_file = "wifi_positioning.tflite"
with open(output_file, 'wb') as f:
    f.write(tflite_model)

print(f"✓ Model saved: {output_file} ({len(tflite_model)/1024:.2f} KB)")

# Test TFLite
print("\n" + "="*60)
print("Testing TFLite Model")
print("="*60)

interpreter = tf.lite.Interpreter(model_path=output_file)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

tflite_pred_labels = []
for i in range(len(X)):
    interpreter.set_tensor(input_details[0]['index'], X[i:i+1])
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    pred_idx = np.argmax(output[0])
    tflite_pred_labels.append(label_encoder.classes_[pred_idx])

tflite_matches = sum(t == r for t, r in zip(tflite_pred_labels, rf_pred_labels))
tflite_accuracy = tflite_matches / len(rf_pred_labels) * 100

print(f"\nTFLite agreement with RF: {tflite_matches}/{len(rf_pred_labels)} ({tflite_accuracy:.1f}%)")
print(f"\nPer-class TFLite accuracy:")
print(classification_report(rf_pred_labels, tflite_pred_labels, zero_division=0))

# Save metadata
metadata = {
    "feature_list": feature_list,
    "classes": label_encoder.classes_.tolist(),
    "n_features": len(feature_list),
    "n_classes": len(label_encoder.classes_),
    "tf_version": tf.__version__,
    "nn_agreement_with_rf": f"{nn_accuracy:.1f}%",
    "tflite_agreement_with_rf": f"{tflite_accuracy:.1f}%",
    "training_samples": len(X)
}

with open("model_metadata.json", "w") as f:
    json.dump(metadata, f, indent=2)

print("\n✓ Metadata saved: model_metadata.json")

# Final summary
print("\n" + "="*60)
print("✅ CONVERSION COMPLETE!")
print("="*60)
print(f"\n📊 Results:")
print(f"  • Training samples: {len(X)}")
print(f"  • Features: {len(feature_list)}")
print(f"  • Locations: {len(label_encoder.classes_)}")
print(f"  • NN → RF agreement: {nn_accuracy:.1f}%")
print(f"  • TFLite → RF agreement: {tflite_accuracy:.1f}%")
print(f"  • Model size: {len(tflite_model)/1024:.2f} KB")

print(f"\n📥 Download:")
print(f"  • wifi_positioning.tflite")
print(f"  • model_metadata.json")

print(f"\n📍 Locations: {', '.join(label_encoder.classes_)}")

if tflite_accuracy >= 95:
    print("\n✅ EXCELLENT! TFLite matches RF perfectly!")
elif tflite_accuracy >= 90:
    print("\n✅ GOOD quality (≥90%)")
elif tflite_accuracy >= 80:
    print("\n⚠️  ACCEPTABLE (80-90%)")
else:
    print("\n❌ POOR (<80%) - Try more training epochs or larger model")
