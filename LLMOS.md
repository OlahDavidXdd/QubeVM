# LLM.OS guest setup

Use the x86 QubeVM app on ARM64 Android. Create a VM named LLM.OS, select x86_64, 2048 MiB RAM initially (adjust in VM settings), 2 vCPUs, software TCG, and a CPU exposing AVX2, FMA, F16C and XSAVE (try max). Attach the original llm.asm bootable ISO as CD-ROM, select CD-ROM boot, and use VGA/QGE and PS/2 keyboard. No hard disk or networking is required. Do not select KVM on ARM64 for an x86 guest.

Build the ISO from https://github.com/alesha-pro/llm.asm using its documented model export and `make os-iso MODEL=models/qwen3-0.6b-q8_0.asmbin`. The model is separate from the kernel. CPU feature availability, boot and inference have not yet been verified on Android. Do not allocate all physical phone RAM to the VM.
