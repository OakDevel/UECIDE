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

import jssc.*;
import java.io.*;
import java.util.*;

public class SerialCommunicationPort implements CommunicationPort,SerialPortEventListener {

    String portName = null;
    SerialPort serialPort = null;
    Board board = null;
    String lastError = "No error";
    CommsListener listener = null;

    public SerialCommunicationPort(String n) {
        portName = n;
        serialPort = new SerialPort(portName);
    }

    public String getConsoleAddress() {
        return null;
    }

    public String getConsolePort() {
        return portName;
    }

    public String getProgrammingAddress() {
        return null;
    }

    public String getProgrammingPort() {
        return portName;
    }

    public Board getBoard() {
        return board;
    }

    public String getName() {
        if (Base.isLinux()) {
            return getNameLinux();
        } else if (Base.isMacOS()) {
            return getNameMacOS();
        } else {
            return portName;
        }
    }

    public String toString() {
        return portName;
    }

    HashMap<String, String>macPortNameCache = new HashMap<String,String>();

    String getNameMacOS() {
        String deviceName = serialPort.getPortName();

        if (macPortNameCache.get(deviceName) != null) {
            return macPortNameCache.get(deviceName);
        }

        String subName = deviceName;
        if (subName.startsWith("/dev/tty.")) {
            subName = subName.substring(9);
        } else if (subName.startsWith("/dev/cu.")) {
            subName = subName.substring(8);
        }

        IOKit.parseRegistry();
        ArrayList<IOKitNode> list = IOKit.findByClass("IOSerialBSDClient");

        try {
            for (IOKitNode n : list) {
                if (n.get("IOTTYDevice").equals(subName)) {

                    IOKitNode par = n.getParentNode();
                    while (par != null) {
                        if (par.getNodeClass().equals("IOUSBDevice")) {
                            String vid = String.format("%04x", Integer.parseInt(par.get("idVendor")));
                            String pid = String.format("%04x", Integer.parseInt(par.get("idProduct")));

                            for (Board b : Base.boards.values()) {
                                if (b.get("usb.vid") != null && b.get("usb.pid") != null) {
                                    if (b.get("usb.vid").equals(vid) && b.get("usb.pid").equals(pid)) {
                                        board = b;
                                        macPortNameCache.put(deviceName, board.getDescription() + " (" + deviceName + ")");
                                        return board.getDescription() + " (" + deviceName + ")";
                                    }
                                }
                            }

                            macPortNameCache.put(deviceName, deviceName + " (" + par.get("USB Vendor Name") + " " + par.get("USB Product Name") + ")");
                            return deviceName + " (" + par.get("USB Vendor Name") + " " + par.get("USB Product Name") + ")";
                        }
                        par = par.getParentNode();
                    }
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        macPortNameCache.put(deviceName, deviceName);
        return deviceName;
        
    }

    String getNameLinux() {
        BufferedReader reader;
        try {
            String pn = serialPort.getPortName();
            File f = new File(pn);
            pn = f.getCanonicalPath();
            pn = pn.substring(pn.lastIndexOf("/") + 1);

            File classFolder = new File("/sys/class/tty", pn);

            if(classFolder == null || !classFolder.exists()) {
                return portName;
            }

            File dev = new File(classFolder.getCanonicalPath());

            if(dev.getAbsolutePath().indexOf("/usb") == -1) {
                return portName;
            }

            File root = dev;
            File prodFile = new File(root, "product");

            while(!root.getName().startsWith("usb") && !prodFile.exists()) {
                root = root.getParentFile();
                prodFile = new File(root, "product");
            }

            if(!prodFile.exists()) {
                return portName;
            }

            File mfgFile = new File(root, "manufacturer");

            if(!mfgFile.exists()) {
                return portName;
            }

            File vidFile = new File(root, "idVendor");
            File pidFile = new File(root, "idProduct");

            if (vidFile.exists() && pidFile.exists()) {
                reader = new BufferedReader(new FileReader(vidFile));
                String vid = reader.readLine();
                reader.close();
                reader = new BufferedReader(new FileReader(pidFile));
                String pid = reader.readLine();
                reader.close();

                for (Board b : Base.boards.values()) {
                    if (b.get("usb.vid") != null && b.get("usb.pid") != null) {
                        if (b.get("usb.vid").equals(vid) && b.get("usb.pid").equals(pid)) {
                            board = b;
                        }
                    }
                }
            }
            
            reader = new BufferedReader(new FileReader(prodFile));
            String product = reader.readLine();
            reader.close();
            reader = new BufferedReader(new FileReader(mfgFile));
            String manufacturer = reader.readLine();
            reader.close();

            if (board != null) {
                return board.getDescription() + " (" + portName + ")";
            }
            return portName + " (" + manufacturer + " " + product + ")";
        } catch(Exception e) {
            Base.error(e);
            return portName;
        }
    }

    public void addCommsListener(CommsListener l) {
        listener = l;
    }

    public void removeCommsListener() {
        listener = null;
    }

    public boolean print(String s) {
        try {
            serialPort.writeString(s);
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
        }
        return false;
    }

    public boolean println(String s) {
        try {
            serialPort.writeString(s + "\r\n");
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
        }
        return false;
    }

    public boolean write(byte[] b) {
        try {
            serialPort.writeBytes(b);
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
        }
        return false;
    }

    public boolean write(byte b) {
        try {
            serialPort.writeByte(b);
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
        }
        return false;
    }

    public void closePort() {
        try {
            if (serialPort.isOpened()) {
                serialPort.removeEventListener();
                serialPort.purgePort(1);
                serialPort.purgePort(2);
                serialPort.setDTR(false);
                serialPort.setRTS(false);
                serialPort.closePort();
            }
        } catch (Exception e) {
        }
    }

    public boolean openPort() {
        try {
            serialPort.openPort();
            serialPort.setParams(9600, 8, 1, 0);
            serialPort.addEventListener(this);
            serialPort.setDTR(true);
            serialPort.setRTS(true);
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
            e.printStackTrace();
        }
        return false;
    }

    public String getLastError() {
        return lastError;
    }

    public void serialEvent(SerialPortEvent e) {
        if (e.isRXCHAR()) {
            try {
                byte[] bytes = serialPort.readBytes();
                if (bytes == null) {
                    return;
                }
                if (listener != null) {
                    listener.commsDataReceived(bytes);
                }
            } catch (Exception ex) {
            }
        }
    }

    public boolean setSpeed(int speed) {
        try {
            serialPort.setParams(speed, 8, 1, 0);
        } catch (Exception e) {
            lastError = e.getMessage();
            return false;
        }
        return true;
    }
            

    public CommsSpeed[] getSpeeds() {
        CommsSpeed[] s = new CommsSpeed[25];
        s[0] = new CommsSpeed(300, "300 baud");
        s[1] = new CommsSpeed(1200, "1200 baud");
        s[2] = new CommsSpeed(2400, "2400 baud");
        s[3] = new CommsSpeed(4800, "4800 baud");
        s[4] = new CommsSpeed(9600, "9600 baud");
        s[5] = new CommsSpeed(14400, "14400 baud");
        s[6] = new CommsSpeed(19200, "19200 baud");
        s[7] = new CommsSpeed(28800, "28800 baud");
        s[8] = new CommsSpeed(38400, "38400 baud");
        s[9] = new CommsSpeed(57600, "57600 baud");
        s[10] = new CommsSpeed(115200, "115200 baud");
        s[11] = new CommsSpeed(230400, "230400 baud");
        s[12] = new CommsSpeed(460800, "460800 baud");
        s[13] = new CommsSpeed(500000, "500000 baud");
        s[14] = new CommsSpeed(512000, "512000 baud");
        s[15] = new CommsSpeed(576000, "576000 baud");
        s[16] = new CommsSpeed(1000000, "1000000 baud");
        s[17] = new CommsSpeed(1024000, "1024000 baud");
        s[18] = new CommsSpeed(1152000, "1152000 baud");
        s[19] = new CommsSpeed(2000000, "2000000 baud");
        s[20] = new CommsSpeed(2304000, "2304000 baud");
        s[21] = new CommsSpeed(2500000, "2500000 baud");
        s[22] = new CommsSpeed(3000000, "3000000 baud");
        s[23] = new CommsSpeed(3500000, "3500000 baud");
        s[24] = new CommsSpeed(4000000, "4000000 baud");
        return s;
    }

    public void pulseLine() {
        try {
            serialPort.setDTR(true);
            serialPort.setRTS(true);
            Thread.sleep(100);
            serialPort.setDTR(false);
            serialPort.setRTS(false);
            Thread.sleep(100);
            serialPort.setDTR(true);
            serialPort.setRTS(true);
        } catch (Exception e) {
            lastError = e.getMessage();
        }
    }

    public void setDTR(boolean s) {
        try {
            serialPort.setDTR(s);
        } catch (Exception e) {
            lastError = e.getMessage();
        }
    }

    public void setRTS(boolean s) {
        try {
            serialPort.setRTS(s);
        } catch (Exception e) {
            lastError = e.getMessage();
        }
    }

    public String getBaseName() {
        String n = serialPort.getPortName();
        if (Base.isWindows()) { 
            return n;
        } else {
            File f = new File(n);
            return f.getName();
        }
    }

    HashMap<String, String> data = new HashMap<String, String>();
    public void set(String key, String value) {
        data.put(key, value);
    }
    public String get(String key) {
        return data.get(key);
    }


    public int getVID() {
        if (Base.isLinux()) {
            return getVIDPIDLinux("idVendor");
        } else if (Base.isMacOS()) {
            return getVIDPIDMacOS("idVendor");
        } else {
            return 0;
        }
    }

    public int getPID() {
        if (Base.isLinux()) {
            return getVIDPIDLinux("idProduct");
        } else if (Base.isMacOS()) {
            return getVIDPIDMacOS("idProduct");
        } else {
            return 0;
        }
    }

    int getVIDPIDLinux(String name) {
        BufferedReader reader;
        try {
            String pn = serialPort.getPortName();
            File f = new File(pn);
            pn = f.getCanonicalPath();
            pn = pn.substring(pn.lastIndexOf("/") + 1);

            File classFolder = new File("/sys/class/tty", pn);

            if(classFolder == null || !classFolder.exists()) {
                return 0;
            }

            File dev = new File(classFolder.getCanonicalPath());

            if(dev.getAbsolutePath().indexOf("/usb") == -1) {
                return 0;
            }

            File root = dev;
            File vidFile = new File(root, name);

            while(!root.getName().startsWith("usb") && !vidFile.exists()) {
                root = root.getParentFile();
                vidFile = new File(root, name);
            }

            if(!vidFile.exists()) {
                return 0;
            }

            reader = new BufferedReader(new FileReader(vidFile));
            String vidstr = reader.readLine();
            reader.close();

            int vid = Integer.parseInt(vidstr, 16);
            return vid;
        } catch(Exception e) {
            Base.error(e);
            return 0;
        }
    }


    int getVIDPIDMacOS(String name) {
        String deviceName = serialPort.getPortName();

        String subName = deviceName;

        if (subName.startsWith("/dev/tty.")) {
            subName = subName.substring(9);
        } else if (subName.startsWith("/dev/cu.")) {
            subName = subName.substring(8);
        }

        IOKit.parseRegistry();
        ArrayList<IOKitNode> list = IOKit.findByClass("IOSerialBSDClient");

        try {
            for (IOKitNode n : list) {
                if (n.get("IOTTYDevice").equals(subName)) {

                    IOKitNode par = n.getParentNode();
                    while (par != null) {
                        if (par.getNodeClass().equals("IOUSBDevice")) {
                            int vid = Integer.parseInt(par.get(name));
                            return vid;
                        }
                        par = par.getParentNode();
                    }
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;

    }



}
