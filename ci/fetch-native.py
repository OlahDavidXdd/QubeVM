#!/usr/bin/env python3
"""Fetch hash-pinned native sources; extract only after verification."""
import hashlib,json,pathlib,subprocess,tarfile,urllib.request
root=pathlib.Path(__file__).resolve().parents[1]
jni=root/'qube-android-lib/src/main/jni'
for name,spec in json.loads((root/'ci/native-sources.json').read_text()).items():
    dest=jni/name
    if dest.exists(): continue
    archive=root/'ci'/('download-'+name.replace('/', '-')+'.tar')
    urllib.request.urlretrieve(spec['url'],archive)
    assert hashlib.sha256(archive.read_bytes()).hexdigest()==spec['sha256'], name+' checksum mismatch'
    dest.mkdir(parents=True)
    subprocess.run(['tar','xf',str(archive),'--strip-components=1','-C',str(dest)],check=True)
    archive.unlink()
patches={'qemu':'qemu-11.1.3.patch','glib':'glib-2.90.1.patch','qemu/subprojects/libslirp':'libslirp-4.9.5.patch','virglrenderer':'virglrenderer-1.3.0-android-egl.patch'}
for folder,patch in patches.items():
    marker=jni/folder/'.llmos-patched'
    if not marker.exists():
        with (jni/'patches'/patch).open('rb') as f: subprocess.run(['patch','-p1','--batch'],cwd=jni/folder,stdin=f,check=True)
        marker.touch()
