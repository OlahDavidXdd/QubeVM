package com.qubevm.main;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;

/** Installs verified, unmodified upstream kernel/model payloads with bounded memory. */
final class BundledGuest {
    static File directory(Context c) { return new File(c.getFilesDir(), "llmos"); }
    static File kernel(Context c) { return new File(directory(c), "kernel.elf"); }
    static File model(Context c) { return new File(directory(c), "model.asmbin"); }
    static boolean ready(Context c) {
        return kernel(c).isFile() && model(c).isFile() && new File(directory(c), "ready").isFile();
    }
    static void install(Context c) throws Exception {
        File dir=directory(c);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create guest directory");
        new File(dir,"ready").delete();
        try(BufferedReader manifest=new BufferedReader(new InputStreamReader(c.getAssets().open("llmos/SHA256SUMS"),java.nio.charset.StandardCharsets.UTF_8))) {
            String line; int installed=0;
            while((line=manifest.readLine())!=null) {
                String[] parts=line.trim().split("\\s+");
                if(parts.length!=2 || !parts[0].matches("[a-fA-F0-9]{64}") || !(parts[1].equals("kernel.elf") || parts[1].equals("model.asmbin")))
                    throw new IOException("Invalid bundled media manifest");
                File tmp=new File(dir,parts[1]+".partial");
                try {
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");
                    try(InputStream in=c.getAssets().open("llmos/"+parts[1]);OutputStream out=new FileOutputStream(tmp)) {
                        byte[] b=new byte[1048576];int n;
                        while((n=in.read(b))!=-1){out.write(b,0,n);digest.update(b,0,n);}
                    }
                    StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format("%02x",b&255));
                    if(!actual.toString().equalsIgnoreCase(parts[0]))throw new IOException("Bundled "+parts[1]+" checksum mismatch");
                    Files.move(tmp.toPath(),new File(dir,parts[1]).toPath(),StandardCopyOption.REPLACE_EXISTING);
                    installed++;
                } finally { tmp.delete(); }
            }
            if(installed!=2)throw new IOException("Incomplete bundled media manifest");
        }
        try(FileOutputStream done=new FileOutputStream(new File(dir,"ready"))){done.write(1);}
    }
    private BundledGuest() {}
}
