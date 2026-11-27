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
5. Prepare `guest-media` with an original ISO and SHA256SUMS, install xorriso, then run `python3 ci/prepare-bundled-media.py`.
6. Run `./gradlew :qube-android-x86:assembleDebug -PqemuVersion=11.1.3`.
7. Run `python3 ci/verify-apk.py <apk> <abi>` and `apksigner verify <apk>`.

The LLM.OS APK workflow performs these steps and uploads a debug-signed test APK separately per ABI. Do not distribute the repository's shared release test key as a production signing identity. Native binaries are not built by Gradle automatically; an APK from Gradle alone cannot boot a guest. The CI payload check rejects such an APK.

## Guest media

The Build original llm.asm ISO workflow pins upstream commit `48feccbfd57cb669440f31ff64958d95bb8b848e`, downloads Qwen/Qwen3-0.6B safetensors at an immutable resolved revision, exports upstream Q8_0 `.asmbin` v2 with FP16 scales and builds a GRUB ISO. GGUF is not interchangeable with `.asmbin`. The ISO is roughly 614 MiB. Allow several GiB of temporary space during conversion and ISO construction.

For optional ISO import, download and unzip the guest artifact. Enter the ISO's SHA-256 from SHA256SUMS in the app, then select Import ISO from device. Import streams to a private temporary file, verifies the digest, and replaces guest.iso only after verification. Custom bootable ISOs may also be imported. The APK pipeline verifies the successful original ISO artifact (run 46035265522), extracts its unchanged kernel ELF and ASML v2 model, and bundles those payloads with individual checksums. On first launch the app streams and verifies both into private storage. Default boot uses the upstream-supported QEMU Multiboot kernel/initrd loader to avoid reading the large model through emulated CD/IDE hardware. Imported ISOs still boot from the emulated CD drive. If the retained guest artifact expires, CI regenerates it from pinned upstream source using the tested guest build script. Importing media is disabled while a VM is running. No internet is required by the bundled setup.

## Use

Select RAM (1024–16384 MB, default 2048), cores (1–8), CPU model (max recommended), and multithreaded TCG. Boot System opens QubeVM's actual QGE display with its existing keyboard, fullscreen and input controls. Stop and Restart use the existing VM controller. QubeVM deliberately exits the app process after Stop to unload native global state; reopen LLM.OS to start a fresh session. Stop the VM before changing hardware. Settings are applied to Machine and passed by VMExecutor to actual `-m`, `-smp`, `-cpu` and TCG thread arguments. No KVM/root is required. Advanced arguments can override normal settings and should be used carefully.

## Verification and limits

* Local original guest kernel assembled and linked successfully (24,252-byte Multiboot ELF32 container entering x86-64 long mode).
* Local Android Java/resource build succeeded after selecting a valid QEMU resource version. This local APK had no native payload and is not a usable emulator deliverable.
* Guest ISO/model workflow succeeded: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46035265522
* Linux software-TCG test passed with 2 vCPUs and 2048 MB: original guest reported 2/2 cores online, initialized the 614 MiB Q8_0 model and answered Hello. Total QEMU wall time was 121.81 s; 13-token prefill took 98.70 s (0.13 tok/s). The one-token decode counter excludes meaningful sustained decode work and is not a throughput benchmark. Evidence: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46035421112
* Final native APK pipeline passed for ARM64 and x86-64, including bundled guest checksums, native payload and v2 signature verification: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46041069545
* ARM64 APK: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46041069545/artifacts/15221480249
* x86-64 APK: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46041069545/artifacts/15219942729
* Android 15 x86-64 ISO boot, native loading, model initialization, actual VGA display, keyboard commands and generated AI output were verified: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46040660062. The unmodified guest answered `Hi` with `Hello`. ISO model readiness polling took 253 s after fullscreen onboarding; first generated output was observed within a further 222 s (10 s polling granularity). These are slow software-emulation measurements, not sustained token throughput.
* Direct Multiboot offline boot, UI-selected 1536 MB RAM, two vCPUs, actual keyboard commands and completed two-token inference passed: https://github.com/OlahDavidXdd/QubeVM/actions/runs/46043015101. The guest reported 1535 MiB and 2/2 cores and generated `Hello!`. Model readiness was observed on the first poll after onboarding; completed two-token inference took 547 s of test wall time (10 s polling granularity). This is not a sustained throughput benchmark.
* No physical ARM64 device is attached. Restart, 1 GiB context fallback, thermal behavior and sustained Android throughput remain under verification or unmeasured.
* Sustained Android token throughput has not been benchmarked; the readiness and first-output observations above are coarse integration-test timings.
* OSXSAVE is dynamically enabled by the guest through CR4/XCR0, not simply a static CPU flag. QEMU 11.1.3 has TCG AVX2/FMA/F16C and XSAVE implementations; end-to-end guest execution is the required validation.
* Original guest boot page tables identity-map 0–4 GiB. Selecting larger RAM does not remove guest addressability limits. For the bundled original OS, allocations below 1536 MB use the original `ctx=512` boot option to fit the model and KV arena; larger allocations retain the original 2048-token default. Default 2 GiB is recommended.
* RAM warnings are advisory; Android may still terminate the foreground VM under pressure. Resolution is fixed by the original VGA text guest. No fake progress or inference output is generated.
* Performance mode increases the TCG translation cache from 256 to 512 MiB. Actual COM1 output can be opened and copied from the dashboard. Prompt entry and clipboard text input use the original QGE console. Thermal controls are not implemented.

## Install the APK

Download the artifact matching your Android host CPU, unzip it, and install the APK (Android 11/API 30 or newer). Allow installation from the download app if Android asks. These are debug test builds; uninstall an older differently signed QubeVM/LLM.OS build if Android reports a signature conflict. First launch copies and verifies the original kernel and model; allow roughly 1.3 GiB for APK plus private boot media. After boot media shows ready, select hardware and press Boot System. QGE displays the real VGA console; use its keyboard/Send Text controls to enter prompts. The dashboard's Guest serial log/Copy button reads actual COM1 output.

Recorded Android ISO-test evidence: [raw guest serial output](docs/testing/android-iso-serial.log) and [actual guest display](docs/testing/android-iso-inference.png). The screenshot comes directly from the running Android emulator, not a terminal mockup.
