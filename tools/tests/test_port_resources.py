"""Release resource contracts that server-only smoke tests cannot exercise."""

import json
from pathlib import Path
import tempfile
import unittest

from tools import port


class ClientMixinResourcesTest(unittest.TestCase):
    def test_forge_1201_declares_the_processor_refmap(self):
        with tempfile.TemporaryDirectory() as temporary:
            resources = Path(temporary) / 'resources'
            port.generate_resources('neoforge', '1.20.1', resources)
            config = json.loads((resources / 'gamblingitems.client.mixins.json').read_text())
            self.assertEqual(config.get('refmap'), 'gamblingitems.refmap.json')
            self.assertEqual(config['compatibilityLevel'], 'JAVA_17')
            self.assertTrue(config['required'])
            self.assertEqual(config['injectors']['defaultRequire'], 1)

    def test_other_ports_do_not_reference_a_legacy_forge_refmap(self):
        for loader, minecraft in [('fabric', '1.20.1'), ('neoforge', '1.21.1')]:
            with self.subTest(loader=loader, minecraft=minecraft), tempfile.TemporaryDirectory() as temporary:
                resources = Path(temporary) / 'resources'
                port.generate_resources(loader, minecraft, resources)
                config = json.loads((resources / 'gamblingitems.client.mixins.json').read_text())
                self.assertNotIn('refmap', config)


if __name__ == '__main__':
    unittest.main()
