package org.uecide;

import javax.swing.UIManager;

class OfficeXPLAF extends LookAndFeel {
    public static String getName() { return "Office XP"; }

    public static void applyLAF() {
        try {
            UIManager.setLookAndFeel("org.fife.plaf.OfficeXP.OfficeXPLookAndFeel");
        } catch (Exception e) {
            Base.error(e);
        }
    }

    public static PropertyFile getPreferencesTree() {
        return null;
    }

    public static boolean isCompatible() { return Base.isWindows(); }
}
