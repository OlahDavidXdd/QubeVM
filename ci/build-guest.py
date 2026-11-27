"""One-time model conversion; guest assembly sources remain unchanged."""
from huggingface_hub import HfApi, snapshot_download
import subprocess,pathlib,hashlib
root=pathlib.Path('guest')
# Resolve an immutable model revision and retain it with the generated media.
revision=HfApi().model_info('Qwen/Qwen3-0.6B').sha
snapshot_download('Qwen/Qwen3-0.6B',revision=revision,local_dir=str(root/'models/source'),allow_patterns=['config.json','model.safetensors','tokenizer.json'])
subprocess.run(['python3','scripts/export_weights.py','--model-dir','models/source','--out','models/qwen.asmbin','--scale-dtype','fp16'],cwd=root,check=True)
subprocess.run(['make','os-iso','MODEL=models/qwen.asmbin'],cwd=root,check=True)
with (root/'build/SHA256SUMS').open('w') as f:
    for p in (root/'build').glob('*.iso'):
        with p.open('rb') as src: digest=hashlib.file_digest(src,'sha256').hexdigest()
        f.write(f'{digest}  {p.name}\n')
(root/'build/model-revision.txt').write_text(revision+'\n')
