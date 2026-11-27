"""Extract the original Multiboot payload, avoiding slow emulated CD I/O at boot."""
import pathlib,hashlib,subprocess
media=pathlib.Path('guest-media')
subprocess.run(['sha256sum','-c','SHA256SUMS'],cwd=media,check=True)
iso=next(media.glob('*.iso'))
out=pathlib.Path('qube-android-x86/src/main/assets/llmos')
out.mkdir(parents=True,exist_ok=True)
(out/'guest.iso').unlink(missing_ok=True)
for src,name in [('asm-llm-os.elf','kernel.elf'),('model.asmbin','model.asmbin')]:
    dest=out/name
    if dest.exists(): dest.unlink()
    subprocess.run(['xorriso','-osirrox','on','-indev',str(iso),'-extract','/boot/'+src,str(dest)],check=True)
with (out/'SHA256SUMS').open('w') as sums:
    for name in ['kernel.elf','model.asmbin']:
        with (out/name).open('rb') as f: digest=hashlib.file_digest(f,'sha256').hexdigest()
        sums.write(f'{digest}  {name}\n')

with (out/'kernel.elf').open('rb') as f: assert f.read(4)==b'\x7fELF', 'Kernel is not ELF'
with (out/'model.asmbin').open('rb') as f: assert f.read(8)==b'ASML\x02\x00\x00\x00', 'Expected .asmbin v2 model'
print('Verified original kernel ELF and ASML v2 Q8_0 model for direct Multiboot')
