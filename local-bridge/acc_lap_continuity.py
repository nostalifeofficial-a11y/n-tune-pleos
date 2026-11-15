"""A timing-line crossing is not a session restart, including ACC's outlap."""
import math


def timing_line_crossing(previous, current):
    if not isinstance(previous, dict) or not isinstance(current, dict):
        return False
    for key in ('game', 'car', 'track', 'session', 'session_index'):
        if previous.get(key) != current.get(key):
            return False
    if current.get('game') != 'ACC':
        return False
    def number(point, name):
        value = point.get(name)
        return value if type(value) in (int, float) and math.isfinite(value) else None
    before, after = (number(point, 'normalized_car_position') for point in (previous,current))
    old_time, new_time = (number(point, 'current_lap_time_ms') for point in (previous,current))
    old_mono, new_mono = (number(point, 'mono_time') for point in (previous,current))
    old_lap, new_lap = (number(point, 'completed_laps') for point in (previous,current))
    if any(value is None for value in (before,after,old_time,new_time,old_mono,new_mono,old_lap,new_lap)):
        return False
    return (0.97 <= before <= 1 and 0 <= after <= .03 and 1-before+after <= .035
            and 0 <= new_mono-old_mono <= 1 and new_time <= 1000 and old_time-new_time > 1000
            and new_lap in (old_lap,old_lap+1)
            and all(point.get('status') == 2 and point.get('is_recordable') is True
                    and (number(point,'speed_kmh') or 0) > 5
                    for point in (previous,current)))
