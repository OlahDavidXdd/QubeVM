"""Execute unmodified guest under software TCG, retain actual serial output."""
import subprocess,pathlib,time
root=pathlib.Path('guest')
cmd=['qemu-system-x86_64','-accel','tcg,thread=multi','-cpu','max','-smp','2','-m','2048','-display','none','-monitor','none','-serial','stdio','-no-reboot','-kernel',str(root/'build/asm-llm-os.elf'),'-initrd',str(root/'models/qwen.asmbin'),'-append','prompt=Hello max_tokens=1 shutdown=1']
start=time.monotonic()
try:
    p=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=900)
    data=p.stdout
except subprocess.TimeoutExpired as e:
    data=e.stdout or b''
    (root/'build/tcg-serial.log').write_bytes(data)
    raise
(root/'build/tcg-serial.log').write_bytes(data)
print(data.decode(errors='replace'))
assert b'[FATAL]' not in data, 'Guest fatal error'
assert b'GEN_TOKENS:' in data or b'decode :' in data, 'Inference completion not observed'
print('TCG elapsed seconds:',time.monotonic()-start)
