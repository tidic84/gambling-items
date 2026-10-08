"""Publish the built jars of build/release/<version> to Modrinth and CurseForge.

Only targets whose build and server check passed (report.json status "built") are sent.
Each upload is recorded in build/release/<version>/receipts.json and never sent twice.
Without --go nothing is sent: the script prints what it would do.

Tokens are read from ~/.config/gambling-items/tokens.env (MODRINTH_TOKEN, CURSEFORGE_TOKEN).
"""
import argparse
import json
from pathlib import Path
import time
import urllib.request
import uuid

from port import version_key

ROOT = Path(__file__).resolve().parents[1]
CURSEFORGE_PROJECT = 1716536
MODRINTH_SLUG = 'gambling-items'
FABRIC_API_MODRINTH = 'P7dR8mSH'
USER_AGENT = 'tidic84/gambling-items-publisher (alexim13550@gmail.com)'


def tokens():
    values = {}
    for line in (Path.home() / '.config/gambling-items/tokens.env').read_text().splitlines():
        if '=' in line:
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip()
    return values


def multipart(fields, files):
    boundary = uuid.uuid4().hex
    body = b''
    for name, value in fields.items():
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n'.encode() + value.encode() + b'\r\n'
    for name, (filename, content, kind) in files.items():
        body += (f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                 f'Content-Type: {kind}\r\n\r\n').encode() + content + b'\r\n'
    body += f'--{boundary}--\r\n'.encode()
    return body, f'multipart/form-data; boundary={boundary}'


def request(method, url, headers, body=None, kind=None):
    headers = dict(headers, **{'User-Agent': USER_AGENT})
    if kind:
        headers['Content-Type'] = kind
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers, method=method), timeout=300) as response:
                text = response.read().decode()
                return json.loads(text) if text else None
        except urllib.error.HTTPError as error:
            detail = error.read().decode(errors='replace')
            if error.code in (429, 500, 502, 503, 504) and attempt < 3:
                time.sleep(10 * (attempt + 1))
                continue
            raise SystemExit(f'{method} {url} failed: {error.code} {detail}')


def changelog(version):
    text = (ROOT / 'release/curseforge/changelog-en.md').read_text(encoding='utf-8')
    return text


# ---------------------------------------------------------------- Modrinth

def modrinth_project(token, go):
    headers = {'Authorization': token}
    try:
        return request('GET', f'https://api.modrinth.com/v2/project/{MODRINTH_SLUG}', headers)['id']
    except SystemExit as missing:
        if '404' not in str(missing):
            raise
    fields = json.loads((ROOT / 'release/curseforge/project-fields.json').read_text())
    data = {
        'slug': MODRINTH_SLUG, 'title': 'Gambling Items', 'description': fields['summary'],
        'categories': ['minigame', 'economy', 'game-mechanics'], 'client_side': 'required', 'server_side': 'required',
        'body': (ROOT / 'release/curseforge/description-en.md').read_text(encoding='utf-8'),
        'license_id': 'LicenseRef-All-Rights-Reserved', 'project_type': 'mod',
        'source_url': fields.get('sourceUrl'), 'initial_versions': [], 'is_draft': True,
    }
    print('Modrinth: create project', MODRINTH_SLUG)
    if not go:
        return 'DRY-RUN'
    icon = (ROOT / 'release/curseforge/project-icon.png').read_bytes()
    body, kind = multipart({'data': json.dumps(data)}, {'icon': ('icon.png', icon, 'image/png')})
    return request('POST', 'https://api.modrinth.com/v2/project', headers, body, kind)['id']


def modrinth_version(token, project, record, jar, version, go):
    loaders = {'fabric': ['fabric'], 'neoforge': ['neoforge']}[record['loader']]
    if record['loader'] == 'neoforge' and record['minecraft'] == '1.20.1':
        loaders = ['forge', 'neoforge']
    data = {
        'name': f"Gambling Items {version} — {record['minecraft']} {'Forge' if loaders[0] == 'forge' else record['loader'].capitalize()}",
        'version_number': f"{version}+{record['minecraft']}-{record['loader']}",
        'changelog': changelog(version),
        'dependencies': [{'project_id': FABRIC_API_MODRINTH, 'dependency_type': 'required'}] if record['loader'] == 'fabric' else [],
        'game_versions': [record['minecraft']], 'version_type': 'release', 'loaders': loaders,
        'featured': False, 'project_id': project, 'file_parts': ['file'], 'primary_file': 'file',
    }
    print('Modrinth:', data['version_number'])
    if not go:
        return {'dry_run': True}
    body, kind = multipart({'data': json.dumps(data)}, {'file': (jar.name, jar.read_bytes(), 'application/java-archive')})
    result = request('POST', 'https://api.modrinth.com/v2/version', {'Authorization': token}, body, kind)
    return {'id': result['id'], 'version_number': result['version_number']}


def modrinth_submit(token, project, go):
    if not go:
        print('Modrinth: submit project for review')
        return
    status = request('GET', f'https://api.modrinth.com/v2/project/{project}', {'Authorization': token})['status']
    if status == 'draft':
        print('Modrinth: submit project for review')
        request('PATCH', f'https://api.modrinth.com/v2/project/{project}', {'Authorization': token},
                json.dumps({'status': 'processing'}).encode(), 'application/json')


# ---------------------------------------------------------------- CurseForge

def curseforge_versions(token):
    headers = {'X-Api-Token': token}
    types = request('GET', 'https://minecraft.curseforge.com/api/game/version-types', headers)
    minecraft_types = {t['id'] for t in types if t['slug'].startswith('minecraft-')}
    names = {}
    for version in request('GET', 'https://minecraft.curseforge.com/api/game/versions', headers):
        kind = version['gameVersionTypeID']
        if kind in minecraft_types:
            names.setdefault(('minecraft', version['name']), version['id'])
        else:
            names.setdefault(('other', version['name']), version['id'])
    return names


def curseforge_file(token, ids, record, jar, version, go):
    minecraft = record['minecraft']
    tags = [ids[('minecraft', minecraft)]]
    loaders = ['Fabric'] if record['loader'] == 'fabric' else (['Forge', 'NeoForge'] if minecraft == '1.20.1' else ['NeoForge'])
    tags += [ids[('other', name)] for name in loaders + [f"Java {record['java']}", 'Client', 'Server'] if ('other', name) in ids]
    metadata = {
        'changelog': changelog(version), 'changelogType': 'markdown',
        'displayName': f"Gambling Items {version} — {minecraft} {loaders[0]}",
        'gameVersions': tags, 'releaseType': 'release',
    }
    if record['loader'] == 'fabric':
        metadata['relations'] = {'projects': [{'slug': 'fabric-api', 'type': 'requiredDependency'}]}
    print('CurseForge:', metadata['displayName'], tags)
    if not go:
        return {'dry_run': True}
    body, kind = multipart({'metadata': json.dumps(metadata)}, {'file': (jar.name, jar.read_bytes(), 'application/java-archive')})
    result = request('POST', f'https://minecraft.curseforge.com/api/projects/{CURSEFORGE_PROJECT}/upload-file',
                     {'X-Api-Token': token}, body, kind)
    return {'id': result['id']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--go', action='store_true', help='actually upload')
    parser.add_argument('--only', choices=['modrinth', 'curseforge'])
    args = parser.parse_args()
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('version='))
    release = ROOT / 'build/release' / version
    report = json.loads((release / 'report.json').read_text())
    receipts_file = release / 'receipts.json'
    receipts = json.loads(receipts_file.read_text()) if receipts_file.exists() else {'modrinth': {}, 'curseforge': {}}
    keys = tokens()
    order = sorted(report, key=lambda key: (report[key]['loader'], version_key(report[key]['minecraft'])))
    ready = [key for key in order if report[key]['status'] == 'built' and (release / report[key]['file']).exists()]
    print(f'{len(ready)} files ready out of {len(report)} targets')
    if args.only != 'curseforge':
        project = modrinth_project(keys['MODRINTH_TOKEN'], args.go)
        for key in ready:
            if key in receipts['modrinth']:
                continue
            receipt = modrinth_version(keys['MODRINTH_TOKEN'], project, report[key], release / report[key]['file'], version, args.go)
            if args.go:
                receipts['modrinth'][key] = receipt
                receipts_file.write_text(json.dumps(receipts, indent=2) + '\n')
        modrinth_submit(keys['MODRINTH_TOKEN'], project, args.go)
    if args.only != 'modrinth':
        ids = curseforge_versions(keys['CURSEFORGE_TOKEN'])
        for key in ready:
            if key in receipts['curseforge']:
                continue
            receipt = curseforge_file(keys['CURSEFORGE_TOKEN'], ids, report[key], release / report[key]['file'], version, args.go)
            if args.go:
                receipts['curseforge'][key] = receipt
                receipts_file.write_text(json.dumps(receipts, indent=2) + '\n')


if __name__ == '__main__':
    main()
