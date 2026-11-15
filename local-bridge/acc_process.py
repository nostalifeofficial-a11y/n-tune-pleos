"""Identify the publisher of acpmf_* maps using process metadata only."""
import ctypes
import os
import threading
import time
from ctypes import wintypes


def running_process_names():
    if os.name != "nt":
        return set()
    class PROCESSENTRY32W(ctypes.Structure):
        _fields_ = [("dwSize", wintypes.DWORD), ("cntUsage", wintypes.DWORD),
                    ("th32ProcessID", wintypes.DWORD), ("th32DefaultHeapID", ctypes.c_size_t),
                    ("th32ModuleID", wintypes.DWORD), ("cntThreads", wintypes.DWORD),
                    ("th32ParentProcessID", wintypes.DWORD), ("pcPriClassBase", wintypes.LONG),
                    ("dwFlags", wintypes.DWORD), ("szExeFile", wintypes.WCHAR * 260)]
    api = ctypes.WinDLL("kernel32", use_last_error=True)
    api.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
    api.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
    api.Process32FirstW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
    api.Process32NextW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
    api.CloseHandle.argtypes = [wintypes.HANDLE]
    ctypes.set_last_error(0)
    handle = api.CreateToolhelp32Snapshot(2, 0)  # TH32CS_SNAPPROCESS
    if handle in (None, ctypes.c_void_p(-1).value):
        raise OSError(ctypes.get_last_error(), "process_inventory_unavailable")
    try:
        item = PROCESSENTRY32W()
        item.dwSize = ctypes.sizeof(item)
        names = set()
        ok = api.Process32FirstW(handle, ctypes.byref(item))
        while ok:
            names.add(item.szExeFile.casefold())
            ok = api.Process32NextW(handle, ctypes.byref(item))
        if ctypes.get_last_error() not in (0, 18):
            raise OSError(ctypes.get_last_error(), "process_inventory_incomplete")
        return names
    finally:
        api.CloseHandle(handle)


class GameIdentity:
    """Fail closed when multiple games can publish the same shared maps."""
    ACC = {"ac2-win64-shipping.exe"}
    AC = {"acs.exe", "acs_x86.exe"}
    EVO = {"assettocorsaevo.exe", "assettocorsaevo-win64-shipping.exe", "ace.exe"}
    LMU = {"le mans ultimate.exe"}
    IRACING = {"iracingsim64dx11.exe", "iracingsim64.exe"}

    def __init__(self, inventory=running_process_names):
        self.inventory = inventory
        self._until = 0
        self._names = set()
        self._lock = threading.Lock()

    def is_game(self, game, *, fresh=False):
        with self._lock:
            if fresh or time.monotonic() >= self._until:
                try:
                    self._names = self.inventory()
                except Exception:
                    self._names = set()
                self._until = time.monotonic() + 0.5
            has_acc = bool(self._names & self.ACC)
            has_ac = bool(self._names & self.AC)
            has_evo = bool(self._names & self.EVO)
            if game == "ACC":
                return has_acc and not has_ac and not has_evo
            if game == "AC":
                return has_ac and not has_acc and not has_evo
            return False

    def current_game(self, *, fresh=False):
        """Return a single running simulator; launchers and menus are excluded."""
        with self._lock:
            if fresh or time.monotonic() >= self._until:
                try:
                    self._names = self.inventory()
                except Exception:
                    self._names = set()
                self._until = time.monotonic() + 0.5
            found = [name for name, names in (
                ("ACC", self.ACC), ("AC", self.AC), ("EVO", self.EVO),
                ("LMU", self.LMU), ("IRACING", self.IRACING),
            ) if self._names & names]
            return found[0] if len(found) == 1 else None


GAME_IDENTITY = GameIdentity()
