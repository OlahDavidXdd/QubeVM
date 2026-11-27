package com.qubevm.main;

import android.os.Bundle;

import com.max2idea.android.qube.log.Logger;
import com.max2idea.android.qube.main.Config;
import com.max2idea.android.qube.main.QubeActivity;
import com.max2idea.android.qube.main.QubeApplication;

public class QubeEmuActivity extends QubeActivity {
    java.util.function.Consumer<android.net.Uri> isoImported;
    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request,result,data);
        if(request==6101 && result==RESULT_OK && data!=null && isoImported!=null) isoImported.accept(data.getData());
    }


    @Override
    public void onCreate(Bundle bundle) {
        QubeApplication.arch = Config.Arch.x86_64;
        Config.clientClass = this.getClass();
        Config.enableKVM = false;
        // Enable MTTCG only on 64-bit hosts when it is enabled globally.
        if (QubeApplication.isHost64Bit() && Config.enableMTTCG) {
            Config.enableMTTCG = true;
        } else {
            Config.enableMTTCG = false;
        }
        super.onCreate(bundle);
        Logger.setupLogFile("/qube/qube-x86-log.txt");
        LlmDashboard.show(this);
    }

    @Override
    protected void loadQEMULib() {
        try {
            System.loadLibrary("qemu-system-i386");
        } catch (Error ex) {
            System.loadLibrary("qemu-system-x86_64");
        }
    }
}
