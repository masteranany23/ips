import pywifi
import time
import csv
import os
import datetime

# --- CONFIGURATION ---
# How many consecutive scans to take per location (Burst)
SCANS_PER_BURST = 5
# Seconds to wait for Windows to refresh the cache (Critical for accuracy)
SCAN_INTERVAL = 4
FILENAME = 'wifi_training_data.csv'
# Set this to your laptop model (e.g., "Dell_XPS", "Lenovo_Thinkpad")
DEVICE_ID = "Laptop_Surveyor"


def init_wifi():
    wifi = pywifi.PyWiFi()
    interfaces = wifi.interfaces()

    if not interfaces:
        print("❌ Error: No WiFi interface found!")
        exit()

    # Use the first interface found
    iface = interfaces[0]
    try:
        name = iface.name()
    except Exception:
        name = str(iface)
    print(f"✅ Using WiFi Interface: {name}")
    return iface


def perform_scan(iface):
    """Triggers a scan and returns clean results."""
    try:
        iface.scan()
    except Exception as e:
        print(f"⚠️ Scan trigger failed: {e}")
        return None

    # Wait for Windows to actually perform the scan and update the cache
    time.sleep(SCAN_INTERVAL)

    try:
        raw_results = iface.scan_results()
    except Exception as e:
        print(f"⚠️ Failed to retrieve results: {e}")
        return None

    clean_data = []
    for net in raw_results:
        # 1. Handle SSID (some hidden networks have empty SSIDs)
        ssid = str(net.ssid).strip()
        if not ssid:
            continue

        # 2. Handle BSSID (MAC Address) normalization
        if net.bssid is None:
            continue
        # Force lowercase and replace dashes with colons for consistency
        bssid = str(net.bssid).lower().replace("-", ":")

        # 3. Filter extremely weak signals (noise)
        # NOTE: you previously said weak signals are useful — consider lowering this
        # threshold to -110 or removing the filter if you want to keep weak APs.
        if net.signal < -110:
            continue

        clean_data.append({
            "BSSID": bssid,
            "SSID": ssid,
            "RSSI": int(net.signal)
        })
    return clean_data


def save_to_csv(location, burst_data, scan_index, burst_id):
    file_exists = os.path.isfile(FILENAME)

    # Order of columns for the CSV
    headers = ["Timestamp", "Device_ID", "Location_Label", "Burst_ID", "Scan_Index", "BSSID", "SSID", "RSSI"]

    with open(FILENAME, mode='a', newline='', encoding='utf-8') as file:
        writer = csv.DictWriter(file, fieldnames=headers)

        if not file_exists:
            writer.writeheader()

        timestamp = int(time.time())  # Unix timestamp

        row_count = 0
        for item in burst_data:
            writer.writerow({
                "Timestamp": timestamp,
                "Device_ID": DEVICE_ID,
                "Location_Label": location,
                "Burst_ID": burst_id,
                "Scan_Index": scan_index,
                "BSSID": item["BSSID"],
                "SSID": item["SSID"],
                "RSSI": item["RSSI"]
            })
            row_count += 1

    return row_count


def main():
    print("==========================================")
    print("   WiFi Fingerprint Collector (ML Ready)  ")
    print(f"   Saving to: {FILENAME}")
    print("==========================================\n")

    iface = init_wifi()

    while True:
        try:
            print("-" * 40)
            location = input("📍 Enter Location Name (e.g., 'Kitchen') or 'q' to quit: ").strip()

            if location.lower() == 'q':
                break
            if not location:
                continue

            # Generate a unique ID for this specific standing position
            burst_id = f"burst_{int(time.time())}"
            print(f"\n🚀 Starting collection for '{location}'...")
            print(f"   Stand still. Taking {SCANS_PER_BURST} samples.")

            for i in range(SCANS_PER_BURST):
                print(f"   📡 Scan {i+1}/{SCANS_PER_BURST}...", end="", flush=True)

                scan_data = perform_scan(iface)

                if scan_data:
                    saved_count = save_to_csv(location, scan_data, i+1, burst_id)
                    print(f" Found {len(scan_data)} APs -> Saved {saved_count} rows.")
                else:
                    print(" ⚠️ No networks found (check adapter).")

            print(f"✅ Collection for '{location}' complete.\n")

        except KeyboardInterrupt:
            break

    print("\nExiting. Good luck with your Indoor Positioning System!")


if __name__ == "__main__":
    main()
