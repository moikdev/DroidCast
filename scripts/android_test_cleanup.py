"""Independent best-effort restoration for guarded, owned-emulator test runners."""
import subprocess
import time


class CleanupError(RuntimeError):
    pass


class Cleanup:
    def __init__(self, record):
        self.results = record.setdefault('cleanup', {})
        self.errors = []

    def attempt(self, name, action):
        try:
            value = action()
            self.results[name] = {'passed': True}
            return value
        except BaseException as error:
            self.results[name] = {'passed': False, 'error_type': type(error).__name__}
            self.errors.append(name)
            return None

    def require(self, condition, message):
        if not condition:
            raise CleanupError(message)

    def finish(self):
        if self.errors:
            raise CleanupError('Restoration failed: ' + ', '.join(self.errors))


def transition_adbd(adb, rooted, timeout=15):
    """ADB may disconnect before returning from root/unroot; wait for the actual UID."""
    command = 'root' if rooted else 'unroot'
    expected = b'uid=0' if rooted else b'uid=2000'
    try:
        subprocess.run(adb + [command], capture_output=True, timeout=5)
    except subprocess.TimeoutExpired:
        pass
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            state = subprocess.run(adb + ['shell', 'id'], capture_output=True, timeout=3)
            if state.returncode == 0 and state.stdout.startswith(expected):
                return
        except subprocess.TimeoutExpired:
            pass
        time.sleep(.15)
    raise CleanupError('adbd did not reach the required UID after ' + command)
