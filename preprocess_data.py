"""
Convert WiFi training data from LONG format to WIDE format for ML training
Input: wifi_training_data.csv (Long format with multiple rows per scan)
Output: wifi_training_wide_per_scan.csv (Wide format with one row per scan)
"""

import pandas as pd
import numpy as np

INPUT_FILE = "wifi_training_data.csv"
OUTPUT_FILE = "wifi_training_wide_per_scan.csv"

def load_and_validate():
    """Load data and validate required columns"""
    print(f"Loading {INPUT_FILE}...")
    df = pd.read_csv(INPUT_FILE)
    
    # Required columns
    required = {"Location_Label", "Burst_ID", "Scan_Index", "BSSID", "SSID", "RSSI"}
    missing = required - set(df.columns)
    
    if missing:
        raise ValueError(f"Missing required columns: {missing}")
    
    print(f"✓ Loaded {len(df)} rows")
    print(f"  Columns: {list(df.columns)}")
    print(f"  Locations: {df['Location_Label'].nunique()}")
    print(f"  Unique BSSIDs: {df['BSSID'].nunique()}")
    print(f"  Bursts: {df['Burst_ID'].nunique()}")
    
    return df

def normalize_bssid(df):
    """Normalize BSSID format for consistency"""
    print("\nNormalizing BSSIDs...")
    
    # Convert to lowercase and replace - with :
    df['BSSID'] = df['BSSID'].str.lower().str.replace('-', ':')
    
    # Ensure trailing colon
    df['BSSID'] = df['BSSID'].apply(lambda x: x if x.endswith(':') else x + ':')
    
    print(f"✓ Normalized {df['BSSID'].nunique()} unique BSSIDs")
    return df

def convert_to_wide(df):
    """
    Convert from LONG to WIDE format
    Each scan (Location + Burst + Scan_Index) becomes ONE row
    Each BSSID becomes a column with RSSI value
    """
    print("\nConverting LONG → WIDE format...")
    
    # Group by scan identifier (Location + Burst + Scan_Index)
    # Pivot: rows = scans, columns = BSSIDs, values = RSSI
    df_wide = df.pivot_table(
        index=['Location_Label', 'Burst_ID', 'Scan_Index'],
        columns='BSSID',
        values='RSSI',
        aggfunc='mean'  # In case of duplicate BSSID in same scan, take average
    )
    
    # Reset index to make Location_Label etc. regular columns
    df_wide = df_wide.reset_index()
    
    print(f"✓ Wide format created")
    print(f"  Rows (scans): {len(df_wide)}")
    print(f"  Columns (BSSIDs + metadata): {len(df_wide.columns)}")
    
    # Fill NaN (missing APs) with -110 dBm (very weak signal)
    bssid_cols = [c for c in df_wide.columns if c not in ['Location_Label', 'Burst_ID', 'Scan_Index']]
    df_wide[bssid_cols] = df_wide[bssid_cols].fillna(-110.0)
    
    print(f"✓ Filled missing values with -110 dBm")
    
    return df_wide

def validate_output(df_wide):
    """Validate the wide format output"""
    print("\nValidating output...")
    
    # Check for required columns
    if 'Location_Label' not in df_wide.columns:
        raise ValueError("Missing Location_Label column!")
    
    # Check class distribution
    print("\nClass distribution:")
    class_counts = df_wide['Location_Label'].value_counts()
    print(class_counts)
    
    # Check for minimum samples
    min_samples = class_counts.min()
    if min_samples < 2:
        print(f"⚠️  WARNING: Some classes have <2 samples (min={min_samples})")
        print("   This may cause issues with stratified splitting")
    
    # Check feature columns
    feature_cols = [c for c in df_wide.columns if c not in ['Location_Label', 'Burst_ID', 'Scan_Index']]
    print(f"\n✓ Feature columns (BSSIDs): {len(feature_cols)}")
    
    # Check for NaN
    if df_wide.isnull().any().any():
        print("⚠️  WARNING: Some NaN values remain!")
        print(df_wide.isnull().sum()[df_wide.isnull().sum() > 0])
    else:
        print("✓ No NaN values")
    
    # Show sample
    print("\nSample of wide format (first 3 rows, first 5 features):")
    sample_cols = ['Location_Label', 'Burst_ID', 'Scan_Index'] + feature_cols[:5]
    print(df_wide[sample_cols].head(3))
    
    return True

def save_output(df_wide):
    """Save wide format to CSV"""
    print(f"\nSaving to {OUTPUT_FILE}...")
    df_wide.to_csv(OUTPUT_FILE, index=False)
    print(f"✓ Saved {len(df_wide)} rows")

def main():
    # Load and validate
    df = load_and_validate()
    
    # Normalize BSSIDs
    df = normalize_bssid(df)
    
    # Convert to wide format
    df_wide = convert_to_wide(df)
    
    # Validate output
    validate_output(df_wide)
    
    # Save
    save_output(df_wide)
    
    print("\n" + "="*60)
    print("✅ PREPROCESSING COMPLETE!")
    print("="*60)
    print(f"Output: {OUTPUT_FILE}")
    print(f"Ready for training with model_train.py")

if __name__ == "__main__":
    main()
