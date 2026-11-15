"""ACC-only, read-only existing named-map reader (no network, game writes or injection).

Layout reference: Kunos ACC Shared Memory v1.8.12, original authored document
mirrored at https://github.com/rrennoir/PyAccSharedMemory/blob/main/ACCSharedMemoryDocumentationV1.8.12.pdf
PDF sha256 a0c279753f154e9145f6854313472a0e1270027057ce27e2db1547a63f56cabf
and Git blob 523b7c2f5e3abb6e39ba539e6636e5bc550092a0.

Field declarations adapted from TinyPedal/pyACCSharedMemory acc_data.py,
blob ad0fadc4a364513e160e29bf610db4764b715ce8, under the MIT license below.
Its mmap implementation is NOT used: it may create maps. The C++ example
in SolusDev-sys/CppAccSharedMemory omits mz[4]; that layout is NOT used.
Windows WCHAR is explicitly uint16 on every test platform; packing is 4.

A process identity callback is mandatory because AC and ACC use the same map
names. The caller must confirm ACC owns the maps and no AC/EVO is competing.
Double copies and packet checks detect concurrent updates; the SDK exposes
no cross-page lock, so these checks do not promise an atomic game-frame capture.
Unsupported fields stay None, never zero. No player/account names are returned.
"""

from __future__ import annotations

UPSTREAM_MIT_LICENSE = "MIT License\n\nCopyright (c) 2026 Xiang\n\nPermission is hereby granted, free of charge, to any person obtaining a copy\nof this software and associated documentation files (the \"Software\"), to deal\nin the Software without restriction, including without limitation the rights\nto use, copy, modify, merge, publish, distribute, sublicense, and/or sell\ncopies of the Software, and to permit persons to whom the Software is\nfurnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all\ncopies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\nIMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\nFITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\nAUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\nLIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\nOUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\nSOFTWARE."


import argparse
import ctypes
import json
import math
import os
import re
import struct
import time
from pathlib import Path
from typing import Callable

I32 = ctypes.c_int32
F32 = ctypes.c_float
WCHAR = ctypes.c_uint16
Vec3 = F32 * 3
ACC_STATUS_OFF = 0
ACC_STATUS_REPLAY = 1
ACC_STATUS_LIVE = 2
ACC_STATUS_PAUSE = 3
STATUS_NAMES = {0: "ACC_OFF", 1: "ACC_REPLAY", 2: "ACC_LIVE", 3: "ACC_PAUSE"}
SUPPORTED_SM_VERSIONS = frozenset({"1.9"})
SUPPORTED_ACC_API_VERSIONS = frozenset({"1.7"})
# Document, MMF and legacy acVersion values are different version namespaces.
# The installed ACC exposed smVersion=1.9 / acVersion=1.7 in root's metadata-only
# probe. Prefix layout was then independently checked against the original PDF,
# the ACC 1.9+ ctypes definitions and an author's documented 1.9 offset probes
# below. No version is automatically accepted merely because it looks numeric.
PROVENANCE = {
    "sdk_document_version": "1.8.12",
    "sdk_pdf_sha256": "a0c279753f154e9145f6854313472a0e1270027057ce27e2db1547a63f56cabf",
    "layout_reference": "https://github.com/TinyPedal/pyACCSharedMemory/blob/master/acc_data.py",
    "layout_reference_blob": "ad0fadc4a364513e160e29bf610db4764b715ce8",
    "mmf_19_ctypes_reference": "https://github.com/theoseleve-glitch/lap-coach-logger/blob/main/src/lap_coach_logger/adapters/acc_shm_structs.py",
    "mmf_19_ctypes_blob": "7966963908e05ce0a0c9966cbc7b8bddc22c8986",
    "mmf_19_offset_reference": "https://github.com/HoraceHuang-ui/Competizione-Companion/blob/main/native/shm-reader/acc-shm-reader.cs#L126-L136",
    "mmf_19_offset_blob": "4065dcc97406ae48859c8794b9e83ad912946920",
    "mmf_19_layout_check": "85/87/45 fields; prefix sizes 800/1588/820; independent anchor offsets agree",
    "license": "MIT; copyright (c) 2026 Xiang",
}

class ACCPageFilePhysics(ctypes.LittleEndianStructure):
    _pack_ = 4
    _layout_ = "ms"
    _fields_ = [
        ("packetId", I32),
        ("gas", F32),
        ("brake", F32),
        ("fuel", F32),
        ("gear", I32),
        ("rpms", I32),
        ("steerAngle", F32),
        ("speedKmh", F32),
        ("velocity", Vec3),
        ("accG", Vec3),
        ("wheelSlip", F32 * 4),
        ("wheelLoad", F32 * 4),
        ("wheelPressure", F32 * 4),
        ("wheelAngularSpeed", F32 * 4),
        ("tyreWear", F32 * 4),
        ("tyreDirtyLevel", F32 * 4),
        ("tyreCoreTemp", F32 * 4),
        ("camberAngle", F32 * 4),
        ("suspensionTravel", F32 * 4),
        ("drs", F32),
        ("tc", F32),
        ("heading", F32),
        ("pitch", F32),
        ("roll", F32),
        ("cgHeight", F32),
        ("carDamage", F32 * 5),
        ("numberOfTyresOut", I32),
        ("pitLimiterOn", I32),
        ("abs", F32),
        ("kersCharge", F32),
        ("kersInput", F32),
        ("autoShifterOn", I32),
        ("rideHeight", F32 * 2),
        ("turboBoost", F32),
        ("ballast", F32),
        ("airDensity", F32),
        ("airTemp", F32),
        ("roadTemp", F32),
        ("localAngularVel", Vec3),
        ("finalFF", F32),
        ("performanceMeter", F32),
        ("engineBrake", I32),
        ("ersRecoveryLevel", I32),
        ("ersPowerLevel", I32),
        ("ersHeatCharging", I32),
        ("ersIsCharging", I32),
        ("kersCurrentKJ", F32),
        ("drsAvailable", I32),
        ("drsEnabled", I32),
        ("brakeTemp", F32 * 4),
        ("clutch", F32),
        ("tyreTempI", F32 * 4),
        ("tyreTempM", F32 * 4),
        ("tyreTempO", F32 * 4),
        ("isAIControlled", I32),
        ("tyreContactPoint", Vec3 * 4),
        ("tyreContactNormal", Vec3 * 4),
        ("tyreContactHeading", Vec3 * 4),
        ("brakeBias", F32),
        ("localVelocity", Vec3),
        ("P2PActivation", I32),
        ("P2PStatus", I32),
        ("currentMaxRpm", F32),
        ("mz", F32 * 4),
        ("fx", F32 * 4),
        ("fy", F32 * 4),
        ("slipRatio", F32 * 4),
        ("slipAngle", F32 * 4),
        ("tcInAction", I32),
        ("absInAction", I32),
        ("suspensionDamage", F32 * 4),
        ("tyreTemp", F32 * 4),
        ("waterTemp", F32),
        ("brakePressure", F32 * 4),
        ("frontBrakeCompound", I32),
        ("rearBrakeCompound", I32),
        ("padLife", F32 * 4),
        ("discLife", F32 * 4),
        ("ignitionOn", I32),
        ("starterEngineOn", I32),
        ("isEngineRunning", I32),
        ("kerbVibration", F32),
        ("slipVibrations", F32),
        ("gVibrations", F32),
        ("absVibrations", F32),
    ]

class ACCPageFileGraphics(ctypes.LittleEndianStructure):
    _pack_ = 4
    _layout_ = "ms"
    _fields_ = [
        ("packetId", I32),
        ("status", I32),
        ("session", I32),
        ("currentTime", WCHAR * 15),
        ("lastTime", WCHAR * 15),
        ("bestTime", WCHAR * 15),
        ("split", WCHAR * 15),
        ("completedLaps", I32),
        ("position", I32),
        ("iCurrentTime", I32),
        ("iLastTime", I32),
        ("iBestTime", I32),
        ("sessionTimeLeft", F32),
        ("distanceTraveled", F32),
        ("isInPit", I32),
        ("currentSectorIndex", I32),
        ("lastSectorTime", I32),
        ("numberOfLaps", I32),
        ("tyreCompound", WCHAR * 33),
        ("replayTimeMultiplier", F32),
        ("normalizedCarPosition", F32),
        ("activeCars", I32),
        ("carCoordinates", Vec3 * 60),
        ("carID", I32 * 60),
        ("playerCarID", I32),
        ("penaltyTime", F32),
        ("flag", I32),
        ("penalty", I32),
        ("idealLineOn", I32),
        ("isInPitLane", I32),
        ("surfaceGrip", F32),
        ("mandatoryPitDone", I32),
        ("windSpeed", F32),
        ("windDirection", F32),
        ("isSetupMenuVisible", I32),
        ("mainDisplayIndex", I32),
        ("secondaryDisplyIndex", I32),
        ("TC", I32),
        ("TCCUT", I32),
        ("EngineMap", I32),
        ("ABS", I32),
        ("fuelXLap", F32),
        ("rainLights", I32),
        ("flashingLights", I32),
        ("lightsStage", I32),
        ("exhaustTemperature", F32),
        ("wiperLV", I32),
        ("driverStintTotalTimeLeft", I32),
        ("driverStintTimeLeft", I32),
        ("rainTyres", I32),
        ("sessionIndex", I32),
        ("usedFuel", F32),
        ("deltaLapTime", WCHAR * 15),
        ("iDeltaLapTime", I32),
        ("estimatedLapTime", WCHAR * 15),
        ("iEstimatedLapTime", I32),
        ("isDeltaPositive", I32),
        ("iSplit", I32),
        ("isValidLap", I32),
        ("fuelEstimatedLaps", F32),
        ("trackStatus", WCHAR * 33),
        ("missingMandatoryPits", I32),
        ("Clock", F32),
        ("directionLightsLeft", I32),
        ("directionLightsRight", I32),
        ("globalYellow", I32),
        ("globalYellow1", I32),
        ("globalYellow2", I32),
        ("globalYellow3", I32),
        ("globalWhite", I32),
        ("globalGreen", I32),
        ("globalChequered", I32),
        ("globalRed", I32),
        ("mfdTyreSet", I32),
        ("mfdFuelToAdd", F32),
        ("mfdTyrePressureLF", F32),
        ("mfdTyrePressureRF", F32),
        ("mfdTyrePressureLR", F32),
        ("mfdTyrePressureRR", F32),
        ("trackGripStatus", I32),
        ("rainIntensity", I32),
        ("rainIntensityIn10min", I32),
        ("rainIntensityIn30min", I32),
        ("currentTyreSet", I32),
        ("strategyTyreSet", I32),
        ("gapAhead", I32),
        ("gapBehind", I32),
    ]

class ACCPageFileStatic(ctypes.LittleEndianStructure):
    _pack_ = 4
    _layout_ = "ms"
    _fields_ = [
        ("smVersion", WCHAR * 15),
        ("acVersion", WCHAR * 15),
        ("numberOfSessions", I32),
        ("numCars", I32),
        ("carModel", WCHAR * 33),
        ("track", WCHAR * 33),
        ("playerName", WCHAR * 33),
        ("playerSurname", WCHAR * 33),
        ("playerNick", WCHAR * 33),
        ("sectorCount", I32),
        ("maxTorque", F32),
        ("maxPower", F32),
        ("maxRpm", I32),
        ("maxFuel", F32),
        ("suspensionMaxTravel", F32 * 4),
        ("tyreRadius", F32 * 4),
        ("maxTurboBoost", F32),
        ("deprecated_1", F32),
        ("deprecated_2", F32),
        ("penaltiesEnabled", I32),
        ("aidFuelRate", F32),
        ("aidTireRate", F32),
        ("aidMechanicalDamage", F32),
        ("allowTyreBlankets", I32),
        ("aidStability", F32),
        ("aidAutoClutch", I32),
        ("aidAutoBlip", I32),
        ("hasDRS", I32),
        ("hasERS", I32),
        ("hasKERS", I32),
        ("kersMaxJ", F32),
        ("engineBrakeSettingsCount", I32),
        ("ersPowerControllerCount", I32),
        ("trackSplineLength", F32),
        ("trackConfiguration", WCHAR * 33),
        ("ersMaxJ", F32),
        ("isTimedRace", I32),
        ("hasExtraLap", I32),
        ("carSkin", WCHAR * 33),
        ("reversedGridPositions", I32),
        ("pitWindowStart", I32),
        ("pitWindowEnd", I32),
        ("isOnline", I32),
        ("dryTyresName", WCHAR * 33),
        ("wetTyresName", WCHAR * 33),
    ]


LAYOUT_SIZES = {"physics": 800, "graphics": 1588, "static": 820}
STRUCTURES = {
    "physics": ACCPageFilePhysics,
    "graphics": ACCPageFileGraphics,
    "static": ACCPageFileStatic,
}
MAP_NAMES = {key: "Local\\acpmf_" + key for key in STRUCTURES}
for _page, _ctype in STRUCTURES.items():
    if ctypes.sizeof(_ctype) != LAYOUT_SIZES[_page]:
        raise RuntimeError("ACC layout size mismatch: " + _page)

UNSUPPORTED_CHANNELS = (
    "wheel_load_fl", "wheel_load_fr", "wheel_load_rl", "wheel_load_rr",
    "ride_height_front", "ride_height_rear", "track_length_m",
    "track_configuration", "lap_distance_m",
)
CHANNEL_UNITS = {
    "wheels_out_count": "SDK wheel count; only after a live nonzero value; zero-only support unknown",
    "engine_max_rpm": "rpm", "kerb_vibration": "SDK FFB amplitude",
    "tyre_core_temperature_c": "degC; four-wheel mean", "race_position": "rank",
    "tc_level": "level", "abs_level": "level",
    "speed_kmh": "km/h", "gas": "fraction", "brake": "fraction",
    "steering_input": "normalized", "steer_angle": "normalized",
    "yaw_rate": "rad/s", "acc_g_x": "g", "acc_g_y": "g", "acc_g_z": "g",
    "pitch": "rad", "roll": "rad", "heading": "rad",
    "distance_traveled": "m", "normalized_car_position": "fraction",
    "world_x": "m", "world_y": "m", "world_z": "m",
}
for _wheel in ("fl", "fr", "rl", "rr"):
    CHANNEL_UNITS["wheel_slip_" + _wheel] = "SDK raw"
    CHANNEL_UNITS["wheel_speed_" + _wheel] = "rad/s"
    CHANNEL_UNITS["suspension_travel_" + _wheel] = "m"


def utf16_text(value) -> str:
    """Decode fixed-width Windows UTF-16 fields without host wchar assumptions."""
    raw = bytes(value)
    end = next((i for i in range(0, len(raw), 2) if raw[i:i + 2] == b"\0\0"), len(raw))
    return raw[:end].decode("utf-16-le", errors="strict").strip()


def finite_number(value, low=None, high=None):
    value = float(value)
    if not math.isfinite(value):
        return None
    if low is not None and value < low:
        return None
    if high is not None and value > high:
        return None
    return value


def status_name(status) -> str:
    return STATUS_NAMES.get(status, "UNKNOWN")


def player_coordinates(graphics):
    """Select the player ID, never a car's array index or another driver's path."""
    count = int(graphics.activeCars)
    player_id = int(graphics.playerCarID)
    if not 1 <= count <= 60 or player_id < 0:
        return (None, None, None)
    matches = [index for index in range(count) if int(graphics.carID[index]) == player_id]
    if len(matches) != 1:
        return (None, None, None)
    point = tuple(finite_number(value) for value in graphics.carCoordinates[matches[0]])
    return point if all(value is not None for value in point) else (None, None, None)


class WinReadOnlySharedMap:
    """OpenFileMappingW(FILE_MAP_READ) only. No creation, writable access or process API."""

    FILE_MAP_READ = 0x0004

    def __init__(self, name, size, *, kernel32=None):
        self.name = str(name)
        self.size = int(size)
        self.handle = None
        self.view = None
        if self.size <= 0:
            raise ValueError("Map size must be positive")
        if kernel32 is None:
            if os.name != "nt":
                raise OSError("Windows required")
            kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        self._kernel32 = kernel32
        kernel32.OpenFileMappingW.argtypes = [ctypes.c_uint32, ctypes.c_int, ctypes.c_wchar_p]
        kernel32.OpenFileMappingW.restype = ctypes.c_void_p
        kernel32.MapViewOfFile.argtypes = [
            ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32,
            ctypes.c_uint32, ctypes.c_size_t,
        ]
        kernel32.MapViewOfFile.restype = ctypes.c_void_p
        kernel32.UnmapViewOfFile.argtypes = [ctypes.c_void_p]
        kernel32.UnmapViewOfFile.restype = ctypes.c_int
        kernel32.CloseHandle.argtypes = [ctypes.c_void_p]
        kernel32.CloseHandle.restype = ctypes.c_int
        handle = kernel32.OpenFileMappingW(self.FILE_MAP_READ, 0, self.name)
        if not handle:
            raise FileNotFoundError("Existing ACC map unavailable")
        view = kernel32.MapViewOfFile(handle, self.FILE_MAP_READ, 0, 0, self.size)
        if not view:
            kernel32.CloseHandle(handle)
            raise OSError("ACC read-only view unavailable")
        self.handle, self.view = handle, view

    def read_bytes(self):
        if not self.view:
            raise OSError("ACC map closed")
        return ctypes.string_at(self.view, self.size)

    def close(self):
        try:
            if self.view:
                self._kernel32.UnmapViewOfFile(self.view)
        finally:
            self.view = None
            if self.handle:
                self._kernel32.CloseHandle(self.handle)
                self.handle = None


class ACCSharedMemoryReader:
    """Return AC-compatible flat snapshots, tagged ACC, from independently validated pages.

    identity_check must return exactly True only while ACC owns the shared names.
    map_factory(name, size) is an injectable fixture interface, not a map creator.
    None means no fresh usable snapshot; inspect last_error/last_state. A changed
    OFF/REPLAY/PAUSE/setup state is emitted once with is_recordable=False, even if
    the physics packet did not change. Session splitting belongs to the caller.
    """

    def __init__(
        self, identity_check: Callable[[], bool] | None = None, *,
        map_factory=None, supported_sm_versions=None, copy_attempts=3,
        wall_clock=None, mono_clock=None,
    ):
        self.identity_check = identity_check
        self._map_factory = map_factory or WinReadOnlySharedMap
        self._supported_sm_versions = frozenset(
            SUPPORTED_SM_VERSIONS if supported_sm_versions is None else supported_sm_versions
        )
        self._copy_attempts = max(1, min(int(copy_attempts), 5))
        self._wall_clock = wall_clock or time.time
        self._mono_clock = mono_clock or time.monotonic
        self._maps = {}
        self.last_error = ""
        self.last_state = {}
        self.last_static_meta = {}
        self._last_snapshot = None
        self._last_emission_key = None
        self._wheel_out_active = False

    @property
    def physics_map(self):
        return self._maps.get("physics")

    @property
    def graphics_map(self):
        return self._maps.get("graphics")

    @property
    def static_map(self):
        return self._maps.get("static")

    def _identity_ok(self):
        try:
            return self.identity_check is not None and self.identity_check() is True
        except Exception:
            return False

    def close(self):
        for page in self._maps.values():
            try:
                page.close()
            except Exception:
                pass
        self._maps.clear()
        self.last_state = {}
        self.last_static_meta = {}
        self._last_snapshot = None
        self._last_emission_key = None
        self._wheel_out_active = False

    def ensure_open(self):
        if not self._identity_ok():
            self.close()
            self.last_error = "acc_identity_unconfirmed"
            return False
        try:
            for key, ctype in STRUCTURES.items():
                if key not in self._maps:
                    self._maps[key] = self._map_factory(MAP_NAMES[key], ctypes.sizeof(ctype))
            return True
        except Exception:
            self.close()
            self.last_error = "missing_or_unreadable_map"
            return False

    def _stable_pages(self):
        """Best-effort stable copies, independently packeted; never writable views."""
        for _ in range(self._copy_attempts):
            first = {key: self._maps[key].read_bytes() for key in STRUCTURES}
            second = {key: self._maps[key].read_bytes() for key in reversed(STRUCTURES)}
            if any(
                raw is None or len(raw) != LAYOUT_SIZES[key]
                or second[key] is None or len(second[key]) != LAYOUT_SIZES[key]
                for key, raw in first.items()
            ):
                self.last_error = "short_page"
                return None
            if all(first[key] == second[key] for key in STRUCTURES):
                return {key: STRUCTURES[key].from_buffer_copy(raw) for key, raw in first.items()}
        self.last_error = "concurrent_page_update"
        return None

    def _validate_meta(self, static):
        sm_version = utf16_text(static.smVersion)
        ac_version = utf16_text(static.acVersion)
        # Diagnostic metadata intentionally excludes all player name/nick fields.
        self.last_static_meta = {"sm_version": sm_version, "ac_version": ac_version}
        if sm_version not in self._supported_sm_versions:
            self.last_error = "unsupported_sm_version"
            return None
        if ac_version not in SUPPORTED_ACC_API_VERSIONS:
            self.last_error = "unsupported_acc_version"
            return None
        car, track = utf16_text(static.carModel), utf16_text(static.track)
        # These ACC-specific trailing fields are absent from AC's shorter static page.
        dry, wet = utf16_text(static.dryTyresName), utf16_text(static.wetTyresName)
        if not car or not track or not dry or not wet:
            self.last_error = "acc_static_not_ready"
            return None
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", car) or not re.fullmatch(r"[A-Za-z0-9_-]{1,32}", track):
            self.last_error = "invalid_acc_identity_fields"
            return None
        if not 1 <= int(static.sectorCount) <= 10 or int(static.isOnline) not in (0, 1):
            self.last_error = "invalid_acc_static_fields"
            return None
        if any(any(ord(c) < 32 for c in text) for text in (car, track, dry, wet)):
            self.last_error = "invalid_static_text"
            return None
        return {"car": car, "track": track}

    def read_snapshot(self):
        if not self.ensure_open():
            return None
        try:
            pages = self._stable_pages()
            if pages is None:
                return None
            if not self._identity_ok():
                self.close()
                self.last_error = "acc_identity_changed"
                return None
            physics, graphics, static = (pages[key] for key in ("physics", "graphics", "static"))
            meta = self._validate_meta(static)
            if meta is None:
                return None
            status = int(graphics.status)
            if status not in STATUS_NAMES or not -1 <= int(graphics.session) <= 8:
                self.last_error = "invalid_graphics_state"
                return None
            if not 0 <= int(graphics.activeCars) <= 60:
                self.last_error = "invalid_active_cars"
                return None
            for field in ("isInPit", "isInPitLane", "isSetupMenuVisible", "isValidLap"):
                if int(getattr(graphics, field)) not in (0, 1):
                    self.last_error = "invalid_graphics_boolean"
                    return None
            if int(physics.isAIControlled) not in (0, 1) or int(physics.pitLimiterOn) not in (0, 1):
                self.last_error = "invalid_physics_boolean"
                return None
            is_live = status == ACC_STATUS_LIVE
            menu_visible = bool(graphics.isSetupMenuVisible)
            ai_controlled = bool(physics.isAIControlled)
            recordable = is_live and not menu_visible and graphics.activeCars > 0 and not ai_controlled
            self.last_state = {
                "status": status, "status_name": status_name(status),
                "is_setup_menu_visible": menu_visible, "is_recordable": recordable,
                "is_ai_controlled": ai_controlled,
            }
            identity = ("ACC", meta["car"], meta["track"], int(graphics.session), int(graphics.sessionIndex))
            emission_key = (identity, int(physics.packetId), status, menu_visible, ai_controlled)
            if self._last_emission_key == emission_key:
                self.last_error = "duplicate_packet"
                return None
            # Do not repeatedly emit the same non-recordable state merely because
            # the game happens to keep advancing a physics packet in that state.
            if not recordable and self._last_snapshot is not None:
                old = self._last_snapshot
                if (
                    tuple(old["session_identity"]) == identity
                    and old["status"] == status
                    and old["is_setup_menu_visible"] == menu_visible
                    and old["is_ai_controlled"] == ai_controlled
                    and not old["is_recordable"]
                ):
                    self.last_error = "duplicate_state"
                    return None
            number = finite_number
            # Kunos v1.8.12 defines this count, but many ACC builds leave it at
            # zero. Never call zero-only data a verified on-track signal.
            wheel_out = int(physics.numberOfTyresOut)
            if self._last_snapshot is None or tuple(self._last_snapshot['session_identity']) != identity:
                self._wheel_out_active = False
            if recordable and 0 < wheel_out <= 4:
                self._wheel_out_active = True
            world_x, world_y, world_z = player_coordinates(graphics)
            snapshot = {
                "game": "ACC", "source": "acc_shared_memory",
                "wheels_out_count": wheel_out if self._wheel_out_active and 0 <= wheel_out <= 4 else None,
                "wall_time": self._wall_clock(), "mono_time": self._mono_clock(),
                "physics_packet_id": int(physics.packetId),
                "graphics_packet_id": int(graphics.packetId),
                "status": status, "status_name": status_name(status),
                "session": int(graphics.session), "session_index": int(graphics.sessionIndex),
                **meta, "session_identity": list(identity),
                "sm_version": self.last_static_meta["sm_version"],
                "ac_version": self.last_static_meta["ac_version"],
                "completed_laps": int(graphics.completedLaps),
                "current_lap_time_ms": int(graphics.iCurrentTime),
                "last_lap_time_ms": int(graphics.iLastTime),
                "best_lap_time_ms": int(graphics.iBestTime),
                "is_valid_lap": bool(graphics.isValidLap),
                "is_in_pit": bool(graphics.isInPit),
                "is_in_pit_lane": bool(graphics.isInPitLane),
                "is_setup_menu_visible": menu_visible, "is_recordable": recordable,
                "distance_traveled": number(graphics.distanceTraveled, 0),
                "normalized_car_position": number(graphics.normalizedCarPosition, 0, 1),
                "world_x": world_x, "world_y": world_y, "world_z": world_z,
                "speed_kmh": number(physics.speedKmh, 0, 600),
                "gas": number(physics.gas, -0.0001, 1.0001),
                "brake": number(physics.brake, -0.0001, 1.0001),
                "steering_input": number(physics.steerAngle, -1.0001, 1.0001),
                "steer_angle": number(physics.steerAngle, -1.0001, 1.0001),
                "gear": int(physics.gear), "rpm": int(physics.rpms),
                "engine_max_rpm": (number(physics.currentMaxRpm, 1000, 30000)
                                   or number(static.maxRpm, 1000, 30000)),
                "kerb_vibration": number(physics.kerbVibration, 0, 10),
                "race_position": number(graphics.position, 1, 100),
                "session_type": 'race' if graphics.session == 2 else 'other',
                "acc_g_x": number(physics.accG[0]),
                "acc_g_y": number(physics.accG[1]),
                "acc_g_z": number(physics.accG[2]),
                "yaw_rate": number(physics.localAngularVel[1]),
                "pitch": number(physics.pitch), "roll": number(physics.roll),
                "heading": number(physics.heading),
                "tc": number(physics.tc), "abs": number(physics.abs),
                "tc_level": int(graphics.TC) if graphics.TC >= 0 else None,
                "abs_level": int(graphics.ABS) if graphics.ABS >= 0 else None,
                "pit_limiter_on": bool(physics.pitLimiterOn),
                "is_ai_controlled": bool(physics.isAIControlled),
                "is_online": bool(static.isOnline),
            }
            # Conditions are independent observations, not an applied setup or
            # a performance outcome. SDK tyreWear is unused in ACC and stays
            # unavailable. Wheel-out zero-only publishers also stay unknown.
            tyre_temperatures = [number(value, -100, 300) for value in physics.tyreCoreTemp]
            snapshot['tyre_core_temperature_c'] = (sum(tyre_temperatures)/4
                if recordable and all(v is not None for v in tyre_temperatures) else None)
            snapshot["conditions"] = {
                "fuel_l": number(physics.fuel, 0, 1000) if recordable else None,
                "tyre_compound": (utf16_text(graphics.tyreCompound) or None) if recordable else None,
                "initial_tyre_wear": None,
                "initial_tyre_temperature": (sum(tyre_temperatures) / 4
                    if recordable and all(value is not None for value in tyre_temperatures) else None),
                "weather": {"rain_intensity_sdk": int(graphics.rainIntensity)}
                    if recordable and 0 <= graphics.rainIntensity <= 5 else None,
                "track_condition": {"grip_status_sdk": int(graphics.trackGripStatus),
                                    "road_temperature_c": number(physics.roadTemp, -100, 150)}
                    if recordable and 0 <= graphics.trackGripStatus <= 6 else None,
                "assists": {"tc_level": snapshot["tc_level"], "abs_level": snapshot["abs_level"]}
                    if recordable and snapshot["tc_level"] is not None and snapshot["abs_level"] is not None else None,
                "damage": [number(value, 0) for value in physics.carDamage] if recordable else None,
                "traffic": None, "physics_version": None, "game_version": None,
            }
            snapshot["condition_provenance"] = {
                "source": "acc_shared_memory", "sample_mono": snapshot["mono_time"],
                "fuel_unit": "litre", "tyre_temperature_unit": "degC; four-wheel mean",
                "damage_unit": "SDK raw; no physical conversion",
                "weather_unit": "ACC rain intensity enum", "track_condition_unit": "ACC grip enum and degC",
                "version_note": "acVersion is API compatibility version, not verified game/physics version",
            }
            for index, wheel in enumerate(("fl", "fr", "rl", "rr")):
                for prefix, field, low in (
                    ("wheel_slip_", "wheelSlip", None),
                    ("wheel_speed_", "wheelAngularSpeed", None),
                    ("suspension_travel_", "suspensionTravel", 0),
                ):
                    snapshot[prefix + wheel] = number(getattr(physics, field)[index], low)
            for channel in UNSUPPORTED_CHANNELS:
                snapshot[channel] = None
            validity = {
                key: ("unsupported_in_acc" if key in UNSUPPORTED_CHANNELS else
                      "unavailable_or_invalid" if snapshot[key] is None else "available")
                for key in set(CHANNEL_UNITS) | set(UNSUPPORTED_CHANNELS)
            }
            if not recordable:
                for key in CHANNEL_UNITS:
                    snapshot[key] = None
                    validity[key] = "not_live_player_driving"
            elif (
                any(snapshot[key] is None for key in
                    ("speed_kmh", "gas", "brake", "steering_input", "normalized_car_position"))
                or not 0 <= snapshot["rpm"] <= 30000 or not 0 <= snapshot["gear"] <= 20
                or snapshot["completed_laps"] < 0 or snapshot["current_lap_time_ms"] < 0
            ):
                self.last_error = "invalid_live_physics"
                return None
            reset_reasons = []
            old = self._last_snapshot
            if old is not None:
                if tuple(old["session_identity"]) != identity:
                    reset_reasons.append("session_identity_changed")
                if snapshot["physics_packet_id"] < old["physics_packet_id"]:
                    reset_reasons.append("physics_packet_rewind")
                if snapshot["graphics_packet_id"] < old["graphics_packet_id"]:
                    reset_reasons.append("graphics_packet_rewind")
                if snapshot["completed_laps"] < old["completed_laps"]:
                    reset_reasons.append("completed_laps_rewind")
                # ACC's outlap resets the clock at the timing line without
                # incrementing completedLaps. The same stint continues.
                from acc_lap_continuity import timing_line_crossing
                if (
                    snapshot["completed_laps"] == old["completed_laps"]
                    and snapshot["current_lap_time_ms"] + 1000 < old["current_lap_time_ms"]
                    and recordable and old["is_recordable"]
                    and not timing_line_crossing(old, snapshot)
                ):
                    reset_reasons.append("lap_clock_rewind_without_lap")
            snapshot["stream_reset"] = bool(reset_reasons)
            snapshot["stream_reset_reasons"] = reset_reasons
            snapshot["channel_validity"] = validity
            snapshot["unknown_channels"] = sorted(key for key, value in validity.items() if value != "available")
            snapshot["channel_units"] = dict(CHANNEL_UNITS)
            snapshot["coherence"] = "stable_double_copy; independent_page_packets"
            snapshot["identity_evidence"] = {
                "publisher_game": "ACC", "sdk_family": "ACC",
                "sm_version": self.last_static_meta["sm_version"],
                "ac_version": self.last_static_meta["ac_version"],
                "process_verified": True, "layout_profile": "acc-1.9-1.7",
            }
            self._last_snapshot = snapshot
            self._last_emission_key = emission_key
            self.last_error = ""
            return snapshot
        except (ValueError, UnicodeError, OSError, struct.error, TypeError):
            self.close()
            self.last_error = "invalid_or_unreadable_page"
            return None

    def diagnostics(self):
        """Safe metadata only; does not read a map or include driver/account fields."""
        return {
            "error": self.last_error, "state": dict(self.last_state),
            "versions": dict(self.last_static_meta),
            "layout_sizes": dict(LAYOUT_SIZES),
            "supported_sm_versions": sorted(self._supported_sm_versions),
            "supported_acc_api_versions": sorted(SUPPORTED_ACC_API_VERSIONS),
            "provenance": dict(PROVENANCE),
        }


def main(argv=None):
    parser = argparse.ArgumentParser(description="Explicit ACC read-only map probe; no network or game writes")
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--confirm-acc-process", action="store_true",
                        help="Operator has checked that ACC, with no competing AC/EVO, owns the maps")
    parser.add_argument("--output", type=Path, help="Write metadata-only JSON report to this explicit path")
    args = parser.parse_args(argv)
    if not args.probe:
        parser.error("--probe required")
    if not args.output:
        parser.error("--output is required; live data is never printed to stdout")
    reader = ACCSharedMemoryReader(identity_check=lambda: args.confirm_acc_process)
    try:
        snapshot = reader.read_snapshot()
        report = reader.diagnostics()
        report.update({
            "available": snapshot is not None,
            "read_only_existing_maps": True,
            "game_modified": False,
            "raw_channels_saved": False,
        })
        if snapshot is not None:
            report["snapshot_metadata"] = {
                key: snapshot[key] for key in (
                    "game", "status", "status_name", "is_recordable",
                    "physics_packet_id", "graphics_packet_id",
                    "session", "session_index", "completed_laps",
                    "current_lap_time_ms", "last_lap_time_ms",
                    "is_valid_lap", "is_setup_menu_visible",
                )
            }
            report["available_channels"] = sorted(
                key for key, value in snapshot["channel_validity"].items() if value == "available"
            )
            report["unknown_channels"] = snapshot["unknown_channels"]
        args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        return 0 if snapshot is not None else 2
    finally:
        reader.close()


if __name__ == "__main__":
    raise SystemExit(main())

