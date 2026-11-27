package com.max2idea.android.qube.machine;

/** Applies real QEMU configuration through the existing persistent machine model. */
public final class LlmProfile {
    public static void apply(String iso, int ram, int cores, String cpu, boolean multi, boolean performance, String extra) {
        if (ram < 1024 || ram > 16384 || cores < 1 || cores > 8)
            throw new IllegalArgumentException("RAM or CPU allocation outside supported range");
        MachineController c = MachineController.getInstance();
        if (c.isRunning()) throw new IllegalStateException("Stop the system before changing hardware");
        if (c.getStoredMachines().contains("LLM.OS")) c.setStoredMachine("LLM.OS");
        else c.createVM("LLM.OS");
        Machine m = c.getMachine();
        m.setMemory(ram); m.setCpuNum(cores); m.setCpu(cpu);
        m.setEnableKVM(0); m.setEnableMTTCG(multi ? 1 : 0);
        m.setTcgBuffer(performance ? 512 : 256);
        m.setRenderer(0); m.setVga("std"); m.setMachineType("pc");
        java.io.File original = new java.io.File(com.max2idea.android.qube.main.QubeApplication.getInstance().getFilesDir(), "llmos/kernel.elf");
        if (iso.equals(original.getPath())) {
            m.setEnableCDROM(false); m.setCdImagePath(null); m.setBootDevice("Default");
            m.setKernel(iso); m.setInitRd(new java.io.File(original.getParentFile(), "model.asmbin").getPath());
            m.setAppend(ram < 1536 ? "ctx=512" : "");
        } else {
            m.setKernel(null); m.setInitRd(null); m.setAppend(null);
            m.setEnableCDROM(true); m.setCdImagePath(iso); m.setBootDevice("CDROM");
        }
        m.setNetwork(null); m.setSoundCard(null); m.setExtraParams("-serial file:" + com.max2idea.android.qube.main.QubeApplication.getInstance().getFilesDir() + "/guest-serial.log " + extra);
    }
    private LlmProfile() {}
}
