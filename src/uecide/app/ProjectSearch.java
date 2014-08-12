package uecide.app;

import uecide.app.debug.*;
import uecide.app.editors.*;
import java.io.*;
import java.util.*;
import java.net.*;
import java.util.zip.*;
import java.util.regex.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import say.swing.*;
import org.json.simple.*;
import java.beans.*;
import javax.imageio.*;

import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.*;


public class ProjectSearch {
    JFrame frame;
    JPanel mainContainer;
    Editor editor;
    JScrollPane scroll;
    JTextPane text;
    HTMLDocument doc;
    HTMLEditorKit kit;


    JTextField searchTerm;

    public ProjectSearch(Editor e) {
        editor = e;
        mainContainer = new JPanel();
        mainContainer.setLayout(new BorderLayout());
        JToolBar tb = new JToolBar();
        searchTerm = new JTextField();
        tb.add(searchTerm);
        JButton searchButton = new JButton("Search");

        searchButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doSearch();
            }
        });

        searchTerm.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doSearch();
            }
        });

        tb.add(searchButton);
        tb.setFloatable(false);

        mainContainer.add(tb, BorderLayout.NORTH);

        scroll = new JScrollPane();
        mainContainer.add(scroll, BorderLayout.CENTER);


        text = new JTextPane();
        text.setContentType("text/html");
        text.setEditable(false);
        text.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        String theme = Base.preferences.get("theme.selected", "default");
        theme = "theme." + theme + ".";
        text.setBackground(Base.theme.getColor(theme + "console.color"));

       text.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    if (e.getDescription().startsWith("uecide://goto/")) {
                        Pattern p = Pattern.compile("^uecide://goto/(\\d+)/(.*)$");
                        Matcher m = p.matcher(e.getDescription());
                        if (m.find()) {

                            File f = new File(m.group(2));
                            int line = 0;
                            try {
                                line = Integer.parseInt(m.group(1));
                            } catch (Exception ee) {
                            }
                            if (line > 0) {
                                int tab = editor.openOrSelectFile(f);
                                if (tab >= 0) {
                                    EditorBase eb = editor.getTab(tab);
                                    eb.gotoLine(line-1);
                                    eb.requestFocus();
                                }
                            }
                        }
                        return;
                    }

                    if (e.getURL() != null) {
                        Base.openURL(e.getURL().toString());
                    }
                }
            }
        });


        kit = new HTMLEditorKit();
        text.setEditorKit(kit);
        StyleSheet css = kit.getStyleSheet();

        css.addRule("body {" + Base.theme.get(theme + "console.body", Base.theme.get("theme.default.console.body")) + "}");
        css.addRule("span.warning {" + Base.theme.get(theme + "console.warning", Base.theme.get("theme.default.console.warning")) + "}");
        css.addRule("span.error {" + Base.theme.get(theme + "console.error", Base.theme.get("theme.default.console.error")) + "}");
        css.addRule("div.command {" + Base.theme.get(theme + "console.command", Base.theme.get("theme.default.console.command")) + "}");
        css.addRule("a {" + Base.theme.get(theme + "console.a", Base.theme.get("theme.default.console.a")) + "}");
        css.addRule("ul {" + Base.theme.get(theme + "console.ul", Base.theme.get("theme.default.console.ul")) + "}");
        css.addRule("ol {" + Base.theme.get(theme + "console.ol", Base.theme.get("theme.default.console.ol")) + "}");
        css.addRule("li {" + Base.theme.get(theme + "console.li", Base.theme.get("theme.default.console.li")) + "}");
        css.addRule("h1 {" + Base.theme.get(theme + "console.h1", Base.theme.get("theme.default.console.h1")) + "}");
        css.addRule("h2 {" + Base.theme.get(theme + "console.h2", Base.theme.get("theme.default.console.h2")) + "}");
        css.addRule("h3 {" + Base.theme.get(theme + "console.h3", Base.theme.get("theme.default.console.h3")) + "}");
        css.addRule("h4 {" + Base.theme.get(theme + "console.h4", Base.theme.get("theme.default.console.h4")) + "}");
        doc = (HTMLDocument)kit.createDefaultDocument();
        text.setDocument(doc);
        text.setCaretPosition(0);
        clearText();

        scroll.setViewportView(text);

        //mainContainer.pack();
        editor.attachPanelAsTab("Search Project", mainContainer);
        searchTerm.requestFocus();
        
    }

    public void clearText() {
        try {
            doc.remove(0, doc.getLength());
        } catch(BadLocationException e) {
        }
    }

    public void appendToText(String s) {
        try {
            kit.insertHTML(doc, doc.getLength(), s, 0, 0, null);
            text.setCaretPosition(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void doSearch() {
        String term = searchTerm.getText().toLowerCase();
        clearText();
        for(File f : editor.loadedSketch.sketchFiles) {
            StringBuilder chunk = new StringBuilder();

            String[] content = loadFileLines(f);
            boolean foundText = false;

            chunk.append("<h3>" + f.getName() + "</h3>");
            chunk.append("<table><tr><th>Line</th><th>Content</th></tr>");

            int lineno = 0;
            ArrayList<Integer> finds = new ArrayList<Integer>();

            for(String line : content) {
                lineno++;

                if(line.toLowerCase().contains(term)) {
                    foundText = true;

                    String rep = line.replaceAll("(?i)(" + term + ")", "<span class='error'>$1</span>");

                    chunk.append("<hr><td><a href='uecide://goto/" + lineno + "/" + f.getAbsolutePath() + "'>" + lineno + "</a></td><td>" + rep + "</td></tr>");
                }
            }

            if(foundText) {
                chunk.append("</table>");
                appendToText(chunk.toString());
            }
        }
    }

    public String[] loadFileLines(File f) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String line = null;
            StringBuilder sb = new StringBuilder();

            while((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }

            reader.close();
            return sb.toString().split("\n");
        } catch(Exception e) {
            Base.error(e);
        }

        return null;
    }
}
