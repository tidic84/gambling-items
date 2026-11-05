"""Generated loader seams must route every 1.20.1 screen through the full-width packet."""

from pathlib import Path
import re
import tempfile
import unittest

from tools import port


def active_java(path):
    return re.sub(r'//[^\n]*', '', path.read_text(encoding='utf-8'))


class MenuNetworkingTest(unittest.TestCase):
    def test_both_1201_loaders_send_and_register_full_width_actions(self):
        for loader in ('fabric', 'neoforge'):
            with self.subTest(loader=loader), tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary) / 'java'
                port.generate_java(loader, '1.20.1', out)
                game = out / 'dev/gamblingitems/fabric'
                screen = active_java(game / 'client/CasinoScreen.java')
                self.assertIn('Platform.menuButtonPacket(menu.containerId, button)', screen)
                for path in (game / 'client').glob('*.java'):
                    self.assertNotIn('handleInventoryButtonClick(', active_java(path), path.name)
                platform = active_java(game / 'platform/Platform.java')
                self.assertIn('MenuButton.read', platform.replace('::', '.'))
                self.assertIn('MenuButton.handle(player, action)', platform)
                bootstrap = (game / 'GamblingItemsFabric.java' if loader == 'fabric' else
                             out / 'dev/gamblingitems/forge/GamblingItemsForge.java')
                self.assertIn('Platform.initializeMenuNetworking()', active_java(bootstrap))

    def test_1211_keeps_its_native_varint_packet(self):
        for loader in ('fabric', 'neoforge'):
            with self.subTest(loader=loader), tempfile.TemporaryDirectory() as temporary:
                out = Path(temporary) / 'java'
                port.generate_java(loader, '1.21.1', out)
                game = out / 'dev/gamblingitems/fabric'
                screen = active_java(game / 'client/CasinoScreen.java')
                self.assertIn('handleInventoryButtonClick(menu.containerId, button)', screen)
                self.assertNotIn('Platform.menuButtonPacket', screen)
                self.assertNotIn('initializeMenuNetworking', active_java(game / 'platform/Platform.java'))


if __name__ == '__main__':
    unittest.main()
