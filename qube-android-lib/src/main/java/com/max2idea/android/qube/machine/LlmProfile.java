package com.max2idea.android.qube.machine;

/** Applies real QEMU configuration through the existing persistent machine model. */
public final class LlmProfile {
    public static void apply(String iso, int ram, int cores, String cpu, boolean multi, String extra) {
        if (ram < 1024 || ram > 16384 || cores < 1 || cores > 8)
            throw new IllegalArgumentException("RAM or CPU allocation outside supported range");
        MachineController c = MachineController.getInstance();
        if (c.isRunning()) throw new IllegalStateException("Stop the system before changing hardware");
        if (c.getStoredMachines().contains("LLM.OS")) c.setStoredMachine("LLM.OS");
        else c.createVM("LLM.OS");
        Machine m = c.getMachine();
        m.setMemory(ram); m.setCpuNum(cores); m.setCpu(cpu);
        m.setEnableKVM(0); m.setEnableMTTCG(multi ? 1 : 0);
        m.setRenderer(0); m.setVga("std"); m.setMachineType("pc");
        m.setEnableCDROM(true); m.setCdImagePath(iso); m.setBootDevice("CDROM");
        m.setNetwork(null); m.setSoundCard(null); m.setExtraParams(extra);
    }
    private LlmProfile() {}
}
