#!/usr/bin/env python3
"""Repeatable source inventory, not a compiler or a device-behavior test."""
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [p for module in ('app', 'old-photon', 'baselineprofile') for p in (ROOT / module / 'src').rglob('*') if p.suffix in ('.kt', '.xml')]
PASSES = {
    '1-build': (list(ROOT.glob('*.kts')) + list(ROOT.glob('*/build.gradle.kts')) + list(ROOT.glob('gradle/*.toml')) + list(ROOT.glob('**/*.pro')), r'namespace|applicationId|compileSdk|ksp|room|ignorewarnings'),
    '2-manifests': (list(ROOT.glob('*/src/main/AndroidManifest.xml')), r'permission|uses-feature|exported|intent-filter|configChanges|service'),
    '3-network': ([p for p in SOURCES if re.search(r'Repository|Links|Stream|Transport|Downloader|Network', p.name)], r'Dispatchers.IO|execute\(|Timeout|User-Agent|403|429|startPositionMs'),
    '4-playback': ([p for p in SOURCES if re.search(r'Player|Playback|ViewModel|SleepTimer', p.name)], r'release\(|onCleared|onDestroy|AudioAttributes|BecomingNoisy|while \(|preservePosition|seekTo'),
    '5-compose': ([p for p in SOURCES if p.suffix == '.kt'], r'LazyColumn|LazyRow|LazyVerticalGrid|items\(|viewModelScope.launch|readText\(|decodeFromString'),
    '6-resources': ([p for p in SOURCES if re.search(r'Image|Artwork|Thumbnail', p.name)] + list(ROOT.glob('design/icons/*.svg')), r'maxresdefault|size\(|MemoryCache|isLowRamDevice|<path|<circle|<rect'),
    '7-tv': ([p for p in SOURCES if re.search(r'Tv|PhotonActivity|PhotonPlayerView', p.name)], r'BackHandler|backDispatcher|FocusRequester|focusable|padding\(|remoteFocus'),
    '8-storage': ([p for p in SOURCES if re.search(r'Backup|Database|Store|Prefs|Subscriptions|Upnp', p.name)], r'Migration|version =|decodeFromString|readBytes|XML|DocumentBuilder|subscriptions|BackupReadLimits'),
}
report = {}
for name, (files, pattern) in PASSES.items():
    hits = []
    files = sorted(set(files))
    for path in files:
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if re.search(pattern, line):
                hits.append({'file': str(path.relative_to(ROOT)), 'line': number, 'text': line.strip()[:220]})
    report[name] = {'files': [str(p.relative_to(ROOT)) for p in files], 'hits': hits}
    print(f'{name}: inspected {len(files)} files; {len(hits)} search hits (manual review required)')

# Parse resources, including all the actual SVGs. This does not validate Android linking.
xmls = [p for p in SOURCES if p.suffix == '.xml'] + list(ROOT.glob('design/icons/*.svg'))
for p in xmls:
    ET.parse(p)
icons = list(ROOT.glob('design/icons/*.svg'))
for p in icons:
    for el in ET.parse(p).getroot():
        assert el.tag.split('}')[-1] in ('style', 'title', 'desc', 'path', 'circle', 'rect'), p
print(f'XML/SVG parse: PASS ({len(xmls)} files; {len(icons)} icons)')
a = '{http://schemas.android.com/apk/res/android}'
m = ET.parse(ROOT / 'old-photon/src/main/AndroidManifest.xml').getroot()
assert {x.get(a+'name') for x in m.findall('uses-permission')} >= {
    'android.permission.INTERNET', 'android.permission.ACCESS_NETWORK_STATE',
    'android.permission.FOREGROUND_SERVICE', 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'}
data = m.findall('./application/activity/intent-filter/data')
assert {'http','https'} <= {x.get(a+'scheme') for x in data}
assert {'music.youtube.com','m.youtube.com','youtube.com','youtu.be'} <= {x.get(a+'host') for x in data}
assert (ROOT / 'app/src/main/java/com/example/MainActivity.kt').exists()
assert (ROOT / 'old-photon/src/main/res/font/inter.ttf').stat().st_size > 0
print('Manifest links/permissions, MainActivity and Inter existence: PASS')
out = ROOT / 'docs/audit/source-inventory.json'
out.write_text(json.dumps(report, indent=2) + '\n')
print(f'Full per-pass paths and matching lines: {out.relative_to(ROOT)}')
