package uecide.app.varcmd;

import uecide.app.*;

public class vc_exec implements VariableCommand {
    public String main(Sketch sketch, String args) {
        if(sketch.executeKey(args)) {
            return "true";
        }

        return "false";
    }
}