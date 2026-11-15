"""Offline synthetic ACC layout/protocol tests. No real maps or game are opened."""
from __future__ import annotations

import ctypes
import importlib.util
import json
import math
from pathlib import Path
import struct
import unittest

MODULE = Path(__file__).resolve().parents[1] / "acc_shared_memory.py"
SPEC = importlib.util.spec_from_file_location("acc_shared_memory_under_test", MODULE)
acc = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(acc)


def put_utf16(page, offset, text, count):
    data = text.encode("utf-16-le")
    if len(data) > (count - 1) * 2:
        raise ValueError("Synthetic string exceeds field")
    page[offset:offset + count * 2] = data + b"\0" * (count * 2 - len(data))


def put(page, offset, fmt, *values):
    struct.pack_into("<" + fmt, page, offset, *values)


def fixture():
    """Hand-packed SDK offsets, independent of ctypes field assignment."""
    p, g, s = bytearray(800), bytearray(1588), bytearray(820)
    put(p, 0, "i", 101)
    put(p, 4, "fff", 0.65, 0.25, 30.0)
    put(p, 16, "ii", 4, 7000)
    put(p, 24, "ff", -0.3, 123.5)
    put(p, 44, "fff", 0.5, 0.1, -0.2)
    put(p, 56, "4f", 0.01, 0.02, 0.03, 0.04)
    put(p, 72, "4f", 999.0, 999.0, 999.0, 999.0)  # NOT USED IN ACC.
    put(p, 104, "4f", 11.0, 12.0, 13.0, 14.0)
    put(p, 184, "4f", 0.04, 0.05, 0.06, 0.07)
    put(p, 204, "4f", 0.1, 0.2, 0.3, 0.4)  # TC, heading, pitch, roll.
    put(p, 248, "if", 0, 0.2)
    put(p, 268, "2f", 888.0, 888.0)  # NOT USED IN ACC.
    put(p, 296, "3f", 0.11, 0.22, 0.33)
    put(p, 416, "i", 0)
    put(p, 592, "4f", 7.0, 8.0, 9.0, 10.0)  # mz must occupy 16 bytes.
    put(p, 640, "4f", 0.1, 0.2, 0.3, 0.4)
    put(p, 716, "4f", 0.21, 0.22, 0.23, 0.24)
    put(p, 772, "3i", 1, 0, 1)
    put(g, 0, "iii", 201, 2, 0)
    put(g, 132, "5i", 2, 1, 45000, 90000, 89000)
    put(g, 156, "f", 2000.5)
    put(g, 160, "i", 0)
    put(g, 248, "fi", 0.25, 1)
    put(g, 976, "i", 42)
    put(g, 1216, "i", 42)
    put(g, 1236, "i", 0)
    put(g, 1256, "i", 0)
    put(g, 1268, "i", 3)
    put(g, 1280, "i", 4)
    put(g, 1320, "i", 5)
    put(g, 1408, "i", 1)
    put_utf16(s, 0, "1.9", 15)
    put_utf16(s, 30, "1.7", 15)
    put(s, 60, "ii", 1, 1)
    put_utf16(s, 68, "bmw_m4_gt3", 33)
    put_utf16(s, 134, "nurburgring_24h", 33)
    # Explicit synthetic private-field sentinels must never leave the reader.
    put_utf16(s, 200, "DO_NOT_EXPORT_NAME", 33)
    put_utf16(s, 266, "DO_NOT_EXPORT_SURNAME", 33)
    put_utf16(s, 332, "DO_NOT_EXPORT_NICK", 33)
    put(s, 400, "i", 3)
    put(s, 520, "f", 99999.0)  # NOT USED IN ACC; must not become track length.
    put(s, 684, "i", 0)
    put_utf16(s, 688, "dry_compound", 33)
    put_utf16(s, 754, "wet_compound", 33)
    return {"physics": p, "graphics": g, "static": s}


class FakeMap:
    def __init__(self, raw, transform=None, size=None):
        self.raw = raw
        self.transform = transform
        self.size = size
        self.read_count = 0
        self.closed = False

    def read_bytes(self):
        self.read_count += 1
        data = bytes(self.raw[:self.size])
        return self.transform(data, self.read_count) if self.transform else data

    def close(self):
        self.closed = True


class FakeFactory:
    def __init__(self, pages):
        self.pages = pages
        self.calls = []
        self.maps = {}
        self.transform = {}

    def __call__(self, name, size):
        self.calls.append((name, size))
        page = name.rsplit("_", 1)[1]
        self.maps[page] = FakeMap(self.pages[page], self.transform.get(page), size)
        return self.maps[page]


def reader_for(pages=None, **kwargs):
    factory = FakeFactory(pages or fixture())
    reader = acc.ACCSharedMemoryReader(
        identity_check=lambda: True, map_factory=factory,
        wall_clock=lambda: 1000.0, mono_clock=lambda: 2000.0, **kwargs
    )
    return reader, factory


class LayoutTests(unittest.TestCase):
    def test_fixed_width_and_full_page_sizes(self):
        self.assertEqual(ctypes.sizeof(acc.WCHAR), 2)
        self.assertEqual({k: ctypes.sizeof(v) for k, v in acc.STRUCTURES.items()},
                         {"physics": 800, "graphics": 1588, "static": 820})

    def test_independent_sdk_offsets(self):
        expected = {
            acc.ACCPageFilePhysics: {
                "gas": 4, "rpms": 20, "steerAngle": 24, "speedKmh": 28,
                "accG": 44, "wheelSlip": 56, "wheelLoad": 72,
                "wheelAngularSpeed": 104, "suspensionTravel": 184,
                "localAngularVel": 296, "isAIControlled": 416,
                "currentMaxRpm": 588, "mz": 592, "slipRatio": 640,
                "brakePressure": 716, "ignitionOn": 772,
            },
            acc.ACCPageFileGraphics: {
                "completedLaps": 132, "iCurrentTime": 140,
                "normalizedCarPosition": 248, "activeCars": 252,
                "carCoordinates": 256, "carID": 976, "playerCarID": 1216,
                "isInPitLane": 1236, "isSetupMenuVisible": 1256,
                "TC": 1268, "ABS": 1280, "sessionIndex": 1320,
                "isValidLap": 1408,
            },
            acc.ACCPageFileStatic: {
                "carModel": 68, "track": 134, "sectorCount": 400,
                "trackSplineLength": 520, "trackConfiguration": 524,
                "dryTyresName": 688, "wetTyresName": 754,
            },
        }
        for ctype, fields in expected.items():
            for field, offset in fields.items():
                with self.subTest(structure=ctype.__name__, field=field):
                    self.assertEqual(getattr(ctype, field).offset, offset)

    def test_utf16_uses_windows_width(self):
        page = (acc.WCHAR * 15)()
        data = "테스트".encode("utf-16-le")
        ctypes.memmove(ctypes.addressof(page), data, len(data))
        self.assertEqual(acc.utf16_text(page), "테스트")


class SnapshotTests(unittest.TestCase):
    def test_video_conditions_are_observed_and_unsupported_wear_stays_unknown(self):
        pages = fixture()
        put(pages["physics"], 12, "f", 42.5)
        put(pages["physics"], 152, "4f", 78, 80, 82, 84)
        put_utf16(pages["graphics"], 176, "dry_compound", 33)
        reader, _ = reader_for(pages)
        snapshot = reader.read_snapshot()
        self.assertEqual(snapshot["conditions"]["fuel_l"], 42.5)
        self.assertEqual(snapshot["conditions"]["initial_tyre_temperature"], 81)
        self.assertEqual(snapshot["conditions"]["tyre_compound"], "dry_compound")
        self.assertIsNone(snapshot["conditions"]["initial_tyre_wear"])
        self.assertIsNone(snapshot["conditions"]["game_version"])
        self.assertIsNone(snapshot["conditions"]["traffic"])
        self.assertEqual(snapshot["condition_provenance"]["fuel_unit"], "litre")

    def test_video_conditions_are_not_backfilled_from_replay_or_invalid_numbers(self):
        pages = fixture()
        put(pages["physics"], 12, "f", float("nan"))
        put(pages["physics"], 152, "4f", 80, 80, float("nan"), 80)
        reader, _ = reader_for(pages)
        snapshot = reader.read_snapshot()
        self.assertIsNone(snapshot["conditions"]["fuel_l"])
        self.assertIsNone(snapshot["conditions"]["initial_tyre_temperature"])
        pages = fixture()
        put(pages["graphics"], 4, "i", 1)
        reader, _ = reader_for(pages)
        snapshot = reader.read_snapshot()
        self.assertTrue(all(value is None for value in snapshot["conditions"].values()))

    def test_mmf_19_legacy_api_17_pair_and_new_trailing_bytes(self):
        pages = fixture()
        for page in pages.values():
            page.extend(b"unread_future_suffix" * 10)
        reader, factory = reader_for(pages)
        snap = reader.read_snapshot()
        self.assertEqual((snap["sm_version"], snap["ac_version"]), ("1.9", "1.7"))
        self.assertEqual(snap["session_index"], 5)
        self.assertTrue(snap["is_valid_lap"])
        self.assertEqual([size for name, size in factory.calls], [800, 1588, 820])
        self.assertEqual(snap["speed_kmh"], 123.5)

    def test_known_synthetic_fixture_and_units(self):
        reader, _ = reader_for()
        snap = reader.read_snapshot()
        self.assertEqual(snap["game"], "ACC")
        self.assertEqual(snap["car"], "bmw_m4_gt3")
        self.assertEqual(snap["track"], "nurburgring_24h")
        self.assertEqual(snap["session_index"], 5)
        self.assertEqual(snap["current_lap_time_ms"], 45000)
        self.assertEqual(snap["last_lap_time_ms"], 90000)
        self.assertTrue(snap["is_valid_lap"])
        self.assertTrue(snap["is_recordable"])
        self.assertEqual(snap["speed_kmh"], 123.5)
        self.assertAlmostEqual(snap["steering_input"], -0.3, places=6)
        self.assertAlmostEqual(snap["yaw_rate"], 0.22, places=6)
        self.assertAlmostEqual(snap["suspension_travel_rr"], 0.07, places=6)
        self.assertEqual(snap["wheel_speed_fl"], 11.0)
        self.assertEqual(snap["channel_units"]["steering_input"], "normalized")
        self.assertNotIn("DO_NOT_EXPORT", json.dumps(snap))
        self.assertFalse(snap["stream_reset"])

    def test_acc_unsupported_values_never_leak_as_zero_or_fixture_numbers(self):
        reader, _ = reader_for()
        snap = reader.read_snapshot()
        for key in acc.UNSUPPORTED_CHANNELS:
            with self.subTest(key=key):
                self.assertIsNone(snap[key])
                self.assertEqual(snap["channel_validity"][key], "unsupported_in_acc")
                self.assertIn(key, snap["unknown_channels"])

    def test_wheel_out_zero_only_is_unknown_until_live_positive_then_session_resets(self):
        pages=fixture()
        reader,_=reader_for(pages)
        self.addCleanup(reader.close)
        self.assertEqual(acc.ACCPageFilePhysics.numberOfTyresOut.offset,244)
        self.assertIsNone(reader.read_snapshot()['wheels_out_count'])
        put(pages['physics'],0,'i',102)
        put(pages['physics'],244,'i',4)
        self.assertEqual(reader.read_snapshot()['wheels_out_count'],4)
        put(pages['physics'],0,'i',103)
        put(pages['physics'],244,'i',0)
        self.assertEqual(reader.read_snapshot()['wheels_out_count'],0)
        put(pages['physics'],0,'i',104)
        put(pages['graphics'],1320,'i',6)
        self.assertIsNone(reader.read_snapshot()['wheels_out_count'])

    def test_replay_or_invalid_count_does_not_enable_offtrack_capability(self):
        for count,status in [(4,1),(999,2)]:
            pages=fixture()
            put(pages['physics'],244,'i',count)
            put(pages['graphics'],4,'i',status)
            reader,_=reader_for(pages)
            self.addCleanup(reader.close)
            self.assertIsNone(reader.read_snapshot()['wheels_out_count'])
            put(pages['physics'],0,'i',102)
            put(pages['physics'],244,'i',0)
            put(pages['graphics'],4,'i',2)
            self.assertIsNone(reader.read_snapshot()['wheels_out_count'])

    def test_equal_physics_packet_is_deduplicated_even_if_graphics_moves(self):
        pages = fixture()
        reader, _ = reader_for(pages)
        self.assertIsNotNone(reader.read_snapshot())
        put(pages["graphics"], 0, "i", 202)
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "duplicate_packet")
        put(pages["physics"], 0, "i", 102)
        self.assertIsNotNone(reader.read_snapshot())

    def test_status_changes_emit_once_without_fresh_physics(self):
        for status in (0, 1, 3):
            with self.subTest(status=status):
                pages = fixture()
                reader, _ = reader_for(pages)
                self.assertTrue(reader.read_snapshot()["is_recordable"])
                put(pages["graphics"], 4, "i", status)
                snap = reader.read_snapshot()
                self.assertEqual(snap["status"], status)
                self.assertFalse(snap["is_recordable"])
                self.assertIsNone(snap["speed_kmh"])
                put(pages["physics"], 0, "i", 102)
                self.assertIsNone(reader.read_snapshot())
                put(pages["graphics"], 4, "i", 2)
                self.assertTrue(reader.read_snapshot()["is_recordable"])

    def test_setup_menu_and_ai_are_not_driving_samples(self):
        for page, offset in (("graphics", 1256), ("physics", 416)):
            with self.subTest(page=page):
                pages = fixture()
                reader, _ = reader_for(pages)
                self.assertTrue(reader.read_snapshot()["is_recordable"])
                put(pages[page], offset, "i", 1)
                snap = reader.read_snapshot()
                self.assertFalse(snap["is_recordable"])
                self.assertIsNone(snap["steering_input"])

    def test_packet_and_lap_counter_reset_is_explicit(self):
        pages = fixture()
        reader, _ = reader_for(pages)
        reader.read_snapshot()
        put(pages["physics"], 0, "i", 1)
        put(pages["graphics"], 0, "i", 2)
        put(pages["graphics"], 132, "i", 0)
        snap = reader.read_snapshot()
        self.assertTrue(snap["stream_reset"])
        self.assertEqual(set(snap["stream_reset_reasons"]), {
            "physics_packet_rewind", "graphics_packet_rewind", "completed_laps_rewind"
        })

    def test_normal_new_lap_is_not_clock_rewind(self):
        pages = fixture()
        reader, _ = reader_for(pages)
        reader.read_snapshot()
        put(pages["physics"], 0, "i", 102)
        put(pages["graphics"], 132, "i", 3)
        put(pages["graphics"], 140, "i", 50)
        snap = reader.read_snapshot()
        self.assertFalse(snap["stream_reset"])

    def test_clock_rewind_without_lap_is_explicit(self):
        pages = fixture()
        reader, _ = reader_for(pages)
        reader.read_snapshot()
        put(pages["physics"], 0, "i", 102)
        put(pages["graphics"], 140, "i", 50)
        snap = reader.read_snapshot()
        self.assertIn("lap_clock_rewind_without_lap", snap["stream_reset_reasons"])

    def test_outlap_timing_line_keeps_stream_when_completed_counter_does_not_change(self):
        pages = fixture()
        put(pages['graphics'],132,'i',0)
        put(pages['graphics'],248,'f',.9996)
        reader,_ = reader_for(pages)
        self.addCleanup(reader.close)
        first = reader.read_snapshot()
        put(pages['physics'],0,'i',102)
        put(pages['graphics'],140,'i',25)
        put(pages['graphics'],248,'f',.0001)
        second = reader.read_snapshot()
        self.assertEqual(second['session_identity'],first['session_identity'])
        self.assertEqual(second['completed_laps'],0)
        self.assertFalse(second['stream_reset'])
        self.assertFalse(second['stream_reset_reasons'])

    def test_car_track_or_session_change_is_explicit(self):
        for field in ("car", "track", "session"):
            with self.subTest(field=field):
                pages = fixture()
                reader, _ = reader_for(pages)
                reader.read_snapshot()
                put(pages["physics"], 0, "i", 102)
                if field == "car":
                    put_utf16(pages["static"], 68, "ferrari_296_gt3", 33)
                elif field == "track":
                    put_utf16(pages["static"], 134, "spa", 33)
                else:
                    put(pages["graphics"], 1320, "i", 6)
                snap = reader.read_snapshot()
                self.assertIn("session_identity_changed", snap["stream_reset_reasons"])

    def test_unknown_version_rejected_and_diagnostics_preserve_only_versions(self):
        pages = fixture()
        put_utf16(pages["static"], 0, "9.99", 15)
        reader, _ = reader_for(pages)
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "unsupported_sm_version")
        diag = reader.diagnostics()
        self.assertEqual(diag["versions"]["sm_version"], "9.99")
        self.assertNotIn("DO_NOT_EXPORT", json.dumps(diag))

    def test_ac_version_rejected_despite_shared_map_names(self):
        pages = fixture()
        put_utf16(pages["static"], 30, "1.16.4", 15)
        reader, _ = reader_for(pages)
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "unsupported_acc_version")

    def test_unverified_older_mmf_and_future_pairs_are_rejected(self):
        for sm_version, api_version in (("1.7", "1.7"), ("2.0", "1.7"), ("1.9", "1.10.4")):
            with self.subTest(pair=(sm_version, api_version)):
                pages = fixture()
                put_utf16(pages["static"], 0, sm_version, 15)
                put_utf16(pages["static"], 30, api_version, 15)
                reader, _ = reader_for(pages)
                self.assertIsNone(reader.read_snapshot())
                self.assertIn(reader.last_error, ("unsupported_sm_version", "unsupported_acc_version"))

    def test_acc_tail_marker_required(self):
        pages = fixture()
        put_utf16(pages["static"], 754, "", 33)
        reader, _ = reader_for(pages)
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "acc_static_not_ready")

    def test_invalid_required_channel_rejects_not_replaces_zero(self):
        pages = fixture()
        put(pages["physics"], 28, "f", math.nan)
        reader, _ = reader_for(pages)
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "invalid_live_physics")

    def test_invalid_optional_channel_is_unknown(self):
        pages = fixture()
        put(pages["physics"], 296 + 4, "f", math.nan)
        reader, _ = reader_for(pages)
        snap = reader.read_snapshot()
        self.assertIsNone(snap["yaw_rate"])
        self.assertEqual(snap["channel_validity"]["yaw_rate"], "unavailable_or_invalid")

    def test_acc_graphics_car_count_bound(self):
        pages = fixture()
        put(pages["graphics"], 252, "i", 61)
        reader, _ = reader_for(pages)
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "invalid_active_cars")


class MappingBoundaryTests(unittest.TestCase):
    def test_identity_is_mandatory_and_false_does_not_open_any_map(self):
        for checker in (None, lambda: False, lambda: "ACC"):
            with self.subTest(checker=checker):
                factory = FakeFactory(fixture())
                reader = acc.ACCSharedMemoryReader(identity_check=checker, map_factory=factory)
                self.assertIsNone(reader.read_snapshot())
                self.assertEqual(factory.calls, [])
                self.assertEqual(reader.last_error, "acc_identity_unconfirmed")

    def test_identity_change_after_copy_releases_maps(self):
        factory = FakeFactory(fixture())
        checks = iter((True, False))
        reader = acc.ACCSharedMemoryReader(identity_check=lambda: next(checks), map_factory=factory)
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "acc_identity_changed")
        self.assertTrue(all(page.closed for page in factory.maps.values()))

    def test_missing_map_closes_partial_handles(self):
        factory = FakeFactory(fixture())
        def opener(name, size):
            if name.endswith("_graphics"):
                raise FileNotFoundError("synthetic missing map")
            return factory(name, size)
        reader = acc.ACCSharedMemoryReader(identity_check=lambda: True, map_factory=opener)
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "missing_or_unreadable_map")
        self.assertTrue(factory.maps["physics"].closed)

    def test_short_page_fails_closed(self):
        reader, factory = reader_for()
        factory.transform["graphics"] = lambda data, count: data[:-4]
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "short_page")

    def test_concurrent_updates_fail_closed(self):
        reader, factory = reader_for(copy_attempts=2)
        def changing(data, count):
            page = bytearray(data)
            put(page, 0, "i", count)
            return bytes(page)
        factory.transform["physics"] = changing
        self.assertIsNone(reader.read_snapshot())
        self.assertEqual(reader.last_error, "concurrent_page_update")
        self.assertEqual(factory.maps["physics"].read_count, 4)

    def test_fake_kernel_requests_only_existing_read_access_and_closes(self):
        buffer = ctypes.create_string_buffer(b"fixture-only")
        calls = []
        class Function:
            def __init__(self, name, result):
                self.name, self.result = name, result
            def __call__(self, *args):
                calls.append((self.name, args))
                return self.result
        class Kernel:
            OpenFileMappingW = Function("OpenFileMappingW", 1234)
            MapViewOfFile = Function("MapViewOfFile", ctypes.addressof(buffer))
            UnmapViewOfFile = Function("UnmapViewOfFile", 1)
            CloseHandle = Function("CloseHandle", 1)
        page = acc.WinReadOnlySharedMap("Local\\fixture_only", 12, kernel32=Kernel())
        self.assertEqual(page.read_bytes(), b"fixture-only")
        page.close()
        page.close()
        self.assertEqual(calls, [
            ("OpenFileMappingW", (0x0004, 0, "Local\\fixture_only")),
            ("MapViewOfFile", (1234, 0x0004, 0, 0, 12)),
            ("UnmapViewOfFile", (ctypes.addressof(buffer),)),
            ("CloseHandle", (1234,)),
        ])


if __name__ == "__main__":
    unittest.main()

