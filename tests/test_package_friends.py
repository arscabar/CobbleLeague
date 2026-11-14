"""Exercise artifact selection and channel safety without touching real dist files."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('package_friends', ROOT/'scripts/package_friends.py')
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class PackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=ROOT/'.test-tmp')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root/'build.gradle').write_text("version = '1.0.0'\n")
        (self.root/'server-addons/cobble-synergy').mkdir(parents=True)
        (self.root/'server-addons/cobble-synergy/gradle.properties').write_text('mod_version=2.0.0\n')
        (self.root/'server-addons/cobble-synergy/UPSTREAM.md').write_text('upstream')
        (self.root/'installer').mkdir()
        (self.root/'LICENSE').write_text('MIT')
        self.jar(self.root/'build/libs/cobbleleague-1.0.0.jar', 'cobbleleague', '1.0.0')
        self.jar(self.root/'server-addons/cobble-synergy/build/libs/cobblesynergy-2.0.0.jar', 'cobblesynergy', '2.0.0', {'cobbleleague': '1.0.0'})
        self.spire = self.root/'spire/build/libs/spire.jar'
        (self.root/'spire').mkdir()
        (self.root/'spire/LICENSE').write_text('MIT Spire')
        self.depends = {'minecraft': '1.21.1', 'java': '>=21', 'fabricloader': '>=0.17.2', 'fabric-api': '*', 'fabric-language-kotlin': '*', 'cobblemon': '>=1.7.3 <1.8'}
        self.jar(self.spire, 'cobblespire', '0.6.0-dev', self.depends)

    def jar(self, path, mod_id, version, depends=None):
        path.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(path, 'w') as z:
            z.writestr('fabric.mod.json', json.dumps(dict(id=mod_id, version=version, depends=depends or {})))
            if mod_id == 'cobblesynergy':
                z.writestr('cobblesynergy/build.properties', 'test=false')

    def run_package(self, *args):
        with patch.object(package, 'ROOT', self.root), patch('sys.argv', ['package_friends.py', *map(str, args)]):
            package.main()
        return json.loads((self.root/'dist/client-release.json').read_text())

    def test_legacy_stable_pair(self):
        self.assertEqual(len(self.run_package()['mods']), 2)

    def test_prerelease_cannot_be_promoted(self):
        with self.assertRaisesRegex(ValueError, 'Prerelease'):
            self.run_package('--spire-jar', self.spire)
        self.assertFalse((self.root/'dist').exists())

    def test_triple_identity_changes_with_spire_only(self):
        first = self.run_package('--spire-jar', self.spire, '--channel', 'development')
        self.jar(self.spire, 'cobblespire', '0.6.0-dev.2', self.depends)
        second = self.run_package('--spire-jar', self.spire, '--channel', 'development')
        self.assertEqual(first['mods'][:2], second['mods'][:2])
        self.assertNotEqual(first['release'], second['release'])
        bundle = self.root/'dist'/f'cobbleleague-friends-{second["release"]}'
        self.assertIn('-Offline', (bundle/'INSTALL.bat').read_text())
        self.assertTrue((bundle/'LICENSE-CobbleSpire.txt').exists())

    def test_wrong_id_and_changed_dependencies_rejected(self):
        for mod_id, depends in [('unrelated', self.depends), ('cobblespire', {'cobblemon': '>=1.8'})]:
            self.jar(self.spire, mod_id, '0.6.0-dev', depends)
            with self.assertRaises(ValueError):
                self.run_package('--spire-jar', self.spire, '--channel', 'development')

    def test_official_stable_triple(self):
        self.jar(self.spire, 'cobblespire', '0.6.1', self.depends)
        manifest = self.run_package('--spire-jar', self.spire, '--channel', 'stable')
        self.assertEqual(manifest['release'], '1.0.0')
        self.assertEqual(manifest['channel'], 'stable')
        self.assertEqual([m['id'] for m in manifest['mods']], ['cobbleleague', 'cobblesynergy', 'cobblespire'])
        bundle = self.root/'dist'/f'cobbleleague-friends-{manifest["release"]}'
        self.assertNotIn('-Offline', (bundle/'INSTALL.bat').read_text())
        self.assertTrue((bundle/'LICENSE-CobbleSpire.txt').exists())


if __name__ == '__main__':
    (ROOT/'.test-tmp').mkdir(exist_ok=True)
    unittest.main()
