package org.uecide;


public class BackgroundLibraryCompileService extends Service {
    public BackgroundLibraryCompileService() {
        setName("Background Library Compile");
        setInterval(5000);
    }

    public void setup() { }
    public void cleanup() { }

    public void loop() {
        synchronized (Editor.editorList) {
            for (Editor e : Editor.editorList) {
                if (!e.compilerRunning()) {
                    e.loadedSketch.updateLibraryList();
                    e.loadedSketch.generateIncludes();
                    for (Library lib : e.loadedSketch.getImportedLibraries()) {
                        if (!e.loadedSketch.libraryIsCompiled(lib)) {
                            System.err.println(lib);
                            e.loadedSketch.precompileLibrary(lib);
                        }
                    }
                }
            }
        }
    }

}
