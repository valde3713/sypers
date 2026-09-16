import json
import secrets
import threading
from datetime import datetime, timezone
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
STATS_FILE = ROOT / "sypers-visitor-stats.json"
TRACKING_FILE = ROOT / "sypers-location.json"
LOCK = threading.Lock()
TRACKING_USERNAME = "sypers"
TRACKING_PASSWORD = "SypersGPS-2026"
DEVICE_KEY = "sypers-device-2026"
SESSIONS = set()
tracking_authorized = False
PAIRING_CODES = {}
PAIRING_TOKENS = set()


def load_stats():
    if STATS_FILE.exists():
        try:
            return json.loads(STATS_FILE.read_text())
        except (OSError, json.JSONDecodeError):
            pass
    return {"totalVisits": 0, "uniqueVisitors": [], "siteVisits": 0, "appDownloads": 0, "recent": []}


stats = load_stats()


def load_location():
    if TRACKING_FILE.exists():
        try:
            return json.loads(TRACKING_FILE.read_text())
        except (OSError, json.JSONDecodeError):
            pass
    return {}


location = load_location()


def save_stats():
    STATS_FILE.write_text(json.dumps(stats, ensure_ascii=False, indent=2))


class SypersHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path in {"/track.html", "/family-track.html"}:
            if path == "/family-track.html":
                self.path = "/track.html"
            super().do_GET()
            return
        if path == "/sypers.fam":
            self.path = "/syper-family-download.html"
            super().do_GET()
            return
        if path == "/api/location":
            if not self.has_session():
                self.send_error(401, "Login required")
                return
            self.send_json(location)
            return
        if path == "/api/pair/status":
            self.send_json({"paired": bool(PAIRING_TOKENS)})
            return
        if path in {"/dashboard.html", "/api/stats"}:
            self.send_error(404, "Not found")
            return

        if path not in {"/dashboard.html", "/api/stats"}:
            visitor = self.client_address[0]
            with LOCK:
                stats["totalVisits"] += 1
                if visitor not in stats["uniqueVisitors"]:
                    stats["uniqueVisitors"].append(visitor)
                if path == "/sypers.apk":
                    stats["appDownloads"] += 1
                    kind = "APK-download"
                else:
                    stats["siteVisits"] += 1
                    kind = "Webside"
                stats["recent"].append({
                    "time": datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M:%S"),
                    "kind": kind,
                    "path": path,
                })
                stats["recent"] = stats["recent"][-100:]
                save_stats()
        super().do_GET()

    def end_headers(self):
        if self.path.startswith("/api/"):
            self.send_header("Access-Control-Allow-Origin", "null")
            self.send_header("Access-Control-Allow-Credentials", "true")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "null")
        self.send_header("Access-Control-Allow-Credentials", "true")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()

    def do_POST(self):
        path = urlsplit(self.path).path
        length = int(self.headers.get("Content-Length", "0"))
        try:
            payload = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            self.send_error(400, "Invalid JSON")
            return
        if path == "/api/login":
            if payload.get("username") != TRACKING_USERNAME or payload.get("password") != TRACKING_PASSWORD:
                self.send_error(401, "Invalid login")
                return
            global tracking_authorized
            tracking_authorized = True
            session = secrets.token_urlsafe(32)
            SESSIONS.add(session)
            self.send_response(200)
            self.send_header(
                "Set-Cookie",
                f"sypers_session={session}; Path=/; Max-Age=86400; Secure; HttpOnly; SameSite=Lax",
            )
            self.end_headers()
            return
        if path == "/api/pair/start":
            if payload.get("deviceKey") != DEVICE_KEY:
                self.send_error(403, "Invalid device")
                return
            code = f"{secrets.randbelow(1000000):06d}"
            PAIRING_CODES[code] = DEVICE_KEY
            self.send_json({"code": code})
            return
        if path == "/api/pair/confirm":
            code = str(payload.get("code", ""))
            if PAIRING_CODES.get(code) != DEVICE_KEY:
                self.send_error(403, "Invalid pairing code")
                return
            token = secrets.token_urlsafe(32)
            PAIRING_TOKENS.add(token)
            PAIRING_CODES.pop(code, None)
            self.send_json({"token": token})
            return
        if path == "/api/pair/unpair":
            PAIRING_TOKENS.clear()
            PAIRING_CODES.clear()
            self.send_response(204)
            self.end_headers()
            return
        if path == "/api/location":
            if payload.get("deviceKey") != DEVICE_KEY or not PAIRING_TOKENS:
                self.send_error(403, "Invalid device")
                return
            if not isinstance(payload.get("latitude"), (int, float)) or not isinstance(payload.get("longitude"), (int, float)):
                self.send_error(400, "Invalid coordinates")
                return
            location.update({
                "latitude": payload["latitude"],
                "longitude": payload["longitude"],
                "accuracy": payload.get("accuracy"),
                "battery": payload.get("battery"),
                "charging": payload.get("charging"),
                "updatedAt": datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M:%S"),
            })
            TRACKING_FILE.write_text(json.dumps(location, indent=2))
            self.send_response(204)
            self.end_headers()
            return
        self.send_error(404, "Not found")

    def send_json(self, value):
        payload = json.dumps(value).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def has_session(self):
        cookies = {}
        for item in self.headers.get("Cookie", "").split(";"):
            if "=" in item:
                key, value = item.strip().split("=", 1)
                cookies[key] = value
        return cookies.get("sypers_session") in SESSIONS


class DashboardHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/api/stats":
            payload = json.dumps({
                "totalVisits": stats["totalVisits"],
                "uniqueVisitors": len(stats["uniqueVisitors"]),
                "siteVisits": stats["siteVisits"],
                "appDownloads": stats["appDownloads"],
                "recent": stats["recent"][-20:][::-1],
            }).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        super().do_GET()


if __name__ == "__main__":
    public_server = ThreadingHTTPServer(("127.0.0.1", 8000), SypersHandler)
    dashboard_server = ThreadingHTTPServer(("127.0.0.1", 8001), DashboardHandler)
    threading.Thread(target=public_server.serve_forever, daemon=True).start()
    print("Sypers kører på http://localhost:8000")
    print("Lokal besøgsoversigt: http://localhost:8001/dashboard.html")
    dashboard_server.serve_forever()
