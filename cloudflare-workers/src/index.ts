import { createRemoteJWKSet, jwtVerify } from "jose";

interface Env {
  PAIR_DB: D1Database;
  FIREBASE_PROJECT_ID: string;
  PAIR_HMAC_KEY: string;
}

const cors = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization,content-type",
  "access-control-allow-methods": "POST,OPTIONS",
};
const reply = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: { ...cors, "content-type": "application/json; charset=utf-8", "cache-control": "no-store" },
  });

async function auth(req: Request, env: Env): Promise<string | null> {
  const token = req.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!token || !env.FIREBASE_PROJECT_ID) return null;
  try {
    const jwks = createRemoteJWKSet(
      new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
    );
    const { payload } = await jwtVerify(token, jwks, {
      issuer: "https://securetoken.google.com/" + env.FIREBASE_PROJECT_ID,
      audience: env.FIREBASE_PROJECT_ID,
    });
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

async function digest(code: string, secret: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const bytes = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(code));
  return [...new Uint8Array(bytes)].map((value) => value.toString(16).padStart(2, "0")).join("");
}

function code6(): string {
  const digits: string[] = [];
  // 250 is divisible by 10, so every digit has equal probability.
  while (digits.length < 6) {
    for (const byte of crypto.getRandomValues(new Uint8Array(16))) {
      if (byte < 250 && digits.length < 6) digits.push(String(byte % 10));
    }
  }
  return digits.join("");
}

async function rateLimit(db: D1Database, subject: string, bucket: number, max: number): Promise<boolean> {
  await db.prepare(
    "INSERT INTO pairing_rate_limits(uid,bucket,hits) VALUES(?,?,1) " +
    "ON CONFLICT(uid,bucket) DO UPDATE SET hits=hits+1",
  ).bind(subject, bucket).run();
  const row = await db.prepare(
    "SELECT hits FROM pairing_rate_limits WHERE uid=? AND bucket=?",
  ).bind(subject, bucket).first<{ hits: number }>();
  return (row?.hits ?? 0) > max;
}

export default {
  async fetch(req: Request, env: Env): Promise<Response> {
    if (req.method === "OPTIONS") return new Response(null, { headers: cors });
    if (req.method !== "POST") return reply({ error: "not_found" }, 404);
    if (!env.PAIR_DB || !env.PAIR_HMAC_KEY || !env.FIREBASE_PROJECT_ID) {
      return reply({ error: "service_not_configured" }, 503);
    }

    const uid = await auth(req, env);
    if (!uid) return reply({ error: "unauthorized" }, 401);
    let body: Record<string, unknown>;
    try {
      const parsed: unknown = await req.json();
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return reply({ error: "invalid_request" }, 400);
      }
      body = parsed as Record<string, unknown>;
    } catch {
      return reply({ error: "invalid_request" }, 400);
    }

    const now = Math.floor(Date.now() / 1000);
    const bucket = Math.floor(now / 60);
    const path = new URL(req.url).pathname;
    if (bucket % 5 === 0) {
      await env.PAIR_DB.prepare("DELETE FROM pairing_challenges WHERE expires_at < ?").bind(now - 86400).run();
      await env.PAIR_DB.prepare("DELETE FROM pairing_rate_limits WHERE bucket < ?").bind(bucket - 1440).run();
    }

    if (path === "/pair/create" || path === "/pair/redeem") {
      const ip = req.headers.get("CF-Connecting-IP");
      const ipKey = ip ? "ip:" + ip : "uid:" + uid;
      const max = path === "/pair/create" ? 3 : 8;
      if (await rateLimit(env.PAIR_DB, "user:" + uid, bucket, max) ||
          await rateLimit(env.PAIR_DB, ipKey, bucket, max * 3)) {
        return reply({ error: "rate_limited" }, 429);
      }
    }

    if (path === "/pair/create") {
      const deviceId = typeof body.deviceId === "string" ? body.deviceId : "";
      if (!/^[A-Za-z0-9_-]{8,128}$/.test(deviceId)) return reply({ error: "invalid_request" }, 400);
      const code = code6();
      const hash = await digest(code, env.PAIR_HMAC_KEY);
      await env.PAIR_DB.prepare(
        "DELETE FROM pairing_challenges WHERE child_uid=? AND device_id=?",
      ).bind(uid, deviceId).run();
      await env.PAIR_DB.prepare(
        "INSERT INTO pairing_challenges(code_hash,child_uid,device_id,created_at,expires_at) VALUES(?,?,?,?,?)",
      ).bind(hash, uid, deviceId, now, now + 300).run();
      return reply({ code, expiresIn: 300 });
    }

    if (path === "/pair/redeem") {
      const code = typeof body.code === "string" ? body.code : "";
      if (!/^\d{6}$/.test(code)) return reply({ error: "pairing_failed" }, 400);
      const hash = await digest(code, env.PAIR_HMAC_KEY);
      const claim = await env.PAIR_DB.prepare(
        "UPDATE pairing_challenges SET consumed_at=? " +
        "WHERE code_hash=? AND expires_at>? AND consumed_at IS NULL AND attempts<5",
      ).bind(now, hash, now).run();
      if ((claim.meta.changes ?? 0) === 0) {
        await env.PAIR_DB.prepare(
          "UPDATE pairing_challenges SET attempts=attempts+1 " +
          "WHERE code_hash=? AND consumed_at IS NULL AND expires_at>?",
        ).bind(hash, now).run();
        return reply({ error: "pairing_failed" }, 400);
      }

      const challenge = await env.PAIR_DB.prepare(
        "SELECT child_uid,device_id FROM pairing_challenges WHERE code_hash=?",
      ).bind(hash).first<{ child_uid: string; device_id: string }>();
      if (!challenge || challenge.child_uid === uid) return reply({ error: "pairing_failed" }, 400);

      const active = await env.PAIR_DB.prepare(
        "SELECT device_id FROM device_links WHERE device_id=? AND revoked_at IS NULL",
      ).bind(challenge.device_id).first();
      if (active) return reply({ error: "device_already_linked" }, 409);

      // A previously revoked device may be linked again only after its child creates a fresh code.
      const link = await env.PAIR_DB.prepare(
        "INSERT INTO device_links(device_id,child_uid,parent_uid,linked_at) VALUES(?,?,?,?) " +
        "ON CONFLICT(device_id) DO UPDATE SET parent_uid=excluded.parent_uid, " +
        "linked_at=excluded.linked_at,revoked_at=NULL " +
        "WHERE device_links.revoked_at IS NOT NULL AND device_links.child_uid=excluded.child_uid",
      ).bind(challenge.device_id, challenge.child_uid, uid, now).run();
      if ((link.meta.changes ?? 0) === 0) return reply({ error: "device_already_linked" }, 409);
      return reply({ deviceId: challenge.device_id, childUid: challenge.child_uid });
    }

    if (path === "/location/update") {
      const deviceId = typeof body.deviceId === "string" ? body.deviceId : "";
      const latitude = body.latitude;
      const longitude = body.longitude;
      const accuracy = body.accuracy;
      if (!/^[A-Za-z0-9_-]{8,128}$/.test(deviceId) ||
          typeof latitude !== "number" || !Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
          typeof longitude !== "number" || !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
          (accuracy !== undefined && (typeof accuracy !== "number" || !Number.isFinite(accuracy) || accuracy < 0 || accuracy > 100000))) {
        return reply({ error: "invalid_request" }, 400);
      }
      const owned = await env.PAIR_DB.prepare(
        "SELECT device_id FROM device_links WHERE device_id=? AND child_uid=? AND revoked_at IS NULL",
      ).bind(deviceId, uid).first();
      if (!owned) return reply({ error: "device_not_linked" }, 403);
      await env.PAIR_DB.prepare(
        "INSERT INTO latest_locations(device_id,latitude,longitude,accuracy,updated_at) VALUES(?,?,?,?,?) " +
        "ON CONFLICT(device_id) DO UPDATE SET latitude=excluded.latitude,longitude=excluded.longitude," +
        "accuracy=excluded.accuracy,updated_at=excluded.updated_at",
      ).bind(deviceId, latitude, longitude, typeof accuracy === "number" ? accuracy : null, now).run();
      return reply({ saved: true, updatedAt: now });
    }

    if (path === "/location/latest") {
      const deviceId = typeof body.deviceId === "string" ? body.deviceId : "";
      if (!/^[A-Za-z0-9_-]{8,128}$/.test(deviceId)) return reply({ error: "invalid_request" }, 400);
      const allowed = await env.PAIR_DB.prepare(
        "SELECT device_id FROM device_links WHERE device_id=? AND parent_uid=? AND revoked_at IS NULL",
      ).bind(deviceId, uid).first();
      if (!allowed) return reply({ error: "device_not_linked" }, 403);
      const location = await env.PAIR_DB.prepare(
        "SELECT latitude,longitude,accuracy,updated_at FROM latest_locations WHERE device_id=?",
      ).bind(deviceId).first<{ latitude: number; longitude: number; accuracy: number | null; updated_at: number }>();
      return location ? reply(location) : reply({ error: "location_not_available" }, 404);
    }

    if (path === "/pair/revoke") {
      const deviceId = typeof body.deviceId === "string" ? body.deviceId : "";
      const result = await env.PAIR_DB.prepare(
        "UPDATE device_links SET revoked_at=? WHERE device_id=? AND parent_uid=? AND revoked_at IS NULL",
      ).bind(now, deviceId, uid).run();
      return result.meta.changes ? reply({ revoked: true }) : reply({ error: "not_found" }, 404);
    }

    return reply({ error: "not_found" }, 404);
  },
};
