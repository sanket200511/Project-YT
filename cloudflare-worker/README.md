# Vista 24/7 Cloudflare Worker Deployment Guide

This worker runs on Cloudflare's global edge network. It provides:
- **Zero Inactivity / Never Sleeps**: Never gets paused, sleeps, or gets deleted.
- **100% Free**: 100,000 requests per day permanently on Cloudflare's free plan.
- **Live Telemetry & Fleet Tracking**: Automatically logs real device IDs, Android versions, device models, and online status when the Vista APK starts.
- **Instant Killswitch & Device Ban**: Blocks suspended devices or shuts down the fleet in real-time.

---

## 🚀 60-Second Deployment Steps

### Step 1: Create a Free Cloudflare Account
1. Go to [cloudflare.com](https://dash.cloudflare.com/) and sign up / log in (100% free).
2. In the left navigation, click **Workers & Pages**.

### Step 2: Create the Worker
1. Click the blue **Create application** button, then select **Create Worker**.
2. Give it a name (e.g. `vista-control`) and click **Deploy**.
3. On the next screen, click **Edit code** in the top right.
4. Replace all code in the editor with the code from [`cloudflare-worker/worker.js`](./worker.js).
5. Click **Deploy** in the top right!

### Step 3: (Optional, Recommended) Enable Permanent KV Storage
To ensure device records and bans persist forever across edge cache flushes:
1. In Cloudflare dashboard, go to **Workers & Pages** -> **KV**.
2. Click **Create Namespace**, name it `VISTA_KV`, and click **Add**.
3. Go back to your Worker (`vista-control`) -> **Settings** tab -> **Variables** (or **Bindings**).
4. Under **KV Namespace Bindings**, click **Add binding**:
   - Variable name: `VISTA_KV`
   - KV namespace: select `VISTA_KV`
5. Click **Deploy**.

---

## 🔗 Connecting Your Worker to Vista

Your worker URL will look like:
`https://vista-control.<your-subdomain>.workers.dev`

### In Your Dashboard:
1. Open [`dashboard/index.html`](../dashboard/index.html) in your browser.
2. Click **API Settings** (or the Worker URL button) in the top bar.
3. Paste your Cloudflare Worker URL (`https://vista-control.<your-subdomain>.workers.dev`).
4. Enter your Master Key (default is `vista_master_2026`, or the custom key you set in Cloudflare Worker environment variables).
5. You will instantly see your real fleet statistics, active devices, and live controls!
