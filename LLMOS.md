# LLM.OS Android integration

Source: https://github.com/OlahDavidXdd/QubeVM/tree/llmos

This integration runs the original alesha-pro/llm.asm bare-metal guest (upstream repository: https://github.com/alesha-pro/llm.asm), rather than an Android inference implementation. QubeVM remains GPLv3; its COPYING and existing attribution are retained. llm.asm is MIT licensed; Qwen3-0.6B model licensing is provided by Qwen on Hugging Face.

## Build

Use JDK 17, SDK platform 36, build-tools 36.0.0 and NDK 30.0.16248370.
SDK platform 37 was not listed by sdkmanager on 2026-11-27; build-tools 37 existed.
QEMU 11.1.3 was downloaded and its SIMD decoder/helper implementation inspected.
All native archive URLs and SHA-256 digests are in ci/native-sources.json.

1. Install native prerequisites listed in `.github/workflows/llmos.yml`.
2. Run `python3 ci/fetch-native.py`.
3. Set `NDK_ROOT` to your installed NDK; set `BUILD_HOST=arm64-v8a` (or experimental `x86_64`), `BUILD_GUEST=x86_64-softmmu`, `USE_QEMU_VERSION=11.1.3`, `USE_KVM=false`.
4. Run `make -C qube-android-lib/src/main/jni qube`.
5. Run `./gradlew :qube-android-x86:assembleDebug -PqemuVersion=11.1.3`.
6. Run `python3 ci/verify-apk.py <apk> <abi>` and `apksigner verify <apk>`.

The LLM.OS APK workflow performs these steps and uploads a debug-signed test APK separately per ABI. Do not distribute the repository's shared release test key as a production signing identity. Native binaries are not built by Gradle automatically; an APK from Gradle alone cannot boot a guest. The CI payload check rejects such an APK.

## Guest media

The Build original llm.asm ISO workflow pins upstream commit `48feccbfd57cb669440f31ff64958d95bb8b848e`, downloads Qwen/Qwen3-0.6B safetensors at an immutable resolved revision, exports upstream Q8_0 `.asmbin` v2 with FP16 scales and builds a GRUB ISO. GGUF is not interchangeable with `.asmbin`. The ISO is roughly 614 MiB. Allow several GiB of temporary space during conversion and ISO construction.

Download and unzip the guest artifact. Enter the ISO's SHA-256 from SHA256SUMS in the app, then select Import ISO from device. Import streams to a private temporary file, verifies the digest, and replaces guest.iso only after verification. Custom bootable ISOs may also be imported. The APK pipeline bundles the successful guest ISO artifact (run 46035265522). On first launch the app copies it to private storage and verifies its checksum. Importing media is disabled while a VM is running. No internet is required by the bundled setup.

## Use

Select RAM (1024–16384 MB, default 2048), cores (1–8), CPU model (max recommended), and multithreaded TCG. Boot System opens QubeVM's actual QGE display with its existing keyboard, fullscreen and input controls. Stop and Restart use the existing VM controller. Stop the VM before changing hardware. Settings are applied to Machine and passed by VMExecutor to actual `-m`, `-smp`, `-cpu` and TCG thread arguments. No KVM/root is required. Advanced arguments can override normal settings and should be used carefully.

## Verification and limits

* Local original guest kernel assembled and linked successfully (24,252-byte Multiboot ELF32 container entering x86-64 long mode).
* Local Android Java/resource build succeeded after selecting a valid QEMU resource version. This local APK had no native payload and is not a usable emulator deliverable.
* Guest ISO/model workflow succeeded: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46035265522
* Linux software-TCG test passed with 2 vCPUs and 2048 MB: original guest reported 2/2 cores online, initialized the 614 MiB Q8_0 model and answered Hello. Total QEMU wall time was 121.81 s; 13-token prefill took 98.70 s (0.13 tok/s). The one-token decode counter excludes meaningful sustained decode work and is not a throughput benchmark. Evidence: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46035421112
* Native Android compilation is being exercised in Actions; consult the latest run for current results.
* No physical Android device is attached. Installation, graphics/input, native loading, inference, restart, thermal behavior and offline operation on Android are not yet verified.
* No Android boot-time or token throughput measurements are available.
* OSXSAVE is dynamically enabled by the guest through CR4/XCR0, not simply a static CPU flag. QEMU 11.1.3 has TCG AVX2/FMA/F16C and XSAVE implementations; end-to-end guest execution is the required validation.
* Original guest boot page tables identity-map 0–4 GiB. Selecting larger RAM does not remove guest addressability limits. 1 GiB may be insufficient for model plus arena. Default 2 GiB is recommended.
* RAM warnings are advisory; Android may still terminate the foreground VM under pressure. Resolution is fixed by the original VGA text guest. No fake progress or inference output is generated.
* Performance mode increases the TCG translation cache from 256 to 512 MiB. Dedicated serial prompt/copy/paste UI and thermal controls are not yet implemented. The original QGE console is retained.
