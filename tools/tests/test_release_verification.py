"""Publishing must not bypass startup checks or upload a modified build."""

import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import build_ports, publish


class ReleaseVerificationTest(unittest.TestCase):
    def test_only_unchanged_server_checked_builds_can_be_published(self):
        with tempfile.TemporaryDirectory() as temporary:
            release = Path(temporary)
            jar = release / 'test.jar'
            jar.write_bytes(b'verified artifact')
            record = dict(minecraft='1.20.1', loader='fabric', status='built', file=jar.name,
                          sha256=hashlib.sha256(jar.read_bytes()).hexdigest())
            report = {'1.20.1-fabric': record}
            self.assertEqual([], publish.verified_targets(report, release))
            record['server'] = 'failed'
            self.assertEqual([], publish.verified_targets(report, release))
            record['server'] = 'passed'
            self.assertEqual(['1.20.1-fabric'], publish.verified_targets(report, release))
            jar.write_bytes(b'changed after verification')
            with self.assertRaises(SystemExit):
                publish.verified_targets(report, release)

    def test_smoke_uses_a_separate_loopback_world_and_requires_clean_exit(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'gradle.properties').write_text('version=0.2.2\n')
            project = root / 'ports/fabric120'
            old_world = project / 'run/1.20.1/world/keep.txt'
            old_world.parent.mkdir(parents=True)
            old_world.write_text('player world')
            log = root / 'server.log'

            def failed_server(args, log):
                self.assertTrue(any(arg.startswith('-PreleaseSmokeDir=') for arg in args))
                log.write_text('Gambling Items smoke test: server started\n')
                return 1

            with patch.object(build_ports, 'ROOT', root), patch.object(build_ports, 'gradle', failed_server):
                self.assertEqual('failed', build_ports.smoke('fabric', '1.20.1', project, log))
            self.assertEqual('player world', old_world.read_text())
            properties = (root / 'build/release/0.2.2/smoke/1.20.1-fabric/server.properties').read_text()
            self.assertIn('server-ip=127.0.0.1', properties)
            self.assertIn('server-port=0', properties)


if __name__ == '__main__':
    unittest.main()
