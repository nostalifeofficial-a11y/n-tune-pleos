import http.client
import json
import threading
import time
import unittest
from unittest.mock import patch, MagicMock
import bridge

class BridgeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = bridge.ThreadingHTTPServer(('127.0.0.1', 0), bridge.Handler)
        cls.port = cls.server.server_port
        cls.worker = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.worker.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown(); cls.server.server_close(); cls.worker.join()

    def setUp(self):
        with bridge.LOCK:
            bridge.STATE.update(sim=None, acc=None, latest=None, received_id=None, code=None, channel=None, app_seen=0)

    def request(self, method, path, value=None, extra=None):
        connection = http.client.HTTPConnection('127.0.0.1', self.port, timeout=3)
        headers = {'Host': '127.0.0.1:4182', 'Content-Type': 'application/json', **(extra or {})}
        connection.request(method, path, json.dumps(value) if value is not None else None, headers)
        response = connection.getresponse()
        status, value = response.status, json.loads(response.read())
        connection.close()
        return status, value

    def test_missing_does_not_become_zero(self):
        self.assertIsNone(bridge.normalized_sample({})['speed_kmh'])
        self.assertIsNone(bridge.normalized_sample({'speed_kmh': True})['speed_kmh'])
        self.assertIsNone(bridge.normalized_sample({'lat_g': float('nan')})['lat_g'])
        self.assertEqual(bridge.normalized_sample({'brake_pct': 0})['brake_pct'], 0)

    def test_sim_is_fresh_then_expires(self):
        status, _ = self.request('POST', '/local/sim', {'running': True, 'values': {'speed_kmh': 32.1}})
        self.assertEqual(status, 200)
        self.assertEqual(bridge.live('sim')['values']['speed_kmh'], 32.1)
        with bridge.LOCK: bridge.STATE['sim']['at'] -= 3
        self.assertFalse(bridge.live('sim')['fresh'])
        self.assertEqual(bridge.live('sim')['values'], {})

    def test_pause_invalidates_live(self):
        self.request('POST', '/local/sim', {'running': True, 'values': {'speed_kmh': 55}})
        self.request('POST', '/local/sim', {'running': False, 'values': {'speed_kmh': 55}})
        self.assertFalse(bridge.live('sim')['fresh'])

    def test_origin_and_dns_rebinding_rejected(self):
        self.assertEqual(self.request('POST', '/local/sim', {}, {'Origin': 'https://example.com'})[0], 403)
        self.assertEqual(self.request('GET', '/local/health', extra={'Host':'attacker.example'})[0], 403)

    def test_no_arbitrary_proxy_or_directory_access(self):
        self.assertEqual(self.request('POST', '/api/arbitrary', {})[0], 404)
        self.assertEqual(self.request('GET', '/%2e%2e/local-bridge/bridge.py')[0], 404)

    def test_actual_receipt_required(self):
        with bridge.LOCK:
            bridge.STATE['code'] = 'ABCDEFGHJK'
            bridge.STATE['latest'] = {'recommendation_id': 'fixture'}
        self.request('GET', '/recommendations/latest')
        self.assertFalse(self.request('GET', '/api/channels/ABCDEFGHJK/status')[1]['app_online'])
        self.request('GET', '/recommendations/latest', extra={'X-Nostalife-App':'pleos'})
        status, value = self.request('GET', '/api/channels/ABCDEFGHJK/status')
        self.assertTrue(value['app_online']); self.assertFalse(value['received'])
        self.assertEqual(self.request('POST', '/local/received', {'id':'wrong'})[0], 409)
        self.assertEqual(self.request('POST', '/local/received', {'id':'fixture'})[0], 200)
        self.assertTrue(self.request('GET', '/api/channels/ABCDEFGHJK/status')[1]['received'])

    def test_acc_not_converted_to_sim_or_sent_online(self):
        with patch('bridge.urllib.request.urlopen', side_effect=AssertionError('must stay local')):
            status, value = self.request('GET', '/local/live?source=acc')
            self.assertEqual(status, 200); self.assertEqual(value['source'], 'acc')
            self.assertFalse(value['fresh'])

    def test_restart_accepts_authorized_existing_browser_channel(self):
        response = MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        response.read.return_value = b'{"recommendation_id":"fixture"}'
        with patch('bridge.urllib.request.urlopen', return_value=response) as upstream:
            status, _ = self.request('POST', '/api/channels/ABCDEFGHJK/analyze', {'log': {}})
            self.assertEqual(status, 200)
            self.assertEqual(bridge.STATE['latest']['recommendation_id'], 'fixture')
            self.assertEqual(bridge.STATE['code'], 'ABCDEFGHJK')
            self.assertEqual(upstream.call_args.args[0].get_header('User-agent'), 'Nostalife-Pleos/5.1')
            self.assertIn('driving_preference', json.loads(upstream.call_args.args[0].data))

    def test_idle_tab_cannot_erase_another_live_session(self):
        self.request('POST', '/local/sim', {'running': True, 'session_id': 'active', 'values': {'speed_kmh': 80}})
        self.request('POST', '/local/sim', {'running': False, 'session_id': 'idle', 'values': {}})
        self.assertEqual(bridge.live('sim')['values']['speed_kmh'], 80)
        self.request('POST', '/local/sim', {'running': False, 'session_id': 'active', 'values': {}})
        self.assertFalse(bridge.live('sim')['fresh'])

    def test_new_tab_reuses_channel_without_clearing_result(self):
        with bridge.LOCK:
            bridge.STATE.update(channel={'code': 'ABCDEFGHJK', 'expires_at': '2099-01-01T00:00:00.000Z'}, latest={'recommendation_id': 'existing'})
        with patch('bridge.urllib.request.urlopen', side_effect=AssertionError('no new channel')):
            self.assertEqual(self.request('POST', '/api/channels', {})[1]['code'], 'ABCDEFGHJK')
            self.assertEqual(bridge.STATE['latest']['recommendation_id'], 'existing')

    def test_preference_validation_and_transfer(self):
        good={'goal':'편안하게', 'comfort':75, 'steering_response':40}
        self.assertEqual(self.request('POST','/local/preference',good)[0],200)
        self.assertEqual(self.request('GET','/local/preference')[1],good)
        for value in (-5, 101, 74, True):
            self.assertEqual(self.request('POST','/local/preference',{**good, 'comfort':value})[0],400)

if __name__ == '__main__':
    unittest.main()
