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

import org.uecide.editors.*;
import org.uecide.plugin.*;
import org.uecide.builtin.BuiltinCommand;
import org.uecide.varcmd.VariableCommand;

import java.util.regex.*;

import jssc.*;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.jar.*;
import java.util.zip.*;
import java.text.*;
import java.lang.reflect.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import org.uecide.Compiler;

import javax.script.*;

/**
 * The sketch class is the heart of the IDE.  It manages not only what files a
 * sketch consists of, but also deals with compilation of the sketch and uploading
 * the sketch to a target board.
 */
public class Sketch {

    public Context ctx;

    public String sketchName;       // The name of the sketch
    public File sketchFolder;       // Where the sketch is
    public Editor editor = null;    // The editor window the sketch is loaded in
    public File buildFolder;        // Where to build the sketch
    public String uuid;             // A globally unique ID for temporary folders etc

    public String percentageFilter = null;
    public float percentageMultiplier = 1.0f;

    public String percentageCharacter = null;
    public int percentageCharacterCount = 0;

    boolean isUntitled;             // Whether or not the sketch has been named

    // These are all the plugins selected for this sketch in order to compile
    // the sketch and upload it.
    Core selectedCore = null;
    Board selectedBoard = null;
    String selectedBoardName = null;
    Compiler selectedCompiler = null;
    Programmer selectedProgrammer = null;
    CommunicationPort selectedDevice = null;

    boolean terminateExecution = false;

    Process runningProcess = null;

    // This lot is what the sketch consists of - the list of files, libraries, parameters etc.
    public ArrayList<File> sketchFiles = new ArrayList<File>();

    public HashMap<String, Library> importedLibraries = new HashMap<String, Library>();
    public ArrayList<Library> orderedLibraries = new ArrayList<Library>();
    public ArrayList<String> unknownLibraries = new ArrayList<String>();

    TreeMap<String, String> selectedOptions = new TreeMap<String, String>();

    HashMap<File, HashMap<Integer, String>> functionList = new HashMap<File, HashMap<Integer, String>>();

    // Do we want to purge the cache files before building?  This is set by the
    // options system.
    public boolean doPrePurge = false;

    /**************************************************************************
     * CONSTRUCTORS                                                           *
     *                                                                        *
     * The Sketch takes a file, be that as a string or a File object, as the  *
     * only parameter.  The sketch is loaded either from the provided folder, *
     * or the folder the provided file resides in.                            *
     **************************************************************************/

    HashMap<File, HashMap<Integer, String>>lineComments = new HashMap<File, HashMap<Integer, String>>();

    HashMap<String, Integer>keywords = new HashMap<String, Integer>();

    public void setLineComment(File file, int line, String comment) {
        HashMap<Integer, String> comments = lineComments.get(file);

        if(comments == null) {
            comments = new HashMap<Integer, String>();
        }

        comments.put(line, comment);
        lineComments.put(file, comments);
    }

    public String getLineComment(File file, int line) {
        HashMap<Integer, String> comments = lineComments.get(file);

        if(comments == null) {
            return null;
        }

        return comments.get(line);
    }

    public HashMap<Integer, String> getLineComments(File file) {
        return lineComments.get(file);
    }

    public void clearLineComments() {
        lineComments = new HashMap<File, HashMap<Integer, String>>();
    }

    public Sketch() {
        // This is only for dummy use. We don't want to do ANYTHING!
        ctx = new Context();
        ctx.setSketch(this);
    }

    public Sketch(String path) {
        this(new File(path));
    }

    public Sketch(File path) {
        this(path, null);
    }

    public Sketch(File path, Editor e) {
        uuid = UUID.randomUUID().toString();
        ctx = new Context();
        ctx.setSketch(this);

        isUntitled = false;

        if(path == null) {
            path = createUntitledSketch();
        }

        if (e != null) {
            attachToEditor(e);
        }

        sketchFolder = path;

        if(!path.exists()) {
            path.mkdirs();
            createBlankFile(path.getName() + ".ino");
        }

        String fn = path.getName().toLowerCase();

        if(fn.endsWith(".ino") || fn.endsWith(".pde")) {
            String pathName = path.getName();

            File oldPath = path;

            String inoName = pathName.substring(0, pathName.length() - 4);
            path = path.getParentFile();
            if (!path.getName().equals(inoName)) {
                File nsf = new File(path, path.getName() + ".ino");
                if (!nsf.exists()) {
                    nsf = new File(path, path.getName() + ".pde");
                    if (!nsf.exists()) {
                        if (Base.yesno("Import Sketch?", "This sketch file isn't in a properly named sketch folder.\nImport this sketch into your sketchbook area?")) {
                            File sb = new File(Base.getSketchbookFolder(), inoName);
                            sb.mkdirs();
                            File inof = new File(sb, inoName + ".ino");
                            Base.copyFile(oldPath, inof);
                            path = sb;
                        } else {
                            path = createUntitledSketch();
                            sketchFolder = path;
                            sketchName = sketchFolder.getName();
                            path.mkdirs();
                            createBlankFile(path.getName() + ".ino");
                            inoName = path.getName();
                        }
                    }
                }
            }
        }

        sketchFolder = path;
        sketchName = sketchFolder.getName();

        loadSketchFromFolder(path);
        buildFolder = createBuildFolder();

    }

    /**************************************************************************
     * OBJECTS                                                                *
     *                                                                        *
     * Internal object management.  All the routines to deal with boards,     *
     * cores, compilers, etc, and what happens when you switch from one to    *
     * another.                                                               *
     **************************************************************************/

    // Get the board as a string
    public String getBoardName() {
        return selectedBoardName;
    }

    // Get the board as a Board object
    public Board getBoard() {
        return selectedBoard;
    }

    // Look up a board in the global boards map and set the board appropriately
    public void setBoard(String board) {
        if(board == null || board.equals("")) {
            Debug.message("NULL or blank board when calling setBoard");
            return;
        }

        Board b = Base.getBoard(board);
        setBoard(b);
    }

    // Set the current board.  Also looks up the last settings used for that board
    // and propogates them onwards (core, compiler, etc).
    public void setBoard(Board board) {
        Debug.message("Selecting board " + board);

        if(board == null) {
            Debug.message("NULL board when calling setBoard");
            return;
        }

        ctx.setBoard(board);

        selectedBoard = board;
        selectedBoardName = selectedBoard.getName();
        Preferences.set("board", board.getName());
        String boardsCore = Preferences.get("board." + selectedBoard.getName() + ".core");
        Core core = null;

        if(boardsCore != null) {
            Debug.message("Board core is " + boardsCore);
            core = Base.cores.get(boardsCore);
        }

        if(core == null) {
            Debug.message("Board core is null!");
            core = board.getCore();
        }

        Debug.message("Board's core is " + core);

        if(core != null) {
            setCore(core);
        }

    }

    public Core getCore() {
        return selectedCore;
    }

    public void setCore(String core) {
        if(core == null || core.equals("")) {
            Debug.message("NULL or blank core when calling setCore");
            return;
        }

        Core c = Base.getCore(core);
        setCore(c);
    }

    public void setSettings() {
        ctx.set("sketch.name", getName());
        ctx.set("sketch.path", getFolder().getAbsolutePath());
    }

    public void setCore(Core core) {
        Debug.message("Selecting core " + core);
        if(core == null) {
            Debug.message("NULL core when calling setCore");
            return;
        }

        ctx.setCore(core);

        selectedCore = core;
        Preferences.set("board." + selectedBoard.getName() + ".core", core.getName());
        String boardsCompiler = Preferences.get("board." + selectedBoard.getName() + ".compiler");

        Compiler compiler = null;

        if(boardsCompiler != null) {
            compiler = Base.compilers.get(boardsCompiler);
        }

        if(compiler == null) {
            compiler = core.getCompiler();
        }

        if(compiler != null) {
            Debug.message("Compiler set to " + compiler);
            setCompiler(compiler);
        } else {
            Debug.message("No compiler available!");
        }

        if(editor != null) {
            editor.updateTree();
        }
    }

    public Compiler getCompiler() {
        return selectedCompiler;
    }

    public void setCompiler(String compiler) {
        if(compiler == null || compiler.equals("")) return;

        Compiler c = Base.getCompiler(compiler);
        setCompiler(c);
    }

    public void setCompiler(Compiler compiler) {
        Debug.message("Selecting compiler " + compiler);
        if(compiler == null) {
            return;
        }

        ctx.setCompiler(compiler);

        selectedCompiler = compiler;
        if (selectedBoard == null) {
            return;
        }

        Preferences.set("board." + selectedBoard.getName() + ".compiler", compiler.getName());
        String programmer = Preferences.get("board." + selectedBoard.getName() + ".programmer");

        Debug.message("Found programmer in prefs: " + programmer);
        setProgrammer(programmer);
    }

    public Programmer getProgrammer() {
        return selectedProgrammer;
    }

    public void setProgrammer(String programmer) {
        if (programmer == null) {
            Debug.message("NULL programmer specified");
            setProgrammer((Programmer)null);
            return;
        }
        Programmer p = Base.programmers.get(programmer);
        setProgrammer(p);
    }

    public void setProgrammer(Programmer programmer) {
        if(programmer == null) {
            String cp = getCore().get("default.programmer");
            if (cp != null) {
                programmer = Base.programmers.get(programmer);
            }
        }

        if (programmer == null) {
            for (Programmer p : Base.programmers.values()) {
                if (p.worksWith(getBoard())) {
                    programmer = p;
                    break;
                }
            }
        }

        if (programmer != null) {
            Preferences.set("board." + getBoard().getName() + ".programmer", programmer.getName());
        }

        selectedProgrammer = programmer;
        ctx.setProgrammer(programmer);

        if(editor != null) editor.updateAll();
    }

    public CommunicationPort getDevice() {
        return selectedDevice;
    }

    public void setDevice(String name) {
        for (CommunicationPort dev : Base.communicationPorts) {
            if (dev.toString().equals(name)) {
                setDevice(dev);
                return;
            }
        }
        if (Base.isPosix()) {
            File f = new File(name);
            if (f.exists() && !f.isDirectory()) {
                SerialCommunicationPort p = new SerialCommunicationPort(f.getAbsolutePath());
                Base.communicationPorts.add(p);
                setDevice(p);
            }
        }
    }

    public void setDevice(CommunicationPort dev) {
        selectedDevice = dev;
        ctx.setDevice(dev);
        if (editor != null) {
            editor.updateAll();
        }
        if (getBoard() != null) {
            if (getDevice() != null) {
                Preferences.set("board." + getBoard().getName() + ".port", getDevice().toString());
            }
        }
    }

    void attachToEditor(Editor e) {
        editor = e;
        editor.setTitle(Base.theme.get("product.cap") + " | " + sketchName);
        ctx.setEditor(e);
        if (editor != null) {
            PropertyFile props = ctx.getMerged();
            if (props.get("sketch.window.x") != null) {
                editor.setXPosition(props.getInteger("sketch.window.x"));
            }
            if (props.get("sketch.window.y") != null) {
                editor.setYPosition(props.getInteger("sketch.window.y"));
            }
            if (props.get("sketch.window.w") != null) {
                editor.setWidth(props.getInteger("sketch.window.w"));
            }
            if (props.get("sketch.window.h") != null) {
                editor.setHeight(props.getInteger("sketch.window.h"));
            }
        }
    }



    /**************************************************************************
     * FILE HANDLING                                                          *
     *                                                                        *
     * These are all routines to deal with files - file reading, writing,     *
     * copying, etc.                                                          *
     **************************************************************************/

    public void createNewLibrary(String libname) {
        try {
            libname = libname.replaceAll(" ", "_");
            File libs = new File(sketchFolder, "libraries");
            libs.mkdirs();
            File lib = new File(libs, libname);
            lib.mkdirs();
            File header = new File(lib, libname + ".h");
            File code = new File(lib, libname + ".cpp");

            PrintWriter out = new PrintWriter(header);
            String capname = libname.toUpperCase();
            out.println("#ifndef _" + capname + "_H");
            out.println("#define _" + capname + "_H");
            out.println("");
            out.println("#if (ARDUINO >= 100)");
            out.println("# include <Arduino.h>");
            out.println("#else");
            out.println("# include <WProgram.h>");
            out.println("#endif");
            out.println("");
            out.println("class " + libname + " {");
            out.println("\tprivate:");
            out.println("\t\t// Private functions and variables here");
            out.println("\tpublic:");
            out.println("\t\t" + libname + "();");
            out.println("\t\tvoid begin();");
            out.println("};");
            out.println("");
            out.println("#endif");
            out.close();

            out = new PrintWriter(code);
            out.println("#include <" + libname + ".h>");
            out.println("");
            out.println(libname + "::" + libname + "() {");
            out.println("\t// Constructor code here");
            out.println("}");
            out.println("");
            out.println("void " + libname + "::begin() {");
            out.println("\t// Initialization code here");
            out.println("}");
            out.close();

            if(editor != null) {
                editor.insertStringAtStart(getMainFile(), "#include <" + libname + ".h>\n");
                editor.updateTree();
                editor.openOrSelectFile(header);
                editor.openOrSelectFile(code);
            }
        } catch(Exception e) {
            Base.error(e);
        }
    }

    public void createNewFile(String filename) {
        if(filename.endsWith(".lib")) {
            createNewLibrary(filename.substring(0, filename.lastIndexOf(".")));
            return;
        }

        File f = createBlankFile(filename);
        sketchFiles.add(f);

        if(editor != null) {
            editor.updateTree();
            editor.openOrSelectFile(f);
        }
    }

    public File createBlankFile(String fileName) {
        File f = new File(sketchFolder, fileName);

        if(f.exists()) {
            return f;
        }

        try {
            f.createNewFile();
        } catch(Exception e) {
        }

        return f;
    }

    public void loadSketchFromFolder(File folder) {
        sketchFolder = folder;

        if(!isUntitled()) {
            Base.updateMRU(sketchFolder);
        }

        File fileList[] = sketchFolder.listFiles();
        if (fileList != null) {

            Arrays.sort(fileList);

            for(File f : fileList) {
                switch(FileType.getType(f)) {
                case FileType.SKETCH:
                case FileType.CSOURCE:
                case FileType.CPPSOURCE:
                case FileType.ASMSOURCE:
                case FileType.HEADER:
                    loadFile(f);
                }
            }
        }

        setBoard(Preferences.get("board"));

        if(selectedBoard != null) {
            String portName = Preferences.get("board." + selectedBoard.getName() + ".port");

            if (portName != null) {

                CommunicationPort p = null;

                for (CommunicationPort dev : Base.communicationPorts) {
                    if (dev.toString().equals(portName)) {
                        p = dev;
                        break;
                    }
                }

                if (p == null) {
                    // Let's add a missing device - it can always be removed later.
                    if (portName.startsWith("ssh://")) {
//                        SSHCommunicationPort sp = new SSHCommunicationPort(portName, selectedBoard);
//                        Base.communicationPorts.add(sp);
//                        p = sp;
                    } else {
                        SerialCommunicationPort sp = new SerialCommunicationPort(portName);
                        Base.communicationPorts.add(sp);
                        p = sp;
                    }
                }

                setDevice(p);
            } else {
                setDevice((CommunicationPort)null);
            } 
        } else {
            setDevice((CommunicationPort)null);
        }

        updateSketchConfig();

        updateLibraryList();

        if(editor != null) {
            editor.fireEvent(UEvent.SKETCH_OPEN);
        }

        loadConfig();
    }

    public boolean loadFile(File f) {
        if(!f.exists()) {
            return false;
        }

        sketchFiles.add(f);
        return true;
    }

    public File getFileByName(String filename) {
        for(File f : sketchFiles) {
            if(f.getName().equals(filename)) {
                return f;
            }
        }

        return null;
    }

    public String[] getFileNames() {
        String[] out = new String[sketchFiles.size()];
        int i = 0;

        for(File f : sketchFiles) {
            out[i++] = f.getName();
        }

        Arrays.sort(out);
        return out;
    }

    public File createBuildFolder() {
        if(Preferences.getBoolean("compiler.buildinsketch") || Base.cli.isSet("force-local-build") || Base.cli.isSet("cli")) {
            if(!parentIsProtected()) {
                File f = new File(sketchFolder, "build");

                if(!f.exists()) {
                    f.mkdirs();
                }

                return f;
            }
        }

        String name = "build-" + uuid;
        Debug.message("Creating build folder " + name);
        File f = new File(Base.getTmpDir(), name);

        if(!f.exists()) {
            f.mkdirs();
        }

        f.deleteOnExit();
        return f;
    }

    public File createUntitledSketch() {
        int num = 0;
        isUntitled = true;
        File f = null;

        do {
            num++;
            String name = "untitled" + Integer.toString(num);
            f = new File(Base.getTmpDir(), name);
        } while(f.exists());

        f.deleteOnExit();
        return f;
    }

    public File getMainFile() {
        File f = getFileByName(sketchName + ".ino");

        if(f == null) {
            f = getFileByName(sketchName + ".pde");
        }

        return f;
    }

    public String getMainFilePath() {
        File f = getMainFile();

        if(f == null) {
            return "";
        }

        return f.getAbsolutePath();
    }

    public boolean isModified() {
        // If we have no editor then it's impossible for a file
        // to be modified.
        if(editor == null) {
            return false;
        }

        boolean modified = false;
        int tabs = editor.getTabCount();

        for(int tab = 0; tab < tabs; tab++) {
            EditorBase ed = editor.getTab(tab);

            if(ed.isModified()) {
                modified = true;
            }
        }

        return modified;
    }

    static public boolean isSanitaryName(String name) {
        return sanitizeName(name).equals(name);
    }

    static public String sanitizeName(String origName) {
        char c[] = origName.toCharArray();
        StringBuffer buffer = new StringBuffer();

        for(int i = 0; i < c.length; i++) {
            if(((c[i] >= '0') && (c[i] <= '9')) ||
                    ((c[i] >= 'a') && (c[i] <= 'z')) ||
                    ((c[i] >= 'A') && (c[i] <= 'Z'))) {
                buffer.append(c[i]);
            } else {
                buffer.append('_');
            }
        }

        if(buffer.length() > 63) {
            buffer.setLength(63);
        }

        return buffer.toString();
    }

    public File getFolder() {
        return sketchFolder;
    }


    // Get the content of a file.  If it's open in the editor then
    // return the content of the editor, otherwise load the data from
    // the file.
    public String getFileContent(File f) {
        return getFileContent(f, false);
    }
    public String getFileContent(File f, boolean forceOffDisk) {
        if(!f.exists()) {
            return "";
        }

        if(f.isDirectory()) {
            return "";
        }

        if(!forceOffDisk) {
            if(editor != null) {
                int tabNumber = editor.getTabByFile(f);

                if(tabNumber > -1) {
                    EditorBase eb = editor.getTab(tabNumber);
                    return eb.getText();
                }
            }
        }

        InputStream    fis;
        BufferedReader br;
        String         line;
        StringBuilder sb = new StringBuilder();

        try {

            fis = new FileInputStream(f);
            br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));

            while((line = br.readLine()) != null) {
                sb.append(line + "\n");
            }

            br.close();
        } catch(Exception e) {
            error(e);
        }

        return sb.toString();
    }

    // Write the content of a file to a folder.
    public void writeFileToFolder(File file, File folder) {
        File dest = new File(folder, file.getName());

        try {
            PrintWriter out = new PrintWriter(dest);
            out.print(getFileContent(file));
            out.close();
        } catch(Exception e) {
            error(e);
        }
    }

    // Write out any locally cached copies of files (in editors)
    // to the disk.
    public void saveAllFiles() {
        // If we don't have an editor then we don't have any cached files.
        if(editor == null) {
            return;
        }

        for(File f : sketchFiles) {
            int tab = editor.getTabByFile(f);

            if(tab > -1) {
                EditorBase eb = editor.getTab(tab);

                if(eb.isModified()) {
                    eb.setModified(false);
                    writeFileToFolder(f, sketchFolder);
                }
            }
        }

        Base.updateMRU(sketchFolder);

        if(editor != null) {
            editor.updateMenus();
        }
    }

    /**************************************************************************
     * COMPILATION                                                            *
     *                                                                        *
     * This section deals with the compilation of the sketch.  It has all the *
     * routines needed to convert a sketch into C++ or C source code, and to  *
     * compile, link, and archive it all.                                     *
     **************************************************************************/

    TreeMap<File, String> cleanedFiles;

    public boolean cleanFiles() {
        cleanedFiles = new TreeMap<File, String>();

        for(File f : sketchFiles) {
            if(FileType.getType(f) == FileType.SKETCH) {
                String data = getFileContent(f);
                String cleanData = stripComments(data);
                String[] lines = cleanData.split("\n");
                int lineno = 1;
                StringBuilder out = new StringBuilder();

                for(String line : lines) {
                    line = line.trim();
                    out.append(line + "\n");
                }

                cleanedFiles.put(f, out.toString());
            } else {
                cleanedFiles.put(f, getFileContent(f));
            }
        }

        return true;
    }

    public int linesInString(String l) {
        return l.split("\n").length;
    }

    public String stripBlock(String in, String start, String end) {
        String regexp;
        String mid;

        if(start.equals(end)) {
            mid = start;
        } else {
            mid = start + end;
        }

        if(start == "{") start = "\\" + start;

        if(end == "}") end = "\\" + end;

        regexp = "(?s)" + start + "[^" + mid + "]*" + end;

        boolean done = false;
        String out = in;
        int pass = 1;

        while(!done) {
            String rep = out.replaceFirst(regexp, "");
            int lines = linesInString(out);
            int newLines = linesInString(rep);
            int rets = lines - newLines;
            String retStr = "";

            for(int i = 0; i < rets; i++) {
                retStr += "\n";
            }

            rep = out.replaceFirst(regexp, retStr);

            String[] left = out.split("\n");
            String[] right = out.split("\n");

            if(rep.equals(out)) {
                done = true;
            } else {
                out = rep;
            }

            pass++;
        }

        return out;
    }

    // Locate a library by its header file.  Scan through all the known
    // libraries for the right file.  First pass looks for libraries named
    // after the included file.  Second pass looks through all the
    // libraries for that include file.
    public Library findLibrary(String filename) {
        int dot = filename.lastIndexOf(".");

        String trimmedName = filename;

        if(dot >= 0) {
            trimmedName = filename.substring(0, dot);
        }

        // If the include file is part of the sketch, then we're not
        // interested in it.

        if(getFileByName(filename) != null) {
            return null;
        }


        // First look in the sketch libs folder if it exists

        File libfolder = getLibrariesFolder();

        if(libfolder.exists() && libfolder.isDirectory()) {
            File[] files = libfolder.listFiles();

            for(File f : files) {
                if(f.getName().equals(trimmedName)) {
                    File testFile = new File(f, filename);

                    if(f.exists()) {
                        return new Library(testFile, sketchName, "all");
                    }
                }
            }
        }

        if (getCore() == null) {
            return null;
        }
        Library lib = Library.getLibraryByInclude(filename, getCore().getName());
        if (lib == null) {
            if (getCore().get("core.alias") != null) {
                String[] aliases = ctx.parseString(getCore().get("core.alias")).split("::");
                for (String alias : aliases) {
                    lib = Library.getLibraryByInclude(filename, alias);
                    if (lib != null) {
                        return lib;
                    }
                }
            }
        }
        return lib;
    }

    public TreeSet<String> getAllFunctionNames() {
        TreeSet<String>funcs = new TreeSet<String>();
        for (HashMap<Integer, String> ent : functionList.values()) {
            funcs.addAll(ent.values());
        }
        return funcs;
    }

    public HashMap<Integer, String> getFunctionsForFile(File f) {
        return functionList.get(f);
    }

    public HashMap<Integer, String> findLabels(String data) {
        HashMap<Integer, String>labels = new HashMap<Integer, String>();

        if(data == null) return labels;

        if(data.equals("")) return labels;

        String[] lines = data.split("\n");
        ArrayList<String>ents = new ArrayList<String>();

        // First find any entries.
        Pattern epat = Pattern.compile("\\.ent\\s+([^\\s]+)");

        for(String line : lines) {
            Matcher m = epat.matcher(line);

            if(m.find()) {
                ents.add(m.group(1));
            }
        }

        // Now match them to labels.
        int lineno = 0;

        for(String line : lines) {
            lineno++;
            line = line.trim();

            if(!line.contains(":")) {
                continue;
            }

            line = line.substring(0, line.indexOf(":"));

            if(ents.indexOf(line) > -1) {
                labels.put(lineno - 1, line);
            }
        }

        return labels;
    }

    public void findAllFunctions() {
        if(editor == null) {
            return;
        }

        functionList = new HashMap<File, HashMap<Integer, String>>();
        String data;
        HashMap<Integer, String>funcs = null;

        for(File f : sketchFiles) {
            switch(FileType.getType(f)) {
            case FileType.SKETCH:
            case FileType.CSOURCE:
            case FileType.CPPSOURCE:
                data = getFileContent(f);
                data = stripComments(data);
                funcs = findFunctions(data);
                functionList.put(f, funcs);
                break;

            case FileType.ASMSOURCE:
                data = getFileContent(f);
                funcs = findLabels(data);
                functionList.put(f, funcs);
                break;
            }
        }
    }

    public HashMap<Integer, String> findFunctions(String in) {
        HashMap<Integer, String> funcs = new HashMap<Integer, String>();

        String out = in.replaceAll("\\\\.", "");

        out = out.replaceAll("'[^'\\n\\r]*'", "");
        out = out.replaceAll("\"[^\"\\n\\r]*\"", "");
        out = stripBlock(out, "{", "}");
        String[] s = out.split("\n");
        StringBuilder decimated = new StringBuilder();
        boolean continuation = false;
        int lineno = 0;

        for(String line : s) {
            line = line.trim();
            lineno++;

            if(line.equals("")) {
                continue;
            }

            if(line.startsWith("#")) {
                continue;
            }

            if(continuation) {
                decimated.append(line);

                if(line.endsWith(")")) {
                    continuation = false;
                    funcs.put(lineno - 1, decimated.toString());
                    decimated = new StringBuilder();
                }

                continue;
            }

            if(line.endsWith(";")) {
                continue;
            }

            if(line.indexOf("(") == -1) {
                continue;
            }

            Pattern p = Pattern.compile("[a-zA-Z0-9_\\*]+\\s+[a-zA-Z0-9_\\*]+\\s*\\(");
            Matcher m = p.matcher(line);

            if(m.find()) {
                decimated.append(line);

                if(!line.endsWith(")")) {
                    continuation = true;
                } else {
                    funcs.put(lineno - 1, decimated.toString());
                    decimated = new StringBuilder();
                }
            }
        }

        return funcs;
    }

    public static final int LIB_PENDING = 0;
    public static final int LIB_PROCESSED = 1;
    public static final int LIB_SYSTEM = 2;

    ArrayList<String> includeOrder = new ArrayList<String>();

    public synchronized void updateLibraryList() {
        PropertyFile props = ctx.getMerged();
        cleanFiles();

        importedLibraries = new HashMap<String, Library>();
        unknownLibraries = new ArrayList<String>();
        includeOrder = new ArrayList<String>();
        HashMap<String, Integer> inclist = new HashMap<String, Integer>();
        Pattern inc = Pattern.compile("^#\\s*include\\s+[<\"](.*)[>\"]");

        for(File f : cleanedFiles.keySet()) {
            try {
                String data = cleanedFiles.get(f);
                String fname = f.getName();
                if(FileType.getType(f) == FileType.SKETCH) {
                    String ext = ctx.parseString(props.get("build.extension"));
                    if (ext == null) {
                        ext = "cpp";
                    }
                    fname = "deps-temp." + ext;
                } else {
                    String[] bits = fname.split("\\.");
                    fname = "deps-temp." + bits[bits.length-1];
                }

                File tempFile = new File(getBuildFolder(), fname);
                PrintWriter pw = new PrintWriter(tempFile);
                pw.print(data);
                pw.close();
                
                boolean haveHunted = huntForLibraries(tempFile, importedLibraries, unknownLibraries);

                tempFile.delete();

                String lines[] = data.split("\n");

                for(String line : lines) {
                    Matcher match = inc.matcher(line.trim());

                    if (match.find()) {
                        if (!haveHunted) {
                            Library lib = findLibrary(match.group(1));
                            if (lib != null) {
                                importedLibraries.put(match.group(1), lib);
                            } else {
                                if(unknownLibraries.indexOf(match.group(1)) == -1) {
                                    unknownLibraries.add(match.group(1));
                                }
                            }
                        }
                        if(includeOrder.indexOf(match.group(1)) == -1) {
                            includeOrder.add(match.group(1));
                        }
                    }
                }
            } catch (Exception e) {
            }
        }

        orderedLibraries = new ArrayList<Library>();
        for (String inclib : includeOrder) {
            if (importedLibraries.get(inclib) != null) {
                orderedLibraries.add(importedLibraries.get(inclib));
            }
        }

        if(editor != null) {
            editor.updateLibrariesTree();
        }
    }

    public synchronized boolean prepare() {
        PropertyFile props = ctx.getMerged();

        if(getBoard() == null) {
            error(Base.i18n.string("err.noboard"));
            return false;
        }

        if(getCore() == null) {
            error(Base.i18n.string("err.nocore"));
            return false;
        }

        if(Preferences.getBoolean("compiler.purge")) {
            cleanBuild();
        }

        updateSketchConfig();
        updateLibraryList();

        if (Preferences.getBoolean("compiler.generate_makefile")) {
            if (props.get("makefile.template") != null) {
                generateMakefile();
            }
        }

        // We now have the data.  Now, if we're combining files, we shall do it
        // in this map.

        if(Preferences.getBoolean("compiler.combine_ino") || Base.cli.isSet("force-join-files")) {
            File mainFile = getMainFile();
            StringBuilder out = new StringBuilder();

            if(!Preferences.getBoolean("compiler.disableline")) out.append("#line 1 \"" + mainFile.getAbsolutePath().replaceAll("\\\\", "\\\\\\\\") + "\"\n");

            out.append(cleanedFiles.get(mainFile));

            for(String fn : getFileNames()) {
                File f = getFileByName(fn);

                if(FileType.getType(f) == FileType.SKETCH) {
                    if(f != mainFile) {
                        String data = cleanedFiles.get(f);

                        if(!Preferences.getBoolean("compiler.disableline")) out.append("#line 1 \"" + f.getAbsolutePath().replaceAll("\\\\", "\\\\\\\\") + "\"\n");

                        out.append(data);
                        cleanedFiles.remove(f);
                    }
                }
            }

            cleanedFiles.put(mainFile, out.toString());
        }

        // We now have a has of all the file data, with any ino or pde
        // files combined into the main file entry.  The old ino and pde
        // entries have been removed.  Now to parse any INO or PDE files
        // for prototypes etc and do the Arduino fudging.  We should
        // do it properly, really, looking for the first function in the
        // file and placing the prototypes directly before that.

        Pattern pragma = Pattern.compile("^#pragma\\s+parameter\\s+([^=]+)\\s*=\\s*(.*)\\s*$");
        Pattern paramsplit = Pattern.compile("(?:\"[^\"]*\"|[^\\s\"])+");

        for(File f : cleanedFiles.keySet()) {
            if(FileType.getType(f) == FileType.SKETCH) {
                HashMap<Integer, String> funcs = findFunctions(cleanedFiles.get(f));

                Integer ffLineNo = Integer.MAX_VALUE;
                String firstFunction = "";

                for(Integer i : funcs.keySet()) {
                    if(i < ffLineNo) {
                        ffLineNo = i;
                        firstFunction = funcs.get(i);
                    }
                }

                int line = 1;
                StringBuilder munged = new StringBuilder();

                for(String l : cleanedFiles.get(f).split("\n")) {
                    if(!Preferences.getBoolean("compiler.disable_prototypes")) {
                        if(l.trim().startsWith(firstFunction)) {
                            for(String func : funcs.values()) {
                                func = func.replaceAll("=[^,)]+", "");
                                munged.append(func + ";\n");
                            }

                            if(!Preferences.getBoolean("compiler.disableline")) munged.append("#line " + line + " \"" + f.getAbsolutePath().replaceAll("\\\\", "\\\\\\\\") + "\"\n");
                        }
                    }

                    Matcher mtch = pragma.matcher(l.trim());

                    if(mtch.find()) {
                        l = "// " + l;
                        Matcher part = paramsplit.matcher(mtch.group(2).trim());
                        String parms = "";

                        while(part.find()) {
                            if(parms.equals("") == false) {
                                parms += "::";
                            }

                            parms += part.group(0);
                        }

                        ctx.set(mtch.group(1), parms);
                    }

                    munged.append(l + "\n");

                    if(!l.startsWith("#line 1 ")) {
                        line++;
                    }

                    cleanedFiles.put(f, munged.toString());
                }
            }
        }

        // Now we have munged the files, let's dump them into files in the
        // build folder ready for compilation.

        try {
            for(File f : cleanedFiles.keySet()) {
                if(FileType.getType(f) == FileType.SKETCH) {
                    String ext = ctx.parseString(props.get("build.extension"));

                    if(ext == null) {
                        ext = "cpp";
                    }

                    String newFileName = f.getName().substring(0, f.getName().lastIndexOf(".")) + "." + ext;
                    File newFile = new File(buildFolder, newFileName);
                    PrintWriter pw = new PrintWriter(newFile);
                    pw.write("/***************************************************\n");
                    pw.write(" * ! ! ! !        I M P O R T A N T        ! ! ! ! *\n");
                    pw.write(" *                                                 *\n");
                    pw.write(" * THIS FILE IS AUTOMATICALLY GENERATED AND SHOULD *\n");
                    pw.write(" * NOT BE DIRECTLY EDITED. INSTEAD EDIT THE INO OR *\n");
                    pw.write(" * PDE FILE THIS FILE IS GENERATED FROM!!!         *\n");
                    pw.write(" ***************************************************/\n");
                    pw.write("\n");

                    if(props.get("core.header") != null) {
                        boolean gotHeader = false;
                        String hdr = ctx.parseString(props.get("core.header"));
                        String[] lines = cleanedFiles.get(f).split("\n");
                        Pattern inc = Pattern.compile("^#\\s*include\\s+[<\"](.*)[>\"]");
                        for (String l : lines) {
                            Matcher m = inc.matcher(l);
                            if (m.find()) {
                                if (m.group(1).equals(hdr)) {
                                    gotHeader = true;
                                }
                            }
                        }
                        
                        if (!gotHeader) {
                            pw.write("#include <" + hdr + ">\n");
                        }
                    }

                    if(!Preferences.getBoolean("compiler.combine_ino") || Base.cli.isSet("force-join-files")) {
                        if(!Preferences.getBoolean("compiler.disableline")) pw.write("#line 1 \"" + f.getAbsolutePath().replaceAll("\\\\", "\\\\\\\\") + "\"\n");
                    }

                    pw.write(cleanedFiles.get(f));
                    pw.close();
                } else {
                    File buildFile = new File(buildFolder, f.getName());
                    PrintWriter pw = new PrintWriter(buildFile);
                    pw.write("/***************************************************\n");
                    pw.write(" * ! ! ! !        I M P O R T A N T        ! ! ! ! *\n");
                    pw.write(" *                                                 *\n");
                    pw.write(" * THIS FILE IS AUTOMATICALLY GENERATED AND SHOULD *\n");
                    pw.write(" * NOT BE DIRECTLY EDITED. INSTEAD EDIT THE SOURCE *\n");
                    pw.write(" * FILE THIS FILE IS GENERATED FROM!!!             *\n");
                    pw.write(" ***************************************************/\n");

                    if(!Preferences.getBoolean("compiler.disableline")) pw.write("#line 1 \"" + f.getAbsolutePath().replaceAll("\\\\", "\\\\\\\\") + "\"\n");

                    pw.write(cleanedFiles.get(f));
                    pw.close();
                }
            }
        } catch(Exception e) {
            error(e);
        }

        if(editor != null) {
            editor.updateOutputTree();
        }

        return true;
    }

    public String stripComments(String data) {
        int cpos = 0;
        boolean inString = false;
        boolean inEscape = false;
        boolean inMultiComment = false;
        boolean inSingleComment = false;

        // We'll work through the string a character at a time pushing it on to the
        // string builder if we want it, or pushing a space if we don't.

        StringBuilder out = new StringBuilder();

        while (cpos < data.length()) {
            char thisChar = data.charAt(cpos);
            char nextChar = ' ';
            if (cpos < data.length() - 1) {
                nextChar = data.charAt(cpos + 1);
            }

            // Don't process any escaped characters - just add them verbatim.
            if (thisChar == '\\') {
                if (!inSingleComment && !inMultiComment)
                    out.append(thisChar);
                cpos++;
                if (cpos < data.length()) {
                    if (!inSingleComment && !inMultiComment)
                        out.append(data.charAt(cpos));
                    cpos++;
                }
                continue;
            }

            // If we're currently in a string then keep moving on until the end of the string.
            // If we hit the closing quote we still want to move on since it'll start a new
            // string otherwise.
            if (inString) {
                out.append(thisChar);
                if (thisChar == '"') {
                    inString = false;
                }
                cpos++;
                continue;
            }
            
            // If we're in a single line comment then keep skipping until we hit the end of the line.
            if (inSingleComment) {
                if (thisChar == '\n') {
                    out.append(thisChar);
                    inSingleComment = false;
                    cpos++;
                    continue;
                }
                cpos++;
                continue;
            }

            // If we're in a multi-line comment then keep skipping until we
            // hit the end of comment sequence.  Preserve newlines.
            if (inMultiComment) {
                if  (thisChar == '*' && nextChar == '/') {
                    inMultiComment = false;
                    cpos++;
                    cpos++;
                    continue;
                }
                if (thisChar == '\n') {
                    out.append(thisChar);
                    cpos++;
                    continue;
                }
                cpos++;
                continue;
            }

            // Is this the start of a quote?
            if (thisChar == '"') {
                out.append(thisChar);
                cpos++;
                inString = true;
                continue;
            }

            // How about the start of a single line comment?
            if (thisChar == '/' && nextChar == '/') {
                inSingleComment = true;
                out.append(" ");
                cpos++;
                continue;
            }
  
            // The start of a muti-line comment?
            if (thisChar == '/' && nextChar == '*') {
                inMultiComment = true;
                out.append(" ");
                cpos++;
                continue;
            }

            // None of those? Then let's just append.
            out.append(thisChar);
            cpos++;
        }
                
        return out.toString();
    }

    public boolean upload() {
        ctx.executeKey("upload.precmd");
        boolean ret = programFile(getProgrammer(), sketchName);
        ctx.executeKey("upload.postcmd");
        return ret;
    }

    public boolean performSerialReset(boolean dtr, boolean rts, int speed, int predelay, int delay, int postdelay) {
        if (!Base.isQuiet()) ctx.bullet("Resetting board.");
        try {
            CommunicationPort port = ctx.getDevice();
            if (port instanceof SerialCommunicationPort) {
                SerialCommunicationPort sport = (SerialCommunicationPort)port;
                if (!sport.openPort()) {
                    ctx.error("Error: " + sport.getLastError());
                    return false;
                }
                sport.setDTR(false);
                sport.setRTS(false);
                Thread.sleep(predelay);
                sport.setDTR(dtr);
                sport.setRTS(rts);
                Thread.sleep(delay);
                sport.setDTR(false);
                sport.setRTS(false);
                sport.closePort();
                System.gc();
                Thread.sleep(postdelay);
            }
        } catch (Exception e) {
            ctx.error(e);
            return false;
        }
        return true;
    }

    public boolean performBaudBasedReset(int b, int predelay, int delay, int postdelay) {
        if (!Base.isQuiet()) ctx.bullet("Resetting board.");
        try {
            CommunicationPort port = ctx.getDevice();
            if (port instanceof SerialCommunicationPort) {
                SerialCommunicationPort sport = (SerialCommunicationPort)port;
                if (!sport.openPort()) {
                    ctx.error("Error: " + sport.getLastError());
                    return false;
                }
                sport.setDTR(false);
                sport.setRTS(false);
                Thread.sleep(predelay);
                sport.setDTR(true);
                sport.setRTS(true);
                if (!sport.setSpeed(b)) {
                    ctx.error("Error: " + sport.getLastError());
                }
                Thread.sleep(delay);
                sport.setDTR(false);
                sport.setRTS(false);
                sport.closePort();
                Thread.sleep(postdelay);
                System.gc();
            }
        } catch (Exception e) {
            ctx.error(e);
            return false;
        }
        return true;
    }

    public boolean build() {
        if(Preferences.getBoolean("editor.external.command")) {
            //reloadAllFiles();
        }

        if(getBoard() == null) {
            error(Base.i18n.string("err.noboard"));
            return false;
        }

        if(getCore() == null) {
            error(Base.i18n.string("err.nocore"));
            return false;
        }

        if(getCompiler() == null) {
            error(Base.i18n.string("err.nocompiler"));
            return false;
        }

        if (!Base.isQuiet()) heading(Base.i18n.string("msg.compiling"));

        if (!Base.isQuiet()) bullet(Base.i18n.string("msg.preprocessing"));
        try {
            if(!prepare()) {
                error(Base.i18n.string("err.compiling.failed"));
                setCompilingProgress(0);
                return false;
            }
        } catch(Exception e) {
            error(e);
            setCompilingProgress(0);
            return false;
        }

        boolean done = compile();
        setCompilingProgress(0);
        return done;
    }

    public boolean saveAs(File newPath) {
        if(newPath.exists()) {
            return false;
        }

        Debug.message("Save as " + newPath.getAbsolutePath());
        newPath.mkdirs();
        File newMainFile = new File(newPath, newPath.getName() + ".ino");
        File oldMainFile = getMainFile();

        // First let's copy the contents of the existing sketch folder, renaming the main file.

        File[] files = sketchFolder.listFiles();

        Base.removeDescendants(buildFolder);

        for(File f : files) {
            if(f.equals(oldMainFile)) {
                Debug.message("Copy main file " + f.getAbsolutePath() + " to " + newMainFile.getAbsolutePath());
                Base.copyFile(f, newMainFile);
                continue;
            }

            File dest = new File(newPath, f.getName());

            if(f.isDirectory()) {
                Base.copyDir(f, dest);
                Debug.message("Copy dir " + f.getAbsolutePath() + " to " + dest.getAbsolutePath());
                continue;
            }

            Debug.message("Copy file " + f.getAbsolutePath() + " to " + dest.getAbsolutePath());
            Base.copyFile(f, dest);
        }

        String oldPrefix = sketchFolder.getAbsolutePath();
        Debug.message("Old prefix: " + oldPrefix);
        sketchFolder = newPath;
        sketchName = newPath.getName();
        Debug.message("Sketch name: " + sketchName);
        isUntitled = false;
        // Now we can shuffle the files around in the sketchFiles array.
        // We want to try and keep the indexes in the same order if that
        // is possible, so we'll use a numeric iterator.

        ArrayList<File> newSketchFiles = new ArrayList<File>();

        for(int i = 0; i < sketchFiles.size(); i++) {
            File sf = sketchFiles.get(i);

            if(sf.equals(oldMainFile)) {
                newSketchFiles.add(i, newMainFile);

                if(editor != null) {
                    int tab = editor.getTabByFile(oldMainFile);

                    if(tab > -1) {
                        TabLabel tl = editor.getTabLabel(tab);
                        tl.setFile(newMainFile);
                    }
                }
            } else {
                File newFile = new File(newPath, sf.getName());
                Debug.message("New file name " + newFile.getAbsolutePath());
                newSketchFiles.add(i, newFile);

                if(editor != null) {
                    int tab = editor.getTabByFile(sf);

                    if(tab > -1) {
                        TabLabel tl = editor.getTabLabel(tab);
                        tl.setFile(newFile);
                    }
                }
            }
        }

        sketchFiles = newSketchFiles;

        buildFolder = createBuildFolder();

        // Now we have reconstructed the sketch in a new location we can save any
        // changed files from the editor.

        if(editor != null) {
            // Now step through the files looking for any that are open, change the tab's file pointer
            // and save the data.
            for(int tab = 0; tab < editor.getTabCount(); tab++) {
                TabLabel tl = editor.getTabLabel(tab);
                tl.save();
            }

            editor.updateTree();
            editor.setTitle(Base.theme.get("product.cap") + " | " + sketchName);
        }

        save();
        return true;
    }

    public boolean save() {
        // We can't really save it if it's untitled - there's no point.
        if(isUntitled()) {
            return false;
        }

        // Same if it's an example in a protected area:
        if(parentIsProtected()) {
            return false;
        }

        if(Preferences.getBoolean("editor.save.version")) {
            int numToSave = Preferences.getInteger("editor.save.version_num");
            File versionsFolder = new File(sketchFolder, "backup");

            if(!versionsFolder.exists()) {
                versionsFolder.mkdirs();
            }

            // Prune the oldest version if it exists
            File last = new File(versionsFolder, sketchName + "-" + numToSave);

            if(last.exists()) {
                Debug.message("Deleting " + last.getAbsolutePath());
                last.delete();
            }

            for(int i = numToSave - 1; i >= 1; i--) {
                File low = new File(versionsFolder, sketchName + "-" + i);
                File high = new File(versionsFolder, sketchName + "-" + (i + 1));

                if(low.exists()) {
                    Debug.message("Shuffling " + low.getAbsolutePath() + " to " + high.getAbsolutePath());
                    low.renameTo(high);
                }
            }

            File bottom = new File(versionsFolder, sketchName + "-1");
            Debug.message("Backing up as " + bottom.getAbsolutePath());
            bottom.mkdirs();

            for(File f : sketchFiles) {
                File to = new File(bottom, f.getName());
                Debug.message("    Backing up " + f.getAbsolutePath() + " to " + to.getAbsolutePath());
                Base.copyFile(f, to);
            }
        }

        saveAllFiles();
        Debug.message("All saved");
        return true;
    }

    public String getName() {
        return sketchName;
    }

    public Collection<Library> getImportedLibraries() {
        return importedLibraries.values();
    }

    public ArrayList<Library> getOrderedLibraries() {
        
        return orderedLibraries;
    }

    public ArrayList<String> getIncludePaths() {
        ArrayList<String> libFiles = new ArrayList<String>();

        PropertyFile props = ctx.getMerged();

        libFiles.add(buildFolder.getAbsolutePath());
        libFiles.add(getBoard().getFolder().getAbsolutePath());
        libFiles.add(getCore().getLibrariesFolder().getAbsolutePath());

        for(String key : props.childKeysOf("compiler.library")) {
            String coreLibName = key.substring(17);
            String libPaths = ctx.parseString(props.get(key));

            if(libPaths != null && !(libPaths.trim().equals(""))) {
                String[] libPathsArray = libPaths.split("::");

                for(String p : libPathsArray) {
                    File f = new File(getCompiler().getFolder(), p);

                    if(f.exists()) {
                        libFiles.add(f.getAbsolutePath());
                    }
                }
            }
        }

        for(String key : props.childKeysOf("core.library")) {
            String coreLibName = key.substring(13);
            String libPaths = ctx.parseString(props.get(key));

            if(libPaths != null && !(libPaths.trim().equals(""))) {
                String[] libPathsArray = libPaths.split("::");

                for(String p : libPathsArray) {
                    File f = new File(getCore().getFolder(), p);

                    if(f.exists()) {
                        libFiles.add(f.getAbsolutePath());
                    }
                }
            }
        }

        for(String key : props.childKeysOf("board.library")) {
            String coreLibName = key.substring(14);
            String libPaths = ctx.parseString(props.get(key));

            if(libPaths != null && !(libPaths.trim().equals(""))) {
                String[] libPathsArray = libPaths.split("::");

                for(String p : libPathsArray) {
                    File f = new File(getBoard().getFolder(), p);

                    if(f.exists()) {
                        libFiles.add(f.getAbsolutePath());
                    }
                }
            }
        }

        for(Library l : getOrderedLibraries()) {
            libFiles.add(l.getSourceFolder().getAbsolutePath());
        }

        return libFiles;
    }

    public void checkForSettings() {
        File mainFile = getMainFile();

        Pattern p = Pattern.compile("^#pragma\\s+parameter\\s+([^\\s]+)\\s*=\\s*(.*)$");

        String[] data = getFileContent(mainFile).split("\n");

        for(String line : data) {
            line = line.trim();
            Matcher m = p.matcher(line);

            if(m.find()) {
                String key = m.group(1);
                String value = m.group(2);

                if(key.equals("board")) {
                    setBoard(value);
                }
            }
        }
    }

    public File getCode(int i) {
        return sketchFiles.get(i);
    }

    public File getBuildFolder() {
        return buildFolder;
    }

    public String getBuildPath() {
        return buildFolder.getAbsolutePath();
    }

    public void cleanup() {
        System.gc();
        Base.removeDescendants(buildFolder);
    }

    public void setCompilingProgress(int percent) {
        if(editor != null) {
            editor.setProgress(percent);
        }
    }

    public boolean isReadOnly() {

        if(isInternal()) {
            return true;
        }

        File testFile = new File(sketchFolder, ".testWrite");
        boolean canWrite = false;

        try {
            testFile.createNewFile();

            if(testFile.exists()) {
                testFile.delete();
                canWrite = true;
            }
        } catch(Exception e) {
            return true;
        }

        if(!canWrite) {
            return true;
        }

        canWrite = true;

        for(File c : sketchFiles) {
            if(!c.canWrite()) {
                canWrite = false;
            }
        }

        return !canWrite;
    }

    public void needPurge() {
        doPrePurge = true;
    }

    public Library getSketchLibrary(String lib) {
        if (lib.endsWith(".h")) {
            lib = lib.substring(0, lib.lastIndexOf("."));
        }

        File libs = new File(sketchFolder, "libraries");
        if (!libs.exists()) {
            return null;
        }

        if (!libs.isDirectory()) {
            return null;
        }

        File libDir = new File(libs, lib);
        if (!libDir.exists()) {
            return null;
        }
        if (!libDir.isDirectory()) {
            return null;
        }
        Library libob = new Library(libDir, "sketch", "all");
        if (!libob.isValid()) {
            return null;
        }
        return libob;
    }

    public String generateIncludes() {
        updateLibraryList();
        ArrayList<File> includes = new ArrayList<File>();

        TreeMap<String, ArrayList<File>> coreLibs = getCoreLibs();

        for(String lib : coreLibs.keySet()) {
            ArrayList<File> libfiles = coreLibs.get(lib);
            includes.addAll(libfiles);
        }

        for (Library l : orderedLibraries) {
            if(includes.indexOf(l) < 0) {
                includes.add(l.getSourceFolder());
            }
        }

        for (Library l : importedLibraries.values()) {
            if (includes.indexOf(l) < 0) {
                includes.add(l.getSourceFolder());
            }
        }

        includes.add(getBoard().getFolder());
        includes.add(buildFolder);
        includes.add(sketchFolder);

        String includeList = "";

        for(File f : includes) {
            if (f == null) {
                continue;
            }
            String path = f.getAbsolutePath();

            if(!(includeList.equals(""))) {
                includeList += "::";
            }

            includeList += "-I" + path;
        }

        return includeList;
    }

    public TreeMap<String, ArrayList<File>> getCoreLibs() {
        PropertyFile props = ctx.getMerged();
        TreeMap<String, ArrayList<File>> libs = new TreeMap<String, ArrayList<File>>();

        for(String coreLibName : props.childKeysOf("compiler.library")) {
            ArrayList<File> files = new ArrayList<File>();
            String libPaths = ctx.parseString(props.get("compiler.library." + coreLibName));

            if(libPaths != null && !(libPaths.trim().equals(""))) {
                libPaths = ctx.parseString(libPaths);
                String[] libPathsArray = libPaths.split("::");

                for(String p : libPathsArray) {
                    File f = new File(getCompiler().getFolder(), p);

                    if(f.exists() && f.isDirectory()) {
                        files.add(f);
                    }
                }

                libs.put(coreLibName, files);
            }
        }

        for(String coreLibName : props.childKeysOf("core.library")) {
            ArrayList<File> files = new ArrayList<File>();
            String libPaths = ctx.parseString(props.get("core.library." + coreLibName));

            if(libPaths != null && !(libPaths.trim().equals(""))) {
                libPaths = ctx.parseString(libPaths);
                String[] libPathsArray = libPaths.split("::");

                for(String p : libPathsArray) {
                    File f = new File(getCore().getFolder(), p);

                    if(f.exists() && f.isDirectory()) {
                        files.add(f);
                    }
                }

                libs.put(coreLibName, files);
            }
        }

        for(String coreLibName : props.childKeysOf("board.library")) {
            ArrayList<File> files = new ArrayList<File>();
            String libPaths = ctx.parseString(props.get("board.library." + coreLibName));

            if(libPaths != null && !(libPaths.trim().equals(""))) {
                libPaths = ctx.parseString(libPaths);
                String[] libPathsArray = libPaths.split("::");

                for(String p : libPathsArray) {
                    File f = new File(getBoard().getFolder(), p);

                    if(f.exists() && f.isDirectory()) {
                        files.add(f);
                    }
                }

                libs.put(coreLibName, files);
            }
        }

        return libs;
    }

    public void installLibrary(APT apt, Package pkg, boolean recurse) {
        pkg = apt.getPackage(pkg.getName());

        if (recurse) {
            Package[] deps = apt.resolveDepends(pkg);
            if (deps != null) {
                for (Package p : deps) {
                    if (!apt.isInstalled(p)) {
                        installLibrary(apt, p, false);
                    }
                }
            }
        }

        if (!apt.isInstalled(pkg)) {
            bullet2("Downloading " + pkg.getName());
            pkg.fetchPackage(Base.getDataFile("apt/cache"));
        
            bullet2("Installing " + pkg.getName());
            pkg.extractPackage(Base.getDataFile("apt/cache"), Base.getDataFile("apt/db/packages"), Base.getDataFolder());
        }
    }

    public boolean compile() {

        
        long startTime = System.currentTimeMillis();

        PropertyFile props = ctx.getMerged();

        ctx.set("cache.root", getCacheFolder().getAbsolutePath());
        clearLineComments();

        if (props.getBoolean("purge")) {
            doPrePurge = true;
        }

        ctx.executeKey("compile.precmd");

        if(editor != null) {
            for(int i = 0; i < editor.getTabCount(); i++) {
                EditorBase eb = editor.getTab(i);

                if(eb != null) {
                    eb.clearHighlights();
                    eb.removeFlagGroup(0x1000); // Error flags
                    eb.removeFlagGroup(0x1001); // Warning flags
                }
            }
        }

        // Rewritten compilation system from the ground up.

        // Step one, we need to build a generic set of includes:

        ctx.set("includes", generateIncludes());
        ctx.set("filename", sketchName);

        if ((Preferences.getBoolean("editor.dialog.missinglibs")) && (Base.isOnline())) {
            if (editor != null) {
                if (unknownLibraries.size() > 0) {
                    PluginManager pm = new PluginManager();
                    HashMap<String, Package> foundPackages = new HashMap<String, Package>();
                    for (String l : unknownLibraries) {
                        File sketchHeader = new File(sketchFolder, l);
                        if (!sketchHeader.exists()) {
                            Package p = pm.findLibraryByInclude(getCore(), l);
                            if (p != null) {
                                foundPackages.put(l, p);
                            }
                        }
                    }

                    if (foundPackages.size() > 0) {
                        JPanel panel = new JPanel();
                        panel.setLayout(new GridBagLayout());
                        GridBagConstraints c = new GridBagConstraints();

                        c.gridwidth = 2;
                        c.gridx = 0;
                        c.gridy = 0;
                        c.fill = GridBagConstraints.HORIZONTAL;
                        c.anchor = GridBagConstraints.LINE_START;

                        panel.add(new JLabel("I have identified some libraries you may be missing:"), c);
                        
                        c.gridy++;
                        panel.add(new JLabel(" "), c);

                        c.gridwidth = 1;

                        for(String l : foundPackages.keySet()) {
                            c.gridx = 0;
                            c.gridy++;
                            panel.add(new JLabel(l + ": "), c);
                            c.gridx = 1;
                            panel.add(new JLabel(foundPackages.get(l).getName()), c);
                        }
                        c.gridx = 0;
                        c.gridy++;
                        c.gridwidth = 2;
                        panel.add(new JLabel(" "), c);

                        c.gridy++;
                        panel.add(new JLabel("Do you want me to install these libraries for you?"), c);

                        JCheckBox cb = new JCheckBox("Never ask me again");
                        Object[] options = {cb, "Yes", "No"};
                        int n = JOptionPane.showOptionDialog(editor, panel, "Missing Libraries", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);

                        if (cb.isSelected()) {
                            Preferences.setBoolean("editor.dialog.missinglibs", false);
                        }

                        if (n == 1) {
                            bullet("Updating repository information...");
                            APT apt = pm.getApt();
                            apt.update();
                            bullet("Installing missing libraries...");
                            for (Package p : foundPackages.values()) {
                                installLibrary(pm.getApt(), p, true);
                            }
                            Base.rescanLibraries();
                            ctx.set("includes", generateIncludes());
                        }
                    }
                }
            }
        }

        if(doPrePurge) {
            doPrePurge = false;
            Base.removeDir(getCacheFolder());
        }

        ctx.set("option.flags", getFlags("flags"));
        ctx.set("option.cflags", getFlags("cflags"));
        ctx.set("option.cppflags", getFlags("cppflags"));
        ctx.set("option.ldflags", getFlags("ldflags"));

        String libPaths = "";
        String libNames = "";

        for (Library lib : importedLibraries.values()) {
            if (!libPaths.equals("")) {
                libPaths += "::";
            }
            if (!libNames.equals("")) {
                libNames += "::";
            }
            libPaths += lib.getSourceFolder().getAbsolutePath();
            libNames += lib.getName();
            ctx.set("library." + lib.getName() + ".path", lib.getSourceFolder().getAbsolutePath());
        }

        ctx.set("library.paths", libPaths);
        ctx.set("library.names", libNames);

        ctx.set("build.path", buildFolder.getAbsolutePath());


        if (props.keyExists("compile.script")) {
            return (Boolean)ctx.executeKey("compile.script");
        }

        // Copy any specified files from the compiler, core or board folders
        // into the build folder.  This is especially good if the compiler
        // executable needs certain DLL files to be in the current working
        // directory (which is the build folder).

        String precopy = ctx.parseString(props.getPlatformSpecific("compile.precopy"));

        if(precopy != null) {
            Debug.message("Copying files...");
            String[] copyfiles = precopy.split("::");

            for(String cf : copyfiles) {
                Debug.message("  " + cf + "...");
                File src = new File(getCompiler().getFolder(), cf);

                if(!src.exists()) {
                    src = new File(getCore().getFolder(), cf);
                }

                if(!src.exists()) {
                    src = new File(getBoard().getFolder(), cf);
                }

                if(src.exists()) {
                    File dest = new File(buildFolder, src.getName());
                    Base.copyFile(src, dest);
                    Debug.message("    ... ok");
                } else {
                    Debug.message("    ... not found");
                }
            }
        }

        if (!Base.isQuiet()) bullet("Compiling sketch...");
        setCompilingProgress(10);
        ArrayList<File>sketchObjects = compileSketch();

        if(sketchObjects == null) {
            error(Base.i18n.string("err.compiling.failed"));
            return false;
        }

        if (!Base.isQuiet()) bullet(Base.i18n.string("msg.compiling.core"));
        setCompilingProgress(20);

        if(!compileCore()) {
            error(Base.i18n.string("err.compiling.failed"));
            return false;
        }

        setCompilingProgress(30);

        if (!Base.isQuiet()) bullet(Base.i18n.string("msg.compiling.libraries"));

        if(!compileLibraries()) {
            error(Base.i18n.string("err.compiling.failed"));
            return false;
        }

        setCompilingProgress(40);

        if (!Base.isQuiet()) bullet(Base.i18n.string("msg.linking"));

        if(!compileLink(sketchObjects)) {
            error(Base.i18n.string("err.compiling.failed"));
            return false;
        }

        setCompilingProgress(50);

        setCompilingProgress(60);

        PropertyFile autogen = props.getChildren("compile.autogen");
        String[] types = autogen.childKeys();

        if (types.length > 0) {
            int steps = 50 / types.length;
            int pct = 50;

            for (String type : types) {
                ctx.bullet2(Base.i18n.string("msg.compiling.genfile", type));
                ctx.executeKey("compile.autogen." + type);
                pct += steps;
                setCompilingProgress(pct);
            }
        }

        if(Preferences.getBoolean("compiler.save_lss") && !parentIsProtected()) {
            try {
                File lss = new File(buildFolder, sketchName + ".lss");
                if (lss.exists()) {
                    Base.copyFile(new File(buildFolder, sketchName + ".lss"), new File(sketchFolder, sketchName + ".lss"));

                    if(editor != null) {
                        editor.updateFilesTree();
                    }
                }
            } catch(Exception e) {
                error(e);
            }
        }

        setCompilingProgress(70);

        if((
            Preferences.getBoolean("compiler.save_hex") || Base.cli.isSet("force-save-hex") || Base.cli.isSet("cli")) 
            && !parentIsProtected()) {
            try {
                String exeSuffix = props.get("exe.extension");
                if (exeSuffix == null) {
                    exeSuffix = ".hex";
                }
                File dest = new File(sketchFolder, sketchName + exeSuffix);
                Base.copyFile(new File(buildFolder, sketchName + exeSuffix), dest);
                if (dest.exists()) {
                    dest.setExecutable(true);
                }
                

                if(editor != null) {
                    editor.updateFilesTree();
                }
            } catch(Exception e) {
                error(e);
            }
        }


        if (!Base.isQuiet()) heading(Base.i18n.string("msg.compiling.done"));
        setCompilingProgress(100);

        if(editor != null) {
            editor.updateOutputTree();
        }


        compileSize();

        long endTime = System.currentTimeMillis();
        double compileTime = (double)(endTime - startTime) / 1000d;
        if (!Base.isQuiet()) bullet(Base.i18n.string("msg.compiling.time", compileTime));
        ctx.executeKey("compile.postcmd");
        return true;
    }

    public boolean compileSize() {
        PropertyFile props = ctx.getMerged();

        if (props.get("compile.size") != null) {
            if (!Base.isQuiet()) heading(Base.i18n.string("msg.compiling.memory"));

            ctx.startBuffer();
            ctx.executeKey("compile.size");
            String output = ctx.endBuffer();

            String reg = props.get("compiler.size.regex", "^\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)");
            int tpos = props.getInteger("compiler.size.text", 1);
            int rpos = props.getInteger("compiler.size.rodata", 0);
            int dpos = props.getInteger("compiler.size.data", 2);
            int bpos = props.getInteger("compiler.size.bss", 3);
            String[] lines = output.split("\n");
            Pattern p = Pattern.compile(reg);
            int textSize = 0;
            int rodataSize = 0;
            int dataSize = 0;
            int bssSize = 0;
            for (String line : lines) {

                try {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        if (tpos > 0) {
                            textSize = Integer.parseInt(m.group(tpos));
                        }
                        if (rpos > 0) {
                            rodataSize = Integer.parseInt(m.group(rpos));
                        }
                        if (dpos > 0) {
                            dataSize = Integer.parseInt(m.group(dpos));
                        }
                        if (bpos > 0) {
                            bssSize = Integer.parseInt(m.group(bpos));
                        }
                    }
                } catch (Exception e) {
                }
            }

            ctx.set("size.text", textSize + "");
            ctx.set("size.data", dataSize + "");
            ctx.set("size.rodata", rodataSize + "");
            ctx.set("size.bss", bssSize + "");

            ctx.set("size.flash", (textSize + dataSize + rodataSize) + "");
            ctx.set("size.ram", (bssSize + dataSize) + "");

            if (!Base.isQuiet()) {
                int max_ram = props.getInteger("memory.sram");
                int max_rom = props.getInteger("memory.flash");

                if (max_rom > 0) {
                    int romperc = (textSize + dataSize + rodataSize) * 100 / max_rom;
                    bullet(Base.i18n.string("msg.compiling.progsize.perc", (textSize + dataSize + rodataSize), romperc)); 
                } else {
                    bullet(Base.i18n.string("msg.compiling.progsize", (textSize + dataSize + rodataSize))); 
                }

                if (max_ram > 0) {
                    int ramperc = (bssSize + dataSize) * 100 / max_ram;
                    bullet(Base.i18n.string("msg.compiling.ramsize.perc", (bssSize + dataSize), ramperc)); 
                } else {
                    bullet(Base.i18n.string("msg.compiling.ramsize", (bssSize + dataSize))); 
                }
            }
        }
        return true;
    }

    public boolean compileLibraries() {
        for(String lib : importedLibraries.keySet()) {
            if(!compileLibrary(importedLibraries.get(lib))) {
                return false;
            }
        }

        return true;
    }

    private File compileFile(File src) {
        return compileFile(src, buildFolder);
    }

    private File compileFile(File src, File fileBuildFolder) {
    
        String fileName = src.getName();
        String recipe = null;

        if(terminateExecution) {
            terminateExecution = false;
            error("Compilation terminated");
            return null;
        }

        PropertyFile props = ctx.getMerged();

        if(fileName.endsWith(".cpp")) {
            recipe = "compile.cpp";
        }

        if(fileName.endsWith(".cxx")) {
            recipe = "compile.cpp";
        }

        if(fileName.endsWith(".cc")) {
            recipe = "compile.cpp";
        }

        if(fileName.endsWith(".c")) {
            recipe = "compile.c";
        }

        if(fileName.endsWith(".S")) {
            recipe = "compile.S";
        }

        if(recipe == null) {
            error(Base.i18n.string("err.badfile", fileName));
            return null;
        }

        if (Preferences.getBoolean("compiler.verbose_files")) {
            bullet3(fileName);
        }

        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String objExt = ctx.parseString(props.get("compiler.object","o"));
        File dest = new File(fileBuildFolder, fileName + "." +objExt);

        if(dest.exists()) {
            if(dest.lastModified() > src.lastModified()) {
                return dest;
            }
        }

        ctx.set("build.path", fileBuildFolder.getAbsolutePath());
        ctx.set("source.name", src.getAbsolutePath());
        ctx.set("object.name", dest.getAbsolutePath());

        ctx.addDataStreamParser(new DataStreamParser() {
            public String parseStreamMessage(Context ctx, String m) {
                if (parseLineForWarningMessage(ctx, m)) {
                    return "";
                }
                return m;
            }
            public String parseStreamError(Context ctx, String m) {
                if (parseLineForErrorMessage(ctx, m)) {
                    return "";
                }
                if (parseLineForWarningMessage(ctx, m)) {
                    return "";
                }
                return m;
            }
        });

        String output = "";
        if(!(Boolean)ctx.executeKey(recipe)) {
            ctx.removeDataStreamParser();
            return null;
        }
        ctx.removeDataStreamParser();

        if(!dest.exists()) {
            return null;
        }

        if(editor != null) {
            editor.updateOutputTree();
        }

        return dest;
    }

    public File getCacheFolder() {
        File cacheRoot = Base.getCacheFolder();
        Core c = getCore();
        File boardCache = new File(cacheRoot, "unknownCache");
        if (c != null) {
            Board b = getBoard();
            if (b != null) {
                File coreCache = new File(cacheRoot, c.getName());
                boardCache = new File(coreCache, b.getName());
            }
        }

        if(!boardCache.exists()) {
            boardCache.mkdirs();
        }

        return boardCache;
    }

    public File getCacheFile(String fileName) {
        File cacheFolder = getCacheFolder();
        File out = new File(cacheFolder, fileName);
        return out;
    }

    public boolean compileCore() {
        TreeMap<String, ArrayList<File>> coreLibs = getCoreLibs();
        PropertyFile props = ctx.getMerged();

        if (props.get("compile.stub") != null) {
            String mainStub = ctx.parseString(props.get("compile.stub"));
            String[] bits = mainStub.split("::");
            for (String stubFile : bits) {
                File mainStubFile = new File(stubFile);
                if (mainStubFile.exists()) {
                    File mainStubObject = compileFile(mainStubFile);
                    File cachedStubObject = getCacheFile(mainStubObject.getName());
                    if (mainStubObject.exists()) {
                        Base.copyFile(mainStubObject, cachedStubObject);
                        mainStubObject.delete();
                    }
                }
            }
        }

        for(String lib : coreLibs.keySet()) {
            if (!Base.isQuiet()) bullet2(lib.toString());

            if(!compileCore(coreLibs.get(lib), "Core_" + lib)) {
                return false;
            }
        }

        return true;
    }

    public boolean compileCore(ArrayList<File> core, String name) {
        PropertyFile props = ctx.getMerged();
        String prefix = ctx.parseString(props.get("compiler.library.prefix","lib"));
        String suffix = ctx.parseString(props.get("compiler.library", "a"));
        File archive = getCacheFile(prefix + name + "." + suffix);

        ctx.set("library", archive.getAbsolutePath());

        long archiveDate = 0;

        if(archive.exists()) {
            archiveDate = archive.lastModified();
        }

        File coreBuildFolder = new File(buildFolder, "libCore_" + name);
        coreBuildFolder.mkdirs();

        ArrayList<File> fileList = new ArrayList<File>();

        for(File f : core) {
            if(f.exists() && f.isDirectory()) {
                fileList.addAll(findFilesInFolder(f, "S", false));
                fileList.addAll(findFilesInFolder(f, "c", false));
                fileList.addAll(findFilesInFolder(f, "cpp", false));
                fileList.addAll(findFilesInFolder(f, "cxx", false));
                fileList.addAll(findFilesInFolder(f, "cc", false));
            }
        }

        for(File f : fileList) {
            if(f.lastModified() > archiveDate) {
                File out = compileFile(f, coreBuildFolder);

                if(out == null) {
                    coreBuildFolder.delete();
                    if (archive.exists()) archive.delete();
                    return false;
                }

                ctx.set("object.name", out.getAbsolutePath());
                boolean ok = (Boolean)ctx.executeKey("compile.ar");

                if(!ok) {
                    out.delete();
                    coreBuildFolder.delete();
                    if (archive.exists()) archive.delete();
                    return false;
                }

                out.delete();
            }
        }

        coreBuildFolder.delete();
        return true;
    }

    public void put(String k, String v) {
        ctx.set(k, v);
    }

    public String get(String k) {
        return ctx.get(k);
    }

    public String getArchiveName(Library lib) {
        PropertyFile props = ctx.getMerged();
        String prefix = ctx.parseString(props.get("compiler.library.prefix","lib"));
        String suffix = ctx.parseString(props.get("compiler.library", "a"));
        return prefix + lib.getLinkName() + "." + suffix;
    }

    public boolean compileLibrary(Library lib) {
        File archive = getCacheFile(getArchiveName(lib));  //getCacheFile("lib" + lib.getName() + ".a");
        File utility = lib.getUtilityFolder();
        PropertyFile props = ctx.getMerged();
        if (!Base.isQuiet()) bullet2(lib.toString() + " [" + lib.getFolder().getAbsolutePath() + "]");

        ctx.set("library", archive.getAbsolutePath());

        long archiveDate = 0;

        if(archive.exists()) {
            archiveDate = archive.lastModified();
        }

        File libBuildFolder = new File(buildFolder, "lib" + lib.getLinkName());
        libBuildFolder.mkdirs();

        TreeSet<File> fileList = lib.getSourceFiles(this);

        String origIncs = ctx.get("includes");
        ctx.set("includes", origIncs + "::" + "-I" + utility.getAbsolutePath());

        int fileCount = fileList.size();

        int count = 0;

        for(File f : fileList) {
            if(f.lastModified() > archiveDate) {
                File out = compileFile(f, libBuildFolder);

                if(out == null) {
                    purgeLibrary(lib);
                    lib.setCompiledPercent(0);

                    if(editor != null) {
                        editor.updateLibrariesTree();
                    }

                    libBuildFolder.delete();
                    return false;
                }

                ctx.set("object.name", out.getAbsolutePath());
                boolean ok = (Boolean)ctx.executeKey("compile.ar");

                if(!ok) {
                    purgeLibrary(lib);
                    lib.setCompiledPercent(0);

                    if(editor != null) {
                        editor.updateLibrariesTree();
                    }

                    out.delete();
                    libBuildFolder.delete();
                    return false;
                }

                count++;
                lib.setCompiledPercent(count * 100 / fileCount);

                if(editor != null) {
                    editor.updateLibrariesTree();
                }

                out.delete();
            }
        }

        if(editor != null) {
            editor.updateOutputTree();
        }

        ctx.set("includes", origIncs);
        lib.setCompiledPercent(100);

        if(editor != null) {
            editor.updateLibrariesTree();
        }

        libBuildFolder.delete();

        return true;
    }

    private ArrayList<File> convertFiles(File dest, ArrayList<File> sources) {
        ArrayList<File> objectPaths = new ArrayList<File>();
        PropertyFile props = ctx.getMerged();

        ctx.set("build.path", dest.getAbsolutePath());
        String objExt = ctx.parseString(props.get("compiler.object","o"));

        for(File file : sources) {
            File objectFile = new File(dest, file.getName() + "." + objExt);
            objectPaths.add(objectFile);

            if(objectFile.exists() && objectFile.lastModified() > file.lastModified()) {
                continue;
            }

            String sp = file.getAbsolutePath();
            String sf = sketchFolder.getAbsolutePath();
            sp = sp.substring(sf.length() + 1);

            String spf = file.getParentFile().getAbsolutePath();
            spf = spf.substring(sf.length() + 1);

            File destObjects = new File(dest, spf);
            destObjects.mkdirs();

            File destFile = new File(destObjects, file.getName());
            Base.copyFile(file, destFile);

            ctx.set("source.name", sp);
            ctx.set("object.name", objectFile.getAbsolutePath());

            if(!(Boolean)ctx.executeKey("compile.bin"))
                return null;

            if(!objectFile.exists())
                return null;
        }

        return objectPaths;
    }

    private ArrayList<File> compileFileList(File dest, ArrayList<File> sources, String key) {
        ArrayList<File> objectPaths = new ArrayList<File>();
        PropertyFile props = ctx.getMerged();

        ctx.set("build.path", dest.getAbsolutePath());
        String objExt = ctx.parseString(props.get("compiler.object","o"));

        ctx.addDataStreamParser(new DataStreamParser() {
            public String parseStreamMessage(Context ctx, String m) {
                if (parseLineForWarningMessage(ctx, m)) {
                    return "";
                }
                return m;
            }
            public String parseStreamError(Context ctx, String m) {
                if (parseLineForErrorMessage(ctx, m)) {
                    return "";
                }
                if (parseLineForWarningMessage(ctx, m)) {
                    return "";
                }
                return m;
            }
        });

        for(File file : sources) {
            String fileName = file.getName();
            String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
            File objectFile = new File(dest, fileName + "." + objExt);
            objectPaths.add(objectFile);

            ctx.set("source.name", file.getAbsolutePath());
            ctx.set("object.name", objectFile.getAbsolutePath());

            if(objectFile.exists() && objectFile.lastModified() > file.lastModified()) {
                continue;
            }

            if(!(Boolean)ctx.executeKey(key)) {
                ctx.removeDataStreamParser();
                return null;
            }

            if(!objectFile.exists()) {
                ctx.removeDataStreamParser();
                return null;
            }
        }
        ctx.removeDataStreamParser();
        return objectPaths;
    }

    private ArrayList<File> compileFiles(File dest, ArrayList<File> sSources, ArrayList<File> cSources, ArrayList<File> cppSources) {

        ArrayList<File> objectPaths = new ArrayList<File>();
        PropertyFile props = ctx.getMerged();

        ctx.set("build.path", dest.getAbsolutePath());
        String objExt = ctx.parseString(props.get("compiler.object","o"));

        ArrayList<File> sObjects = compileFileList(dest, sSources, "compile.S");
        if (sObjects == null) { return null; }

        ArrayList<File> cObjects = compileFileList(dest, cSources, "compile.c");
        if (cObjects == null) { return null; }

        ArrayList<File> cppObjects = compileFileList(dest, cppSources, "compile.cpp");
        if (cppObjects == null) { return null; }

        objectPaths.addAll(sObjects);
        objectPaths.addAll(cObjects);
        objectPaths.addAll(cppObjects);

        return objectPaths;
    }

    private ArrayList<File> compileSketch() {
        ArrayList<File> sf = new ArrayList<File>();

        PropertyFile props = ctx.getMerged();

        // We can only do this if the core supports binary conversions
        if(props.get("compile.bin") != null) {
            File obj = new File(sketchFolder, "objects");

            if(obj.exists()) {
                File buf = new File(buildFolder, "objects");
                buf.mkdirs();
                ArrayList<File> uf = convertFiles(buildFolder, findFilesInFolder(obj, null, true));

                if(uf != null) {
                    sf.addAll(uf);
                }
            }
        }

        ArrayList<File> compiledFiles = compileFiles(
                                            buildFolder,
                                            findFilesInFolder(buildFolder, "S", false),
                                            findFilesInFolder(buildFolder, "c", false),
                                            findFilesInFolder(buildFolder, "cpp", false)
                                        );

        if(compiledFiles != null) {
            sf.addAll(compiledFiles);
        } else {
            return null;
        }

        String boardFiles = ctx.parseString(props.get("build.files"));

        if(boardFiles != null) {
            if(!boardFiles.equals("")) {
                ArrayList<File> sFiles = new ArrayList<File>();
                ArrayList<File> cFiles = new ArrayList<File>();
                ArrayList<File> cppFiles = new ArrayList<File>();

                String[] bfs = boardFiles.split("::");

                for(String bf : bfs) {
                    if(bf.endsWith(".c")) {
                        File f = new File(getBoard().getFolder(), bf);

                        if(f.exists()) {
                            cFiles.add(f);
                        }
                    } else if(bf.endsWith(".cpp")) {
                        File f = new File(getBoard().getFolder(), bf);

                        if(f.exists()) {
                            cppFiles.add(f);
                        }
                    } else if(bf.endsWith(".S")) {
                        File f = new File(getBoard().getFolder(), bf);

                        if(f.exists()) {
                            sFiles.add(f);
                        }
                    }
                }

                sf.addAll(compileFiles(buildFolder, sFiles, cFiles, cppFiles));
            }
        }

        File suf = new File(sketchFolder, "utility");

        if(suf.exists()) {
            File buf = new File(buildFolder, "utility");
            buf.mkdirs();
            ArrayList<File> uf = compileFiles(
                                buf,
                                findFilesInFolder(suf, "S", true),
                                findFilesInFolder(suf, "c", true),
                                findFilesInFolder(suf, "cpp", true)
                            );
            sf.addAll(uf);
        }

        return sf;
    }

    static public ArrayList<File> findFilesInFolder(File sketchFolder,
            String extension, boolean recurse) {
        ArrayList<File> files = new ArrayList<File>();

        if (sketchFolder == null) {
            return files;
        }

        if(sketchFolder.listFiles() == null)
            return files;

        for(File file : sketchFolder.listFiles()) {
            if(file.getName().startsWith("."))
                continue; // skip hidden files

            if(file.isDirectory()) {
                if(recurse) {
                    files.addAll(findFilesInFolder(file, extension, recurse));
                }

                continue;
            }

            if(extension == null) {
                files.add(file);
                continue;
            }

            if(file.getName().endsWith("." + extension)) {
                files.add(file);
                continue;
            }

        }

        return files;
    }

    static private boolean createFolder(File folder) {
        if(folder.isDirectory())
            return false;

        if(!folder.mkdir())
            return false;

        return true;
    }

    private boolean compileLink(List<File> objectFiles) {
        PropertyFile props = ctx.getMerged();
        TreeMap<String, ArrayList<File>> coreLibs = getCoreLibs();

        String objectFileList = "";

        ctx.set("libraries.path", getCacheFolder().getAbsolutePath());

        String neverInclude = props.get("neverinclude");

        if(neverInclude == null) {
            neverInclude = "";
        }

        neverInclude.replaceAll(" ", "::");
        String neverIncludes[] = neverInclude.split("::");

        String liboption = props.get("compile.liboption");

        if(liboption == null) {
            liboption = "-l${library}";
        }

        String liblist = "";

        for(String libName : importedLibraries.keySet()) {
            Library lib = importedLibraries.get(libName);
            File aFile = getCacheFile(getArchiveName(lib));
            String headerName = lib.getName() + ".h";
            boolean inc = true;

            for(String ni : neverIncludes) {
                if(ni.equals(headerName)) {
                    inc = false;
                }
            }

            if(aFile.exists() && inc) {
                ctx.set("library", lib.getLinkName());
                liblist += "::" + ctx.parseString(liboption);
            }
        }

        for(String libName : coreLibs.keySet()) {
            ctx.set("library", "Core_" + libName);
            liblist += "::" + ctx.parseString(liboption);
        }

        ctx.set("libraries", liblist);

        for(File file : objectFiles) {
            objectFileList = objectFileList + file.getAbsolutePath() + "::";
        }

        ctx.set("build.path", buildFolder.getAbsolutePath());
        ctx.set("object.filelist", objectFileList);

        return (Boolean)ctx.executeKey("compile.link");
    }

    private boolean compileEEP() {
        PropertyFile props = ctx.getMerged();
        if (!props.keyExists("compile.eep")) {
            return true;
        }
        return (Boolean)ctx.executeKey("compile.eep");
    }

    private boolean compileLSS() {
        PropertyFile props = ctx.getMerged();
        if (!props.keyExists("compile.lss")) {
            return true;
        }
        return (Boolean)ctx.executeKey("compile.lss");
    }

    private boolean compileHEX() {
        PropertyFile props = ctx.getMerged();
        if (!props.keyExists("compile.hex")) {
            return true;
        }
        return (Boolean)ctx.executeKey("compile.hex");
    }

    public boolean isUntitled() {
        return isUntitled;
    }


    // Scan all the "common" areas where examples may be found, and see if the
    // start of the sketch folder's path matches any of them.

    public boolean isInternal() {
        String path = sketchFolder.getAbsolutePath();
        String basePath = Base.getContentFile(".").getAbsolutePath() + File.separator;
        String cachePath = getCacheFolder().getAbsolutePath() + File.separator;
        String corePath = getCore().getFolder().getAbsolutePath() + File.separator;
        String boardPath = getBoard().getFolder().getAbsolutePath() + File.separator;

        if(path.startsWith(basePath)) return true;

        if(path.startsWith(cachePath)) return true;

        if(path.startsWith(corePath)) return true;

        if(path.startsWith(boardPath)) return true;

        return false;
    }

    public void cleanBuild() {
        System.gc();
        Base.removeDescendants(buildFolder);
    }

    public void about() {
        bullet("Sketch folder: " + sketchFolder.getAbsolutePath());
        bullet("Selected board: " + getBoard().getName());
        bullet("Board folder: " + getBoard().getFolder().getAbsolutePath());
    }

    public File getLibrariesFolder() {
        return new File(sketchFolder, "libraries");
    }

    public HashMap<String, Library> getLibraries() {
        return importedLibraries;
    }

    class programContextListener implements ContextListener {

        String pctFormat = null;
        String pctChar = null;
        float pctMultiply = 1.0f;
        int pctCount = 0;

        public programContextListener(String format, String chr, float mul) {
            pctFormat = format;
            pctChar = chr;
            pctMultiply = mul;
            pctCount = 0;
        }

        public void contextMessage(String m) {
            process(m);
        }

        public void contextWarning(String m) {
            process(m);
        }

        public void contextError(String m) {
            process(m);
        }

        void process(String s) {
            if (pctChar != null) {
                String[] chars = s.split("(?!^)");
                for (String c : chars) {
                    if (c.equals(pctChar)) {
                        pctCount++;
                    } else {
                        pctCount = 0;
                    }
                    setCompilingProgress((int)((float)pctCount * pctMultiply));
                }
            }

            if (pctFormat != null) {
                Pattern p = Pattern.compile(pctFormat);
                Matcher m = p.matcher(s);
                if (m.find()) {
                    try {
                        String ps = m.group(1);
                        float perc = Float.parseFloat(ps);
                        setCompilingProgress((int)(perc * pctMultiply));
                    } catch (Exception e) {
                        Base.error(e);
                    }
                }
            }
        }
    }

    public boolean programFile(Programmer programmer, String file) {
        Programmer p = getProgrammer();
        if (p == null) {
            ctx.error(Base.i18n.string("err.noprogrammer"));
            return false;
        }
        return p.programFile(ctx, file);
    }

    /**************************************************************************
     * MESSAGING AND FEEDBACK                                                 *
     *                                                                        *
     * Any output to the user gets fed through these routines.  If there is   *
     * a valid editor then the output goes to the editor's console, otherwise *
     * it goes to stdout / stderr.                                            *
     **************************************************************************/

    public boolean isWarningMessage(String s) {
        PropertyFile props = ctx.getMerged();
        String wRec = props.get("compiler.warning");
        int wFilename = props.getInteger("compiler.warning.filename", 1);
        int wLine = props.getInteger("compiler.warning.line", 2);
        int wMessage = props.getInteger("compiler.warning.message", 3);

        if(wRec != null) {
            Pattern wPat = Pattern.compile(wRec);
            Matcher wMat = wPat.matcher(s);

            if(wMat.find()) {
                return true;
            }
        }
        return false;
    }

    public void flagError(String s) {
        if(editor == null) {
            return;
        }

        PropertyFile theme = Base.getTheme();
        PropertyFile props = ctx.getMerged();

        String eRec = props.get("compiler.error");
        int eFilename = props.getInteger("compiler.error.filename", 1);
        int eLine = props.getInteger("compiler.error.line", 2);
        int eMessage = props.getInteger("compiler.error.message", 3);

        String wRec = props.get("compiler.warning");
        int wFilename = props.getInteger("compiler.warning.filename", 1);
        int wLine = props.getInteger("compiler.warning.line", 2);
        int wMessage = props.getInteger("compiler.warning.message", 3);

        if(eRec != null) {
            Pattern ePat = Pattern.compile(eRec);
            Matcher eMat = ePat.matcher(s);

            if(eMat.find()) {
                try {
                    int errorLineNumber = Integer.parseInt(eMat.group(eLine));

                    File errorFile = new File(eMat.group(eFilename));

                    if(errorFile != null) {
                        int tabNumber = editor.getTabByFile(errorFile);

                        if(tabNumber > -1) {
                            EditorBase eb = editor.getTab(tabNumber);
                            eb.highlightLine(errorLineNumber - 1, theme.getColor("editor.compile.error.bgcolor"));
                            eb.flagLine(errorLineNumber - 1, Base.getIcon("flags", "fixme", 16), 0x1000);
                        }

                        link("uecide://error/" + errorLineNumber + "/" + errorFile.getAbsolutePath() + "|Error at line " + errorLineNumber + " in file " + errorFile.getName());

                        setLineComment(errorFile, errorLineNumber, eMat.group(eMessage));
                    }
                } catch(Exception e) {
                    Base.error(e);
                }
            }
        }

        if(wRec != null) {
            Pattern wPat = Pattern.compile(wRec);
            Matcher wMat = wPat.matcher(s);

            if(wMat.find()) {
                try {
                    int warningLineNumber = Integer.parseInt(wMat.group(wLine));

                    File warningFile = new File(wMat.group(wFilename));

                    if(warningFile != null) {
                        int tabNumber = editor.getTabByFile(warningFile);

                        if(tabNumber > -1) {
                            EditorBase eb = editor.getTab(tabNumber);
                            eb.highlightLine(warningLineNumber - 1, theme.getColor("editor.compile.warning.bgcolor"));
                            eb.flagLine(warningLineNumber - 1, Base.getIcon("flags", "todo", 16), 0x1001);
                            link("uecide://error/" + warningLineNumber + "/" + warningFile.getAbsolutePath() + "|Warning at line " + warningLineNumber + " in file " + warningFile.getName());
                        }

                        setLineComment(warningFile, warningLineNumber, wMat.group(wMessage));
                    }
                } catch(Exception e) {
                    Base.error(e);
                }
            }
        }
    }

    String mBuffer = "";
    public void messageStream(String msg) {
        if(editor != null) {
            editor.messageStream(msg);
            return;
        }

        mBuffer += msg;
        int nlpos = mBuffer.lastIndexOf("\n");

        if(nlpos == -1) {
            nlpos = mBuffer.lastIndexOf("\r");
        }
        if(nlpos == -1) {
            return;
        }

        mBuffer.replaceAll("\\r\\n", "\\n");
        mBuffer.replaceAll("\\r", "\\n");

        boolean eol = false;

        if(mBuffer.endsWith("\n")) {
            mBuffer = mBuffer.substring(0, mBuffer.length() - 1);
            eol = true;
        }

        String[] bits = mBuffer.split("\n");

        for(int i = 0; i < bits.length - 1; i++) {
            message(bits[i]);
        }

        if(eol) {
            mBuffer = "";
            message(bits[bits.length - 1]);
        } else {
            mBuffer = bits[bits.length - 1];
        }
    }

    String wBuffer = "";
    public void warningStream(String msg) {
        wBuffer += msg;
        int nlpos = wBuffer.lastIndexOf("\n");

        if(nlpos == -1) {
            return;
        }

        boolean eol = false;

        if(wBuffer.endsWith("\n")) {
            wBuffer = wBuffer.substring(0, wBuffer.length() - 1);
            eol = true;
        }

        String[] bits = wBuffer.split("\n");

        for(int i = 0; i < bits.length - 1; i++) {
            warning(bits[i]);
        }

        if(eol) {
            wBuffer = "";
            warning(bits[bits.length - 1]);
        } else {
            wBuffer = bits[bits.length - 1];
        }
    }

    String eBuffer = "";
    public void errorStream(String msg) {
        eBuffer += msg;
        int nlpos = eBuffer.lastIndexOf("\n");

        if(nlpos == -1) {
            return;
        }

        boolean eol = false;

        if(eBuffer.endsWith("\n")) {
            eBuffer = eBuffer.substring(0, eBuffer.length() - 1);
            eol = true;
        }

        String[] bits = eBuffer.split("\n");

        for(int i = 0; i < bits.length - 1; i++) {
            error(bits[i]);
        }

        if(eol) {
            eBuffer = "";
            error(bits[bits.length - 1]);
        } else {
            eBuffer = bits[bits.length - 1];
        }
    }

    public void link(String s) {
        if(!s.endsWith("\n")) {
            s += "\n";
        }
        if(editor != null) {
            editor.link(s);
        }
    }        

    public void command(String s) {
        if(!s.endsWith("\n")) {
            s += "\n";
        }
        if(editor != null) {
            editor.command(s);
        } else {
            System.out.print(s);
        }
    }        

    public void bullet(String s) {
        if(!s.endsWith("\n")) {
            s += "\n";
        }
        if(editor != null) {
            editor.bullet(s);
        } else {
            System.out.print("    * " + s);
        }
    }        

    public void bullet2(String s) {
        if(!s.endsWith("\n")) {
            s += "\n";
        }
        if(editor != null) {
            editor.bullet2(s);
        } else {
            System.out.print("      > " + s);
        }
    }        

    public void bullet3(String s) {
        if(!s.endsWith("\n")) {
            s += "\n";
        }
        if(editor != null) {
            editor.bullet3(s);
        } else {
            System.out.print("        o " + s);
        }
    }        

    public void heading(String s) {
        if(!s.endsWith("\n")) {
            s += "\n";
        }
        if(editor != null) {
            editor.heading(s);
        } else {
            System.out.print(s);
            for (int i = 0; i < s.trim().length(); i++) {
                System.out.print("=");
            }
            System.out.println();
        }
    }        

    public void message(String s) {
        flagError(s);

        if (percentageCharacter != null) {
            String[] chars = s.split("(?!^)");
            for (String c : chars) {
                if (c.equals(percentageCharacter)) {
                    percentageCharacterCount++;
                } else {
                    percentageCharacterCount = 0;
                }
                setCompilingProgress((int)((float)percentageCharacterCount * percentageMultiplier));
            }
        }

        if (percentageFilter != null) {
            Pattern p = Pattern.compile(percentageFilter);
            Matcher m = p.matcher(s);
            if (m.find()) {
                try {
                    String ps = m.group(1);
                    float perc = Float.parseFloat(ps);
                    setCompilingProgress((int)(perc * percentageMultiplier));
                    return;
                } catch (Exception e) {
                    Base.error(e);
                }
            }
        }

        if(!s.endsWith("\n")) {
            s += "\n";
        }

        if(editor != null) {
            editor.message(s);
        } else {
            System.out.print(s);
        }
    }

    public void warning(String s) {
        flagError(s);

        if(!s.endsWith("\n")) {
            s += "\n";
        }

        if(editor != null) {
            editor.warning(s);
        } else {
            System.out.print(s);
        }
    }

    public void error(String s) {
        if (isWarningMessage(s)) {
            warning(s);
            return;
        }
        flagError(s);

        if (percentageCharacter != null) {
            String[] chars = s.split("(?!^)");
            for (String c : chars) {
                if (c.equals(percentageCharacter)) {
                    percentageCharacterCount++;
                } else {
                    percentageCharacterCount = 0;
                }
                setCompilingProgress((int)((float)percentageCharacterCount * percentageMultiplier));
            }
        }

        if (percentageFilter != null) {
            Pattern p = Pattern.compile(percentageFilter);
            Matcher m = p.matcher(s);
            if (m.find()) {
                try {
                    String ps = m.group(1);
                    float perc = Float.parseFloat(ps);
                    setCompilingProgress((int)(perc * percentageMultiplier));
                    return;
                } catch (Exception e) {
                    Base.error(e);
                }
            }
        }
        if(!s.endsWith("\n")) {
            s += "\n";
        }

        if(editor != null) {
            editor.error(s);
        } else {
            System.err.print(s);
        }
    }

    public void error(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        error(sw.toString());
    }

    /**************************************************************************
     * OPTIONS HANDLING                                                       *
     *                                                                        *
     * Any options specified by the Options menu are controlled through these *
     * routines.                                                              *
     **************************************************************************/

    public String getOption(String opt) {
        PropertyFile props = ctx.getMerged();
        PropertyFile opts = Base.preferences.getChildren("board." + selectedBoard.getName() + ".options");
        String optval = opts.get(opt);


        if(optval == null) {
            if (opt.contains("@")) {
                String bits[] = opt.split("@");
                String libLinkName = bits[0];
                String libCatName = bits[1];
                for(Library lib : importedLibraries.values()) {
                    if (lib.getLinkName().equals(libLinkName)) {
                        props = lib.getProperties();
                        optval = props.get("options." + libCatName + ".default");
                    }
                }
            } else {
                optval = props.get("options." + opt + ".default");
            }
        }

        return optval;
    }

    public void setOption(String opt, String val) {
        PropertyFile props = ctx.getMerged();
        Preferences.set("board." + selectedBoard.getName() + ".options." + opt, val);

        if (opt.contains("@")) {
            String bits[] = opt.split("@");
            String libLinkName = bits[0];
            String libCatName = bits[1];
            for(Library lib : importedLibraries.values()) {
                if (lib.getLinkName().equals(libLinkName)) {
                    props = lib.getProperties();
                    if (props.getBoolean("options." + libCatName + ".purge")) {
                        needPurge();
                    }
                }
            }
        } else {
            if(props.getBoolean("options." + opt + ".purge")) {
                needPurge();
            }
        }
    }

    public TreeMap<String, String> getOptionGroups() {
        PropertyFile props = ctx.getMerged();

        String[] options = props.childKeysOf("options");
        TreeMap<String, String> out = new TreeMap<String, String>();

        for(String opt : options) {
            String optName = props.get("options." + opt + ".name");
            out.put(opt, optName);
        }

        for(Library lib : importedLibraries.values()) {
            props = lib.getProperties();
            options = props.childKeysOf("options");
            for (String opt : options) {
                String optName = props.get("options." + opt + ".name");
                out.put(lib.getLinkName() + "@" + opt, optName);
            }
        }

        return out;
    }

    @SuppressWarnings("unchecked")
    public TreeMap<String, String> getOptionNames(String group) {
        TreeMap<String, String> out = new TreeMap<String, String>(new NaturalOrderComparator());
        PropertyFile props = ctx.getMerged();

        if (group.contains("@")) {
            String bits[] = group.split("@");
            String libLinkName = bits[0];
            String libCatName = bits[1];
            for(Library lib : importedLibraries.values()) {
                if (lib.getLinkName().equals(libLinkName)) {
                    props = lib.getProperties();
                    PropertyFile opts = props.getChildren("options." + libCatName);
                    for (String opt : opts.childKeys()) {
                        if(opt.equals("name")) continue;

                        if(opt.equals("default")) continue;

                        if(opt.equals("purge")) continue;

                        String name = opts.get(opt + ".name");

                        if(name != null) {
                            out.put(opt, name);
                        }
                    }
                }
            }
        } else {
            PropertyFile opts = props.getChildren("options." + group);
            for(String opt : opts.childKeys()) {
                if(opt.equals("name")) continue;

                if(opt.equals("default")) continue;

                if(opt.equals("purge")) continue;

                String name = opts.get(opt + ".name");

                if(name != null) {
                    out.put(opt, name);
                }
            }
        }

        return out;
    }

    public String getFlags(String type) {
        PropertyFile props = ctx.getMerged();
        PropertyFile opts = Base.preferences.getChildren("board." + selectedBoard.getName() + ".options");
        TreeMap<String, String> options = getOptionGroups();

        String flags = "";

        for(String opt : options.keySet()) {
            String value = getOption(opt);
            if (opt.contains("@")) {
                String bits[] = opt.split("@");
                String libLinkName = bits[0];
                String libOptName = bits[1];
                for (Library lib : importedLibraries.values()) {
                    if (lib.getLinkName().equals(libLinkName)) {
                        PropertyFile lprops = lib.getProperties();
                        String data = lprops.get("options." + libOptName + "." + value + "." + type);
                        if(data != null) {
                            flags = flags + "::" + data;
                        }
                    }
                }
            } else {
                String data = props.get("options." + opt + "." + value + "." + type);

                if(data != null) {
                    flags = flags + "::" + data;
                }
            }
        }

        return flags;
    }

    public File getBinariesFolder() {
        return new File(sketchFolder, "objects");
    }

    public boolean libraryIsCompiled(Library l) {
        if(l == null) return false;

        if(l.isHeaderOnly()) {
            return true;
        }

        File arch = new File(getCacheFolder(), getArchiveName(l));
        return arch.exists();
    }

    public void purgeLibrary(Library lib) {
        File arch = new File(getCacheFolder(), getArchiveName(lib));
        arch.delete();
    }

    public void purgeCache() {
        Base.removeDir(getCacheFolder());
    }

    public void precompileLibrary(Library lib) {
        ctx.set("includes", generateIncludes());
        ctx.set("filename", sketchName);
        ctx.set("cache.root", getCacheFolder().getAbsolutePath());

        if(doPrePurge) {
            doPrePurge = false;
            Base.removeDir(getCacheFolder());
        }

        ctx.set("option.flags", getFlags("flags"));
        ctx.set("option.cflags", getFlags("cflags"));
        ctx.set("option.cppflags", getFlags("cppflags"));
        ctx.set("option.ldflags", getFlags("ldflags"));

        compileLibrary(lib);
    }
    public void renameFile(File old, File newFile) {
        if(sketchFiles.indexOf(old) >= 0) {
            sketchFiles.remove(old);
            sketchFiles.add(newFile);
        }
    }

    public void deleteFile(File f) {
        if(sketchFiles.indexOf(f) >= 0) {
            sketchFiles.remove(f);
        }
    }

    public void rescanFileTree() {
        sketchFiles = new ArrayList<File>();
        loadSketchFromFolder(sketchFolder);
    }

    public void purgeBuildFiles() {
        Base.removeDescendants(buildFolder);
    }

    public boolean isChildOf(File dir) {
        File parent = sketchFolder.getParentFile();

        while(parent != null && parent.exists()) {
            if(parent.equals(dir)) {
                return true;
            }

            parent = parent.getParentFile();
        }

        return false;
    }

    public boolean parentIsIn(File[] files) {
        for (File file : files) {
           if (isChildOf(file)) {
                return true;
            }
        }
        return false;
    }

    public boolean parentIsLibrary() {
        ArrayList<File> filelist = new ArrayList<File>();

        TreeSet<String> groups = Library.getLibraryCategories();

        for(String group : groups) {
            TreeSet<Library> libs = Library.getLibraries(group);

            if(libs == null) {
                continue;
            }

            for(Library lib : libs) {
                filelist.add(lib.getFolder());
            }
        }
        return parentIsIn(filelist.toArray(new File[0]));
    }

    public boolean parentIsBoard() {
        return parentIsIn(Base.getBoardsFolders());
    }

    public boolean parentIsCore() {
        return parentIsIn(Base.getCoresFolders());
    }

    public boolean parentIsCompiler() {
        return parentIsIn(Base.getCompilersFolders());
    }

    public boolean parentIsProtected() {
        return
            parentIsCore() ||
            parentIsBoard() ||
            parentIsCompiler() ||
            parentIsLibrary();
    }

    public void requestTermination() {
        terminateExecution = true;
        ctx.killRunningProcess();
    }

    public boolean generateSarFile(File archiveFile) {
        try {
            if(archiveFile.exists()) {
                archiveFile.delete();
            }

            FileOutputStream outfile = new FileOutputStream(archiveFile);
            ZipOutputStream sar = new ZipOutputStream(outfile);
            sar.putNextEntry(new ZipEntry(sketchFolder.getName() + "/"));
            sar.closeEntry();
            addTree(sketchFolder, sketchFolder.getName(), sar);

            sar.putNextEntry(new ZipEntry("libraries" + "/"));
            sar.closeEntry();

            prepare();

            String libList = "";

            for(Library lib : getImportedLibraries()) {
                if(lib.isContributed()) {
                    sar.putNextEntry(new ZipEntry("libraries" + "/" + lib.getFolder().getName() + "/"));
                    sar.closeEntry();
                    addTree(lib.getFolder(), "libraries/" + lib.getFolder().getName(), sar);

                    if(libList.equals("")) {
                        libList = lib.getFolder().getName();
                    } else {
                        libList = libList + " " + lib.getFolder().getName();
                    }
                }
            }

            sar.putNextEntry(new ZipEntry("META-INF/"));
            sar.closeEntry();
            sar.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());

            StringBuilder mf = new StringBuilder();
            mf.append("SAR-Version: 1.0\n");
            mf.append("Author: " + System.getProperty("user.name") + "\n");
            mf.append("Sketch-Name: " + sketchFolder.getName() + "\n");
            mf.append("Libraries: " + libList + "\n");
            mf.append("Board: " + getBoard().getName() + "\n");
            mf.append("Core: " + getCore().getName() + "\n");
            mf.append("Archived: " + timeStamp + "\n");

            String mfData = mf.toString();
            byte[] bytes = mfData.getBytes();
            sar.write(bytes, 0, bytes.length);
            sar.closeEntry();
            sar.flush();
            sar.close();
        } catch(Exception e) {
            Base.error(e);
            return false;
        }

        return true;
    }

    public void addTree(File dir, String sofar, ZipOutputStream zos) throws IOException {
        String files[] = dir.list();

        for(int i = 0; i < files.length; i++) {
            if(files[i].equals(".") || files[i].equals("..")) continue;

            if(files[i].startsWith(".git")) continue;

            if(files[i].startsWith(".svn")) continue;

            if(files[i].startsWith(".csv")) continue;

            if(files[i].startsWith(".SVN")) continue;

            if(files[i].startsWith(".CSV")) continue;

            File sub = new File(dir, files[i]);
            String nowfar = (sofar == null) ?  files[i] : (sofar + "/" + files[i]);

            if(sub.isDirectory()) {
                // directories are empty entries and have / at the end
                ZipEntry entry = new ZipEntry(nowfar + "/");
                //System.out.println(entry);
                zos.putNextEntry(entry);
                zos.closeEntry();
                addTree(sub, nowfar, zos);
            } else {
                ZipEntry entry = new ZipEntry(nowfar);
                entry.setTime(sub.lastModified());
                zos.putNextEntry(entry);
                zos.write(Base.loadBytesRaw(sub));
                zos.closeEntry();
            }
        }
    }

    public void loadConfig() {
        PropertyFile m = ctx.getMerged();
        if (m.get("sketch.board") != null) {
            String wantedBoard = m.get("sketch.board");
            Board b = Base.getBoard(wantedBoard);
            if (b == null) {
                ctx.error(Base.i18n.string("err.badboard", wantedBoard));
            } else {
                ctx.bullet(Base.i18n.string("msg.selecting.board", b));
                setBoard(b);
            }
        }

        if (m.get("sketch.core") != null) {
            String wantedCore = m.get("sketch.core");
            Core c = Base.getCore(wantedCore);
            if (c == null) {
                ctx.error(Base.i18n.string("err.badcore", wantedCore));
            } else {
                ctx.bullet(Base.i18n.string("msg.selecting.core", c));
                setCore(c);
            }
        }

        if (m.get("sketch.compiler") != null) {
            String wantedCompiler = m.get("sketch.compiler");
            Compiler c = Base.getCompiler(wantedCompiler);
            if (c == null) {
                ctx.error(Base.i18n.string("err.badcompiler", wantedCompiler));
            } else {
                ctx.bullet(Base.i18n.string("msg.selecting.compiler", c));
                setCompiler(c);
            }
        }

        if (m.get("sketch.programmer") != null) {
            setProgrammer(m.get("sketch.programmer"));
        }

        if (m.get("sketch.port") != null) {
            setDevice(m.get("sketch.port"));
        }

    }

    public String toString() {
        return sketchName;
    }

    public ImageIcon getIcon(int size) {
        ImageIcon i = null;

        if (size == 16) {
            i = getIcon();
            if(i != null) return i;
        }

        if(selectedBoard != null) {
            i = selectedBoard.getIcon(size);

            if(i != null) return i;
        }

        if(selectedCore != null) {
            i = selectedCore.getIcon(size);

            if(i != null) return i;
        }

        return null;
    }

    public String getArch() {
        if (getBoard().get("arch") != null) {
            return getBoard().get("arch");
        }
        if (getCore().get("arch") != null) {
            return getCore().get("arch");
        }
        if (getCompiler().get("arch") != null) {
            return getCompiler().get("arch");
        }
        return getBoard().get("family");
    }

    public ArrayList<TodoEntry> todo(File f) {
        if (sketchFiles.indexOf(f) == -1) {
            return null;
        }

        Pattern p = Pattern.compile("(?i)\\/\\/\\s*(TODO|NOTE|FIXME):\\s*(.*)$");

        ArrayList<TodoEntry> found = new ArrayList<TodoEntry>();

        String content = getFileContent(f);
        String[] lines = content.split("\n");
        int lineno = 0;
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                String type = m.group(1).trim().toLowerCase();
                String comment = m.group(2).trim();
                int itype = 0;

                if (type.equals("todo")) {
                    itype = TodoEntry.Todo;
                } else if (type.equals("note")) {
                    itype = TodoEntry.Note;
                } else if (type.equals("fixme")) {
                    itype = TodoEntry.Fixme;
                }
                if (itype != 0) {
                    found.add(new TodoEntry(f, lineno, comment, itype));
                }
            }
            lineno++;
        }

        return found;
    }

    public void addKeywordsFromFile(File f) {
        String kwd = Base.getFileAsString(f);

        String[] lines = kwd.split("\n");
        Pattern p = Pattern.compile("^\\s*([^\\s]+)\\s+([^\\s]+)");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                String name = m.group(1);
                String type = m.group(2);
                if (type.equals("LITERAL1")) {
                    keywords.put(name, KeywordTypes.LITERAL1);
                } else if (type.equals("LITERAL2")) {
                    keywords.put(name, KeywordTypes.LITERAL2);
                } else if (type.equals("LITERAL3")) {
                    keywords.put(name, KeywordTypes.LITERAL3);
                } else if (type.equals("KEYWORD1")) {
                    keywords.put(name, KeywordTypes.KEYWORD1);
                } else if (type.equals("KEYWORD2")) {
                    keywords.put(name, KeywordTypes.KEYWORD2);
                } else if (type.equals("KEYWORD3")) {
                    keywords.put(name, KeywordTypes.KEYWORD3);
                } else if (type.equals("OBJECT")) {
                    keywords.put(name, KeywordTypes.OBJECT);
                } else if (type.equals("VARIABLE")) {
                    keywords.put(name, KeywordTypes.VARIABLE);
                } else if (type.equals("FUNCTION")) {
                    keywords.put(name, KeywordTypes.FUNCTION);
                } else if (type.equals("DATATYPE")) {
                    keywords.put(name, KeywordTypes.DATATYPE);
                } else if (type.equals("RESERVED")) {
                    keywords.put(name, KeywordTypes.RESERVED);
                }
            }
        }
    }


    public void updateKeywords() {
        keywords.clear();
        if (selectedCompiler != null) {
            addKeywordsFromFile(selectedCompiler.getKeywords());
        }
        if (selectedCore != null) {
            addKeywordsFromFile(selectedCore.getKeywords());
        }
        if (selectedBoard != null) {
            addKeywordsFromFile(selectedBoard.getKeywords());
        }
        for (Library l : importedLibraries.values()) {
            addKeywordsFromFile(l.getKeywords());
        }
        TreeSet<String> fl = getAllFunctionNames();
        for (String s : fl) {
            FunctionBookmark bm = new FunctionBookmark(null, 0, s);

            keywords.put(bm.getName(), KeywordTypes.KEYWORD3);
        }

    }

    public HashMap<String, Integer> getKeywords() {
        return keywords;
    }

    public ImageIcon getIcon() {
        PropertyFile m = ctx.getMerged();
        if ((m.get("sketch.icon") != null) && !(m.get("sketch.icon").equals(""))) {
            File iconFile = new File(sketchFolder, m.get("sketch.icon"));
            if (iconFile.exists()) {
                return Base.loadIconFromFile(iconFile);
            }
        }
        return null;
    }

    public void generateFileFromTemplate(String template, File output) {
        try {
            String[] lines = template.split("\n");
            PrintWriter out = new PrintWriter(output);
            for (String line : lines) {
                String parsed = ctx.parseString(line);
                out.println(parsed);
            }
            out.close();
        } catch (Exception e) {
            Base.error(e);
        }
    }

    public void generateMakefile() {
        PropertyFile props = ctx.getMerged();
        if (props.get("makefile.template") != null) {
            String template = Base.getFileAsString(new File(ctx.parseString(props.get("makefile.template"))));
            File out = new File(sketchFolder, "Makefile");
            generateFileFromTemplate(template.toString(), out);
        }
    }

    public Context getContext() {
        return ctx;
    }

    public PropertyFile mergeAllProperties() {
        return ctx.getMerged();
    }

    public String parseString(String s) {
        return ctx.parseString(s);
    }

    public boolean parseLineForErrorMessage(Context ctx, String mess) {
        PropertyFile props = ctx.getMerged();
        Pattern p = Pattern.compile(props.get("compiler.error"));
        Matcher m = p.matcher(mess);
        if (m.find()) {
            File errorFile = new File(m.group(1));
            int errorLineNumber = Integer.parseInt(m.group(2));
            if (editor != null) {
                try {
                    PropertyFile theme = Base.getTheme();
                    int tabNumber = editor.getTabByFile(errorFile);

                    if(tabNumber > -1) {
                        EditorBase eb = editor.getTab(tabNumber);
                        eb.highlightLine(errorLineNumber - 1, theme.getColor("editor.compile.error.bgcolor"));
                        eb.flagLine(errorLineNumber - 1, Base.getIcon("flags", "fixme", 16), 0x1000);
                    }

                    String linkUrl = "uecide://error/" + errorLineNumber + "/" + errorFile.getAbsolutePath();

                    if (containsFile(errorFile)) {
                        String errmess = String.format(
                            "{\\bullet}{\\error Error at }{\\link %s|line %d in file %s}{\\error :}\n",
                                linkUrl, errorLineNumber, errorFile.getName());

                        ctx.parsedMessage(errmess);

                    } else {
                        ctx.parsedMessage("{\\bullet}{\\error Error at line " + errorLineNumber + " in file " + errorFile.getName() + ":}\n");
                    }

                } catch (Exception execpt) {
                }
            } else {
                ctx.parsedMessage("{\\bullet}{\\error Error at line " + errorLineNumber + " in file " + errorFile.getName() + ":}\n");
            }
            ctx.parsedMessage("{\\bullet2}{\\error " + m.group(3) + "}\n");
            setLineComment(errorFile, errorLineNumber, m.group(3));
            return true;
        }
        return false;
    }

    public boolean parseLineForWarningMessage(Context ctx, String mess) {
        PropertyFile props = ctx.getMerged();
        Pattern p = Pattern.compile(props.get("compiler.warning"));
        Matcher m = p.matcher(mess);
        if (m.find()) {
            File errorFile = new File(m.group(1));
            int errorLineNumber = Integer.parseInt(m.group(2));
            if (editor != null) {
                try {
                    PropertyFile theme = Base.getTheme();
                    int tabNumber = editor.getTabByFile(errorFile);

                    if(tabNumber > -1) {
                        EditorBase eb = editor.getTab(tabNumber);
                        eb.highlightLine(errorLineNumber - 1, theme.getColor("editor.compile.warning.bgcolor"));
                        eb.flagLine(errorLineNumber - 1, Base.getIcon("flags", "todo", 16), 0x1001);
                    }
                    String linkUrl = "uecide://error/" + errorLineNumber + "/" + errorFile.getAbsolutePath();

                    if (containsFile(errorFile)) {
                        String errmess = String.format(
                            "{\\bullet}{\\warning Warning at }{\\link %s|line %d in file %s}{\\warning :}\n",
                                linkUrl, errorLineNumber, errorFile.getName());

                        ctx.parsedMessage(errmess);

                    } else {
                        ctx.parsedMessage("{\\bullet}{\\warning Error at line " + errorLineNumber + " in file " + errorFile.getName() + ":}\n");
                    }

                } catch (Exception execpt) {
                }
            } else {
                ctx.parsedMessage("{\\bullet}{\\warning Warning at line " + errorLineNumber + " in file " + errorFile.getName() + ":}\n");
            }
            ctx.parsedMessage("{\\bullet2}{\\warning " + m.group(3) + "}\n");
            setLineComment(errorFile, errorLineNumber, m.group(3));
            return true;
        }
        return false;
    }

    public boolean containsFile(File f) {
        for (File q : sketchFiles) {
            if (q.getAbsolutePath().equals(f.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    public void parsedMessage(String e) {
        ctx.printParsed(e);
    }

    public ArrayList<String> gatherIncludes(File f) {
        String[] data;
        ArrayList<String> requiredLibraries = new ArrayList<String>();

        try {
            FileReader in = new FileReader(f);
            StringBuilder contents = new StringBuilder();
            char[] buffer = new char[4096];
            int read = 0;

            do {
                contents.append(buffer, 0, read);
                read = in.read(buffer);
            } while(read >= 0);

            in.close();
            data = contents.toString().split("\n");
        } catch(Exception e) {
            Base.error(e);
            return null;
        }

        ArrayList<String> includes = new ArrayList<String>();

        for(String line : data) {
            line = line.trim();

            if(line.startsWith("#include")) {
                int qs = line.indexOf("<");

                if(qs == -1) {
                    qs = line.indexOf("\"");
                }

                if(qs == -1) {
                    continue;
                }

                qs++;
                int qe = line.indexOf(">");

                if(qe == -1) {
                    qe = line.indexOf("\"", qs);
                }

                String i = line.substring(qs, qe);

                requiredLibraries.add(i);
            }
        }
        return requiredLibraries;
    }

    public void addRecursiveLibraries(HashMap<String, Library>foundLibs, ArrayList<String> missingLibs, Library lib) {
        for (String inc : lib.getRequiredLibraries()) {
            Library sl = findLibrary(inc);
            if (sl != null) {
                if (foundLibs.get(inc) == null) {
                    foundLibs.put(inc, sl);
                    addRecursiveLibraries(foundLibs, missingLibs, sl);
                }
            } else {
                if (missingLibs.indexOf(inc) == -1) {
                    missingLibs.add(inc);
                }
            }
        }
    }

    public boolean huntForLibraries(File f, HashMap<String, Library>foundLibs, ArrayList<String> missingLibs) {

        if (getBuildFolder() == null) {
            return false;
        }
        PropertyFile props = ctx.getMerged();
        if (props.get("compile.preproc") == null) { // Manually parse it.


            ArrayList<String> incs = gatherIncludes(f);
            for (String inc : incs) {
                Library l = findLibrary(inc);
                if (l != null) {
                    if (foundLibs.get(inc) == null) {
                        foundLibs.put(inc, l);
                        addRecursiveLibraries(foundLibs, missingLibs, l);
                    }
                } else {
                    if (missingLibs.indexOf(inc) == -1) {
                        missingLibs.add(inc);
                    }
                }
            }

            return true;
        }

        File dst = new File(getBuildFolder(), "deps.txt");

        ctx.silence = true;

        int numberFoundThisPass = 0;
        do {
            ctx.snapshot();

            ctx.set("option.flags", getFlags("flags"));
            ctx.set("option.cflags", getFlags("cflags"));
            ctx.set("option.cppflags", getFlags("cppflags"));
            ctx.set("option.ldflags", getFlags("ldflags"));

            numberFoundThisPass = 0;
            ctx.set("source.name", f.getAbsolutePath());
            ctx.set("object.name", dst.getAbsolutePath());
            String libPaths = "";
            for (Library aLib : foundLibs.values()) {
                if (!libPaths.equals("")) {
                    libPaths += "::";
                }
                libPaths += "-I" + aLib.getSourceFolder().getAbsolutePath();
            }
            ctx.set("includes", libPaths);

            ctx.startBuffer(true);
            ctx.executeKey("compile.preproc");
            ctx.endBuffer();

            if (dst.exists()) {
                String data = Base.getFileAsString(dst);
                data = data.replaceAll("\\\\\n", " ");
                data = data.replaceAll("\\s+", "::");
                String[] entries = data.split("::");
                for (String entry : entries) {
                    if (entry.endsWith(".h")) {
                        Library lib = findLibrary(entry);
                        if (lib != null) {
                            foundLibs.put(entry, lib);
                            numberFoundThisPass++;
                        } else {
                            if(missingLibs.indexOf(entry) == -1) {
                                missingLibs.add(entry);
                            }
                        }
                    }
                }
            }

            ctx.rollback();
        } while (numberFoundThisPass > 0);

        ctx.silence = false;
        return true;
    }

    long lastConfigChange = 0;
    public boolean updateSketchConfig() {
        File sketchConfigFile = new File(sketchFolder, "sketch.cfg");
        if (!sketchConfigFile.exists()) {
            return false;
        }

        long lastMod = sketchConfigFile.lastModified();
        if (lastConfigChange != lastMod) {
            ctx.loadSketchSettings(sketchConfigFile);
            lastConfigChange = lastMod;
            return true;
        }
        return false;
    }

    public Editor getEditor() { 
        return editor;
    }

    public void outputErrorStream(String msg) {
        System.err.print(msg);
    }
   
    public void outputMessageStream(String msg) {
        System.out.print(msg);
    }

}
