from pathlib import Path
import hashlib
import io
import json
import tarfile
import urllib.request
import zipfile

root = Path(__file__).resolve().parent.parent
lock = json.loads((root / 'android/media-runtime.lock.json').read_text())
cache = root / 'artifacts/media-runtime/sources'
cache.mkdir(parents=True, exist_ok=True)
assets = root / 'artifacts/android/gradle-assets/media-runtime'
assets.mkdir(parents=True, exist_ok=True)
sha = lambda data: hashlib.sha256(data).hexdigest()
worker = root / 'android/python/desktop_extract.py'
assert sha(worker.read_bytes()) == lock['desktopWorkerSha256']
with zipfile.ZipFile(assets / 'packages.zip', 'w', zipfile.ZIP_DEFLATED) as bundle:
    for package in lock['packages']:
        archive = cache / package['file']
        if not archive.exists():
            data = urllib.request.urlopen(package['source'], timeout=60).read()
            assert sha(data) == package['sha256'], package['name']
            archive.write_bytes(data)
        data = archive.read_bytes()
        assert sha(data) == package['sha256'], package['name']
        with tarfile.open(fileobj=io.BytesIO(data)) as source:
            found = False
            for member in source:
                if not member.isfile():
                    continue
                parts = Path(member.name).parts[1:]
                if parts and parts[0] == 'src':
                    parts = parts[1:]
                if parts and parts[0] == package['module'] and '..' not in parts:
                    info = zipfile.ZipInfo('/'.join(parts), (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
                    bundle.writestr(info, source.extractfile(member).read())
                    found = True
            assert found, package['name']
        (assets / package['file']).write_bytes(data)
for file in (root / 'android/python').glob('*.py'):
    (assets / file.name).write_bytes(file.read_bytes())
(assets / 'lock.json').write_text(json.dumps(lock, indent=2) + '\n')
print('Built pinned desktop media packages for Android.')
