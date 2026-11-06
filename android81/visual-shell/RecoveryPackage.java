package ro.leo.visual;
import android.content.*;import java.io.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.TimeUnit;
/** Pins match the deployed Leo Recovery 0.2 catalog. No command/BCB or partition writes. */
final class RecoveryPackage {
 static final String NAME="leo-fresh-0.14.0-dev-20261027-232615.zip";
 static final long BYTES=351257972L;
 static final String SHA="5f65e7e7b0c8ce0ce8b24cf5ff9617e3d078a2a76172c4f4df7628063bdd9478";
 static final String RECOVERY="28f9eb399e9479e135328e795ca7dd6b3a5e6feb530bc3a795cd78dab157b612";
 static boolean accepts(long size,String digest){return size==BYTES&&SHA.equals(digest);}
 static boolean phone(String cmdline){return Arrays.asList(cmdline.trim().split("\\s+")).contains("androidboot.serialno=695d");}
 static void verify(File file)throws IOException{if(!file.isFile()||Files.isSymbolicLink(file.toPath())||file.length()!=BYTES)throw new IOException("Unapproved ROM size or file");if(!accepts(file.length(),FileTransfers.digest(file)))throw new IOException("ROM SHA-256 does not match the recovery catalog");}
 static boolean onCard(Context c,File file)throws IOException{for(Map.Entry<String,File> root:LeoFileProvider.roots(c).entrySet())if(root.getKey().startsWith("sd")&&new File(root.getValue(),"LeoRecovery/packages/"+NAME).getCanonicalFile().equals(file.getCanonicalFile()))return true;return false;}
 static String command(String text)throws Exception{Process p=new ProcessBuilder("/system/bin/leo-su","-c",text).redirectErrorStream(true).start();try{if(!p.waitFor(15,TimeUnit.SECONDS))throw new IOException("Root command timeout");ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] part=new byte[1024];int n;while((n=p.getInputStream().read(part))>0){if(b.size()+n>8192)throw new IOException("Unexpected output");b.write(part,0,n);}if(p.exitValue()!=0)throw new IOException("Root command rejected");return b.toString("UTF-8").trim();}finally{p.destroy();}}
 static boolean recoveryInstalled()throws Exception{return command("sha256sum /dev/block/bootdevice/by-name/recovery").startsWith(RECOVERY+" ");}
 static void reboot(Context c)throws Exception{if(((android.telecom.TelecomManager)c.getSystemService(Context.TELECOM_SERVICE)).isInCall())throw new IOException("Call active");String cmd=new String(java.nio.file.Files.readAllBytes(new File("/proc/cmdline").toPath()),"UTF-8");if(!phone(cmd)||!recoveryInstalled())throw new IOException("Unrecognized phone or recovery");command("setprop sys.powerctl reboot,recovery");}
}
