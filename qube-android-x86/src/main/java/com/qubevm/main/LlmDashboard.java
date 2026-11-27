package com.qubevm.main;

import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Color;
import android.widget.*;
import android.text.InputType;
import com.max2idea.android.qube.machine.*;
import java.io.File;

/** Thin UI over QubeVM's real VM controller and QGE guest display. */
final class LlmDashboard {
    static void show(QubeEmuActivity a) {
        android.content.SharedPreferences prefs = a.getSharedPreferences("llmos", 0);
        ScrollView scroll = new ScrollView(a);
        LinearLayout panel = new LinearLayout(a); panel.setOrientation(1); panel.setPadding(28,48,28,28);
        panel.setBackgroundColor(Color.rgb(8,12,11)); scroll.addView(panel);
        java.util.function.Consumer<String> label = s -> {
            TextView t = new TextView(a); t.setText(s); t.setTextColor(0xff34d399);
            t.setTextSize(18); t.setPadding(0,14,0,14); panel.addView(t);
        };
        label.accept("LLM.OS  /  BARE METAL");
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        label.accept("Physical RAM: " + info.totalMem/1048576 + " MB • Available: " + info.availMem/1048576 + " MB");
        label.accept("Original llm.asm • Qwen3-0.6B Q8_0 • Offline after setup");
        EditText iso = new EditText(a); iso.setText(prefs.getString("iso", "")); iso.setTextColor(-1);
        iso.setHint("App-private ISO path"); panel.addView(iso);
        File bundled = new File(a.getFilesDir(),"guest.iso");
        if (!bundled.exists() && iso.getText().length()==0) {
            label.accept("Preparing bundled original OS and Qwen model…");
            new Thread(() -> {
                File tmp = new File(a.getFilesDir(),"bundled.partial");
                try {
                    String expected;
                    try(java.io.InputStream sums=a.getAssets().open("llmos/SHA256SUMS")) {
                        expected=new java.io.BufferedReader(new java.io.InputStreamReader(sums,java.nio.charset.StandardCharsets.UTF_8)).readLine().trim().split(" ")[0];
                    }
                    java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
                    try(java.io.InputStream in=a.getAssets().open("llmos/guest.iso"); java.io.OutputStream out=new java.io.FileOutputStream(tmp)) {
                        byte[] b=new byte[1048576];int n;while((n=in.read(b))!=-1){out.write(b,0,n);md.update(b,0,n);}
                    }
                    StringBuilder actual=new StringBuilder();for(byte b:md.digest())actual.append(String.format("%02x",b&255));
                    if(!expected.equalsIgnoreCase(actual.toString()))throw new java.io.IOException("Bundled ISO checksum mismatch");
                    java.nio.file.Files.move(tmp.toPath(),bundled.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    prefs.edit().putString("iso",bundled.getPath()).apply();
                    a.runOnUiThread(()->iso.setText(bundled.getPath()));
                }catch(Exception e){tmp.delete();a.runOnUiThread(()->Toast.makeText(a,"Guest setup: "+e.getMessage()+". Import ISO to continue.",1).show());}
            }).start();
        }
        EditText hash = new EditText(a); hash.setTextColor(-1); hash.setHint("Expected ISO SHA-256 (required for import)"); panel.addView(hash);
        Button imp = new Button(a); imp.setText("Import ISO from device"); panel.addView(imp);
        imp.setOnClickListener(v -> {
            if (MachineController.getInstance().isRunning()) { Toast.makeText(a,"Stop the system before importing media",1).show(); return; }
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            a.isoImported = uri -> {
                if (MachineController.getInstance().isRunning()) return;
                String expected = hash.getText().toString().trim();
                if (!expected.matches("[a-fA-F0-9]{64}")) { Toast.makeText(a,"Enter the published SHA-256 first",1).show(); return; }
                imp.setEnabled(false); imp.setText("Verifying and copying…");
                new Thread(() -> {
                    File tmp = new File(a.getFilesDir(),"guest.iso.partial");
                    try {
                        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                        try (java.io.InputStream in = a.getContentResolver().openInputStream(uri);
                             java.io.OutputStream out = new java.io.FileOutputStream(tmp)) {
                            byte[] b = new byte[1024*1024]; int n;
                            while ((n=in.read(b))!=-1) { out.write(b,0,n); md.update(b,0,n); }
                        }
                        StringBuilder actual = new StringBuilder(); for(byte b:md.digest()) actual.append(String.format("%02x",b&255));
                        if (!actual.toString().equalsIgnoreCase(expected)) throw new java.io.IOException("Checksum mismatch");
                        File dest = new File(a.getFilesDir(),"guest.iso");
                        java.nio.file.Files.move(tmp.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        prefs.edit().putString("iso",dest.getPath()).apply();
                        a.runOnUiThread(() -> iso.setText(dest.getPath()));
                    } catch(Exception e) { tmp.delete(); a.runOnUiThread(() -> Toast.makeText(a,e.toString(),1).show()); }
                    finally { a.runOnUiThread(() -> { imp.setEnabled(true); imp.setText("Import ISO from device"); }); }
                }).start();
            };
            a.startActivityForResult(i, 6101);
        });
        label.accept("RAM allocation (MB)");
        EditText ram = new EditText(a); ram.setTextColor(-1); ram.setInputType(InputType.TYPE_CLASS_NUMBER);
        ram.setText(""+prefs.getInt("ram",2048)); panel.addView(ram);
        SeekBar slider = new SeekBar(a); slider.setMin(1024); slider.setMax(16384); slider.setProgress(prefs.getInt("ram",2048)); panel.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int n,boolean user){if(user)ram.setText(""+n);}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        LinearLayout presets = new LinearLayout(a); HorizontalScrollView ps = new HorizontalScrollView(a); ps.addView(presets);panel.addView(ps);
        for(int gb:new int[]{1,2,4,6,8,12,16}) { Button b=new Button(a);b.setText(gb+" GB");presets.addView(b);b.setOnClickListener(v->{ram.setText(""+(gb*1024));slider.setProgress(gb*1024);}); }
        label.accept("Virtual CPUs");
        Spinner cores=new Spinner(a); cores.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"1","2","3","4","5","6","7","8"})); cores.setSelection(prefs.getInt("cores",1)-1);panel.addView(cores);
        Spinner cpu=new Spinner(a);cpu.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"max","Haswell","Broadwell"}));cpu.setSelection(prefs.getInt("cpu",0));panel.addView(cpu);
        Switch multi=new Switch(a);multi.setText("Multithreaded TCG");multi.setTextColor(-1);multi.setChecked(prefs.getBoolean("multi",true));panel.addView(multi);
        EditText extra=new EditText(a);extra.setTextColor(-1);extra.setHint("Advanced QEMU arguments");extra.setText(prefs.getString("extra",""));panel.addView(extra);
        Switch performance=new Switch(a);performance.setText("Performance mode (512 MB translation cache)");performance.setTextColor(-1);performance.setChecked(prefs.getBoolean("performance",false));panel.addView(performance);
        TextView errors=new TextView(a);errors.setTextColor(0xffff8888);panel.addView(errors);
        TextView status=new TextView(a);status.setTextColor(0xff34d399);panel.addView(status);
        Button boot=new Button(a);boot.setText("Boot System / Open Console");panel.addView(boot);
        boot.setOnClickListener(v -> {
            try {
                if(MachineController.getInstance().isRunning()){a.startVM();return;}
                int mb=Integer.parseInt(ram.getText().toString());
                if(!new File(iso.getText().toString()).isFile())throw new IllegalArgumentException("Import a bootable ISO first");
                Runnable launch=()->{
                    try {
                        a.setupNativeLibs();
                        LlmProfile.apply(iso.getText().toString(),mb,cores.getSelectedItemPosition()+1,cpu.getSelectedItem().toString(),multi.isChecked(),performance.isChecked(),extra.getText().toString());
                        prefs.edit().putInt("cpu",cpu.getSelectedItemPosition()).putBoolean("performance",performance.isChecked()).putInt("ram",mb).putInt("cores",cores.getSelectedItemPosition()+1).putBoolean("multi",multi.isChecked()).putString("extra",extra.getText().toString()).putString("iso",iso.getText().toString()).apply();
                        new File(MachineController.getInstance().getMachineSaveDir()).mkdirs();
                        com.max2idea.android.qube.files.FileInstaller.installFiles(a, false);
                        a.startVM();
                    }catch(Exception|LinkageError e){errors.setText(e.toString());}
                };
                ((ActivityManager)a.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
                if(mb*1048576L>info.availMem*0.7)new AlertDialog.Builder(a).setMessage("This allocation may exhaust Android memory and terminate the VM.").setPositiveButton("Boot",(d,w)->launch.run()).setNegativeButton("Cancel",null).show();else launch.run();
            }catch(Exception e){errors.setText(e.toString());}
        });
        for(MachineAction action:new MachineAction[]{MachineAction.STOP_VM,MachineAction.RESET_VM}){
            Button b=new Button(a);b.setText(action==MachineAction.STOP_VM?"Stop System":"Restart System");panel.addView(b);
            b.setOnClickListener(v->Dispatcher.getInstance().onAction(action,null));
        }
        new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){if(a.isDestroyed())return;status.setText("Emulator: "+MachineController.getInstance().getCurrStatus()+" • Guest progress appears in console");status.postDelayed(this,1500);}});
        a.setContentView(scroll);
    }
}
