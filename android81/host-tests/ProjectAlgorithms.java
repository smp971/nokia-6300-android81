package ro.leo.visual;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Android-free branches, run on Java 8 without a connected phone. */
public final class ProjectAlgorithms {
    static int count;
    static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
        count++;
    }
    public static void main(String[] args) throws Exception {
        check(Arrays.equals(HomeCards.order("3,3,-1,100,2"), new int[]{3,2,0,1}), "card order sanitized");
        check(Arrays.equals(HomeCards.order(""), new int[]{0,1,2,3}), "default card order retained");
        check(ContactExtras.monthDay("--02-29")[1] == 29, "yearless leap day accepted");
        check(ContactExtras.monthDay("2004-12-31")[0] == 11, "full birthday date accepted");
        boolean rejected = false;
        try { ContactExtras.monthDay("2026-02-29"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid non-leap date rejected");
        Calendar now = Calendar.getInstance();
        now.clear(); now.set(2026, 9, 21, 12, 0);
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(BirthdaySync.next(1, 29, now.getTimeInMillis()));
        check(next.get(Calendar.YEAR) == 2028 && next.get(Calendar.HOUR_OF_DAY) == 9, "next leap birthday at nine");
        File root = Files.createTempDirectory("leo-host-copy-").toFile();
        File source = new File(root, "demo.txt"), folder = new File(root, "destination");
        folder.mkdir();
        try {
            try (OutputStream out = new FileOutputStream(source)) { out.write(new byte[200000]); }
            final List<String> phases = new ArrayList<String>();
            File copied = FileTransfers.transfer(root, source, root, folder, false, new FileTransfers.Progress() {
                public void update(long done, long total, String phase) { phases.add(phase); }
            });
            check(FileTransfers.digest(source).equals(FileTransfers.digest(copied)), "copy verified by hash");
            check(phases.contains("Copiere") && phases.contains("Verificare SHA-256") && phases.contains("Verificat"), "progress phases emitted");
            rejected = false;
            try { FileTransfers.transfer(root, source, root, folder, false); } catch (IOException expected) { rejected = true; }
            check(rejected && source.exists(), "name collision preserves source");
            copied.delete();
            File moved = FileTransfers.transfer(root, source, root, folder, true);
            check(moved.isFile() && !source.exists(), "verified move removes source");
            moved.delete();
        } finally {
            for (File f : folder.listFiles()) f.delete();
            folder.delete(); source.delete(); root.delete();
        }
        System.out.println("PASS " + count + " host algorithm checks; no Android runtime used");
    }
}
