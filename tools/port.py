"""Generate the sources of one Minecraft version and loader from the canonical 1.21.1 tree.

The canonical sources (core, the shared game code under platforms/fabric-1.21.1 and the
NeoForge seams under platforms/neoforge-1.21.1) compile as they are for Fabric 1.21.1.
Every other target is derived here, never edited by hand:

* Conditional blocks select the code of a version or a loader::

      //#if MC >= 1.21.2
      //$ code for 1.21.2 and later
      //#else
      code for 1.21.1
      //#endif

  Inactive lines carry a ``//$ `` marker after their indentation. Conditions combine
  ``MC <op> <version>`` and ``LOADER == fabric|neoforge`` with ``&&``, ``||`` and ``!``.
* A few renames that Mojang applied everywhere are done textually (see RENAMES).

Usage: port.py <fabric|neoforge> <minecraft version> <java out dir> <resources out dir>
"""
from pathlib import Path
import json
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'core/src/main/java'
GAME = ROOT / 'platforms/fabric-1.21.1/src'
NEO = ROOT / 'platforms/neoforge-1.21.1/src/main'
# Minecraft 1.20.1 only has the Forge 47 line: its own loader seams, same shared game code.
FORGE = ROOT / 'platforms/forge-1.20.1/src/main'
FABRIC_ONLY = {
    'dev/gamblingitems/fabric/GamblingItemsFabric.java',
    'dev/gamblingitems/fabric/platform/Platform.java',
    'dev/gamblingitems/fabric/client/GamblingItemsClient.java',
}
MARK = '//$ '


def version_key(version):
    parts = [int(p) for p in version.split('.')]
    # 26.x follows 1.21.x: compare on (major, minor, patch) after mapping 1.y.z to (y, z) and 26.y to (26 + ...).
    if parts[0] == 1:
        return (parts[1], parts[2] if len(parts) > 2 else 0, 0)
    return (parts[0], parts[1] if len(parts) > 1 else 0, parts[2] if len(parts) > 2 else 0)


def condition(expression, mc, loader):
    def atom(match):
        name, op, value = match.group(1), match.group(2), match.group(3)
        if name == 'MC':
            left, right = version_key(mc), version_key(value)
        else:
            left, right = loader, value
        return str({'>=': left >= right, '>': left > right, '<=': left <= right, '<': left < right,
                    '==': left == right, '!=': left != right}[op])
    python = re.sub(r'(MC|LOADER)\s*(>=|<=|==|!=|>|<)\s*([\w.]+)', atom, expression)
    python = python.replace('&&', ' and ').replace('||', ' or ').replace('!', ' not ')
    return eval(python, {'__builtins__': {}}, {'True': True, 'False': False})


def preprocess(text, mc, loader, name):
    out, stack = [], []  # stack of [branch active, some branch already taken, parent active]
    for number, line in enumerate(text.split('\n'), 1):
        stripped = line.strip()
        if stripped.startswith('//#if '):
            parent = all(frame[0] for frame in stack)
            active = parent and condition(stripped[6:], mc, loader)
            stack.append([active, active, parent])
            out.append(line)
            continue
        if stripped.startswith('//#elif '):
            frame = stack[-1]
            frame[0] = frame[2] and not frame[1] and condition(stripped[8:], mc, loader)
            frame[1] = frame[1] or frame[0]
            out.append(line)
            continue
        if stripped == '//#else':
            frame = stack[-1]
            frame[0] = frame[2] and not frame[1]
            frame[1] = True
            out.append(line)
            continue
        if stripped == '//#endif':
            stack.pop()
            out.append(line)
            continue
        if not stack:
            out.append(line)
            continue
        indent = line[:len(line) - len(line.lstrip())]
        body = line[len(indent):]
        commented = body.startswith(MARK) or body == MARK.rstrip()
        content = body[len(MARK):] if body.startswith(MARK) else ('' if commented else body)
        if all(frame[0] for frame in stack):
            out.append(indent + content)
        else:
            out.append(indent + MARK + content if content else line)
    if stack:
        raise ValueError(f'{name}: unclosed //#if')
    return '\n'.join(out)


# Renames Mojang applied across the whole code base, applied after the conditional blocks.
RENAMES = [
    # Before 1.20.5: no stream codecs nor item components; a small shim keeps the same code.
    ('<1.20.5', [(r'net\.minecraft\.network\.codec\.StreamCodec\b', 'dev.gamblingitems.fabric.compat.StreamCodec'),
                 (r'net\.minecraft\.network\.codec\.ByteBufCodecs\b', 'dev.gamblingitems.fabric.compat.ByteBufCodecs'),
                 (r'\bRegistryFriendlyByteBuf\b', 'FriendlyByteBuf'),
                 (r'\bisSameItemSameComponents\b', 'isSameItemSameTags'),
                 # Block callbacks were public before 1.20.5; widening an override is always legal.
                 (r'@Override protected ', '@Override public ')]),
    # Identifiers were built with their constructor before 1.21.
    ('<1.21', [(r'ResourceLocation\.(fromNamespaceAndPath|parse|withDefaultNamespace)\(', 'new ResourceLocation(')]),
    # A player reaches its server and level through level() since 1.21.6.
    ('1.21.6', [(r'\bplayer\.server\b', 'player.level().getServer()'), (r'\.serverLevel\(\)', '.level()')]),
    # Level.isClientSide became a method and game profiles became records in 1.21.9.
    ('1.21.9', [(r'\.isClientSide\b(?!\()', '.isClientSide()'), (r'getGameProfile\(\)\.getName\(\)', 'getGameProfile().name()')]),
    # 1.21.11: identifiers, the util and render type packages, and button contents.
    ('1.21.11', [(r'\bnet\.minecraft\.Util\b', 'net.minecraft.util.Util'),
                 (r'\bRenderType\.debugQuads\(\)', 'net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads()'),
                 (r'net\.minecraft\.client\.renderer\.RenderType\b', 'net.minecraft.client.renderer.rendertype.RenderType'),
                 (r'\brenderWidget\(', 'renderContents('),
                 (r'(read|write)ResourceLocation\(', r'\1Identifier('), (r'\b(table|key)\.location\(\)', r'\1.identifier()'), (r'\bResourceLocation\b', 'Identifier'), (r'net\.minecraft\.resources\.ResourceLocation', 'net.minecraft.resources.Identifier')]),
    # 26.1 renamed the interface drawing to "extraction" and moved a few classes.
    ('26.1', [(r'\bGuiGraphics\b', 'GuiGraphicsExtractor'), (r'\.drawString\(', '.text('), (r'\.drawCenteredString\(', '.centeredText('),
              (r'\.renderFakeItem\(', '.fakeItem('), (r'\.renderItemDecorations\(', '.itemDecorations('),
              (r'\brenderLabels\(', 'extractLabels('), (r'\brenderContents\(', 'extractContents('),
              (r'net\.minecraft\.client\.gui\.render\.state\.', 'net.minecraft.client.renderer.state.gui.'),
              (r'net\.minecraft\.client\.renderer\.state\.CameraRenderState', 'net.minecraft.client.renderer.state.level.CameraRenderState'),
              (r'\bClickType\b', 'ContainerInput'), (r'\.submitGuiElement\(', '.addGuiElement('),
              (r'net\.fabricmc\.fabric\.api\.screenhandler\.v1\.ExtendedScreenHandlerFactory', 'net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider'),
              (r'net\.fabricmc\.fabric\.api\.screenhandler\.v1\.ExtendedScreenHandlerType', 'net.fabricmc.fabric.api.menu.v1.ExtendedMenuType'),
              (r'\bExtendedScreenHandlerFactory\b', 'ExtendedMenuProvider'), (r'\bExtendedScreenHandlerType\b', 'ExtendedMenuType'),
              (r'net\.fabricmc\.fabric\.api\.itemgroup\.v1\.ItemGroupEvents', 'net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents'),
              (r'ItemGroupEvents\.modifyEntriesEvent', 'CreativeModeTabEvents.modifyOutputEvent')]),
    # 26.2: the screen lives in Minecraft.gui, positions centre through Vec3.
    ('26.2', [(r'([\w.]+\.getBlockPos\(\))\.getCenter\(\)', r'net.minecraft.world.phys.Vec3.atCenterOf(\1)'),
              (r'\bminecraft\.screen\b', 'minecraft.gui.screen()'), (r'\bminecraft\.getMainRenderTarget\(\)', 'minecraft.gameRenderer.mainRenderTarget()')]),
    # 26.3: render pipelines moved to renderpearl, and "block" push reaction became "immoveable".
    ('26.3', [(r'com\.mojang\.blaze3d\.pipeline\.RenderPipeline\b', 'com.mojang.renderpearl.api.pipeline.RenderPipeline'),
              (r'PushReaction\.BLOCK\b', 'PushReaction.IMMOVEABLE'), (r'\bpose\.mulPose\(', 'pose.rotate('),
              (r'loot\.providers\.number\.ConstantValue\b', 'loot.providers.number.ints.ConstantValue')])
]


def renames(text, mc):
    for since, rules in RENAMES:
        # "<x" applies below version x, anything else from that version on.
        applies = version_key(mc) < version_key(since[1:]) if since.startswith('<') else version_key(mc) >= version_key(since)
        if applies:
            for pattern, replacement in rules:
                text = re.sub(pattern, replacement, text)
    return text


def sources(loader, mc):
    yield CORE, CORE
    for part in ('main/java', 'client/java'):
        base = GAME / part
        for path in base.rglob('*.java'):
            relative = path.relative_to(base).as_posix()
            if loader == 'neoforge' and relative in FABRIC_ONLY:
                continue
            yield base, path
    if loader == 'neoforge':
        base = (FORGE if mc == '1.20.1' else NEO) / 'java'
        for path in base.rglob('*.java'):
            if '/gametest/' in path.as_posix():
                continue
            yield base, path


def generate_java(loader, mc, out):
    if out.exists():
        shutil.rmtree(out)
    for base, path in sources(loader, mc):
        files = [path] if path.is_file() else list(path.rglob('*.java'))
        for file in files:
            relative = file.relative_to(base)
            text = file.read_text(encoding='utf-8')
            text = renames(preprocess(text, mc, loader, relative.as_posix()), mc)
            target = out / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding='utf-8')


def generate_resources(loader, mc, out):
    """Resources follow the layout and formats of the target version."""
    if out.exists():
        shutil.rmtree(out)
    source = GAME / 'main/resources'
    key = version_key(mc)
    plural = key < version_key('1.21')  # data folders were renamed to singular in 1.21
    for file in source.rglob('*'):
        if file.is_dir():
            continue
        relative = file.relative_to(source).as_posix()
        if loader == 'neoforge' and relative == 'fabric.mod.json':
            continue
        if plural:
            relative = re.sub(r'^data/([^/]+)/(recipe|advancement|loot_table|tags/item|tags/block|structure|function)/',
                              lambda m: f"data/{m[1]}/{ {'recipe': 'recipes', 'advancement': 'advancements', 'loot_table': 'loot_tables', 'tags/item': 'tags/items', 'tags/block': 'tags/blocks', 'structure': 'structures', 'function': 'functions'}[m[2]]}/", relative)
        target = out / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        if relative == 'gamblingitems.client.mixins.json':
            mixins = file.read_text(encoding='utf-8').replace('JAVA_21', f"JAVA_{target_row(mc)['java']}")
            if key >= version_key('1.21.6'):
                mixins = mixins.replace('"MouseHandlerAccessor"', '"MouseHandlerAccessor", "GuiGraphicsAccessor"')
            if loader == 'neoforge' and mc == '1.20.1':
                mixins = mixins.replace('"required": true,', '"required": true,\n  "minVersion": "0.8",')
            target.write_text(mixins, encoding='utf-8')
        elif relative == 'fabric.mod.json':
            target.write_text(fabric_metadata(file.read_text(encoding='utf-8'), mc), encoding='utf-8')
        elif file.suffix == '.json' and relative.startswith('data/'):
            target.write_text(convert_data(json.loads(file.read_text(encoding='utf-8')), relative, mc), encoding='utf-8')
        else:
            shutil.copy2(file, target)
    if key >= version_key('1.21.4'):
        # Item models are chosen by an item definition since 1.21.4.
        for model in (source / 'assets/gamblingitems/models/item').glob('*.json'):
            target = out / 'assets/gamblingitems/items' / model.name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps({'model': {'type': 'minecraft:model', 'model': f'gamblingitems:item/{model.stem}'}}, indent=2) + '\n')
    if loader == 'neoforge':
        metadata = NEO / 'resources/META-INF/neoforge.mods.toml'
        name = 'mods.toml' if key < version_key('1.20.5') else 'neoforge.mods.toml'
        target = out / 'META-INF' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(neoforge_metadata(metadata.read_text(encoding='utf-8'), mc), encoding='utf-8')
        if key < version_key('1.20.5'):
            # Forge 1.20.1 ignores the mod's assets and data without a pack.mcmeta.
            if mc == '1.20.1':
                pack = {'pack_format': 15, 'forge:resource_pack_format': 15, 'forge:data_pack_format': 15}
            else:
                pack = {'pack_format': 26}
            pack['description'] = 'Gambling Items resources'
            (out / 'pack.mcmeta').write_text(json.dumps({'pack': pack}, indent=2) + '\n')


def target_row(mc):
    for row in json.loads((ROOT / 'ports/versions.json').read_text()):
        if row['minecraft'] == mc:
            return row
    raise SystemExit(f'{mc} is not in ports/versions.json')


def fabric_metadata(text, mc):
    row = target_row(mc)
    data = json.loads(text)
    data['depends'] = {'fabricloader': '>=0.15.0', 'minecraft': mc, 'java': f">={row['java']}",
                       'fabric-api': '*'}
    return json.dumps(data, indent=2, ensure_ascii=False) + '\n'


def neoforge_metadata(text, mc):
    row = target_row(mc)
    legacy = mc == '1.20.1'
    loader_range = ('[47,)' if legacy else '[1,)' if version_key(mc) < version_key('1.20.5')
                    else '[3,)' if version_key(mc) < version_key('1.21') else '[4,)')
    neo = row['neoforge'].split('-')[-1] if legacy else row['neoforge']
    neo_range = '[47,)' if legacy else '[' + '.'.join(neo.split('.')[:2]) + ',)'
    text = text.replace('loaderVersion = "[4,)"', f'loaderVersion = "{loader_range}"')
    text = text.replace('versionRange = "[21.1.0,)"', f'versionRange = "{neo_range}"')
    text = text.replace('versionRange = "[1.21.1]"', f'versionRange = "[{mc}]"')
    if legacy:
        text = text.replace('modId = "neoforge"', 'modId = "forge"')
    if version_key(mc) < version_key('1.20.5'):
        # Older loaders read the mixin configuration from the jar manifest.
        text = text.replace('[[mixins]]\nconfig = "gamblingitems.client.mixins.json"\n\n', '')
    if version_key(mc) >= version_key('26.2'):
        # logoFile is deprecated since 26.2 and opens a warning screen; the icon is square.
        text = text.replace('logoFile = ', 'iconFile = ')
    if legacy:
        # Forge 47 still spells a required dependency "mandatory".
        text = text.replace('type = "required"', 'mandatory = true')
    return text


def convert_data(data, relative, mc):
    key = version_key(mc)
    if '/advancement' in relative and isinstance(data, dict) and key >= version_key('26.3'):
        for criterion in data.get('criteria', {}).values():
            conditions = criterion.get('conditions', {})
            if criterion.get('trigger') == 'minecraft:recipe_unlocked' and 'recipe' in conditions:
                conditions['recipes'] = [conditions.pop('recipe')]
    if '/recipe' in relative and isinstance(data, dict):
        result = data.get('result')
        if isinstance(result, dict) and key < version_key('1.20.5') and 'id' in result:
            result['item'] = result.pop('id')
        if key >= version_key('1.21.2') and 'key' in data:
            # Ingredients became plain item ids or tags in 1.21.2.
            data['key'] = {k: (v['item'] if isinstance(v, dict) and 'item' in v else '#' + v['tag'] if isinstance(v, dict) and 'tag' in v else v)
                           for k, v in data['key'].items()}
        if key >= version_key('1.21.2') and 'ingredients' in data:
            data['ingredients'] = [(v['item'] if isinstance(v, dict) and 'item' in v else '#' + v['tag'] if isinstance(v, dict) and 'tag' in v else v)
                                   for v in data['ingredients']]
    return json.dumps(data, indent=2, ensure_ascii=False) + '\n'


def main():
    loader, mc, java_out, resources_out = sys.argv[1:]
    if loader not in ('fabric', 'neoforge'):
        raise SystemExit('loader must be fabric or neoforge')
    generate_java(loader, mc, Path(java_out))
    generate_resources(loader, mc, Path(resources_out))


if __name__ == '__main__':
    main()
