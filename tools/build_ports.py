"""Build every Minecraft version and loader, then start a real server with each jar's code.

A target is released only when its build, including the core tests, passed and a dedicated
server reached a loaded world with the mod and stopped by itself, without a data error.
Results go to build/release/<version>/report.json; logs to build/port-logs/.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
PORTS = ROOT / 'ports'
DATA_ERRORS = re.compile(r'(Parsing error loading|Couldn.t parse|Failed to load|Exception loading|Missing data pack|Missing metadata in pack).*gamblingitems', re.I)


def project(loader, mc):
    if loader == 'neoforge' and mc == '1.20.1':
        return PORTS / 'forge1201'
    if mc.startswith('1.20.'):
        return PORTS / (loader + '120')
    return PORTS / (loader + ('26' if mc.startswith('26.') else ''))


def build_dir(proj):
    # Keep release outputs separate from any legacy client that is currently running on Windows.
    suffix = ''
    if os.name == 'nt' and proj.name == 'forge1201':
        version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                       if line.startswith('version='))
        suffix = f'-release-{version}'
    return Path.home() / '.cache/gambling-items-ports' / f'gambling-items-{proj.name}{suffix}'


def gradle(args, log):
    with log.open('w', encoding='utf-8') as output:
        project = Path(args[args.index('-p') + 1])
        cache = Path.home() / '.cache/gambling-items-ports' / f'project-cache-{project.name}'
        wrapper = PORTS / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
        extra = [f'-Ppython={sys.executable}']
        if os.name == 'nt' and project.name == 'forge1201':
            extra.append(f'-PreleaseBuildDir={build_dir(project)}')
        if os.environ.get('GAMBLING_JAVA_INSTALLATIONS'):
            extra.append('-Dorg.gradle.java.installations.paths=' + os.environ['GAMBLING_JAVA_INSTALLATIONS'])
        return subprocess.run([str(wrapper), *args, *extra, '--console=plain', '--no-daemon', '--project-cache-dir', str(cache)], cwd=PORTS,
                              stdout=output, stderr=subprocess.STDOUT).returncode


def smoke(loader, mc, proj, log):
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('version='))
    # A release check never deletes a development or player world.
    run = ROOT / 'build/release' / version / 'smoke' / f'{mc}-{loader}'
    run.mkdir(parents=True, exist_ok=True)
    (run / 'eula.txt').write_text('eula=true\n')
    (run / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nmax-tick-time=-1\n')
    relative_run = Path(os.path.relpath(run, proj)).as_posix()
    code = gradle(['-p', str(proj), 'runServer', f'-Pminecraft_version={mc}',
                   f'-PreleaseSmokeDir={relative_run}'], log)
    text = log.read_text(errors='replace')
    if code != 0 or 'Gambling Items smoke test: server started' not in text:
        return 'failed'
    if DATA_ERRORS.search(text):
        return 'data_errors'
    return 'passed'


def main():
    # A single batch at a time: parallel Minecraft builds exhaust the memory of the WSL machine.
    lock = open(Path.home() / '.cache/gambling-items-ports.lock', 'w')
    try:
        if os.name == 'nt':
            import msvcrt
            lock.write('0')
            lock.flush()
            lock.seek(0)
            msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except OSError:
        raise SystemExit('Another build_ports batch is running; wait for it to finish.')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', nargs='+')
    parser.add_argument('--loaders', nargs='+', choices=['fabric', 'neoforge'], default=['fabric', 'neoforge'])
    parser.add_argument('--no-smoke', action='store_true')
    parser.add_argument('--resume', action='store_true', help='skip targets already built and server-checked for this release')
    args = parser.parse_args()
    matrix = json.loads((PORTS / 'versions.json').read_text())
    if args.versions:
        matrix = [row for row in matrix if row['minecraft'] in args.versions]
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('version='))
    release = ROOT / 'build/release' / version
    release.mkdir(parents=True, exist_ok=True)
    logs = ROOT / 'build/port-logs'
    logs.mkdir(parents=True, exist_ok=True)
    report_file = release / 'report.json'
    report = json.loads(report_file.read_text()) if report_file.exists() else {}
    for row in matrix:
        mc = row['minecraft']
        for loader in args.loaders:
            if row.get('fabric_api' if loader == 'fabric' else 'neoforge') is None:
                continue  # no build tooling for this loader on this version
            key = f'{mc}-{loader}'
            previous = report.get(key, {})
            previous_jar = release / previous.get('file', '__missing__')
            if args.resume and previous_jar.is_file() and hashlib.sha256(previous_jar.read_bytes()).hexdigest() == previous.get('sha256'):
                if previous.get('status') == 'built' and previous.get('server') == 'passed':
                    print(f'{key}: already verified', flush=True)
                    continue
                if previous.get('status', '').startswith('server_') and not args.no_smoke:
                    print(f'{key}: retrying server check', flush=True)
                    previous['server'] = smoke(loader, mc, project(loader, mc), logs / f'{key}-server.log')
                    previous['status'] = 'built' if previous['server'] == 'passed' else 'server_' + previous['server']
                    report = json.loads(report_file.read_text())
                    report[key] = previous
                    report_file.write_text(json.dumps(report, indent=2) + '\n')
                    print(f'{key}: {previous["status"]}', flush=True)
                    continue
            proj = project(loader, mc)
            start = time.monotonic()
            print(f'{key}: building', flush=True)
            jar = f'gambling-items-{mc}-{loader}-{version}.jar'
            (build_dir(proj) / 'libs' / jar).unlink(missing_ok=True)
            code = gradle(['-p', str(proj), 'build', f'-Pminecraft_version={mc}'], logs / f'{key}-build.log')
            record = {'minecraft': mc, 'loader': loader, 'java': row['java'],
                      'dependency': row['fabric_api' if loader == 'fabric' else 'neoforge']}
            built = build_dir(proj) / 'libs' / jar
            if code != 0 or not built.exists():
                record['status'] = 'build_failed'
                (release / jar).unlink(missing_ok=True)
            else:
                shutil.copy2(built, release / jar)
                record['file'] = jar
                record['sha256'] = hashlib.sha256((release / jar).read_bytes()).hexdigest()
                record['status'] = 'built'
                if not args.no_smoke:
                    record['server'] = smoke(loader, mc, proj, logs / f'{key}-server.log')
                    if record['server'] != 'passed':
                        record['status'] = 'server_' + record['server']
            record['seconds'] = round(time.monotonic() - start)
            # Several batches may run side by side: merge into what is on disk now.
            report = json.loads(report_file.read_text()) if report_file.exists() else {}
            report[key] = record
            report_file.write_text(json.dumps(report, indent=2) + '\n')
            print(f'{key}: {record["status"]} ({record["seconds"]} s)', flush=True)


if __name__ == '__main__':
    main()
