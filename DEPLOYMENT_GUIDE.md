# Deployment Guide - Render.com

## Step 1: Prepare Your Repository

### 1.1 Initialize Git (if not already done)
```bash
cd d:\ips
git init
```

### 1.2 Add Files to Git
```bash
# Add required files
git add app.py
git add requirements.txt
git add render.yaml
git add .gitignore

# Add model files (IMPORTANT!)
git add rf_wifi_model.pkl
git add label_encoder.pkl
git add feature_list_used.csv

# Add frontend (optional, can host separately)
git add frontend_example.html

# Commit
git commit -m "Initial commit for Render deployment"
```

### 1.3 Create GitHub Repository
1. Go to https://github.com/new
2. Name it: `wifi-indoor-positioning`
3. Don't initialize with README (you already have files)
4. Click "Create repository"

### 1.4 Push to GitHub
```bash
git remote add origin https://github.com/YOUR_USERNAME/wifi-indoor-positioning.git
git branch -M main
git push -u origin main
```

## Step 2: Deploy to Render

### 2.1 Sign Up / Login
1. Go to https://render.com
2. Sign up or login (can use GitHub account)

### 2.2 Create New Web Service
1. Click "New +" button
2. Select "Web Service"
3. Connect your GitHub repository
4. Select `wifi-indoor-positioning` repository

### 2.3 Configure Service
Render will auto-detect `render.yaml`, but verify:
- **Name**: wifi-positioning-api
- **Environment**: Python 3
- **Build Command**: `pip install -r requirements.txt`
- **Start Command**: `uvicorn app:app --host 0.0.0.0 --port $PORT`
- **Plan**: Free

### 2.4 Deploy
1. Click "Create Web Service"
2. Wait 5-10 minutes for initial deployment
3. You'll get a URL like: `https://wifi-positioning-api.onrender.com`

## Step 3: Update Your Devices

### 3.1 Update ESP8266 Code
```cpp
const char* SERVER_URL = "https://wifi-positioning-api.onrender.com/predict";
```

### 3.2 Update Frontend HTML
```javascript
const API_URL = 'https://wifi-positioning-api.onrender.com';
```

## Step 4: Test Deployment

### 4.1 Test Health Endpoint
```bash
curl https://wifi-positioning-api.onrender.com/health
```

Should return:
```json
{"status":"healthy"}
```

### 4.2 Test Prediction Endpoint
```bash
curl -X POST https://wifi-positioning-api.onrender.com/predict \
  -H "Content-Type: application/json" \
  -d '{
    "scans": [
      {"bssid": "70:e4:22:c0:1a:08", "rssi": -45}
    ]
  }'
```

### 4.3 Upload ESP8266 Code
- Update the `SERVER_URL` in your Arduino sketch
- Upload to ESP8266
- Monitor Serial output - should show successful predictions

### 4.4 Open Frontend
- Host `frontend_example.html` on GitHub Pages, Netlify, or Vercel
- Or open locally and update `API_URL`
- Should see live updates when ESP8266 sends data

## Step 5: Deploy Frontend (Optional)

### Option A: GitHub Pages
```bash
# Create a separate branch for frontend
git checkout -b gh-pages
git add frontend_example.html
git commit -m "Add frontend"
git push origin gh-pages
```
Access at: `https://YOUR_USERNAME.github.io/wifi-indoor-positioning/frontend_example.html`

### Option B: Netlify
1. Drag and drop `frontend_example.html` to https://app.netlify.com/drop
2. Update `API_URL` in the file before uploading

### Option C: Vercel
```bash
npm install -g vercel
cd d:\ips
vercel
```

## Troubleshooting

### Issue: Model files too large for Git
If your `.pkl` files are >100MB:
```bash
# Install Git LFS
git lfs install
git lfs track "*.pkl"
git add .gitattributes
git commit -m "Add Git LFS"
git push
```

### Issue: Render build fails
- Check logs in Render dashboard
- Verify all files in `requirements.txt` are correct versions
- Ensure Python version is 3.11

### Issue: API works but WebSocket doesn't
- Render free tier supports WebSockets
- Check browser console for errors
- Verify `wss://` protocol is used (not `ws://`)

### Issue: ESP8266 can't connect
- Verify HTTPS URL (Render provides SSL by default)
- ESP8266 may need additional SSL libraries for HTTPS
- Consider using HTTP-only endpoint for ESP (see below)

## ESP8266 HTTPS Note

If ESP8266 has SSL issues, you have two options:

### Option 1: Use HTTP Endpoint (Less secure)
Add to `app.py`:
```python
@app.post("/predict-insecure")
async def predict_insecure(request: ScanRequest):
    return await predict_location(request)
```
Then use: `http://wifi-positioning-api.onrender.com/predict-insecure`

### Option 2: Add SSL Certificate (Recommended)
Update ESP8266 code to include certificate fingerprint.

## Monitoring

- **Logs**: View in Render dashboard under "Logs" tab
- **Metrics**: Check "Metrics" tab for CPU/Memory usage
- **Alerts**: Set up email alerts for downtime

## Free Tier Limitations

- Service sleeps after 15 minutes of inactivity
- First request after sleep takes ~30 seconds
- 750 hours/month runtime
- For always-on service, upgrade to paid tier ($7/month)

## Updating Your Deployment

```bash
# Make changes to code
git add .
git commit -m "Update model or fix bugs"
git push

# Render auto-deploys on push to main branch
```

## Support

- Render Docs: https://render.com/docs
- Issues: Check Render dashboard logs
- Community: https://community.render.com
