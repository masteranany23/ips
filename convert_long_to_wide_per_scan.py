"""
convert_long_to_wide_per_scan.py

Reads:  wifi_training_data.csv  (long format: one AP reading per row)
Writes: wifi_training_wide_per_scan.csv  (one row per scan snapshot)
        ap_list_used.csv                  (canonical ordered AP list used)

Behavior:
 - Creates a unique scan key = Burst_ID || Scan_Index
 - Pivots RSSI by BSSID using median aggregation (one cell = median RSSI of that AP in that scan)
 - Reindexes columns to a canonical AP list (optionally limited to TOP_K_APS)
 - Fills missing AP values with MISSING_RSSI (-110)
 - Preserves metadata columns: Timestamp, Device_ID, Location_Label, Burst_ID, Scan_Index
"""

import os
import sys
import pandas as pd
import numpy as np

# ---------------- CONFIG ----------------
INPUT_CSV = "wifi_training_data.csv"           # your long-format dataset
OUTPUT_WIDE = "wifi_training_wide_per_scan.csv"
AP_LIST_CSV = "ap_list_used.csv"
MISSING_RSSI = -110                            # sentinel for unseen AP
TOP_K_APS = None                               # None => use all APs; or set e.g. 300
# ----------------------------------------

def load_long_csv(path):
    if not os.path.exists(path):
        raise FileNotFoundError(f"Input CSV not found: {path}")
    # try to read with automatic separator detection
    df = pd.read_csv(path, sep=None, engine="python")
    # normalize column names
    df.columns = [c.strip() for c in df.columns]
    required = {"Location_Label", "Burst_ID", "Scan_Index", "BSSID", "RSSI"}
    missing = required - set(df.columns)
    if missing:
        raise ValueError(f"Input CSV is missing required columns: {missing}")
    return df

def normalize_and_typecast(df):
    # Normalize BSSID format, ensure RSSI numeric
    df["BSSID"] = df["BSSID"].astype(str).str.lower().str.replace("-", ":", regex=False).str.strip()
    df["RSSI"] = pd.to_numeric(df["RSSI"], errors="coerce")
    # Ensure Burst_ID and Scan_Index are strings (so concatenation is stable)
    df["Burst_ID"] = df["Burst_ID"].astype(str)
    df["Scan_Index"] = df["Scan_Index"].astype(str)
    # Optional: drop rows with NaN RSSI (rare), but keep most data
    df = df.dropna(subset=["RSSI"])
    return df

def build_ap_list(df, top_k=None):
    counts = df["BSSID"].value_counts()
    if top_k is None:
        ap_list = counts.index.tolist()
    else:
        ap_list = counts.index[:top_k].tolist()
    ap_counts_df = pd.DataFrame({"BSSID": counts.index, "Count": counts.values})
    return ap_list, ap_counts_df

def pivot_per_scan(df, ap_list, missing_rssi=MISSING_RSSI):
    # Create unique scan key
    df["_scan_key"] = df["Burst_ID"].astype(str) + "||" + df["Scan_Index"].astype(str)

    # Meta info per scan (keep first seen values)
    meta = df.groupby("_scan_key").agg({
        "Location_Label": "first",
        "Burst_ID": "first",
        "Scan_Index": "first"
    }).reset_index()

    # Pivot RSSI values: index=_scan_key, columns=BSSID, values=RSSI (median across duplicates)
    pivot = df.pivot_table(index="_scan_key", columns="BSSID", values="RSSI", aggfunc="median")

    # Reindex pivot to canonical AP list (ensures consistent column order)
    pivot = pivot.reindex(columns=ap_list)

    # Fill missing with sentinel and cast to small integer
    pivot = pivot.fillna(missing_rssi).astype(np.int16)

    # Merge meta back in (align by _scan_key)
    pivot = pivot.merge(meta, left_index=True, right_on="_scan_key", how="left")

    # Reorder final columns: meta first, then AP columns
    meta_cols = ["Location_Label", "Burst_ID", "Scan_Index"]
    final_cols = meta_cols + ap_list
    pivot = pivot[final_cols]

    # Reset index for saving
    pivot = pivot.reset_index(drop=True)
    return pivot

def main():
    print("Loading long-format CSV...")
    df = load_long_csv(INPUT_CSV)
    print(f"Raw rows (AP readings): {len(df)}")

    print("Normalizing and typecasting...")
    df = normalize_and_typecast(df)

    print("Building canonical AP list...")
    ap_list, ap_counts_df = build_ap_list(df, top_k=TOP_K_APS)
    print(f"Unique APs found: {len(ap_counts_df)}. Using {len(ap_list)} APs for features.")

    print("Pivoting per-scan (Burst_ID + Scan_Index) ...")
    wide = pivot_per_scan(df, ap_list, missing_rssi=MISSING_RSSI)
    print(f"Resulting per-scan rows (samples): {len(wide)}")
    print(f"Feature columns (APs): {len(ap_list)}")

    # Save wide csv
    print(f"Saving wide CSV to: {OUTPUT_WIDE}")
    wide.to_csv(OUTPUT_WIDE, index=False)

    # Save AP list with counts for inference-time reuse
    print(f"Saving AP list to: {AP_LIST_CSV}")
    ap_counts_df.to_csv(AP_LIST_CSV, index=False)

    # Print basic stats
    print("\nSample counts per Location_Label (top 20):")
    try:
        print(wide["Location_Label"].value_counts().head(20).to_string())
    except Exception:
        pass

    # Print shapes ready for training
    X_shape = (len(wide), len(ap_list))
    y_shape = (len(wide),)
    print(f"\nFinal Training Matrix Shape (X): {X_shape}")
    print(f"Labels Shape (y): {y_shape}")

    print("\nDone. You can now train RandomForest using the generated CSV and the AP list (ap_list_used.csv).")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print("Error:", str(e))
        sys.exit(1)
