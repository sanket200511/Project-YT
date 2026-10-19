/**
 * Vista Project - Sanket Kurve
 * Cloudflare Worker: 24/7 Fleet Telemetry & Remote Control API
 * 
 * Free tier: 100,000 requests/day, never pauses, never sleeps, never deleted.
 */

const DEFAULT_CONFIG = {
  global_killswitch_active: false,
  killswitch_message: "Vista YouTube is currently undergoing scheduled maintenance. Please try again shortly.",
  minimum_supported_version: "1.47.0",
  update_url: "https://github.com/sanket200511/Project-YT/releases/latest",
  suspended_devices: [],
  device_messages: {},
  ban_message: "Your device access to Vista has been deactivated by the administrator.",
  announcement: {
    active: false,
    title: "Welcome to Vista",
    message: "Enjoy an ad-free, premium experience built with Vista.",
    link: "https://linktr.ee/sanketkurve"
  }
};

// In-memory fallback if KV is not bound
const memoryStore = {
  config: JSON.parse(JSON.stringify(DEFAULT_CONFIG)),
  devices: {}
};

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Master-Key, Authorization",
  "Content-Type": "application/json"
};

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: CORS_HEADERS
  });
}

function verifyAuth(request, env) {
  const masterKey = request.headers.get("X-Master-Key");
  const expectedKey = env.MASTER_KEY || "vista_master_2026";
  return masterKey && masterKey === expectedKey;
}

export default {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    const url = new URL(request.url);
    const path = url.pathname;

    try {
      // 1. PUBLIC: APK Remote Config Check
      if (path === "/config" && request.method === "GET") {
        let config = memoryStore.config;
        if (env.VISTA_KV) {
          const raw = await env.VISTA_KV.get("config", { type: "json" });
          if (raw) config = raw;
          else await env.VISTA_KV.put("config", JSON.stringify(config));
        }
        return jsonResponse(config);
      }

      // 2. PUBLIC: APK Anonymous Telemetry Ping on Startup
      if (path === "/telemetry" && request.method === "POST") {
        const body = await request.json().catch(() => ({}));
        const deviceId = (body.deviceId || "").trim().toLowerCase();
        
        if (!deviceId || deviceId.length < 4) {
          return jsonResponse({ error: "Invalid deviceId" }, 400);
        }

        const now = Date.now();
        const country = request.cf?.country || "Global";
        const deviceRecord = {
          id: deviceId,
          version: body.version || "Unknown",
          android: body.android || "Unknown",
          model: body.model || "Android Device",
          country: country,
          lastSeen: now
        };

        if (env.VISTA_KV) {
          // Store device record
          await env.VISTA_KV.put(`dev:${deviceId}`, JSON.stringify(deviceRecord));
          
          // Update device index list
          let list = await env.VISTA_KV.get("device_index", { type: "json" }) || [];
          if (!list.includes(deviceId)) {
            list.push(deviceId);
            // Cap index at most recent 2000 devices to stay within KV limits
            if (list.length > 2000) list = list.slice(-2000);
            await env.VISTA_KV.put("device_index", JSON.stringify(list));
          }
        } else {
          memoryStore.devices[deviceId] = deviceRecord;
        }

        return jsonResponse({ status: "acknowledged", timestamp: now });
      }

      // 3. ADMIN: Get Live Fleet Telemetry & Active Status
      if (path === "/admin/fleet" && request.method === "GET") {
        if (!verifyAuth(request, env)) {
          return jsonResponse({ error: "Unauthorized: Invalid Master Key" }, 401);
        }

        let config = memoryStore.config;
        let devices = [];

        if (env.VISTA_KV) {
          const rawConfig = await env.VISTA_KV.get("config", { type: "json" });
          if (rawConfig) config = rawConfig;

          const index = await env.VISTA_KV.get("device_index", { type: "json" }) || [];
          // Fetch devices in batches
          const fetchPromises = index.map(id => env.VISTA_KV.get(`dev:${id}`, { type: "json" }));
          const results = await Promise.all(fetchPromises);
          devices = results.filter(d => d !== null);
        } else {
          devices = Object.values(memoryStore.devices);
        }

        const now = Date.now();
        const h24 = 24 * 60 * 60 * 1000;
        const h1 = 60 * 60 * 1000;

        let active24h = 0;
        let active1h = 0;
        const suspendedSet = new Set((config.suspended_devices || []).map(id => id.toLowerCase()));

        devices.forEach(d => {
          const age = now - (d.lastSeen || 0);
          if (age <= h24) active24h++;
          if (age <= h1) active1h++;
          d.suspended = suspendedSet.has(d.id.toLowerCase());
        });

        // Sort: Most recently seen first
        devices.sort((a, b) => (b.lastSeen || 0) - (a.lastSeen || 0));

        const stats = {
          totalDevices: devices.length,
          active24h: active24h,
          active1h: active1h,
          suspendedCount: suspendedSet.size
        };

        return jsonResponse({ config, stats, devices });
      }

      // 4. ADMIN: Update Config (Killswitch, Min Version, Announcement)
      if (path === "/admin/config" && request.method === "POST") {
        if (!verifyAuth(request, env)) {
          return jsonResponse({ error: "Unauthorized: Invalid Master Key" }, 401);
        }

        const newConfig = await request.json();
        if (env.VISTA_KV) {
          await env.VISTA_KV.put("config", JSON.stringify(newConfig));
        } else {
          memoryStore.config = newConfig;
        }

        return jsonResponse({ success: true, config: newConfig });
      }

      // 5. ADMIN: Suspend / Reactivate Device
      if (path === "/admin/device/suspend" && request.method === "POST") {
        if (!verifyAuth(request, env)) {
          return jsonResponse({ error: "Unauthorized: Invalid Master Key" }, 401);
        }

        const { deviceId, suspended } = await request.json();
        const cleanId = (deviceId || "").trim().toLowerCase();

        let config = memoryStore.config;
        if (env.VISTA_KV) {
          const raw = await env.VISTA_KV.get("config", { type: "json" });
          if (raw) config = raw;
        }

        let list = (config.suspended_devices || []).map(id => id.toLowerCase());
        if (suspended && !list.includes(cleanId)) {
          list.push(cleanId);
        } else if (!suspended) {
          list = list.filter(id => id !== cleanId);
        }
        config.suspended_devices = list;

        if (env.VISTA_KV) {
          await env.VISTA_KV.put("config", JSON.stringify(config));
        } else {
          memoryStore.config = config;
        }

        return jsonResponse({ success: true, suspended, deviceId: cleanId, count: list.length });
      }

      // 6. ADMIN: Send / Clear Targeted Message to a Particular User
      if (path === "/admin/device/message" && request.method === "POST") {
        if (!verifyAuth(request, env)) {
          return jsonResponse({ error: "Unauthorized: Invalid Master Key" }, 401);
        }

        const { deviceId, title, message, link } = await request.json();
        const cleanId = (deviceId || "").trim().toLowerCase();

        let config = memoryStore.config;
        if (env.VISTA_KV) {
          const raw = await env.VISTA_KV.get("config", { type: "json" });
          if (raw) config = raw;
        }

        if (!config.device_messages) config.device_messages = {};

        if (!message || message.trim() === "") {
          delete config.device_messages[cleanId];
        } else {
          config.device_messages[cleanId] = {
            title: (title || "Vista Notice").trim(),
            message: message.trim(),
            link: (link || "").trim()
          };
        }

        if (env.VISTA_KV) {
          await env.VISTA_KV.put("config", JSON.stringify(config));
        } else {
          memoryStore.config = config;
        }

        return jsonResponse({
          success: true,
          deviceId: cleanId,
          activeMessage: config.device_messages[cleanId] || null
        });
      }

      return jsonResponse({ error: "Not Found", message: "Vista Cloudflare Worker Running 24/7" }, 404);

    } catch (err) {
      return jsonResponse({ error: err.message }, 500);
    }
  }
};
