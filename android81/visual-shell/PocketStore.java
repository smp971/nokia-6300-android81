package ro.leo.visual;
import android.content.*;import android.net.Uri;import org.json.*;import java.io.*;import java.util.*;
/** Atomic, bounded app-owned data. User files and credentials are never silently overwritten. */
final class PocketStore {
 static File file(Context c,String name){if(!name.matches("[a-z0-9-]+"))throw new IllegalArgumentException();File d=new File(c.getFilesDir(),"pocket");if(!d.isDirectory()&&!d.mkdirs())throw new IllegalStateException("Cannot create data folder");return new File(d,name+".json");}
 static synchronized JSONObject read(Context c,String name)throws Exception{File f=file(c,name);if(!BackupEngine.atomicExists(f))return new JSONObject();byte[] b=BackupEngine.atomicBytes(f,4*1024*1024);return new JSONObject(new String(b,"UTF-8"));}
 static synchronized void write(Context c,String name,JSONObject value)throws Exception{byte[] b=value.toString().getBytes("UTF-8");if(b.length>4*1024*1024)throw new IOException("Data limit: 4 MiB");BackupEngine.write(file(c,name),b);}
 static File folder(Context c,String kind)throws IOException{if(!kind.matches("[A-Za-z]+"))throw new IOException();File d=new File(SaveLocations.selected(c),"LeoTools/"+kind);if(!d.isDirectory()&&!d.mkdirs())throw new IOException("Cannot create output folder");return d;}
 static File output(Context c,String kind,String name)throws IOException{String clean=name.replaceAll("[^A-Za-z0-9._-]","_");if(clean.length()>80)clean=clean.substring(0,80);return new File(folder(c,kind),System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,6)+"-"+clean);}
 static boolean readable(Context c,File f)throws IOException{if(!f.isFile()||java.nio.file.Files.isSymbolicLink(f.toPath()))return false;for(File root:LeoFileProvider.roots(c).values())if(LeoFileProvider.inside(root,f))return true;return false;}
 static byte[] bytes(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>max)throw new IOException("File exceeds limit");out.write(b,0,n);}return out.toByteArray();}
 static byte[] fileBytes(File f,int max)throws IOException{if(f.length()>max)throw new IOException("File exceeds import limit");try(InputStream in=new FileInputStream(f)){return bytes(in,max);}}
 static String text(File f,int max)throws IOException{try(InputStream in=new FileInputStream(f)){return new String(bytes(in,max),"UTF-8").replace("\uFEFF","");}}
 static String csv(String s){String v=s==null?"":s;if(!v.isEmpty()&&"=+-@".indexOf(v.charAt(0))>=0)v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}
 static File podcastFolder(Context c)throws IOException{File f="leo".equals(SaveLocations.key(c))?c.getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS):folder(c,"Podcasts");if(f==null||!f.isDirectory()&&!f.mkdirs())throw new IOException("Podcast storage unavailable");return f;}
 static String url(String raw)throws IOException{try{java.net.URI u=new java.net.URI(raw.trim());if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null||u.getUserInfo()!=null||raw.length()>2048)throw new IOException("Use an HTTP/HTTPS address without credentials");return u.toASCIIString();}catch(java.net.URISyntaxException e){throw new IOException("Invalid URL");}}
}
