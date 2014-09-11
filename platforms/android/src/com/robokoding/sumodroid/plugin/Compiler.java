package com.robokoding.sumodroid.plugin;

import java.io.*;
import java.util.UUID;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//import java.io.FileInputStream;
//import java.io.InputStreamReader;
//import java.io.FileNotFoundException;

import android.os.Build;
import android.widget.Toast;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothAdapter;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class compiles Arduino code and sends it to the Arduino.
 */
public class Compiler extends CordovaPlugin {
    private OutputStream outStream = null;
    private InputStream inputStream = null;
    private BluetoothSocket btSocket = null;
    private BluetoothAdapter btAdapter = null;
    private static String address = "98:D3:31:B1:CA:BF"; // SumoKati
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        // initialize bluetooth
        try {
            btAdapter = BluetoothAdapter.getDefaultAdapter();
            btAdapter.cancelDiscovery();
        } catch (Exception e) {
            //log.logcat("initialiseConnectButton: Connection OK...", "d");
            Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void connect() {
        try {
            BluetoothDevice device = btAdapter.getRemoteDevice(address);
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            btSocket.connect();
        } catch (Exception e) {
            //log.logcat("...Output stream creating failed...", "d");
            Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
        try {
            outStream = btSocket.getOutputStream();
        } catch (Exception e) {
            //log.logcat("...Output stream creating failed...", "d");
            Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
        try {
            inputStream = btSocket.getInputStream();
        } catch (Exception e) {
            //log.logcat("...Input stream creation failed...", "d");
            Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void disconnect() {
        try {
            //log.logcat("disconnecting bluetooth", "d");
            outStream.close();
            inputStream.close();
            btSocket.close();
        } catch (Exception e) {
            Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void writeProgram(String program) {
        try {
            File file = new File("/sdcard/sumorobot/main.ino");
 
            // if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
 
            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(program);
            bw.close();
        } catch (IOException e) {
            Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private byte[] readProgram() {
        FileInputStream fis = null;
        File file = new File("/sdcard/sumorobot/main.hex");

        // every line, except last one, has has 45 bytes (including \r\n)
        int programLines = (int) Math.ceil(file.length() / 45.0);
        // every line has 32 bytes of program data (excluding checksums, addresses, etc.)
        int unusedBytes = 45 - 32;
        // calculate program length according to program lines and unused bytes
        int programLength = (int) file.length() - (programLines * unusedBytes);
        // the actualy program data is half the size, as the hex file represents hex data in individual chars
        programLength /= 2;
        // create a byte array with the program length
        byte[] program = new byte[programLength];

        try {
            // open the file stream
            //log.logcat("opening hex file", "d");
            fis = new FileInputStream(file);
            //log.logcat("Total program size (in bytes) : " + programLength, "d");
            //log.logcat("Total file size to read (in bytes) : " + fis.available(), "d");
            //Toast.makeText(cordova.getActivity(), "Total program size (in bytes) : " + programLength, Toast.LENGTH_SHORT).show();
            //Toast.makeText(cordova.getActivity(), "Total file size to read (in bytes) : " + fis.available(), Toast.LENGTH_SHORT).show();

            int content;
            int lineIndex = 0;
            int lineNumber = 1;
            int programIndex = 0;
            char[] line = new char[45];
            // read the file byte by byte
            while ((content = fis.read()) != -1) {
                // append byte to the line
                line[lineIndex++] = (char) content;
                // when the line is complete
                if (content == 10) {
                    // take only the actual program data form the line
                    for (int index = 9; index < lineIndex - 4; index += 2) {
                        // convert hexadecimals represented as chars into bytes
                        program[programIndex++] = Integer.decode("0x" + line[index] + line[index+1]).byteValue();
                    }
                    // start a new line
                    lineIndex = 0;
                }
            }
        } catch (IOException e) {
            //log.logcat("reading hex failed: " + e.getMessage(), "d");
            Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return program;
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("compile")) {

            writeProgram("#include <Servo.h>\n#include <Sumorobot.h>\n\nvoid setup(){start();}\nvoid loop(){\n" + args.getString(0) + "\n}");
            //Toast.makeText(cordova.getActivity(), "compiling code", Toast.LENGTH_SHORT).show();
            try {
                Process process = Runtime.getRuntime().exec("/system/bin/sh /sdcard/sumorobot/make.sh");
                process.waitFor();

                BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = "";
                String output = "";
                while ((line = in.readLine()) != null) {
                    output += line;
                }

                in = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String error = "";
                while ((line = in.readLine()) != null) {
                    error += line;
                }

                //Toast.makeText(cordova.getActivity(), output, Toast.LENGTH_LONG).show();
                //Toast.makeText(cordova.getActivity(), error, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
            }

            byte[] program = readProgram();
            //log.logcat("program length: " + program.length, "d");

            //Toast.makeText(cordova.getActivity(), "connecting bluetooth", Toast.LENGTH_SHORT).show();
            connect();

            //Toast.makeText(cordova.getActivity(), "programming", Toast.LENGTH_SHORT).show();
            try {
                //log.logcat("syncing", "d");
                for (int i = 0; i < 5; i++) {
                    outStream.write(0x30);
                    outStream.write(0x20);
                    try {Thread.sleep(50);} catch (InterruptedException e) {}
                }

                //log.logcat("waiting for response", "d");
                int insync = inputStream.read();
                int ok = inputStream.read();
                if (insync == 0x14 && ok == 0x10) {
                    //log.logcat("insync", "d");
                }

                //log.logcat("reading major version", "d");
                outStream.write(0x41);
                outStream.write(0x81);
                outStream.write(0x20);
                try {Thread.sleep(50);} catch (InterruptedException e) {}

                //log.logcat("waiting for response", "d");
                insync = inputStream.read();
                int major = inputStream.read();
                ok = inputStream.read();
                if (insync == 0x14 && ok == 0x10) {
                    //log.logcat("insync", "d");
                }

                //log.logcat("reading minor version", "d");
                outStream.write(0x41);
                outStream.write(0x82);
                outStream.write(0x20);
                try {Thread.sleep(50);} catch (InterruptedException e) {}

                //log.logcat("waiting for response", "d");
                insync = inputStream.read();
                int minor = inputStream.read();
                ok = inputStream.read();
                if (insync == 0x14 && ok == 0x10) {
                    //log.logcat("insync", "d");
                }

                //log.logcat("version: " + major + "." + minor, "d");

                //log.logcat("entering programming mode", "d");
                outStream.write(0x50);
                outStream.write(0x20);
                try {Thread.sleep(50);} catch (InterruptedException e) {}

                //log.logcat("waiting for response", "d");
                insync = inputStream.read();
                ok = inputStream.read();
                if (insync == 0x14 && ok == 0x10) {
                    //log.logcat("insync", "d");
                }

                //log.logcat("getting device signature", "d");
                outStream.write(0x75);
                outStream.write(0x20);
                try {Thread.sleep(50);} catch (InterruptedException e) {}

                //log.logcat("waiting for response", "d");
                insync = inputStream.read();
                byte [] signature = new byte[3];
                inputStream.read(signature, 0, 3);
                ok = inputStream.read();
                if (insync == 0x14 && ok == 0x10) {
                    //log.logcat("insync", "d");
                }

                //log.logcat("signature: " + signature[0] + "." + signature[1] + "." + signature[2], "d");

                int size = 0;
                int address = 0;
                int programIndex = 0;
                while (true) {
                    int laddress = address % 256;
                    int haddress = address / 256;
                    address += 64;

                    //log.logcat("loading page address", "d");
                    outStream.write(0x55);
                    outStream.write(laddress);
                    outStream.write(haddress);
                    outStream.write(0x20);
                    try {Thread.sleep(50);} catch (InterruptedException e) {}

                    //log.logcat("waiting for response", "d");
                    insync = inputStream.read();
                    ok = inputStream.read();
                    if (insync == 0x14 && ok == 0x10) {
                        //log.logcat("insync", "d");
                    }

                    if (program.length - programIndex < 128) {
                        size = program.length - programIndex;
                    } else {
                        size = 128;
                    }
                    //log.logcat("programming page size: " + size + " haddress: " + haddress + " laddress: " + laddress, "d");
                    outStream.write(0x64);
                    outStream.write(0x00);
                    outStream.write(size);
                    outStream.write(0x46);
                    for (int i = 0; i < size; i++) {
                        outStream.write(program[programIndex++]);
                    }
                    outStream.write(0x20);
                    try {Thread.sleep(50);} catch (InterruptedException e) {}

                    //log.logcat("receiving sync ack", "d");
                    insync = inputStream.read();
                    ok = inputStream.read();

                    if (insync == 0x14 && ok == 0x10) {
                        //log.logcat("insync", "d");
                    }

                    if (size != 0x80) {
                        break;
                    }
                }
                //log.logcat("program index: " + programIndex, "d");

                //log.logcat("leaving programming mode", "d");
                outStream.write(0x51);
                outStream.write(0x20);
                try {Thread.sleep(50);} catch (InterruptedException e) {}

                //log.logcat("receiving sync ack", "d");
                insync = inputStream.read();
                ok = inputStream.read();

                if (insync == 0x14 && ok == 0x10) {
                    //log.logcat("insync", "d");
                }
            } catch (Exception e) {
                Toast.makeText(cordova.getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
            }

            //log.logcat("disconnect bluetooth", "d");
            //Toast.makeText(cordova.getActivity(), "disconnecting bluetooth", Toast.LENGTH_SHORT).show();
            disconnect();
            
            callbackContext.success("upload successfull");
            return true;
        }
        callbackContext.error("Expected one non-empty string argument.");
        return false;
    }
}