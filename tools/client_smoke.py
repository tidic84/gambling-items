"""Open every game in a real client of each version and keep the screenshots.

Reuses the world the server check created (run/<mc>/world), joins it with --quickPlaySingleplayer,
and lets ClientSmoke open each game from its portable item, take a screenshot and quit.
Screenshots land in build/client-smoke/<mc>-<loader>/. Needs a display (WSLg).
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

import build_ports

ROOT = build_ports.ROOT
PORTS = build_ports.PORTS
OPTIONS = 'onboardAccessibility:false\nnarrator:0\npauseOnLostFocus:false\nguiScale:2\ntutorialStep:none\nskipMultiplayerWarning:true\njoinedFirstServer:true\nrenderDistance:4\nsoundCategory_master:0.0\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('targets', nargs='+', help='keys such as 1.21.1-fabric')
    args = parser.parse_args()
    out_root = ROOT / 'build/client-smoke'
    for key in args.targets:
        mc, loader = key.rsplit('-', 1)
        proj = build_ports.project(loader, mc)
        world = proj / 'run' / mc / 'world'
        client = proj / 'run' / f'{mc}-client'
        if not world.exists():
            print(f'{key}: no server world, run the server check first')
            continue
        shutil.rmtree(client / 'saves' / 'world', ignore_errors=True)
        shutil.rmtree(client / 'screenshots', ignore_errors=True)
        (client / 'saves').mkdir(parents=True, exist_ok=True)
        shutil.copytree(world, client / 'saves' / 'world')
        (client / 'options.txt').write_text(OPTIONS)
        log = ROOT / 'build/port-logs' / f'{key}-client.log'
        print(f'{key}: client', flush=True)
        env = dict(os.environ, DISPLAY=os.environ.get('DISPLAY', ':0'))
        try:
            with log.open('w') as output:
                subprocess.run([str(PORTS / 'gradlew'), '-p', str(proj), 'runClient', f'-Pminecraft_version={mc}',
                                '-PclientSmoke', '--console=plain', '--no-daemon', '--project-cache-dir',
                                str(Path.home() / '.cache/gambling-items-ports' / f'project-cache-{proj.name}')],
                               cwd=PORTS, stdout=output, stderr=subprocess.STDOUT, env=env, timeout=900)
        except subprocess.TimeoutExpired:
            print(f'{key}: timed out')
        target = out_root / key
        shutil.rmtree(target, ignore_errors=True)
        target.mkdir(parents=True)
        shots = sorted((client / 'screenshots').glob('*.png')) if (client / 'screenshots').exists() else []
        for shot in shots:
            shutil.copy2(shot, target / shot.name)
        print(f'{key}: {len(shots)} screenshots', flush=True)


if __name__ == '__main__':
    main()
