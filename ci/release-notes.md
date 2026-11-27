Boot and interact with the original **llm.asm bare-metal Qwen3-0.6B OS** inside embedded QEMU 11.1.3 TCG on Android. The unchanged kernel and model are included and installed with checksum verification; normal setup works offline. No root or manual QEMU commands are required.

Download the APK matching your device CPU. ARM64 is appropriate for most Android phones; x86-64 is for compatible devices/emulators. Android 11+ is required. Allow approximately 1.3 GiB for the installed app and private model files, plus downloaded files. Wait for media readiness, select RAM/CPU allocation and press **Boot System**.

These are **debug-signed preview builds**, not production-signed builds. An older differently signed QubeVM/LLM.OS installation may need to be uninstalled first. SHA256SUMS accompanies the APKs.

Verified on Android 15 x86-64: original OS boot, real VGA display and keyboard input, completed two-token inference, offline operation, actual RAM/CPU allocation, warm restart, clean stop, configuration persistence, and a new boot at 1 GiB using the original ctx=512 option. Native APK compilation, packaged libraries and signatures passed for both ABIs.

Limitations:
- Physical ARM64 runtime remains untested.
- TCG inference is slow. The nested Android CI two-core/two-token test took 547 seconds; this is not a sustained-throughput benchmark.
- The original guest maps the first 4 GiB. Larger allocations are intended for compatible imported ISOs and trigger a warning.
- Stop deliberately exits the app process to unload QEMU native state. Reopen the app for a fresh session.
- Thermal behavior and CPU counts 3–8 have not been runtime-tested.

[Build and installation guide](https://github.com/OlahDavidXdd/QubeVM/blob/llmos/LLMOS.md) · [Verified APK build](https://github.com/OlahDavidXdd/QubeVM/actions/runs/46044960815) · [Completed offline inference](https://github.com/OlahDavidXdd/QubeVM/actions/runs/46043015101) · [Latest APK lifecycle and 1 GiB recovery test](https://github.com/OlahDavidXdd/QubeVM/actions/runs/46045767536)

APKs are the unchanged verified artifacts from build commit `435b1a6539c8313c13abc2335ec7ab5d3ce57584`. The release tag additionally includes test evidence, documentation and publishing automation; the application and native source are unchanged. QubeVM, QEMU, llm.asm and Qwen license notices are included in the app/source.
