import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    names=set(z.namelist())
    for lib in ['qube','compat-musl','compat-qube','glib-2.0','pixman-1','qemu-system-x86_64']:
        name=f'lib/{sys.argv[2]}/lib{lib}.so'
        assert name in names, 'Missing '+name
        assert z.read(name)[:4]==b'\x7fELF', 'Invalid ELF '+name
print('Required native libraries present (runtime loading not tested)')
