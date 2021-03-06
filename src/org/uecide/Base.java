/*
 * Copyright (c) 2015, Majenko Technologies
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 *
 * * Neither the name of Majenko Technologies nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.uecide;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.zip.*;
import java.util.jar.*;
import java.text.*;
import org.uecide.plugin.*;
import org.uecide.themes.*;

import javax.script.*;

import org.uecide.builtin.BuiltinCommand;
import org.uecide.varcmd.VariableCommand;

import java.lang.reflect.*;

import java.util.regex.*;


import java.security.*;

import javax.swing.*;
import javax.imageio.*;

import org.uecide.Compiler;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceInfo;

import org.reflections.*;
import org.reflections.util.*;
import org.reflections.scanners.*;

/*! The Base class provides the initial application
 *  startup code, parsing command line options, loading
 *  preferences, themes, etc, then scanning all the boards,
 *  cores, compilers etc.  It also provides a central storage
 *  location for application data, and a selection of useful
 *  helper functions.
 */
public class Base {

    public static HashMap<String, PropertyFile> iconSets = new HashMap<String, PropertyFile>();

    public static final String defaultIconSet = "Gnomic";

    public static String RELEASE = "release";

    public static String overrideSettingsFolder = null;

    public static ArrayList<Process> processes = new ArrayList<Process>();

    public static HashMap<String, JSPlugin> jsplugins;

    static Platform platform;

    static private boolean headless;

    // these are static because they're used by Sketch
    static private File examplesFolder;
    static private File librariesFolder;
    static private File toolsFolder;
    static private File hardwareFolder;
    public static ArrayList<File> MRUList;
    public static HashMap<File,Integer> MCUList;

    static HashSet<File> libraries;

    // maps imported packages to their library folder
    public static TreeMap<String, File> importToLibraryTable;

    // classpath for all known libraries for p5
    // (both those in the p5/libs folder and those with lib subfolders
    // found in the sketchbook)
    public static String librariesClassPath;

    public static Version systemVersion;

    public static TreeMap<String, Compiler> compilers;
    public static TreeMap<String, Board> boards;
    public static TreeMap<String, Core> cores;
    public static TreeMap<String, Programmer> programmers;
//    public static TreeMap<String, Plugin> plugins;
    public static TreeMap<String, Class<?>> plugins = new TreeMap<String, Class<?>>();
    public static ArrayList<Plugin> pluginInstances;
    static Splash splashScreen;

    public static CommandLine cli = new CommandLine();

    // Location for untitled items
    static File untitledFolder;

    public static PropertyFile preferences;
    public static PropertyFile session = new PropertyFile();
    public static Theme theme;

    public static PropertyFile preferencesTree = new PropertyFile();

    public static HashMap<Object, DiscoveredBoard> discoveredBoards = new HashMap<Object, DiscoveredBoard>();

    public static boolean onlineMode = true;

    public static ArrayList<CommunicationPort> communicationPorts = new ArrayList<CommunicationPort>();

    public static TreeMap<String, Class<?>> lookAndFeels = new TreeMap<String, Class<?>>();

    public static TreeMap<String, PropertyFile> themes = new TreeMap<String, PropertyFile>();

    public static I18N i18n = new I18N("Core");

    /*! Get a Board from the internal boards list by its short name. */
    public static Board getBoard(String name) {
        return boards.get(name);
    }

    /*! Get a Core from the internal cores list by its short name. */
    public static Core getCore(String name) {
        return cores.get(name);
    }

    /*! Get a Compiler from the internal compilers list by its short name. */
    public static Compiler getCompiler(String name) {
        return compilers.get(name);
    }

    /*! The main execution entry function. It just creates an instance of this
     *  object and passes the command line arguments.
     */
    public static void main(String args[]) {
        new Base(args);
    }

    /*! Return a File representing the location of the JAR file the
     *  application was loaded from. */
    public static File getJarLocation() {
        return getJarLocation(Base.class);
    }

    public static File getJarLocation(Class<?> cl) {
        try {
            ProtectionDomain pd = cl.getProtectionDomain();
            if (pd == null) {
                return null;
            }

            CodeSource cs = pd.getCodeSource();
            if (cs == null) {
                return null;
            }

            URL sl = cs.getLocation();
            if (sl == null) {
                return null;
            }
            
            URI ui = sl.toURI();
            return new File(ui);
        } catch(Exception e) {
            Base.error(e);
        }

        return new File("/");
    }


    public static HashMap<String, String> settings = new HashMap<String, String>();


    public static boolean autoCompile = false;
    public static boolean autoProgram = false;
    public static String presetPort = null;
    public static String presetBoard = null;
    public static String presetCompiler = null;
    public static String presetCore = null;
    public static String presetProgrammer = null;

    public static boolean cleanBuild = false;
    public static boolean purgeCache = false;

    public static boolean extraDebug = false;

    /*! The constructor is the main execution routine. */
    public Base(String[] args) {



        cli.addParameter("debug",               "",         Boolean.class,  "cli.help.debug");
        cli.addParameter("verbose",             "",         Boolean.class,  "cli.help.verbose");
        cli.addParameter("exceptions",          "",         Boolean.class,  "cli.help.exceptions");
        cli.addParameter("headless",            "",         Boolean.class,  "cli.help.headless");
        cli.addParameter("datadir",             "location", String.class,   "cli.help.datadir");
        cli.addParameter("last-sketch",         "",         Boolean.class,  "cli.help.last-sketch");
        cli.addParameter("clean",               "",         Boolean.class,  "cli.help.clean");
        cli.addParameter("compile",             "",         Boolean.class,  "cli.help.compile");
        cli.addParameter("upload",              "",         Boolean.class,  "cli.help.upload");
        cli.addParameter("board",               "name",     String.class,   "cli.help.board");
        cli.addParameter("core",                "name",     String.class,   "cli.help.core");
        cli.addParameter("compiler",            "name",     String.class,   "cli.help.compiler");
        cli.addParameter("port",                "name",     String.class,   "cli.help.port");
        cli.addParameter("programmer",          "name",     String.class,   "cli.help.programmer");
        cli.addParameter("purge",               "",         Boolean.class,  "cli.help.purge");
        cli.addParameter("help",                "",         Boolean.class,  "cli.help.help");

        cli.addParameter("update",              "",         Boolean.class,  "cli.help.update");
        cli.addParameter("install",             "package",  String.class,   "cli.help.install");
        cli.addParameter("remove",              "package",  String.class,   "cli.help.remove");
        cli.addParameter("remove-all",          "",         Boolean.class,  "cli.help.remove-all");
        cli.addParameter("upgrade",             "",         Boolean.class,  "cli.help.upgrade");
        cli.addParameter("search",              "term",     String.class,   "cli.help.search");
        cli.addParameter("list",                "",         Boolean.class,  "cli.help.list");
        cli.addParameter("section",             "name",     String.class,   "cli.help.section");
        cli.addParameter("group",               "name",     String.class,   "cli.help.group");
        cli.addParameter("subgroup",            "name",     String.class,   "cli.help.subgroup");
        cli.addParameter("family",              "name",     String.class,   "cli.help.family");
        cli.addParameter("force",               "",         Boolean.class,  "cli.help.force");

        cli.addParameter("mkmf",                "",         Boolean.class,  "cli.help.mkmf");
        cli.addParameter("force-local-build",   "",         Boolean.class,  "cli.help.force-local-build");
        cli.addParameter("force-save-hex",      "",         Boolean.class,  "cli.help.force-save-hex");
        cli.addParameter("force-join-files",    "",         Boolean.class,  "cli.help.force-join-files");
        cli.addParameter("online",              "",         Boolean.class,  "cli.help.online");
        cli.addParameter("offline",             "",         Boolean.class,  "cli.help.offline");

        cli.addParameter("version",             "",         Boolean.class,  "cli.help.version");

        cli.addParameter("cli",                 "",         Boolean.class,  "cli.help.cli");

        cli.addParameter("preferences",         "",         Boolean.class,  "cli.help.preferences");
        cli.addParameter("quiet",               "",         Boolean.class,  "cli.help.quiet");

        cli.addParameter("locale",              "name",     String.class,   "cli.help.locale");

        String[] argv = cli.process(args);

        Authenticator.setDefault(new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                System.err.println(getRequestingURL());
                Pattern pat = Pattern.compile(":\\/\\/([a-zA-Z0-9]+):([a-zA-Z0-9]+)@");
                Matcher m = pat.matcher(getRequestingURL().toString());
                if (m.find()) {
                    System.err.println("[" + m.group(1) + "][" + m.group(2) + "]");
                    return new PasswordAuthentication(m.group(1), m.group(2).toCharArray());
                }

                return null;
            }
        });

        i18n = new I18N("Core");

        if (cli.isSet("help")) {
            cli.help();
            System.exit(0);
        }

        headless = cli.isSet("headless");
        boolean loadLastSketch = cli.isSet("last-sketch");

        boolean doExit = false;

        if (cli.isSet("mkmf")) {
            headless = true;
        }

        Debug.setVerbose(cli.isSet("verbose"));
        if (cli.isSet("debug")) {
            Debug.show();
        }

        overrideSettingsFolder = cli.getString("datadir");
        autoCompile = cli.isSet("compile");
        autoProgram = cli.isSet("upload");
        presetPort = cli.getString("port");
        presetBoard = cli.getString("board");
        presetCore = cli.getString("core");
        presetCompiler = cli.getString("compiler");
        presetProgrammer = cli.getString("programmer");
        purgeCache = cli.isSet("purge");
        cleanBuild = cli.isSet("clean");


        if(!cli.isSet("exceptions")) {
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    Base.broken(t, e);
                }
            });
        }



        if(isUnix()) {
            if((System.getenv("DISPLAY") == null) || (System.getenv("DISPLAY").equals(""))) {
                headless = true;
            }
        }

        PropertyFile versionInfo = new PropertyFile("/org/uecide/version.txt");

        RELEASE = versionInfo.get("Release");
        systemVersion = new Version(versionInfo.get("Version"));

        Debug.message("Version: " + systemVersion);

        if (cli.isSet("version")) {
            System.out.println(i18n.string("msg.version", systemVersion));
            System.exit(0);
        }

        // Get the initial basic theme data
        theme = new Theme("/org/uecide/config/theme.txt");
        theme.setPlatformAutoOverride(true);

        initPlatform();

        preferences = new PropertyFile(getDataFile("preferences.txt"), "/org/uecide/config/preferences.txt");
        preferences.setPlatformAutoOverride(true);

        platform.setSettingsFolderEnvironmentVariable();

        if (preferences.getBoolean("network.offline")) {
            setOfflineMode();
        }

        if (cli.isSet("online")) {
            setOnlineMode();
        }

        if (cli.isSet("offline")) {
            setOfflineMode();
        }
        
        if (cli.isSet("update")) {
            PluginManager pm = new PluginManager();
            APT apt = pm.getApt();
            apt.update();
            doExit = true;
        }

        if (cli.isSet("upgrade")) {
            PluginManager pm = new PluginManager();
            APT apt = pm.getApt();
            Package[] pl = apt.getUpgradeList();
            for (Package p : pl) {
                apt.upgradePackage(p);
            }
            doExit = true;
        }

        if (cli.isSet("install")) {
            PluginManager pm = new PluginManager();
            APT apt = pm.getApt();
            String packageName = cli.getString("install");
            if (packageName == null) {
                System.err.println(i18n.string("err.selpkginst"));
                doExit = true;
            }

            Package p = apt.getPackage(packageName);
            if (p == null) {
                System.err.println(i18n.string("err.pkgnotfound", packageName));
                System.err.println(i18n.string("msg.usesearch"));
                doExit = true;
            }

            System.out.println(i18n.string("msg.installing", p.getName()));
            apt.installPackage(p);
            System.out.println(i18n.string("msg.done"));
            doExit = true;
        }

        if (cli.isSet("remove-all")) {
            doExit = true;
            if (!cli.isSet("force")) {
                System.err.println(i18n.string("err.notremove"));
            } else {
                PluginManager pm = new PluginManager();
                APT apt = pm.getApt();
                for (Package p : apt.getInstalledPackages()) {
                    apt.uninstallPackage(p, true);
                }
                doExit = true;
            }
        }
            
        if (cli.isSet("remove")) {
            PluginManager pm = new PluginManager();
            APT apt = pm.getApt();
            String packageName = cli.getString("remove");
            if (packageName == null) {
                System.err.println(i18n.string("err.selpkguninst"));
                doExit = true;
            }

            Package p = apt.getPackage(packageName);
            if (p == null) {
                System.err.println(i18n.string("err.notfound", packageName));
                System.err.println(i18n.string("msg.usesearch"));
            } else {
                System.out.println(i18n.string("msg.uninstalling", p.getName()));
                apt.uninstallPackage(p, cli.isSet("force"));
            }
            doExit = true;
        }

        if (cli.isSet("list")) {
            PluginManager pm = new PluginManager();
            APT apt = pm.getApt();
            Package[] pkgs = apt.getPackages();
            String format = "%-50s %10s %10s %s";
            System.out.println(String.format(format, i18n.string("apt.list.package"), i18n.string("apt.list.installed"), i18n.string("apt.list.available"), ""));
            ArrayList<Package> out = new ArrayList<Package>();
            for (Package p : pkgs) {
                if (cli.isSet("section")) {
                    if (p.get("Section") == null) { continue; }
                    if (!p.get("Section").equals(cli.getString("section"))) { continue; }
                }
                if (cli.isSet("family")) {
                    if (p.get("Family") == null) { continue; }
                    if (!p.get("Family").equals(cli.getString("family"))) { continue; }
                }
                if (cli.isSet("group")) {
                    if (p.get("Group") == null) { continue; }
                    if (!p.get("Group").equals(cli.getString("group"))) { continue; }
                }
                if (cli.isSet("subgroup")) {
                    if (p.get("Subgroup") == null) { continue; }
                    if (!p.get("Subgroup").equals(cli.getString("subgroup"))) { continue; }
                }

                String name = p.getName();
                Package instPack = apt.getInstalledPackage(p.getName());
                Version avail = p.getVersion();
                Version inst = null;
                String msg = "";
                if (instPack != null) {
                    inst = instPack.getVersion();
                    if (avail.compareTo(inst) > 0) {
                        msg = i18n.string("apt.list.update");
                    }
                }
                System.out.println(String.format(format, name, inst == null ? "" : inst.toString(), avail.toString(), msg));
                System.out.println("  " + p.getDescriptionLineOne());

            }
            doExit = true;
        }
                
        if (cli.isSet("search")) {
            PluginManager pm = new PluginManager();
            APT apt = pm.getApt();
            Package[] pkgs = apt.getPackages();
            String format = "%-50s %10s %10s %s";
            System.out.println(String.format(format, i18n.string("apt.list.package"), i18n.string("apt.list.installed"), i18n.string("apt.list.available"), ""));
            ArrayList<Package> out = new ArrayList<Package>();
            String term = cli.getString("search").toLowerCase();
            for (Package p : pkgs) {
                if (cli.isSet("section")) {
                    if (p.get("Section") == null) { continue; }
                    if (!p.get("Section").equals(cli.getString("section"))) { continue; }
                }
                if (cli.isSet("family")) {
                    if (p.get("Family") == null) { continue; }
                    if (!p.get("Family").equals(cli.getString("family"))) { continue; }
                }
                if (cli.isSet("group")) {
                    if (p.get("Group") == null) { continue; }
                    if (!p.get("Group").equals(cli.getString("group"))) { continue; }
                }
                if (cli.isSet("subgroup")) {
                    if (p.get("Subgroup") == null) { continue; }
                    if (!p.get("Subgroup").equals(cli.getString("subgroup"))) { continue; }
                }

                String name = p.getName();
                Package instPack = apt.getInstalledPackage(p.getName());
                Version avail = p.getVersion();
                Version inst = null;
                String msg = "";
                if (instPack != null) {
                    inst = instPack.getVersion();
                    if (avail.compareTo(inst) > 0) {
                        msg = i18n.string("apt.list.update");
                    }
                }
                String comp = p.getName() + " " + p.getDescription();
                if (comp.toLowerCase().contains(term)) {
                    System.out.println(String.format(format, name, inst == null ? "" : inst.toString(), avail.toString(), msg));
                    System.out.println("  " + p.getDescriptionLineOne());
                }

            }
            doExit = true;
        }

        if (doExit) {
            System.exit(0);
        }

        if (cli.isSet("cli")) {
            headless = true;
            platform.init(this);
            compilers = new TreeMap<String, Compiler>();
            cores = new TreeMap<String, Core>();
            boards = new TreeMap<String, Board>();
            programmers = new TreeMap<String, Programmer>();
            plugins = new TreeMap<String, Class<?>>();
            lookAndFeels = new TreeMap<String, Class<?>>();
            pluginInstances = new ArrayList<Plugin>();

            Serial.updatePortList();

            System.out.print(i18n.string("msg.loading.compilers"));
            loadCompilers();
            System.out.println(i18n.string("msg.done"));
            System.out.print(i18n.string("msg.loading.cores"));
            loadCores();
            System.out.println(i18n.string("cli.msg.loading.done"));
            System.out.print(i18n.string("msg.loading.boards"));
            loadBoards();
            System.out.println(i18n.string("cli.msg.loading.done"));
            System.out.print(i18n.string("msg.loading.programmers"));
            loadProgrammers();
            System.out.println(i18n.string("msg.loading.done"));
            System.out.print(i18n.string("msg.loading.libraries"));
            gatherLibraries();
            System.out.println(i18n.string("msg.loading.done"));

            buildPreferencesTree();

            runInitScripts();

            InteractiveCLI icli = new InteractiveCLI(argv);
            icli.run();
            System.exit(0);
        }

        Debug.setLocation(new Point(preferences.getInteger("debug.window.x"), preferences.getInteger("debug.window.y")));
        Debug.setSize(new Dimension(preferences.getInteger("debug.window.width"), preferences.getInteger("debug.window.height")));

        // Now we reload the theme data with user overrides
        // (we didn't know where they were before)
        theme = new Theme(getDataFile("theme.txt"), "/org/uecide/config/theme.txt");
        theme.setPlatformAutoOverride(true);

        if(!headless) {
            splashScreen = new Splash();

//            if(RELEASE.equals("beta")) {
//                splashScreen.setBetaMessage("** BETA VERSION **");
//            }

            splashScreen.setMessage("Loading " + theme.get("product.cap") + "...", 10);
        }

        

        if (!headless) splashScreen.setMessage(i18n.string("splash.msg.packagemanager"), 15);
        initPackageManager();


        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        // Create a location for untitled sketches
        untitledFolder = createTempFolder("untitled");
        untitledFolder.deleteOnExit();

        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.application"), 20);

        platform.init(this);

        // Get paths for the libraries and examples in the Processing folder
        //String workingDirectory = System.getProperty("user.dir");

        examplesFolder = getContentFile("examples");
        toolsFolder = getContentFile("tools");

        // Get the sketchbook path, and make sure it's set properly
        String sketchbookPath = preferences.get("locations.sketchbook");

        // If no path is set, get the default sketchbook folder for this platform
        if(sketchbookPath == null) {
            File defaultFolder = getDefaultSketchbookFolder();
            preferences.set("locations.sketchbook", defaultFolder.getAbsolutePath());
            sketchbookPath = defaultFolder.getAbsolutePath();
        }

        File sketchbookFolder = new File(sketchbookPath);

        if(!sketchbookFolder.exists()) {
            sketchbookFolder.mkdirs();
        }

        compilers = new TreeMap<String, Compiler>();
        cores = new TreeMap<String, Core>();
        boards = new TreeMap<String, Board>();
        programmers = new TreeMap<String, Programmer>();
        plugins = new TreeMap<String, Class<?>>();
        lookAndFeels = new TreeMap<String, Class<?>>();
        pluginInstances = new ArrayList<Plugin>();

        Serial.updatePortList();
        Serial.fillExtraPorts();

        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.themes"), 25);


        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.compilers"), 30);

        loadCompilers();

        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.cores"), 40);

        loadCores();

        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.boards"), 50);

        loadBoards();

        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.programmers"), 55);

        loadProgrammers();

        if (cli.isSet("mkmf")) {
            for(int i = 0; i < argv.length; i++) {
                String path = argv[i];
                if (path.equals(".")) {
                    path = System.getProperty("user.dir");
                }
                Sketch s = new Sketch(path);
                s.loadConfig();
                if(presetPort != null) {
                    s.setDevice(presetPort);
                }

                if(presetBoard != null) {
                    s.setBoard(presetBoard);
                }

                if(presetCore != null) {
                    s.setCore(presetCore);
                }

                if(presetCompiler != null) {
                    s.setCompiler(presetCompiler);
                }

                if(presetProgrammer != null) {
                    s.setProgrammer(presetProgrammer);
                }

                if (purgeCache) {
                    s.purgeCache();
                }

                s.generateMakefile();
            }
            System.exit(0);
        }

        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.plugins"), 60);

        loadPlugins();

        loadIconSets();
        loadThemes();

        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.libraries"), 70);

        gatherLibraries();

        buildPreferencesTree();


        runInitScripts();

        initMRU();

        if(!headless) splashScreen.setMessage(i18n.string("splash.msg.editor"), 80);

        setLookAndFeel();

        if (cli.isSet("preferences")) {
            if(!headless) splashScreen.setMessage(i18n.string("splash.msg.complete"), 100);
            splashScreen.dispose();
            Preferences p = new Preferences(null);
            preferences.save();
            System.exit(0);
        }

        boolean opened = false;

        // Check if any files were passed in on the command line
        for(int i = 0; i < argv.length; i++) {
            String path = argv[i];

            // Fix a problem with systems that use a non-ASCII languages. Paths are
            // being passed in with 8.3 syntax, which makes the sketch loader code
            // unhappy, since the sketch folder naming doesn't match up correctly.
            // http://dev.processing.org/bugs/show_bug.cgi?id=1089
            if(isWindows()) {
                try {
                    File file = new File(argv[i]);
                    path = file.getCanonicalPath();
                } catch(IOException e) {
                    error(e);
                }
            }

            if (path.equals(".")) {
                path = System.getProperty("user.dir");
            }

            File p = new File(path);
            Debug.message("Loading sketch " + p);
            if (p.exists()) {
                opened = doOpenThings(p);
            }
        }

        if(loadLastSketch || Preferences.getBoolean("editor.save.loadlast")) {
            File lastFile = MRUList.get(0);

            if(lastFile != null) {
                opened = doOpenThings(lastFile);
            }
        }

        // Create a new empty window (will be replaced with any files to be opened)
        if(!opened && !headless) {
            handleNew();
        }

        if(!headless) {
            if(!headless) splashScreen.setMessage(i18n.string("splash.msg.complete"), 100);
            splashScreen.dispose();

            if (preferences.get("changelog.hide") != null) {
                Version vhidden = new Version(preferences.get("changelog.hide"));
                if (systemVersion.compareTo(vhidden) > 0) {
System.err.println("Showing changelog as there is a new version");
                    Changelog c = new Changelog(Editor.editorList.get(0));
                }
            } else {
System.err.println("Showing changelog as I don't know better");
                Changelog c = new Changelog(Editor.editorList.get(0));
            }


            synchronized (Editor.editorList) {
                if(boards.size() == 0) {

                    showWarning(
                        i18n.string("err.noboards.title"),
                        i18n.string("err.noboards.body"),
                        null
                    );
                    PluginManager pm = new PluginManager();
                    pm.openWindow(Editor.editorList.get(0), true);
                } else if(cores.size() == 0) {
                    showWarning(
                        i18n.string("err.nocores.title"),
                        i18n.string("err.nocores.body"),
                        null
                    );
                    PluginManager pm = new PluginManager();
                    pm.openWindow(Editor.editorList.get(0), true);
                } else if(compilers.size() == 0) {
                    showWarning(
                        i18n.string("err.nocompilers.title"),
                        i18n.string("err.nocompilers.body"),
                        null
                    );
                    PluginManager pm = new PluginManager();
                    pm.openWindow(Editor.editorList.get(0), true);
                }
            }
        }

        if(isTimeToCheckVersion()) {
            if(isNewVersionAvailable()) {
                if(headless) {
                    System.err.println(i18n.string("msg.version.available"));
                    System.err.println(i18n.string("msg.version.download", Base.theme.get("version." + RELEASE + ".download")));
                } else {
                    String[] options = {"Yes", "No"};
                    int n = JOptionPane.showOptionDialog(
                        null, 
                        i18n.string("msg.version.available.body"),
                        i18n.string("msg.version.available"),
                        JOptionPane.YES_NO_OPTION, 
                        JOptionPane.QUESTION_MESSAGE, 
                        null, 
                        options, 
                        options[0]
                    );

                    if(n == 0) {
                        openURL(Base.theme.get("version." + RELEASE + ".download"));
                    }
                }
            }
        }

        session.set("os.version", getOSVersion());

        if(headless) {
            System.exit(0);
        }

        Reflections mainReflections = new Reflections("");
        Set<Class<? extends Service>> serviceClasses = mainReflections.getSubTypesOf(Service.class);
        for (Class<? extends Service> c : serviceClasses) {
            try {
                Constructor<?> ctor = c.getConstructor();
                Service s = (Service)(ctor.newInstance());
                ServiceManager.addService(s);
            } catch (NoSuchMethodException ex) {
            } catch (InstantiationException ex) {
            } catch (IllegalAccessException ex) {
            } catch (InvocationTargetException ex) {
            }
        }
    }

    public static void addJarFile(File f) {
        try {
            File find = f;
            if (!find.exists()) {
                File[] pfs = getPluginsFolders();
                for (File pf : pfs) {
                    find = new File(pf, f.getPath());
                    if (find.exists()) {
                        break;
                    }
                }
            }
            if (!find.exists()) {
                error("File not found: " + f);
                return;
            }
            Base.addURL(f.toURI().toURL());
        } catch (Exception e) {
            error(e);
        }
    }
    
    /*! Attempt to add a jar file as a URL to the system class loader */
    @SuppressWarnings("unchecked")
    public static void addURL(URL u) {
        final Class[] parameters = new Class[]{URL.class}; 
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class sysclass = URLClassLoader.class;

        try {
            Method method = sysclass.getDeclaredMethod("addURL", parameters);
            method.setAccessible(true);
            method.invoke(sysloader, new Object[]{u});
        } catch (Exception ex) {
            ex.printStackTrace();
            Base.error(ex);
        }
    }

    /*! Open a sketch in a new Editor (if not running headless) and set up any preset values
     *  specified on the command line.
     */
    static boolean doOpenThings(File sketch) {
        Sketch s = null;
        Editor e = null;

        if(!headless) {
            e = createNewEditor(sketch.getAbsolutePath());

            if(e == null) {
                return false;
            }


            s = e.getSketch();
        } else {
            s = new Sketch(sketch);
            s.loadConfig();
        }

        if(presetPort != null) {
            s.setDevice(presetPort);
        }

        if(presetBoard != null) {
            s.setBoard(presetBoard);
        }

        if(presetCore != null) {
            s.setCore(presetCore);
        }

        if(presetCompiler != null) {
            s.setCompiler(presetCompiler);
        }

        if(presetProgrammer != null) {
            s.setProgrammer(presetProgrammer);
        }

        if (purgeCache) {
            s.purgeCache();
        }

        if (cleanBuild) {
            s.purgeBuildFiles();
        }

        if(e == null) {
            if(autoProgram) {
                if(!s.build()) {
                    System.exit(10);
                }
                if (!s.upload()) {
                    System.exit(10);
                }
            } else if(autoCompile) {
                if (!s.build()) {
                    System.exit(10);
                }
            }
        } else {
            if(autoProgram) {
                e.program();
            } else if(autoCompile) {
                e.compile();
            }
        }

        return true;
    }

    /*! Initialize any platform specific settings */
    static protected void initPlatform() {
        try {
            Class<?> platformClass = Class.forName("org.uecide.Platform");

            if(Base.isMacOS()) {
                platformClass = Class.forName("org.uecide.macosx.Platform");
            } else if(Base.isWindows()) {
                platformClass = Class.forName("org.uecide.windows.Platform");
            } else if(Base.isUnix()) {
                platformClass = Class.forName("org.uecide.unix.Platform");
            }

            platform = (Platform) platformClass.newInstance();
        } catch(Exception e) {
            Base.showError("Problem Setting the Platform",
                           "An unknown error occurred while trying to load\n" +
                           "platform-specific code for your machine.", e);
        }
    }

    /* Initialize the internal MRU list from the preferences set */
    public static void initMRU() {
        MRUList = new ArrayList<File>();
        MCUList = new HashMap<File,Integer>();

        for(int i = 0; i < 10; i++) {
            if(preferences.get("sketch.mru." + i) != null) {
                File f = new File(preferences.get("sketch.mru." + i));

                if(f.exists()) {
                    if(MRUList.indexOf(f) == -1) {
                        MRUList.add(f);
                    }
                }
            }
        }

        for(int i = 0; i < 10; i++) {
            if(preferences.get("sketch.mcu." + i) != null) {
                String[] mcuEntry = preferences.getArray("sketch.mcu." + i);

                int hits = 0;
                try {
                    hits = Integer.parseInt(mcuEntry[1]);
                } catch (Exception e) {
                }

                File f = new File(mcuEntry[0]);

                if(f.exists()) {
                    MCUList.put(f, hits);
                }
            }
        }
    }

    /*! Update the internal MRU list with a new File */
    public static void updateMRU(File f) {
        if (f == null) {
            return;
        }

        if(!f.isDirectory()) {
            f = f.getParentFile();
        }

        if ((MRUList == null) || (MCUList == null)) {
            initMRU();
        }

        MRUList.remove(f);
        MRUList.add(0, f);

        while(MRUList.size() > 10) {
            MRUList.remove(10);
        }

        int hits = 0;
        try {
            hits = MCUList.get(f);
        } catch (Exception e) {
        }
        hits++;
        MCUList.put(f, hits);

        for(int i = 0; i < 10; i++) {
            if(i < MRUList.size()) {
                preferences.set("sketch.mru." + i, MRUList.get(i).getAbsolutePath());
            } else {
                preferences.unset("sketch.mru." + i);
            }
        }

        while (MCUList.size() > 10) {
            int minHits = Integer.MAX_VALUE;

            for (Integer i : MCUList.values()) {
                if (i < minHits) {
                    minHits = i;
                }
            }

            for (File i : MCUList.keySet()) {
                if (MCUList.get(i) == minHits) {
                    MCUList.remove(i);
                    break;
                }
            }
        }

        int z = 0;
        for (File i : MCUList.keySet()) {
            preferences.set("sketch.mcu." + z, i.getAbsolutePath() + "::" + MCUList.get(i));
            z++;
        }


        preferences.saveDelay();
    }

    /*! Get the default board to use if no current board can be found */
    public static Board getDefaultBoard() {
        Board tb;
        String prefsBoard = preferences.get("board");
        String[] entries;

        if(boards.size() == 0) {
            return null;
        }

        tb = boards.get(prefsBoard);

        if(tb != null) {
            return tb;
        }

        entries = (String[]) boards.keySet().toArray(new String[0]);
        tb = boards.get(entries[0]);

        if(tb != null) {
            return tb;
        }

        return null;
    }

    /*! Load all the compilers into the main compilers list */
    public static void loadCompilers() {
        compilers.clear();
        File[] files = getCompilersFolders();
        for (File file : files) {
            loadCompilersFromFolder(file);
        }
    }

    /*! Load any compilers found in the specified folder */
    public static void loadCompilersFromFolder(File folder) {
        if(folder == null) {
            return;
        }

        if(!folder.exists()) {
            return;
        }

        if(!folder.isDirectory())
            return;

        String cl[] = folder.list();

        Debug.message("Loading compilers from " + folder.getAbsolutePath());

        for(int i = 0; i < cl.length; i++) {
            if(cl[i].charAt(0) == '.')
                continue;

            File cdir = new File(folder, cl[i]);

            if(cdir.isDirectory()) {
                File cfile = new File(cdir, "compiler.txt");

                if(cfile.exists()) {
                    Debug.message("    Loading compiler " + cfile.getAbsolutePath());
                    Compiler newCompiler = new Compiler(cdir);

                    if(newCompiler.isValid()) {
                        compilers.put(newCompiler.getName(), newCompiler);
                    } else {    
                        Debug.message("    ==> IS NOT VALID!!!");
                    }
                }
            }
        }
    }

    /*! Load all the cores into the main cores list */
    public static void loadCores() {
        cores.clear();
        File[] files = getCoresFolders();
        for(File file : files) {
            loadCoresFromFolder(file);
        }
    }

    /*! Load any cores found in the specified folder */
    public static void loadCoresFromFolder(File folder) {
        if(!folder.isDirectory())
            return;

        String cl[] = folder.list();
        Debug.message("Loading cores from " + folder.getAbsolutePath());

        for(int i = 0; i < cl.length; i++) {
            if(cl[i].charAt(0) == '.')
                continue;

            File cdir = new File(folder, cl[i]);

            if(cdir.isDirectory()) {
                File cfile = new File(cdir, "core.txt");

                if(cfile.exists()) {
                    Debug.message("    Loading core " + cfile.getAbsolutePath());
                    Core newCore = new Core(cdir);

                    if(newCore.isValid()) {
                        cores.put(newCore.getName(), newCore);
                    } else {    
                        Debug.message("    ==> IS NOT VALID!!!");
                    }
                }
            }
        }
    }

    public static void loadProgrammers() {
        programmers.clear();
        File[] files = getProgrammersFolders();
        for (File file : files) {
            loadProgrammersFromFolder(file);
        }
    }

    /*! Load any programmers found in the specified folder */
    public static void loadProgrammersFromFolder(File folder) {
        String bl[] = folder.list();

        if(bl == null) {
            return;
        }

        Debug.message("Loading programmers from " + folder.getAbsolutePath());

        for(int i = 0; i < bl.length; i++) {
            if(bl[i].charAt(0) == '.')
                continue;

            File bdir = new File(folder, bl[i]);

            if(bdir.isDirectory()) {
                File bfile = new File(bdir, "programmer.txt");

                if(bfile.exists()) {
                    Debug.message("    Loading programmer " + bfile.getAbsolutePath());
                    Programmer newProgrammer = new Programmer(bdir);

                    if(newProgrammer.isValid()) {
                        programmers.put(newProgrammer.getName(), newProgrammer);
                    }
                } else {
                    loadProgrammersFromFolder(bdir);
                }
            }
        }
    }

    /*! Load all the boards into the main boards list */
    public static void loadBoards() {
        boards.clear();
        File[] files = getBoardsFolders();
        for (File file : files) {
            loadBoardsFromFolder(file);
        }
    }

    /*! Load any boards found in the specified folder */
    public static void loadBoardsFromFolder(File folder) {
        String bl[] = folder.list();

        if(bl == null) {
            return;
        }

        Debug.message("Loading boards from " + folder.getAbsolutePath());

        for(int i = 0; i < bl.length; i++) {
            if(bl[i].charAt(0) == '.')
                continue;

            File bdir = new File(folder, bl[i]);

            if(bdir.isDirectory()) {
                File bfile = new File(bdir, "board.txt");

                if(bfile.exists()) {
                    Debug.message("    Loading board " + bfile.getAbsolutePath());
                    Board newBoard = new Board(bdir);

                    if(newBoard.isValid()) {
                        boards.put(newBoard.getName(), newBoard);
                    }
                } else {
                    loadBoardsFromFolder(bdir);
                }
            }
        }
    }

//    boolean breakTime = false;
//    String[] months = {
//        "jan", "feb", "mar", "apr", "may", "jun",
//        "jul", "aug", "sep", "oct", "nov", "dec"
//    };

    /*! Create a new untitled document in a new sketch window.  */
    public static void handleNew() {
        createNewEditor(null);
    }


    /*! Determine if the provided folder is a sketch folder or not */
    public static boolean isSketchFolder(File folder) {
        if(folder.isDirectory()) {
            File testFile = new File(folder, folder.getName() + ".ino");

            if(testFile.exists()) {
                return true;
            }

            testFile = new File(folder, folder.getName() + ".pde");

            if(testFile.exists()) {
                return true;
            }
        }

        return false;
    }

    /*! Find if a folder has at least one sketch underneath it */
    public static boolean pathContainsSketchSomewhere(File root) {
        if(!root.isDirectory()) {
            return false;
        }

        if(isSketchFolder(root)) {
            return true;
        }

        File[] files = root.listFiles();

        for(File f : files) {
            if(f.isDirectory()) {
                if(pathContainsSketchSomewhere(f)) {
                    return true;
                }
            }
        }

        return false;
    }

    /*! Opens a sketch given by *path* in a new Editor window */
    public static Editor createNewEditor(String path) {
        Sketch s;

        if(path == null) {
            s = new Sketch((File)null);
        } else {
            s = new Sketch(path);
        }

        Editor editor = new Editor(s);
        editor.setVisible(true);

        if(path != null) {
            updateMRU(new File(path));
        }

        editor.getSketch().loadConfig();

        return editor;
    }

// .................................................................


    /*! Load all the libraries in the system */
    public static void gatherLibraries() {
        Library.loadLibraries();
    }

    public static Platform getPlatform() {
        return platform;
    }

    public static String getOSArch() {
        return System.getProperty("os.arch");
    }

    public static String getOSVersion() {
        return platform.getVersion();
    }

    public static String getOSFlavour() {
        return platform.getFlavour();
    }

    public static String getOSFullName() {
        return getOSName() + "_" + getOSArch();
    }

    public static String getOSName() {
        String osname = System.getProperty("os.name");

        if(osname.indexOf("Mac") != -1) {
            return "macosx";
        } else if(osname.indexOf("Windows") != -1) {
            return "windows";
        } else {
            return osname.toLowerCase();
        }
    }

    /**
    * returns true if Processing is running on a Mac OS X machine.
    */
    public static boolean isMacOS() {
        return getOSName().equals("macosx");
    }


    /**
    * returns true if running on windows.
    */
    public static boolean isWindows() {
        return getOSName().equals("windows");
    }


    /**
    * true if running on linux.
    */
    public static boolean isLinux() {
        return getOSName().equals("linux");
    }

    public static boolean isFreeBSD() {
        return getOSName().equals("freebsd");
    }

    public static boolean isUnix() {
        return isLinux() || isFreeBSD();
    }

    public static boolean isPosix() {
        return isLinux() || isFreeBSD() || isMacOS();
    }

    /**
    * Get the path to the platform's temporary folder, by creating
    * a temporary temporary file and getting its parent folder.
    * <br/>
    * Modified for revision 0094 to actually make the folder randomized
    * to avoid conflicts in multi-user environments. (Bug 177)
    */
    public static File createTempFolder(String name) {
        try {
            File folder = File.createTempFile(name, null);
            //String tempPath = ignored.getParent();
            //return new File(tempPath);
            folder.delete();
            folder.mkdirs();
            return folder;

        } catch(Exception e) {
            error(e);
        }

        return null;
    }


    public static Set<File> getLibraries() {
        return libraries;
    }


    public static File getExamplesFolder() {
        return examplesFolder;
    }

    public static String getExamplesPath() {
        return examplesFolder.getAbsolutePath();
    }


    public static File getToolsFolder() {
        return toolsFolder;
    }


    public static String getToolsPath() {
        return toolsFolder.getAbsolutePath();
    }


    public static File getHardwareFolder() {
        // calculate on the fly because it's needed by Preferences.init() to find
        // the boards.txt and programmers.txt preferences files (which happens
        // before the other folders / paths get cached).
        return getContentFile("hardware");
    }

    public static File getSketchbookFolder() {
        String sbPath = preferences.get("locations.sketchbook");
        if (sbPath == null) {
            preferences.setFile("locations.sketchbook", platform.getDefaultSketchbookFolder());
            return platform.getDefaultSketchbookFolder();
        }
        return new File(preferences.get("locations.sketchbook"));
    }

    protected File getDefaultSketchbookFolder() {
        File sketchbookFolder = null;

        try {
            sketchbookFolder = platform.getDefaultSketchbookFolder();
        } catch(Exception e) { }

        // create the folder if it doesn't exist already
        boolean result = true;

        if(!sketchbookFolder.exists()) {
            result = sketchbookFolder.mkdirs();
        }

        return sketchbookFolder;
    }

    /**
    * Implements the cross-platform headache of opening URLs
    * TODO This code should be replaced by PApplet.link(),
    * however that's not a static method (because it requires
    * an AppletContext when used as an applet), so it's mildly
    * trickier than just removing this method.
    */
    public static void openURL(String url) {
        try {
            platform.openURL(url);

        } catch(Exception e) {
            showWarning(
                i18n.string("err.badurl.title"),
                i18n.string("err.badurl.body", url),
                e
            );
        }
    }


    /**
    * Used to determine whether to disable the "Show Sketch Folder" option.
    * @return true If a means of opening a folder is known to be available.
    */
    static protected boolean openFolderAvailable() {
        return platform.openFolderAvailable();
    }


    /**
    * Implements the other cross-platform headache of opening
    * a folder in the machine's native file browser.
    */
    public static void openFolder(File file) {
        try {
            platform.openFolder(file);

        } catch(Exception e) {
            showWarning(
                i18n.string("err.badfolder.title"),
                i18n.string("err.badfolder.body", file),
                e
            );
        }
    }


    // .................................................................


    /**
    * Prompt for a fodler and return it as a File object (or null).
    * Implementation for choosing directories that handles both the
    * Mac OS X hack to allow the native AWT file dialog, or uses
    * the JFileChooser on other platforms. Mac AWT trick obtained from
    * <A HREF="http://lists.apple.com/archives/java-dev/2003/Jul/msg00243.html">this post</A>
    * on the OS X Java dev archive which explains the cryptic note in
    * Apple's Java 1.4 release docs about the special System property.
    */
    public static File selectFolder(String prompt, File folder, Frame frame) {
        if(Base.isMacOS()) {
            if(frame == null) frame = new Frame();  //.pack();

            FileDialog fd = new FileDialog(frame, prompt, FileDialog.LOAD);

            if(folder != null) {
                fd.setDirectory(folder.getParent());
                //fd.setFile(folder.getName());
            }

            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            fd.setVisible(true);
            System.setProperty("apple.awt.fileDialogForDirectories", "false");

            if(fd.getFile() == null) {
                return null;
            }

            return new File(fd.getDirectory(), fd.getFile());

        } else {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle(prompt);

            if(folder != null) {
                fc.setSelectedFile(folder);
            }

            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int returned = fc.showOpenDialog(new JDialog());

            if(returned == JFileChooser.APPROVE_OPTION) {
                return fc.getSelectedFile();
            }
        }

        return null;
    }


    // .................................................................


    /**
    * Give this Frame a Processing icon.
    */
    public static void setIcon(Frame frame) {
        try {
            frame.setIconImage(loadImageFromResource("icons/icon.png"));
        } catch(Exception e) {
            error(e);
        }
    }


    /**
    * Registers key events for a Ctrl-W and ESC with an ActionListener
    * that will take care of disposing the window.
    */
    public static void registerWindowCloseKeys(JRootPane root, ActionListener disposer) {
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        root.registerKeyboardAction(disposer, stroke,
                                    JComponent.WHEN_IN_FOCUSED_WINDOW);

        int modifiers = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        stroke = KeyStroke.getKeyStroke('W', modifiers);
        root.registerKeyboardAction(disposer, stroke,
                                    JComponent.WHEN_IN_FOCUSED_WINDOW);
    }


    // .................................................................


    public static void showReference(String filename) {
        File referenceFolder = Base.getContentFile("reference");
        File referenceFile = new File(referenceFolder, filename);
        openURL(referenceFile.getAbsolutePath());
    }

    // .................................................................


    /**
    * "No cookie for you" type messages. Nothing fatal or all that
    * much of a bummer, but something to notify the user about.
    */
    public static void showMessage(String title, String message) {
        if(title == null) title = i18n.string("alert.message");

        if(headless) {
            System.out.println(title + ": " + message);

        } else {
            JOptionPane.showMessageDialog(new Frame(), message, title,
                                          JOptionPane.INFORMATION_MESSAGE);
        }
    }


    /**
    * Non-fatal error message with optional stack trace side dish.
    */
    public static void showWarning(String title, String message, Exception e) {
        if(title == null) title = i18n.string("alert.warning");

        if(headless) {
            System.out.println(title + ": " + message);

        } else {
            System.out.println(title + ": " + message);
            JOptionPane.showMessageDialog(new Frame(), message, title,
                                          JOptionPane.WARNING_MESSAGE);
        }

        if(e != null) error(e);
    }


    /**
    * Show an error message that's actually fatal to the program.
    * This is an error that can't be recovered. Use showWarning()
    * for errors that allow P5 to continue running.
    */
    public static void showError(String title, String message, Throwable e) {
        if(title == null) title = i18n.string("alert.error");

        if(headless) {
            System.err.println(title + ": " + message);

        } else {
            JOptionPane.showMessageDialog(new Frame(), message, title,
                                          JOptionPane.ERROR_MESSAGE);
        }

        if(e != null) error(e);

        System.exit(1);
    }


    // ...................................................................

    public static File getContentFile(String name) {
        String path = System.getProperty("user.dir");

        // Get a path to somewhere inside the .app folder
        if(Base.isMacOS()) {
            //      <key>javaroot</key>
            //      <string>$JAVAROOT</string>
            String javaroot = System.getProperty("javaroot");

            if(javaroot != null) {
                path = javaroot;
            }
        }

        File working = new File(path);

        if(name == null) {
            return working;
        }

        return new File(working, name);
    }


    /**
    * Get an image associated with the current color theme.
    */
    public static Image getThemeImage(String name, Component who) {
        return getLibImage("theme/" + name, who);
    }


    /**
    * Return an Image object from inside the Processing lib folder.
    */
    public static Image getLibImage(String name, Component who) {
        Image image = null;
        Toolkit tk = Toolkit.getDefaultToolkit();

        File imageLocation = new File(getContentFile("lib"), name);
        image = tk.getImage(imageLocation.getAbsolutePath());
        MediaTracker tracker = new MediaTracker(who);
        tracker.addImage(image, 0);

        try {
            tracker.waitForAll();
        } catch(InterruptedException e) { }

        return image;
    }

    public static BufferedImage getLibBufferedImage(String name) {
        try {
            BufferedImage image = ImageIO.read(new File(getContentFile("lib"), name));
            return image;
        } catch(Exception e) {
            return null;
        }
    }


    /**
    * Return an InputStream for a file inside the Processing lib folder.
    */
    public static InputStream getLibStream(String filename) {
        try {
            return new FileInputStream(new File(getContentFile("lib"), filename));
        } catch(Exception e) {
            System.err.println("Unable to find " + filename + " in lib");
            return null;
        }
    }


    // ...................................................................


    /**
    * Get the number of lines in a file by counting the number of newline
    * characters inside a String (and adding 1).
    */
    public static int countLines(String what) {
        int count = 1;

        for(char c : what.toCharArray()) {
            if(c == '\n') count++;
        }

        return count;
    }

    public static void copyFile(File sourceFile, File targetFile) {
        try {
            if (sourceFile.getCanonicalPath().equals(targetFile.getCanonicalPath())) return;
            InputStream from =
                new BufferedInputStream(new FileInputStream(sourceFile));
            OutputStream to =
                new BufferedOutputStream(new FileOutputStream(targetFile));
            byte[] buffer = new byte[16 * 1024];
            int bytesRead;

            while((bytesRead = from.read(buffer)) != -1) {
                to.write(buffer, 0, bytesRead);
            }

            to.flush();
            from.close(); // ??
            from = null;
            to.close(); // ??
            to = null;

            targetFile.setLastModified(sourceFile.lastModified());
        } catch(Exception e) {
            error(e);
        }
    }

    /**
    * Copy a folder from one place to another. This ignores all dot files and
    * folders found in the source directory, to avoid copying silly .DS_Store
    * files and potentially troublesome .svn folders.
    */
    public static void copyDir(File sourceDir, File targetDir) {
        try {
            targetDir.mkdirs();
            String files[] = sourceDir.list();

            for(int i = 0; i < files.length; i++) {
                // Ignore dot files (.DS_Store), dot folders (.svn) while copying
                if(files[i].charAt(0) == '.') continue;

                //if (files[i].equals(".") || files[i].equals("..")) continue;
                File source = new File(sourceDir, files[i]);
                File target = new File(targetDir, files[i]);

                if(source.isDirectory()) {
                    //target.mkdirs();
                    copyDir(source, target);
                    target.setLastModified(source.lastModified());
                } else {
                    copyFile(source, target);
                }
            }
        } catch(Exception e) {
            error(e);
        }
    }


    /**
    * Remove all files in a directory and the directory itself.
    */
    public static void removeDir(File dir) {
        if(dir.exists()) {
            Debug.message("Deleting folder " + dir.getAbsolutePath());
            removeDescendants(dir);

            if(!dir.delete()) {
                error(i18n.string("err.nodelete", dir.getName()));
            }
        }
    }


    /**
    * Recursively remove all files within a directory,
    * used with removeDir(), or when the contents of a dir
    * should be removed, but not the directory itself.
    * (i.e. when cleaning temp files from lib/build)
    */
    public static void removeDescendants(File dir) {
        if(!dir.exists()) return;

        String files[] = dir.list();

        for(int i = 0; i < files.length; i++) {
            if(files[i].equals(".") || files[i].equals("..")) continue;

            File dead = new File(dir, files[i]);

            if(!dead.isDirectory()) {
                dead.delete();
            } else {
                removeDir(dead);
            }
        }
    }

    /**
     * Calculate the size of the contents of a folder.
     * Used to determine whether sketches are empty or not.
     * Note that the function calls itself recursively.
     */
    public static int calcFolderSize(File folder) {
        int size = 0;

        String files[] = folder.list();

        // null if folder doesn't exist, happens when deleting sketch
        if(files == null) return -1;

        for(int i = 0; i < files.length; i++) {
            if(files[i].equals(".") || (files[i].equals("..")) ||
                    files[i].equals(".DS_Store")) continue;

            File fella = new File(folder, files[i]);

            if(fella.isDirectory()) {
                size += calcFolderSize(fella);
            } else {
                size += (int) fella.length();
            }
        }

        return size;
    }

    /**
     * Recursively creates a list of all files within the specified folder,
     * and returns a list of their relative paths.
     * Ignores any files/folders prefixed with a dot.
     */
    public static String[] listFiles(String path, boolean relative) {
        return listFiles(new File(path), relative);
    }

    public static String[] listFiles(File folder, boolean relative) {
        String path = folder.getAbsolutePath();
        Vector<String> vector = new Vector<String>();
        listFiles(relative ? (path + File.separator) : "", path, vector);
        String outgoing[] = new String[vector.size()];
        vector.copyInto(outgoing);
        return outgoing;
    }

    static protected void listFiles(String basePath, String path, Vector<String> vector) {
        File folder = new File(path);
        String list[] = folder.list();

        if(list == null) return;

        for(int i = 0; i < list.length; i++) {
            if(list[i].charAt(0) == '.') continue;

            File file = new File(path, list[i]);
            String newPath = file.getAbsolutePath();

            if(newPath.startsWith(basePath)) {
                newPath = newPath.substring(basePath.length());
            }

            vector.add(newPath);

            if(file.isDirectory()) {
                listFiles(basePath, newPath, vector);
            }
        }
    }

    public static void loadPlugins() {

        File[] folders = getPluginsFolders();
        for (File folder : folders) {
            Debug.message("Loading plugins from " + folder);
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    Debug.message("  Loading " + f);
                    try {
                        URL u = f.toURI().toURL();
                        addURL(u);
                    } catch (Exception ex) {
                        error(ex);
                    }
                }
            }
        }

        folders = getThemesFolders();
        for (File folder : folders) {
            Debug.message("Loading plugins from " + folder);
            File[] files = folder.listFiles();
            if (files != null) {
                for (File f : files) {
                    Debug.message("  Loading " + f);
                    if (f.getName().endsWith(".jar")) {
                        try {
                            URL u = f.toURI().toURL();
                            addURL(u);
                        } catch (Exception ex) {
                            error(ex);
                        }
                    }
                }
            }
        }

        Reflections pluginReflections = new Reflections("org.uecide.plugin");
        try {
            Set<Class<? extends Plugin>> pluginClasses = pluginReflections.getSubTypesOf(Plugin.class);
            for (Class<? extends Plugin> c : pluginClasses) {
                Debug.message("Found plugin class " + c.getName());
                if (c.getName().equals("org.uecide.plugin.PluginManager")) {
                    continue;
                }
                plugins.put(c.getName(), c);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        pluginReflections = new Reflections("com.ardublock");
        try {
            Set<Class<? extends Plugin>> pluginClasses = pluginReflections.getSubTypesOf(Plugin.class);
            for (Class<? extends Plugin> c : pluginClasses) {
                Debug.message("Found plugin class " + c.getName());
                if (c.getName().equals("org.uecide.plugin.PluginManager")) {
                    continue;
                }
                plugins.put(c.getName(), c);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        pluginReflections = new Reflections("org.uecide");
        try {
            Set<Class<? extends LookAndFeel>> pluginClasses = pluginReflections.getSubTypesOf(LookAndFeel.class);
            for (Class<? extends LookAndFeel> c : pluginClasses) {
                Debug.message("Found look and feel class " + c.getName());
                lookAndFeels.put(c.getName(), c);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        pluginReflections = new Reflections("org.uecide.themes");
        try {
            // We're not going to store the theme control objects - just execute the "init" function in them.
            Set<Class<? extends org.uecide.themes.ThemeControl>> controlClasses = pluginReflections.getSubTypesOf(org.uecide.themes.ThemeControl.class);
            for (Class<? extends org.uecide.themes.ThemeControl> c : controlClasses) {
                Method init = c.getMethod("init");
                if (init != null) {
                    Object[] noParameters = null;
                    try {
                        init.invoke(null, noParameters);
                    } catch (Exception e) {
                        error(e);
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        loadJSPlugins();
    }

    public static Version getPluginVersion(String plugin) {

        Class<?> pluginClass = plugins.get(plugin);
        if (pluginClass == null) {
            return new Version(null);
        }
        File f = Base.getJarLocation(pluginClass);

        try {
            JarFile myself = new JarFile(f);
            Manifest manifest = myself.getManifest();
            Attributes manifestContents = manifest.getMainAttributes();

            return new Version(manifestContents.getValue("Version"));
        } catch (IOException ex) {
        }
        return new Version(null);
    }

    public static String findClassInZipFile(File file) {
        String base = file.getName();

        if(!base.endsWith(".jar")) {
            return null;
        }

        base = base.substring(0, base.length() - 4);

        String classFileName = "/" + base + ".class";


        try {
            ZipFile zipFile = new ZipFile(file);
            Enumeration<?> entries = zipFile.entries();

            while(entries.hasMoreElements()) {
                ZipEntry entry = (ZipEntry) entries.nextElement();

                if(!entry.isDirectory()) {
                    String name = entry.getName();

                    if(name.endsWith(classFileName)) {
                        // Remove .class and convert slashes to periods.
                        return name.substring(0, name.length() - 6).replace('/', '.');
                    }
                }
            }
        } catch(IOException e) {
            error(e);
        }

        return null;
    }

    public static File getTmpDir() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    public static void applyPreferences() {
        setLookAndFeel();
        Editor.updateLookAndFeel();
    }

    public static void reloadPlugins() {
        loadPlugins();
        Editor.updateAllEditors();
    }

    static String openLauncher;
    static public void open(String filename) {
        open(new String[] { filename });
    }

    static public Process open(String argv[]) {
        String[] params = null;

        if(isWindows()) {
            params = new String[] { "cmd", "/c" };

        } else if(isMacOS()) {
            params = new String[] { "open" };

        } else if(isUnix()) {
            if(openLauncher == null) {
                try {
                    Process p = Runtime.getRuntime().exec(new String[] { "gnome-open" });
                    /*int result =*/ p.waitFor();
                    openLauncher = "gnome-open";
                } catch(Exception e) { }
            }

            if(openLauncher == null) {
                // Attempt with kde-open
                try {
                    Process p = Runtime.getRuntime().exec(new String[] { "kde-open" });
                    /*int result =*/ p.waitFor();
                    openLauncher = "kde-open";
                } catch(Exception e) { }
            }

            if(openLauncher == null) {
                System.err.println("Could not find gnome-open or kde-open, " +
                                   "the open() command may not work.");
            }

            if(openLauncher != null) {
                params = new String[] { openLauncher };
            }
        }

        if(params != null) {
            if(params[0].equals(argv[0])) {
                return exec(argv);
            } else {
                params = concat(params, argv);
                return exec(params);
            }
        } else {
            return exec(argv);
        }
    }

    static public Process exec(String[] argv) {
        try {
            return Runtime.getRuntime().exec(argv);
        } catch(Exception e) {
            error(e);
            return null;
        }
    }

    static public String[] concat(String a[], String b[]) {
        String c[] = new String[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }



    static public int[] expand(int list[]) {
        return expand(list, list.length << 1);
    }

    static public int[] expand(int list[], int newSize) {
        int temp[] = new int[newSize];
        System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
        return temp;
    }

    static public File getDataFolder() {
        File out = null;
        if (overrideSettingsFolder != null) {
            out = new File(overrideSettingsFolder);
        } else {
            if ((preferences != null) && (preferences.getFile("locations.data") != null)) {
                out = preferences.getFile("locations.data");
            } else {
                out = platform.getSettingsFolder();
            }
        }
        if (!out.exists()) {
            out.mkdirs();
        }
        return out;
    }
    
    static public File getDataFolder(String dir) {
        File out = new File(getDataFolder(), dir);
        if (!out.exists()) {
            out.mkdirs();
        }
        return out;
    }

    static public File getDataFile(String file) {
        return new File(getDataFolder(), file);
    }
    
    static public File getCacheFolder() { return getDataFolder("cache"); }

    static public File[] getAnyFolders(String type) {
        ArrayList<File> locs = new ArrayList<File>();
        locs.add(getDataFolder(type));
        locs.add(getDataFolder("usr/share/uecide/" + type));
        if (isPosix()) {
            locs.add(new File("/usr/share/uecide/" + type));
        }
        return locs.toArray(new File[0]);
    }

    static public File[] getCoresFolders() { return getAnyFolders("cores"); }
    static public File[] getProgrammersFolders() { return getAnyFolders("programmers"); }
    static public File[] getBoardsFolders() { return getAnyFolders("boards"); }
    static public File[] getThemesFolders() { return getAnyFolders("themes"); }
    static public File[] getPluginsFolders() { return getAnyFolders("plugins"); }
    static public File[] getCompilersFolders() { return getAnyFolders("compilers"); }
    static public File[] getLibrariesFolders() { return getAnyFolders("libraries"); }

    static public void errorReport(Thread t, Throwable e) {
        showError("Uncaught Exception", "An uncaught exception occurred in thread " + t.getName() + " (" + t.getId() + ")\n" +
                  "The cause is: " + e.getCause() + "\n" +
                  "The message is: " + e.getMessage() + "\n", e);
    }

    static public void broken(Thread t, Throwable e) {
        if (headless) {
            e.printStackTrace();
            return;
        }
        try {
            e.printStackTrace();
            if(e.getCause() == null) {
                return;
            }


            Debug.message("");
            Debug.message("******************** EXCEPTION ********************");
            Debug.message("An uncaught exception occurred in thread " + t.getName() + " (" + t.getId() + ")");
            Debug.message("    The cause is: " + e.getCause());
            Debug.message("    The message is: " + e.getMessage());
            Debug.message("");

            for(StackTraceElement element : e.getStackTrace()) {
                if(element != null) {
                    Debug.message("        " + element);
                }
            }

            Debug.message("******************** EXCEPTION ********************");
            Debug.message("");
        } catch(Exception ee) {
            ee.printStackTrace();
        }
    }

    public static byte[] loadBytesRaw(File file) {
        try {
            int size = (int) file.length();
            FileInputStream input = new FileInputStream(file);
            byte buffer[] = new byte[size];
            int offset = 0;
            int bytesRead;

            while((bytesRead = input.read(buffer, offset, size - offset)) != -1) {
                offset += bytesRead;

                if(bytesRead == 0) break;
            }

            input.close();  // weren't properly being closed
            input = null;
            return buffer;
        } catch(Exception e) {
            error(e);
            return null;
        }
    }


    public static void error(String e) {
        Editor.broadcastError(e);
        System.err.println(e);
        Debug.message(e);
    }

    public static void error(Throwable e) {

        Editor.broadcastError(e.getMessage());

        try {
            Debug.message("");
            Debug.message("******************** EXCEPTION ********************");
            Debug.message("An uncaught exception occurred:");
            Debug.message("    The cause is: " + e.getCause());
            Debug.message("    The message is: " + e.getMessage());
            Debug.message("");

            for(StackTraceElement element : e.getStackTrace()) {
                if(element != null) {
                    Debug.message("        " + element);
                }
            }

            Debug.message("******************** EXCEPTION ********************");
            Debug.message("");
        } catch(Exception ee) {
            ee.printStackTrace();
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        System.err.println(sw.toString());
    }

    // This handy little function will rebuild the whole of the internals of
    // UECIDE - that is, all the boards, cores, compilers and libraries etc.
    public static void cleanAndScanAllSettings() {
        try {

            SwingWorker<String, Object> worker = new SwingWorker<String, Object>() {
                @Override
                protected String doInBackground() throws Exception {
                    Editor.lockAll();
                    Editor.bulletAll(i18n.string("msg.loading.serial"));
                    Serial.updatePortList();
                    Serial.fillExtraPorts();
                    Editor.bulletAll(i18n.string("msg.loading.compilers"));
                    rescanCompilers();
                    Editor.bulletAll(i18n.string("msg.loading.cores"));
                    rescanCores();
                    Editor.bulletAll(i18n.string("msg.loading.boards"));
                    rescanBoards();
                    Editor.bulletAll(i18n.string("msg.loading.programmers"));
                    rescanProgrammers();
                    Editor.bulletAll(i18n.string("msg.loading.plugins"));
                    rescanPlugins();
                    Editor.bulletAll(i18n.string("msg.loading.themes"));
                    rescanThemes();
                    Editor.bulletAll(i18n.string("msg.loading.libraries"));
                    rescanLibraries();

                    buildPreferencesTree();

                    return "OK";
                }

                @Override
                protected void done() {
                    Editor.unlockAll();
                    Editor.bulletAll(i18n.string("msg.loading.updating"));
                    try { Editor.updateAllEditors(); } catch (Exception e) {}
                    try { Editor.selectAllEditorBoards(); } catch (Exception e) {}
                    try { Editor.refreshAllEditors(); } catch (Exception e) {}
                    Editor.bulletAll(i18n.string("msg.done"));
                }
            };
            worker.execute();

            //Editor.unlockAll();
        } catch (Exception e) {
        }
    }

    public static void rescanPlugins() {
        plugins = new TreeMap<String, Class<?>>();
        lookAndFeels = new TreeMap<String, Class<?>>();
        pluginInstances = new ArrayList<Plugin>();
        loadPlugins();
    }

    public static void rescanThemes() {
        loadThemes();
        theme.fullyParseFile();
    }
    public static void rescanCompilers() {
        compilers = new TreeMap<String, Compiler>();
        loadCompilers();
    }

    public static void rescanCores() {
        cores = new TreeMap<String, Core>();
        loadCores();
    }

    public static void rescanProgrammers() {
        try {
            programmers = new TreeMap<String, Programmer>();
            loadProgrammers();
            Editor.updateAllEditors();
            Editor.selectAllEditorProgrammers();
        } catch(Exception e) {
            error(e);
        }
    }

    public static void rescanBoards() {
        try {
            boards = new TreeMap<String, Board>();
            loadBoards();
            Editor.updateAllEditors();
            Editor.selectAllEditorBoards();
        } catch(Exception e) {
            error(e);
        }
    }

    public static void rescanLibraries() {
        gatherLibraries();
    }

    public static void updateLookAndFeel() {
        Editor.updateLookAndFeel();
    }

    public static ImageIcon loadIconFromFile(File f) {
        return new ImageIcon(f.getAbsolutePath());
    }

    public static ImageIcon loadIconFromResource(String res, URLClassLoader loader) {
        URL loc = loader.getResource(res);

        if(loc == null) {
            loc = Base.class.getResource("/org/uecide/icons/unknown.png");
        }

        return new ImageIcon(loc);
    }

    public static BufferedImage loadImageFromResource(String res) {
        if(!res.startsWith("/")) {
            res = "/org/uecide/" + res;
        }

        URL loc = Base.class.getResource(res);

        if(loc == null) {
            loc = Base.class.getResource("/org/uecide/icons/unknown.png");
        }

        try {
            BufferedImage im = ImageIO.read(loc);
            return im;
        } catch(Exception e) {
            error(e);
        }

        return null;
    }

    public static ImageIcon loadIconFromResource(String res) {
        if(!res.startsWith("/")) {
            res = "/org/uecide/icons/" + res;
        }

        URL loc = Base.class.getResource(res);

        if(loc == null) {
            loc = Base.class.getResource("/org/uecide/icons/unknown.png");
        }

        return new ImageIcon(loc);
    }

    public static boolean copyResourceToFile(String res, File dest) {
        try {
            InputStream from = Base.class.getResourceAsStream(res);
            OutputStream to =
                new BufferedOutputStream(new FileOutputStream(dest));
            byte[] buffer = new byte[16 * 1024];
            int bytesRead;

            while((bytesRead = from.read(buffer)) != -1) {
                to.write(buffer, 0, bytesRead);
            }

            to.flush();
            from.close();
            from = null;
            to.close();
            to = null;
        } catch(Exception e) {
            error(e);
            return false;
        }

        return true;
    }

    private static ArrayList<String> getResourcesFromJarFile(File file, String root, String extension) {
        ArrayList<String> retval = new ArrayList<String>();
        ZipFile zf;

        try {
            zf = new ZipFile(file);
            final Enumeration e = zf.entries();

            while(e.hasMoreElements()) {
                final ZipEntry ze = (ZipEntry) e.nextElement();
                final String fileName = ze.getName();

                if(fileName.startsWith(root) && fileName.endsWith(extension)) {
                    retval.add(fileName);
                }
            }

            zf.close();
        } catch(Exception e) {
            error(e);
        }

        return retval;
    }

    private String getBundleVersion(String path) {
        try {
            InputStream instr = Base.class.getResourceAsStream(path);

            if(instr == null) {
                return "0.0.0a";
            }

            JarInputStream jis = new JarInputStream(instr);
            Manifest manifest = jis.getManifest();
            Attributes manifestContents = manifest.getMainAttributes();
            return manifestContents.getValue("Version");
        } catch(Exception e) {
            error(e);
        }

        return "unknown";
    }

    public Version getLatestVersion() {
        try {
            URL url = new URL(Base.theme.get("version." + RELEASE + ".url"));
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
            String data = in.readLine();
            in.close();
            return new Version(data);
        } catch(Exception e) {
            // Unable to get new version details - return nothing.
            // Also switch to offline mode since there was an error.
            onlineMode = false;
        }

        return null;
    }

    public static boolean isOnline() {
        return onlineMode;
    }

    public static void setOnlineMode() {
        onlineMode = true;
    }

    public static void setOfflineMode() {
        onlineMode = false;
    }

    public boolean isNewVersionAvailable() {
        if (!isOnline()) {
            return false;
        }
        int time = (int)(System.currentTimeMillis() / 1000L);
        Preferences.setInteger("version.lastcheck", time);

        if(Preferences.getBoolean("editor.version_check")) {
            Version newVersion = getLatestVersion();

            if(newVersion == null) {
                return false;
            }

            if(newVersion.compareTo(systemVersion) > 0) {
                return true;
            }

            return false;
        }

        return false;
    }

    // Is it time to check the version?  Was the last version check
    // more than 3 hours ago?
    public boolean isTimeToCheckVersion() {
        int lastCheck = Preferences.getInteger("version.lastcheck");
        int time = (int)(System.currentTimeMillis() / 1000L);

        if(time > (lastCheck + (3 * 60 * 60))) {
            return true;
        }

        return false;
    }

    public static void debug(String msg) {
        if(!extraDebug) return;

        if(msg == null) {
            msg = "(null)";
        }

        if(!msg.endsWith("\n")) {
            msg += "\n";
        }

        Thread t = Thread.currentThread();
        StackTraceElement[] st = t.getStackTrace();
        StackTraceElement caller = st[2];
        System.err.print(caller.getFileName() + " " + caller.getLineNumber() + " (" + caller.getMethodName() + "): " + msg);
    }

    public static void loadThemes() {

        themes = new TreeMap<String, PropertyFile>();

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("org.uecide"))
                .setScanners(new ResourcesScanner()));

        Pattern pat = Pattern.compile(".*\\.theme");
        Set<String> thms = reflections.getResources(pat);

        for (String thm : thms) {
            PropertyFile newTheme = new PropertyFile("/" + thm);
            String name = newTheme.get("name");
            if (name != null) {
                Debug.message("Found embedded theme " + name);
                PropertyFile merged = new PropertyFile("/org/uecide/themes/Default.theme");
                merged.mergeData(newTheme);
                themes.put(newTheme.get("name"), merged);
            }
        }

        File[] tfs = getThemesFolders();
        for (File tf : tfs) {
            if (tf.exists()) {
                File[] files = tf.listFiles();

                for(File f : files) {
                    if(f.getName().endsWith(".theme")) {
                        PropertyFile newTheme = new PropertyFile(f);
                        String name = newTheme.get("name");
                        if (name != null) {
                            Debug.message("Found external theme " + name);
                            PropertyFile merged = new PropertyFile("/org/uecide/themes/Default.theme");
                            merged.mergeData(newTheme);
                            themes.put(newTheme.get("name"), merged);
                        }
                    }
                }
            }
        }
    }

    public static PropertyFile getTheme() {
        String selectedTheme = Preferences.get("theme.editor");
        if (themes.get(selectedTheme) != null) {
            return themes.get(selectedTheme);
        } else {    
            return themes.get("Default");
        }
    }

    public static void setTheme(String t) {
        if (themes.get(t) != null) {
            Preferences.set("theme.editor", t);
        }
    }

    public static HashMap<String, String> getThemeList() {
        HashMap<String, String> themeList = new HashMap<String, String>();

        for(String key : themes.keySet()) {
            themeList.put(key, key);
        }

        return themeList;
    }
/*
    public static String runFunctionVariable(Sketch sketch, String command, String param) {
        try {
            Class<?> c = Class.forName("org.uecide.varcmd.vc_" + command);

            if(c == null) {
                return "";
            }

            Constructor<?> ctor = c.getConstructor();
            VariableCommand  p = (VariableCommand)(ctor.newInstance());

            Class[] param_types = new Class<?>[2];
            param_types[0] = Sketch.class;
            param_types[1] = String.class;
            Method m = c.getMethod("main", param_types);

            if(m == null) {
                return "";
            }

            Object[] args = new Object[2];
            args[0] = sketch;
            args[1] = param;
            return (String)m.invoke(p, args);
        } catch(Exception e) {
            Base.error(e);
        }

        return "";
    }

    public static boolean runBuiltinCommand(Sketch sketch, String commandline) {
        try {
            String[] split = commandline.split("::");
            int argc = split.length - 1;

            String cmdName = split[0];

            String[] arg = new String[argc];

            for(int i = 0; i < argc; i++) {
                arg[i] = split[i + 1];
            }

            if(!cmdName.startsWith("__builtin_")) {
                return false;
            }

            cmdName = cmdName.substring(10);
            Class<?> c = Class.forName("org.uecide.builtin." + cmdName);

            Constructor<?> ctor = c.getConstructor();
            BuiltinCommand  p = (BuiltinCommand)(ctor.newInstance());

            if(c == null) {
                return false;
            }

            Class<?>[] param_types = new Class<?>[2];
            param_types[0] = Sketch.class;
            param_types[1] = String[].class;
            Method m = c.getMethod("main", param_types);

            Object[] args = new Object[2];
            args[0] = sketch;
            args[1] = arg;

            return (Boolean)m.invoke(p, args);


        } catch(Exception e) {
            Base.error(e);
        }

        return false;
    }


    public static String parseString(String in, PropertyFile tokens, Sketch sketch) {
        int iStart;
        int iEnd;
        int iTest;
        String out;
        String start;
        String end;
        String mid;

        if(in == null) {
            return null;
        }

        out = in;

        iStart = out.indexOf("${");

        if(iStart == -1) {
            return out;
        }

        iEnd = out.indexOf("}", iStart);
        iTest = out.indexOf("${", iStart + 1);

        while((iTest > -1) && (iTest < iEnd)) {
            iStart = iTest;
            iTest = out.indexOf("${", iStart + 1);
        }

        while(iStart != -1) {
            start = out.substring(0, iStart);
            end = out.substring(iEnd + 1);
            mid = out.substring(iStart + 2, iEnd);

            if(mid.indexOf(":") > -1) {
                String command = mid.substring(0, mid.indexOf(":"));
                String param = mid.substring(mid.indexOf(":") + 1);

                mid = runFunctionVariable(sketch, command, param);
            } else {
                String tmid = tokens.get(mid);

                if(tmid == null) {
                    tmid = "";
                }

                mid = tmid;
            }


            if(mid != null) {
                out = start + mid + end;
            } else {
                out = start + end;
            }

            iStart = out.indexOf("${");
            iEnd = out.indexOf("}", iStart);
            iTest = out.indexOf("${", iStart + 1);

            while((iTest > -1) && (iTest < iEnd)) {
                iStart = iTest;
                iTest = out.indexOf("${", iStart + 1);
            }
        }

        // This shouldn't be needed as the methodology should always find any tokens put in
        // by other token replacements.  But just in case, eh?
        if(out != in) {
            out = parseString(out, tokens, sketch);
        }

        return out;
    }
*/

    public static String getFileAsString(File f) {
        if (f == null) {
            return "";
        }
        if (!f.exists()) {
            return "";
        }
        if (f.isDirectory()) {
            return "";
        }
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            BufferedReader reader = new BufferedReader(new FileReader(f));
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (FileNotFoundException ex) {
        } catch (IOException ex) {
        }
        return "";
    }

    public static String getIconsPath(String name) {
        PropertyFile pf = iconSets.get(name);
        if (pf == null) {
            return null;
        }
        return pf.get("path");
    }

    public static ImageIcon getIcon(String category, String name, int size) {

        String path = getIconsPath(getIconSet());
        if (path == null) {
            return null;
        }

        URL loc = Base.class.getResource(path + "/" + size + "x" + size + "/" + category + "/" + name + ".png");
        if(loc == null) {
            path = getIconsPath(defaultIconSet);
            loc = Base.class.getResource(path + "/" + size + "x" + size + "/" + category + "/" + name + ".png");
        }
        if(loc == null) {
            loc = Base.class.getResource(path + "/" + size + "x" + size + "/actions/unknown.png");
        }

        return new ImageIcon(loc);
    }

    public static String getIconSet() {
        if (getTheme() == null) {
            return null;
        }
        return getTheme().get("icon");
    }

    public static void loadIconSets() {
        iconSets = new HashMap<String, PropertyFile>();

        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("org.uecide"))
                .setScanners(new ResourcesScanner()));

        Pattern pat = Pattern.compile(".*\\.icon");
        Set<String> icons = reflections.getResources(pat);

        for (String icon : icons) {
            PropertyFile pf = new PropertyFile("/" + icon);
            if (pf.get("name") != null) {
                iconSets.put(pf.get("name"), pf);
            }
        }
    }

    // Set a font on a component using the newer style font specification

    public static void setFont(JComponent comp, String key) {

        PropertyFile theme = getTheme();

        String fontData = preferences.get(key);
        if (fontData == null) {
            fontData = theme.get(key);
        }
        if (fontData == null) {
            return;
        }

        String[] bits = fontData.split(",");

        Font f = comp.getFont();
        Color fg = comp.getForeground();

        // Work through each "bit" of the font and decide what it represents, then
        // set the font data accordingly.
        
        for (String bit : bits) {
            bit = bit.trim();

            // Is it just a number?  If so it represents the size.
            if (bit.matches("^\\d+$")) {
                int size = 10;
                try {
                    size = Integer.parseInt(bit);
                } catch (Exception ex) {
                }
                f = new Font(f.getFamily(), f.getStyle(), size);
                continue;
            }

            // Now look for the style of the font
            if (bit.equals("plain")) {
                f = new Font(f.getFamily(), Font.PLAIN, f.getSize());
                continue;
            }
            if (bit.equals("bold")) {
                f = new Font(f.getFamily(), Font.BOLD, f.getSize());
                continue;
            }
            if (bit.equals("italic")) {
                f = new Font(f.getFamily(), Font.ITALIC, f.getSize());
                continue;
            }
            if (bit.equals("bolditalic")) {
                f = new Font(f.getFamily(), Font.BOLD | Font.ITALIC, f.getSize());
                continue;
            }

            // Check for a colour starting with a #
            if (bit.startsWith("#")) {
                try {
                    fg = new Color(Integer.parseInt(bit.substring(1), 16));
                } catch(Exception ex) { 
                }
                continue;
            }

            // Anything else must be the name.
            f = new Font(bit, f.getStyle(), f.getSize());
        }

        comp.setFont(f);
        comp.setForeground(fg);

    }

    public static boolean yesno(String title, String question) {
        if (!headless) {
            return JOptionPane.showOptionDialog(null, question, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null) == JOptionPane.YES_OPTION;
        }
        return false;
    }

    public static void buildPreferencesTree() {
        preferencesTree = new PropertyFile();


        for(Programmer c : programmers.values()) {
            PropertyFile prefs = c.getProperties().getChildren("prefs");
            for (String k : prefs.keySet()) {
                prefs.setSource(k, "programmer:" + c.getName());
            }
            preferencesTree.mergeData(prefs);
        }

        for(Compiler c : compilers.values()) {
            PropertyFile prefs = c.getProperties().getChildren("prefs");
            for (String k : prefs.keySet()) {
                prefs.setSource(k, "compiler:" + c.getName());
            }
            preferencesTree.mergeData(prefs);
        }

        for(Core c : cores.values()) {
            PropertyFile prefs = c.getProperties().getChildren("prefs");
            for (String k : prefs.keySet()) {
                prefs.setSource(k, "core:" + c.getName());
            }
            preferencesTree.mergeData(prefs);
        }

        for(Board c : boards.values()) {
            PropertyFile prefs = c.getProperties().getChildren("prefs");
            for (String k : prefs.keySet()) {
                prefs.setSource(k, "board:" + c.getName());
            }
            preferencesTree.mergeData(prefs);
        }

        for (String p : plugins.keySet()) {
            try {
                Class<?> plg = plugins.get(p);
                Method m = plg.getMethod("getPreferencesTree");
                Object[] foo = null;
                PropertyFile prefs = (PropertyFile)m.invoke(null, foo);
                for (String k : prefs.keySet()) {
                    prefs.setSource(k, "plugin:" + p);
                }
                preferencesTree.mergeData(prefs);
            } catch (Exception e) {
            }
        }

        loadPreferencesTree("/org/uecide/config/prefs.txt");
    }

    public static void registerPreference(String key, String type, String name, String def) {
        registerPreference(key, type, name, def, null);
    }
        
    public static void registerPreference(String key, String type, String name, String def, String plat) {
        preferencesTree.set(key + ".type", type);
        preferencesTree.set(key + ".name", name);
        if (plat == null) {
            preferencesTree.set(key + ".default", def);
        } else {
            preferencesTree.set(key + ".default." + plat, def);
        }
    }

    public static void loadPreferencesTree(String res) {
        PropertyFile pf = new PropertyFile(res);
        preferencesTree.mergeData(pf);
    }

    public static Object executeJavaScript(String resource, String function, Object[] args) {
        Object ret = null;
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");

            String script = getResourceAsString(resource);

            if (script == null) { return null; }
            if (script.equals("")) { return null; }

            engine.eval(script);

            Invocable inv = (Invocable)engine;
            ret = inv.invokeFunction(function, args);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ret;
    }

    public static String getResourceAsString(String resource) {
        String out = "";
        try {
            InputStream from = Base.class.getResourceAsStream(resource);
            byte[] buffer = new byte[16 * 1024];
            int bytesRead;

            StringBuilder sb = new StringBuilder();

            String line = null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(from));
            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }

            out = sb.toString();

            from.close();
            from = null;
        } catch(Exception e) {
            error(e);
        }
        return out;
    }

    public static HashMap<String, String> getLookAndFeelList() {
        HashMap<String, String>lafs = new HashMap<String, String>();

        for (String p : lookAndFeels.keySet()) {
            try {

                Class<?> plg = lookAndFeels.get(p);
                Method m = plg.getMethod("isCompatible");
                Object[] foo = null;
                boolean compat = (Boolean)m.invoke(null, foo);

                if (compat) {
                    m = plg.getMethod("getName");
                    String name = (String)m.invoke(null, foo);
                    lafs.put(p, name);
                }

            } catch (Exception e) {
            }

        }

        return lafs;
    }

    public static HashMap<String, String> getIconSets() {
        HashMap<String, String> hash = new HashMap<String, String>();

        for (String i : iconSets.keySet()) {
            hash.put(i, i);
        }
        return hash;
    }

    // This little routine works through each and every board, core and compiler and
    // runs any "init.script.*" lines.
    public static void runInitScripts() {
        for (Board b : boards.values()) {
            if (b.get("init.script.0") != null) {
                Context ctx = new Context();
                ctx.setBoard(b);
                ctx.executeKey("init.script");
            }
        }
        for (Core c : cores.values()) {
            if (c.get("init.script.0") != null) {
                Context ctx = new Context();
                ctx.setCore(c);
                ctx.executeKey("init.script");
            }
        }
        for (Compiler c : compilers.values()) {
            if (c.get("init.script.0") != null) {
                Context ctx = new Context();
                ctx.setCompiler(c);
                ctx.executeKey("init.script");
            }
        }
        for (Programmer c : programmers.values()) {
            if (c.get("init.script.0") != null) {
                Context ctx = new Context();
                ctx.setProgrammer(c);
                ctx.executeKey("init.script");
            }
        }
    }

    // If the package manager hasn't been configured then 
    // configure it, do an update, and then install the base packages.

    public static void initPackageManager() {
        try {
            File aptFolder = getDataFolder("apt");
            if (!aptFolder.exists()) {
                aptFolder.mkdirs();
            }
            File cacheFolder = new File(aptFolder, "cache");
            if (!cacheFolder.exists()) {
                cacheFolder.mkdirs();
            }
            File dbFolder = new File(aptFolder, "db");
            if (!dbFolder.exists()) {
                dbFolder.mkdirs();
            }
            File packagesFolder = new File(dbFolder, "packages");
            if (!packagesFolder.exists()) {
                packagesFolder.mkdirs();
            }

            File sourcesFile = new File(dbFolder, "sources.db");
            if (!sourcesFile.exists()) {
                PrintWriter pw = new PrintWriter(sourcesFile);
                pw.println("deb res://org/uecide/dist uecide boards cores compilers plugins extra libraries main");
                pw.close();
                PluginManager pm = new PluginManager();
                APT apt = pm.getApt();
                apt.update();
                Package[] packages = apt.getPackages();
                for (Package p : packages) {
                    apt.installPackage(p);
                    System.err.println("Installing " + p);
                }
                apt.save();

//                PropertyFile props = new PropertyFile(new File(dbFolder, "repositories.db"));
//                apt.addSource(props.get("master.url"), props.get("master.codename"), pm.getCleanOSName(), props.get("master.sections").split("::"));
//                apt.saveSources();
//                apt.update();
//                apt.save();
            }

        } catch (Exception e) {
            error(e);
        }

    }

    public static void setLookAndFeel() {
        String laf = getTheme().getPlatformSpecific("laf");
        if(!headless) {
            try {
                Class<?> plg = lookAndFeels.get(laf); //Preferences.get("theme.window"));
                if (plg == null) {
                    System.err.println("LAF " + laf + " NOT FOUND!");
                    return;
                }
                Method m = plg.getMethod("applyLAF");
                Object[] foo = null;
                m.invoke(null, foo);

            } catch (Exception e) {
            }

        }
    }

    public static boolean isQuiet() {
        return cli.isSet("quiet");
    }

    public static Locale getLocale() {
        if (cli != null) {
            if (cli.isSet("locale")) {
                String[] bits = cli.getString("locale").split("_");
                if (bits.length == 2) {
                    return new Locale(bits[0], bits[1]);
                }
            }
        }
        return Locale.getDefault();
    }

    public static void loadJSPlugins() {
        jsplugins = new HashMap<String, JSPlugin>();

        File[] pfs = getPluginsFolders();
        for (File pf : pfs) {
            loadJSPluginsFromFolder(pf);
        }

        for (JSPlugin p : jsplugins.values()) {
            p.onBoot();
        }
    }

    public static void loadJSPluginsFromFolder(File f) {
        if (!f.exists()) return;
        if (!f.isDirectory()) return;
        File[] files = f.listFiles();
        for (File file : files) {
            if (file.getName().endsWith(".jpl")) {
                Debug.message("Loading javascript plugin " + file.getAbsolutePath());
                JSPlugin p = new JSPlugin(file);
                JSPlugin op = jsplugins.get(file.getName());
                if (op != null) {
                    Version vold = new Version(op.getVersion());
                    Version vnew = new Version(p.getVersion());
                    if (vold.compareTo(vnew) < 0) {
                        jsplugins.put(file.getName(), p);
                    }
                } else {
                    jsplugins.put(file.getName(), p);
                }
            }
        }
    }
}
