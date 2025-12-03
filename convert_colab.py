"""
Google Colab Script - Convert Random Forest to TFLite
Upload: rf_wifi_model.pkl, label_encoder.pkl, feature_list_used.csv, wifi_training_wide_per_scan.csv
"""

# First cell - Install dependencies
!pip install scikit-learn pandas joblib numpy

# Second cell - Convert model
import joblib
import numpy as np
import pandas as pd
import json
import sys

print(f"Python: {sys.version}")

# Import TensorFlow
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
print(f"Classes: {label_encoder.classes_}")

# Load training data - BUILD IN EXACT ORDER
print("\nLoading training data...")
df = pd.read_csv("wifi_training_wide_per_scan.csv")

# Verify class distribution BEFORE any processing
print("\nOriginal CSV class distribution:")
print(df['Location_Label'].value_counts().sort_index())

# BUILD DATAFRAME to preserve column names
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
X = X_df.values.astype(np.float32)

# Get ORIGINAL labels from CSV (not RF predictions)
y_original = df["Location_Label"].values
y = label_encoder.transform(y_original).astype(np.int32)

n_features = len(feature_list)
n_classes = len(label_encoder.classes_)

print(f"\nDataset: {len(X)} samples, {n_features} features, {n_classes} classes")
print(f"Original label distribution: {Counter(y_original)}")

# USE RANDOM FOREST PREDICTIONS (Knowledge Distillation)
print("\n" + "="*60)
print("Knowledge Distillation from Random Forest")
print("="*60)

# Get RF predictions as soft labels
rf_predictions = rf_model.predict_proba(X_df)

# Get RF hard predictions to check agreement
rf_hard_labels = rf_model.predict(X_df)
rf_label_names = label_encoder.inverse_transform(rf_hard_labels)

print(f"\nRandom Forest predictions on training data:")
print(Counter(rf_label_names))

# IMPORTANT: Check if RF agrees with original labels
agreement = sum(rf_label_names == y_original)
print(f"\nRF agreement with original labels: {agreement}/{len(y_original)} ({agreement/len(y_original)*100:.1f}%)")

if agreement < len(y_original) * 0.9:  # Less than 90% agreement
    print("\n⚠️  WARNING: RF model disagrees significantly with training labels!")
    print("   This suggests the model may be overtrained or data has issues.")
    print("   Proceeding anyway, but results may be poor.")

# Build neural network
try:
    import keras
    print(f"\nUsing Keras: {keras.__version__}")
except:
    keras = tf.keras
    print(f"\nUsing tf.keras")

print("\nBuilding neural network...")
model = keras.Sequential([
    keras.layers.Input(shape=(n_features,), name='input'),
    keras.layers.Dense(256, activation='relu', name='hidden1'),  # Larger for 258 features
    keras.layers.Dropout(0.4, name='dropout1'),
    keras.layers.Dense(128, activation='relu', name='hidden2'),
    keras.layers.Dropout(0.3, name='dropout2'),
    keras.layers.Dense(64, activation='relu', name='hidden3'),
    keras.layers.Dropout(0.2, name='dropout3'),
    keras.layers.Dense(n_classes, activation='softmax', name='output')
], name='wifi_positioning_nn')

model.compile(
    optimizer=keras.optimizers.Adam(0.0005),  # Lower learning rate for stability
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

model.summary()

# Train to mimic Random Forest
print("\nTraining neural network (mimicking Random Forest)...")

# Use ORIGINAL labels for stratification (not RF predictions)
from sklearn.model_selection import train_test_split
X_train, X_val, y_train, y_val = train_test_split(
    X, rf_predictions,  # Use RF soft labels as targets
    test_size=0.15,
    stratify=y_original,  # Stratify by ORIGINAL labels
    random_state=42
)

print(f"Training samples: {len(X_train)}, Validation samples: {len(X_val)}")

# Check validation set distribution
val_indices = train_test_split(
    range(len(X)), test_size=0.15, stratify=y_original, random_state=42
)[1]
val_labels = y_original[val_indices]
print(f"Validation set distribution: {Counter(val_labels)}")

history = model.fit(
    X_train, y_train,
    validation_data=(X_val, y_val),
    epochs=300,  # Reduced from 500
    batch_size=8,  # Smaller batch for 77 training samples
    verbose=2,  # Less verbose
    callbacks=[
        keras.callbacks.EarlyStopping(
            monitor='val_accuracy',  # Monitor accuracy instead of loss
            patience=50,
            restore_best_weights=True,
            verbose=1,
            mode='max'
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor='val_accuracy',
            factor=0.5,
            patience=20,
            min_lr=0.00001,
            verbose=1,
            mode='max'
        )
    ]
)

# Test against ORIGINAL labels (not RF)
print("\n" + "="*60)
print("Comparing Neural Network vs Original Labels")
print("="*60)

keras_preds = model.predict(X, verbose=0)
keras_labels = [label_encoder.classes_[np.argmax(p)] for p in keras_preds]

matches = sum(k == o for k, o in zip(keras_labels, y_original))
agreement_pct = matches/len(X)*100

print(f"\nAgreement with ORIGINAL labels: {matches}/{len(X)} ({agreement_pct:.1f}%)")
print(f"Agreement with RF labels: {sum(k == r for k, r in zip(keras_labels, rf_label_names))}/{len(X)}")

# Convert to TFLite WITHOUT quantization
print("\n" + "="*60)
print("Converting to TFLite (no quantization for max accuracy)")
print("="*60)

converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = []  # NO quantization
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
tflite_predictions = []

rf_labels_names_all = label_encoder.inverse_transform(rf_model.predict(X_df))

for i in range(len(X)):
    test_input = X[i:i+1].astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    tflite_output = interpreter.get_tensor(output_details[0]['index'])
    
    tflite_pred = label_encoder.classes_[np.argmax(tflite_output[0])]
    rf_pred = rf_labels_names_all[i]
    
    tflite_predictions.append(tflite_pred)
    if tflite_pred == rf_pred:
        tflite_matches_rf += 1

tflite_accuracy = tflite_matches_rf/len(X)*100

print(f"\nTFLite matches Random Forest: {tflite_matches_rf}/{len(X)} ({tflite_accuracy:.1f}%)")

# Show per-location TFLite accuracy
print("\nTFLite vs Random Forest per location:")
print(classification_report(rf_labels_names_all, tflite_predictions))

# Save metadata
metadata = {
    "feature_list": feature_list,
    "classes": label_encoder.classes_.tolist(),
    "n_features": n_features,
    "n_classes": n_classes,
    "tf_version": tf.__version__,
    "tflite_accuracy": f"{tflite_accuracy:.1f}%",
    "training_samples": len(X),
    "training_method": "knowledge_distillation",
    "model_architecture": "256-128-64-9",
    "locations": label_encoder.classes_.tolist()
}

metadata_file = "model_metadata.json"
with open(metadata_file, "w") as f:
    json.dump(metadata, f, indent=2)

print(f"\n✓ Metadata saved: {metadata_file}")

# Final summary
print("\n" + "="*60)
print("✅ CONVERSION COMPLETE!")
print("="*60)
print(f"\n📊 Results:")
print(f"  • Training samples: {len(X)}")
print(f"  • Features: {n_features}")
print(f"  • Locations: {n_classes}")
print(f"  • Neural Network → Random Forest: {agreement_pct:.1f}%")
print(f"  • TFLite → Random Forest: {tflite_accuracy:.1f}%")
print(f"  • Model size: {len(tflite_model)/1024:.2f} KB")

print(f"\n Download these files from Colab:")
print(f"  1. {output_file}")
print(f"  2. {metadata_file}")

print(f"\n📲 Copy to Android:")
print(f"  androidapp/app/src/main/assets/wifi_positioning.tflite")
print(f"  androidapp/app/src/main/assets/model_metadata.json")

if tflite_accuracy >= 90:
    print("\n✅ Model quality: EXCELLENT (≥90%)")
elif tflite_accuracy >= 80:
    print("\n✅ Model quality: GOOD (80-90%)")
elif tflite_accuracy >= 70:
    print("\n⚠️  Model quality: ACCEPTABLE (70-80%)")
else:
    print("\n❌ Model quality: POOR (<70%)")
    print("   Try: More epochs, larger model, or collect more data")

print(f"\n� Your 9 locations: {', '.join(label_encoder.classes_)}")
