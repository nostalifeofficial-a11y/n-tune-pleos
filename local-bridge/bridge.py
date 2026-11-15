"""Loopback-only bridge. ACC is read-only; only explicit simulator analysis goes online."""
import json
import math
import mimetypes
import os
import re
import sys
import threading
import time
from datetime import datetime, timezone
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs, unquote
from acc_process import GameIdentity
from acc_shared_memory import ACCSharedMemoryReader

PORT = 4182
REMOTE = 'https://nostalife-drive.rusticglint.chatgpt.site'
ROOT = Path(getattr(sys, '_MEIPASS', Path(__file__).resolve().parent.parent))
PUBLIC = ROOT / 'web' / 'public'
LOCK = threading.RLock()
CHANNEL_LOCK = threading.Lock()
STATE = {'sim': None, 'acc': None, 'acc_status': 'ACC 실행 대기', 'latest': None,
         'app_seen': 0, 'received_id': None, 'code': None, 'channel': None,
         'preference': {'goal': '출퇴근길을 편안하게 달리고 싶어요.', 'comfort': 65, 'steering_response': 45}}
EMPTY = {'schema_version': '1.0.0', 'status': 'empty', 'recommendation': None}
FIELDS = {'speed_kmh': (0, 600), 'throttle_pct': (0, 100), 'brake_pct': (0, 100),
          'steering': (-1.01, 1.01), 'lat_g': (-20, 20), 'long_g': (-20, 20)}

def normalized_sample(sample):
    if not isinstance(sample, dict):
        raise ValueError('주행 표본 형식이 올바르지 않습니다.')
    out = {}
    for key, (lo, hi) in FIELDS.items():
        value = sample.get(key)
        out[key] = value if type(value) in (float, int) and math.isfinite(value) and lo <= value <= hi else None
    return out

def live(source):
    with LOCK:
        row = STATE.get(source)
        if not row or time.monotonic() - row['at'] > 2:
            return {'source': source, 'fresh': False, 'values': {},
                    'message': STATE['acc_status'] if source == 'acc' else 'PC 주행 창에서 주행을 시작해 주세요.'}
        return {'source': source, 'fresh': True, 'values': row['values'],
                'message': '별도 주행 창 · 가상 차량' if source == 'sim' else 'ACC 공유 메모리 · 읽기 전용'}

def poll_acc():
    identity = GameIdentity()
    reader = ACCSharedMemoryReader(identity_check=lambda: identity.is_game('ACC'))
    try:
        while True:
            snapshot = reader.read_snapshot()
            with LOCK:
                if snapshot and snapshot.get('is_recordable'):
                    value = {'speed_kmh': snapshot.get('speed_kmh'),
                             'throttle_pct': (snapshot['gas'] * 100 if snapshot.get('gas') is not None else None),
                             'brake_pct': (snapshot['brake'] * 100 if snapshot.get('brake') is not None else None),
                             'steering': snapshot.get('steering_input'),
                             'lat_g': snapshot.get('acc_g_x'), 'long_g': snapshot.get('acc_g_z')}
                    STATE['acc'] = {'at': time.monotonic(), 'values': normalized_sample(value)}
                    STATE['acc_status'] = 'ACC 주행 수신 중'
                elif snapshot or reader.last_error != 'duplicate_packet':
                    STATE['acc'] = None
                    STATE['acc_status'] = 'ACC 주행 대기 · ' + (reader.last_error or '주행 중인 세션이 아닙니다.')
            time.sleep(.1)
    finally:
        reader.close()

class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *_):
        pass  # No channel codes, telemetry, tokens or personal paths in request logs.

    def reply(self, value, status=200, content_type='application/json; charset=utf-8'):
        data = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False, allow_nan=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.end_headers()
        self.wfile.write(data)

    def allowed(self):
        hosts = {f'127.0.0.1:{PORT}', f'localhost:{PORT}', f'10.0.2.2:{PORT}'}
        return self.headers.get('Host') in hosts

    def read_json(self, limit=32*1024*1024):
        if not self.headers.get('Content-Type', '').startswith('application/json'):
            raise ValueError('JSON 요청이 필요합니다.')
        size = int(self.headers.get('Content-Length', '-1'))
        if not 0 <= size <= limit:
            raise ValueError('요청 크기가 올바르지 않습니다.')
        return json.loads(self.rfile.read(size), parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Invalid number')))

    def proxy(self, path, data=None):
        headers = {'Content-Type': 'application/json', 'Accept': 'application/json', 'User-Agent': 'Nostalife-Pleos/5.1'}
        if self.headers.get('Authorization'):
            headers['Authorization'] = self.headers['Authorization']
        req = urllib.request.Request(REMOTE + path, data=data, headers=headers,
                                     method='POST' if data is not None else 'GET')
        try:
            with urllib.request.urlopen(req, timeout=90) as response:
                body, status = response.read(2*1024*1024), response.status
        except urllib.error.HTTPError as error:
            body, status = error.read(65536), error.code
        value = json.loads(body)
        if status == 200 and path.endswith('/analyze'):
            with LOCK:
                code = path.split('/')[3]
                if STATE['code'] is None or code == STATE['code']:
                    STATE['code'] = code
                    STATE['latest'] = value
                    STATE['received_id'] = None
        if status == 201 and path == '/api/channels':
            with LOCK:
                STATE['code'] = value['code']
                STATE['channel'] = value
                STATE['latest'] = None
                STATE['received_id'] = None
        self.reply(value, status)

    def do_GET(self):
        if not self.allowed():
            return self.reply({'message': '로컬 연결만 허용합니다.'}, 403)
        url = urlsplit(self.path)
        path = url.path
        try:
            if path == '/local/health':
                return self.reply({'ok': True, 'version': '5.1.0', 'loopback_only': True})
            if path == '/local/preference':
                with LOCK: value = dict(STATE['preference'])
                return self.reply(value)
            if path == '/local/live':
                source = parse_qs(url.query).get('source', ['sim'])[0]
                if source not in ('sim', 'acc'):
                    return self.reply({'message': '지원하지 않는 입력입니다.'}, 400)
                with LOCK:
                    if self.headers.get('X-Nostalife-App') == 'pleos': STATE['app_seen'] = time.monotonic()
                return self.reply(live(source))
            if path == '/recommendations/latest':
                with LOCK:
                    if self.headers.get('X-Nostalife-App') == 'pleos': STATE['app_seen'] = time.monotonic()
                    result = STATE['latest']
                return self.reply(result or EMPTY, 200 if result else 404)
            status_match = re.fullmatch(r'/api/channels/([A-HJ-NP-Z2-9]{10})/status', path)
            if status_match:
                with LOCK:
                    result = STATE['latest'] if status_match[1] == STATE['code'] else None
                    rid = result.get('recommendation_id') if result else None
                    return self.reply({'result_id': rid, 'app_online': time.monotonic()-STATE['app_seen'] < 15,
                                       'received_id': STATE['received_id'], 'received': bool(rid and rid == STATE['received_id'])})
            target = (PUBLIC / unquote(path).lstrip('/')).resolve() if path != '/' else PUBLIC / 'index.html'
            if not target.is_relative_to(PUBLIC.resolve()) or not target.is_file():
                return self.reply({'message': '파일이 없습니다.'}, 404)
            mime = {'.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html'}.get(target.suffix, mimetypes.guess_type(str(target))[0] or 'application/octet-stream')
            return self.reply(target.read_bytes(), content_type=mime)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception:
            self.reply({'message': '로컬 연결을 처리하지 못했습니다.'}, 502)

    def do_POST(self):
        origin = self.headers.get('Origin')
        if not self.allowed() or (origin and origin != 'http://' + self.headers['Host']) or self.headers.get('Sec-Fetch-Site') == 'cross-site':
            return self.reply({'message': '허용되지 않은 요청입니다.'}, 403)
        path = urlsplit(self.path).path
        try:
            data = self.read_json(8192 if path.startswith('/local/') else 32*1024*1024)
            if path == '/local/sim':
                values = normalized_sample(data.get('values'))
                session = data.get('session_id', 'legacy')
                if not isinstance(session, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', session): raise ValueError()
                with LOCK:
                    current = STATE['sim']
                    same = current is None or current.get('session_id', 'legacy') == session
                    if data.get('running') is True:
                        if not same and time.monotonic() - current['at'] < 2:
                            return self.reply({'ok': False, 'message': '다른 주행 창이 연결 중입니다.'}, 409)
                        STATE['sim'] = {'values': values, 'at': time.monotonic(), 'session_id': session}
                    elif same: STATE['sim'] = None
                return self.reply({'ok': True})
            if path == '/local/preference':
                if not isinstance(data, dict) or not isinstance(data.get('goal'), str) or len(data['goal']) > 500: raise ValueError()
                for key in ('comfort', 'steering_response'):
                    value = data.get(key)
                    if type(value) is not int or not 0 <= value <= 100 or value % 5: raise ValueError()
                with LOCK: STATE['preference'] = {key: data[key] for key in ('goal', 'comfort', 'steering_response')}
                return self.reply({'ok': True})
            if path == '/local/received':
                with LOCK:
                    latest = STATE['latest']
                    if latest and data.get('id') == latest.get('recommendation_id'):
                        STATE['received_id'] = data['id']
                        return self.reply({'ok': True})
                return self.reply({'ok': False}, 409)
            if path == '/api/channels':
                with CHANNEL_LOCK:
                    with LOCK: channel = STATE['channel']
                    if channel and channel.get('expires_at', '') > datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z') and not data.get('force'):
                        # All local tabs share the same channel. A second tab must not clear the first result.
                        return self.reply(channel, 201)
                    return self.proxy(path, b'{}')
            if re.fullmatch(r'/api/channels/[A-HJ-NP-Z2-9]{10}/analyze', path):
                with LOCK: data['driving_preference'] = dict(STATE['preference'])
                return self.proxy(path, json.dumps(data, allow_nan=False).encode())
            return self.reply({'message': '지원하지 않는 요청입니다.'}, 404)
        except (ValueError, TypeError):
            self.reply({'message': '요청 형식이 올바르지 않습니다.'}, 400)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception:
            self.reply({'message': '기존 AI 서버에 연결하지 못했습니다. 기록은 주행 창에 남아 있습니다.'}, 503)

def main():
    if len(sys.argv) == 3 and sys.argv[1] == '--diagnose':
        result = {'test': 'public_health_no_user_data'}
        try:
            request = urllib.request.Request(REMOTE + '/api/health', headers={'User-Agent': 'Nostalife-Pleos/5.0'})
            with urllib.request.urlopen(request, timeout=15) as response:
                result['status'] = response.status
                result['response'] = json.load(response)
        except Exception as error:
            import traceback
            result['error'] = type(error).__name__
            result['reason'] = str(error)[:350]
            result['traceback'] = traceback.format_exc()
        Path(sys.argv[2]).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        return
    server = ThreadingHTTPServer(('127.0.0.1', PORT), Handler)
    server.daemon_threads = True
    threading.Thread(target=poll_acc, daemon=True).start()
    server.serve_forever()

if __name__ == '__main__':
    main()
