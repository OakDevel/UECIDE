package org.uecide;

import java.util.*;
import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.security.MessageDigest;

public class PackageAnalyser extends JDialog {

    JScrollPane scroll;
    //JTextPane text;
    Console text;

    public void openWindow(JDialog pm) {
        setModalityType(JDialog.ModalityType.APPLICATION_MODAL);
        setSize(new Dimension(700, 600));
        setTitle("Package Analyser");
        setLocationRelativeTo(pm);
        
//        text = new JTextPane();
        text = new Console();
        scroll = new JScrollPane(text);

        add(scroll);

        Thread thr = new Thread(new Runnable() {
            public void run() {
                analysePackages();
            }
        });
        thr.start();

        setVisible(true);
    }

    public void analysePackages() {

        File dd = Base.getDataFolder();

        File aptFolder = new File(dd, "apt");
        if (!aptFolder.exists()) {
            aptFolder.mkdirs();
        }

        File dbFolder = new File(aptFolder, "db");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }

        File sourcesFile = new File(dbFolder, "sources.db");
        if  (!sourcesFile.exists()) {
            try {
                PrintWriter pw = new PrintWriter(sourcesFile);
                int i = 0;
                String key = "repository." + i;
                while (Preferences.get(key) != null) {
                    String repo = Preferences.get(key);
                    String parts[] = repo.split("::");
                    String url = parts[0];
                    String codename = parts[1];
                    String sections = parts[2];
                    String sects[] = sections.split(",");

                    pw.print("deb " + url + " " + codename);
                    for (String s : sects) {
                        pw.print(" " + s);
                    }
                    pw.println();

                    i++;
                    key = "repository." + i;
                }
                pw.close();
            } catch (Exception e) {
                Base.error(e);
            }
        }

        text.append("Package Analysis Started\n\n", Console.BODY);
        APT apt = new APT(dd);
        Collection<Package> packages = apt.getInstalledPackages();
        for (Package p : packages) {
            analysePackage(p);
        }
        text.append("\nAnalysis finished\n", Console.BODY);
    }

    public void analysePackage(Package p) {
        text.append("[....] " + p.getName(), Console.BODY);

        File dd = Base.getDataFolder();

        File aptFolder = new File(dd, "apt");
        File dbFolder = new File(aptFolder, "db");
        File pkgsFolder = new File(dbFolder, "packages");
        File pkgFolder = new File(pkgsFolder, p.getName());
        File MD5 = new File(pkgFolder, "md5sums");

        if (!MD5.exists()) {
            text.removeLastLine();
            text.append("\n[", Console.BODY);
            text.append("FAIL", Console.YELLOW);
            text.append("] " + p.getName() + ": No MD5 Checksums Available\n", Console.BODY);
            return;
        }

        ArrayList<String> missingFiles = new ArrayList<String>();
        ArrayList<String> changedFiles = new ArrayList<String>();

        String list = Base.getFileAsString(MD5);
        String[] lines = list.split("\n");
        for (String line : lines) {
            String[] bits = line.split("\\s+", 2);
            File testFile = new File(dd, bits[1]);
            if (!testFile.exists()) {
                missingFiles.add(bits[1]);
                continue;
            }

            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                FileInputStream fis = new FileInputStream(testFile);
                byte[] dataBytes = new byte[1024];
                int nread = 0;
                while ((nread = fis.read(dataBytes)) != -1) {
                    md.update(dataBytes, 0, nread);
                }
                byte[] mdbytes = md.digest();

                StringBuffer hexString = new StringBuffer();
                for (int i=0;i<mdbytes.length;i++) {
                    hexString.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
                }

                if (!hexString.toString().equalsIgnoreCase(bits[0])) {
                    changedFiles.add(bits[1]);
                    continue;
                }
            } catch (Exception ignored) {
            }
        }

        if (missingFiles.size() == 0 && changedFiles.size() == 0) {
            text.removeLastLine();
            text.append("\n[", Console.BODY);
            text.append("PASS", Console.GREEN);
            text.append("] " + p.getName() + "\n", Console.BODY);
            return;
        }

        text.removeLastLine();
        text.append("\n[", Console.BODY);
        text.append("FAIL", Console.RED);
        text.append("] " + p.getName() + "\n", Console.BODY);

        if (missingFiles.size() > 0) {
            text.append("\n", Console.BODY);
            text.append("Missing Files\n", Console.BODY);
            text.append("=============\n", Console.BODY);
            for (String f : missingFiles) {
                text.append(f + "\n", Console.BODY);
            }
        }

        if (changedFiles.size() > 0) {
            text.append("\n", Console.BODY);
            text.append("Altered Files\n", Console.BODY);
            text.append("=============\n", Console.BODY);
            for (String f : changedFiles) {
                text.append(f + "\n", Console.BODY);
            }
        }

        text.append("\n", Console.BODY);
    }

    public void appendText(final String t) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                String old = text.getText();
                text.setText(old + t);
            }
        });
    }
}
