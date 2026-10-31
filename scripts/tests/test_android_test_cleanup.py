import sys
from pathlib import Path
import unittest
from unittest.mock import patch
import subprocess
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from android_test_cleanup import Cleanup, CleanupError, transition_adbd


class CleanupFailureTest(unittest.TestCase):
    def test_failed_release_and_clipboard_do_not_skip_preferences_settings_or_unroot(self):
        record = {}; cleanup = Cleanup(record); restored = []
        def fail(): raise AssertionError('controlled restoration failure')
        cleanup.attempt('release', fail)
        cleanup.attempt('clipboard', fail)
        for action in ['preferences', 'settings', 'viewport', 'unroot']:
            cleanup.attempt(action, lambda name=action: restored.append(name))
        self.assertEqual(['preferences', 'settings', 'viewport', 'unroot'], restored)
        self.assertFalse(record['cleanup']['release']['passed'])
        self.assertFalse(record['cleanup']['clipboard']['passed'])
        with self.assertRaisesRegex(CleanupError, 'release, clipboard'): cleanup.finish()

    def test_cleanup_continues_after_interruption_and_never_reports_it_as_success(self):
        cleanup = Cleanup({}); restored = []
        def interrupted(): raise KeyboardInterrupt()
        cleanup.attempt('clipboard', interrupted)
        cleanup.attempt('unroot', lambda: restored.append(True))
        self.assertEqual([True], restored)
        with self.assertRaises(CleanupError): cleanup.finish()

    @patch('android_test_cleanup.time.sleep')
    @patch('android_test_cleanup.subprocess.run')
    def test_unroot_waits_past_old_root_and_offline_states(self, run, sleep):
        run.side_effect = [subprocess.CompletedProcess([], 1),
                           subprocess.CompletedProcess([], 0, b'uid=0(root)'),
                           subprocess.CompletedProcess([], 1, b''),
                           subprocess.CompletedProcess([], 0, b'uid=2000(shell)')]
        transition_adbd(['adb', '-s', 'emulator-test'], False)
        self.assertEqual(4, run.call_count)
        self.assertEqual(2, sleep.call_count)

    @patch('android_test_cleanup.time.monotonic', side_effect=[0, 16])
    @patch('android_test_cleanup.subprocess.run')
    def test_unroot_timeout_is_a_failure(self, run, clock):
        with self.assertRaises(CleanupError): transition_adbd(['adb'], False)


if __name__ == '__main__': unittest.main()
