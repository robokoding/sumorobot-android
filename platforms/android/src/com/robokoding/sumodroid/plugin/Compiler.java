package com.robokoding.sumodroid.plugin;

import java.net.URL;
import java.io.File;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;

import android.util.Log;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothAdapter;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;

import org.apache.http.util.ByteArrayBuffer;

/**
 * This class compiles Arduino code and sends it to the Arduino.
 */
public class Compiler extends CordovaPlugin {
    /* threads */
    private Thread cancelThread = null;
    private Thread programThread = null;
    /* define Arduino program layout */
    private static final String end = "\n}\n";
    private static final String setupAndLoop = "void setup(){\nstart();\n}\n\nvoid loop(){\n";
    private static final String libraries = "#include <Servo.h>\n#include <Sumorobot.h>\n\n";
    /* bluetooth stuff */
    private BluetoothDevice device = null;
    private OutputStream outStream = null;
    private InputStream inputStream = null;
    private BluetoothSocket btSocket = null;
    private BluetoothAdapter btAdapter = null;
    private static final String TAG = "Sumodroid";
    private static final String address = "98:D3:31:B1:CA:BF";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        try {
            /* initialize bluetooth connection */
            Log.d(TAG, "initializing bluetooth");
            btAdapter = BluetoothAdapter.getDefaultAdapter();
            btAdapter.cancelDiscovery();
            device = btAdapter.getRemoteDevice(address);
            /* initialize cancel and programming thread */
            Log.d(TAG, "initializing threads");
        } catch (Exception e) {
            Log.d(TAG, "bluetooth initialization error: " + e.getMessage());
        }
    }

    private void connect() {
        try {
            Log.d(TAG, "connecting bluetooth");
            /* open a bluetooth socket */
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            /* connect the bluetooths */
            btSocket.connect();
            /* open the input and output streams */
            outStream = btSocket.getOutputStream();
            inputStream = btSocket.getInputStream();
        } catch (Exception e) {
            Log.d(TAG, "bluetooth connecting error: " + e.getMessage());
        }
    }

    private void disconnect() {
        try {
            Log.d(TAG, "disconnecting bluetooth");
            /* close the input and output streams */
            outStream.close();
            inputStream.close();
            /* close the bluetooth socket */
            btSocket.close();
        } catch (Exception e) {
            Log.d(TAG, "disconnecting bluetooth error: " + e.getMessage());
        }
    }

    public void writeProgram(String uncompiledProgram) {
        try {
            /* the path to the Arduino sketch */
            File file = new File("/sdcard/sumorobot/main.ino");
            /* if file doesnt exists, then create it */
            if (!file.exists()) {
                file.createNewFile();
            }
            Log.d(TAG, "opening program");
            /* open the filewriter */
            FileOutputStream writer = new FileOutputStream(file);
            Log.d(TAG, "writing program");
            /* write the Arduino sketch */
            writer.write(uncompiledProgram.getBytes());
            /* make sure everything got written */
            writer.flush();
            /* close the file */
            writer.close();
        } catch (Exception e) {
            Log.d(TAG, "writing program error: " + e.getMessage());
        }
    }

    private byte[] readProgram() {
        byte[] compiledProgram = null;
        FileInputStream fis = null;

        try {
            File file = new File("/sdcard/sumorobot/main.hex");
            /* every line, except last one, has has 45 bytes (including \r\n) */
            int programLines = (int) Math.ceil(file.length() / 45.0);
            /* every line has 32 bytes of program data (excluding checksums, addresses, etc.) */
            int unusedBytes = 45 - 32;
            /* calculate program length according to program lines and unused bytes */
            int programLength = (int) file.length() - (programLines * unusedBytes);
            /* the actualy program data is half the size, as the hex file represents hex data in individual chars */
            programLength /= 2;
            /* create a byte array with the program length */
            compiledProgram = new byte[programLength];

            /* opening the file stream */
            Log.d(TAG, "opening program");
            fis = new FileInputStream(file);
            Log.d(TAG, "Total program size (in bytes) : " + programLength);
            Log.d(TAG, "Total file size to read (in bytes) : " + fis.available());

            int content;
            int lineIndex = 0;
            int lineNumber = 1;
            int programIndex = 0;
            char[] line = new char[45];
            /* read the file byte by byte */
            Log.d(TAG, "reading program");
            while ((content = fis.read()) != -1) {
                /* append byte to the line */
                line[lineIndex++] = (char) content;
                /* when the line is complete */
                if (content == 10) {
                    /* take only the actual program data form the line */
                    for (int index = 9; index < lineIndex - 4; index += 2) {
                        /* convert hexadecimals represented as chars into bytes */
                        compiledProgram[programIndex++] = Integer.decode("0x" + line[index] + line[index+1]).byteValue();
                    }
                    /* start a new line */
                    lineIndex = 0;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "reading program error: " + e.getMessage());
        } finally {
            try {
                /* when the stream is open */
                if (fis != null)
                    /* close the stream */
                    fis.close();
            } catch (Exception e) {
                Log.d(TAG, "reading program error: " + e.getMessage());
            }
        }

        return compiledProgram;
    }

    private void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            process.waitFor();

            String line = "";
            StringBuffer output = new StringBuffer();
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = in.readLine()) != null) {
                output.append(line);
            }
            Log.d(TAG, "command output: " + output);

            StringBuffer error = new StringBuffer();
            in = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = in.readLine()) != null) {
                error.append(line);
            }
            Log.d(TAG, "command errors: " + error);
        } catch (Exception e) {
            Log.d(TAG, "command execution error: " + e.getMessage());
        }
    }

    private void upload(byte[] compiledProgram) {
        try {
            Log.d(TAG, "syncing");
            for (int i = 0; i < 5; i++) {
                outStream.write(0x30);
                outStream.write(0x20);
                Thread.sleep(50);
            }

            Log.d(TAG, "waiting for response");
            int insync = inputStream.read();
            int ok = inputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "reading major version");
            outStream.write(0x41);
            outStream.write(0x81);
            outStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "inputstream available: " + inputStream.available());
            Log.d(TAG, "waiting for response");
            insync = inputStream.read();
            int major = inputStream.read();
            ok = inputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "reading minor version");
            outStream.write(0x41);
            outStream.write(0x82);
            outStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "waiting for response");
            insync = inputStream.read();
            int minor = inputStream.read();
            ok = inputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "version: " + major + "." + minor);

            Log.d(TAG, "entering programming mode");
            outStream.write(0x50);
            outStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "waiting for response");
            insync = inputStream.read();
            ok = inputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "getting device signature");
            outStream.write(0x75);
            outStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "waiting for response");
            insync = inputStream.read();
            byte [] signature = new byte[3];
            inputStream.read(signature, 0, 3);
            ok = inputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "signature: " + signature[0] + "." + signature[1] + "." + signature[2]);

            int size = 0;
            int address = 0;
            int programIndex = 0;
            while (true) {
                int laddress = address % 256;
                int haddress = address / 256;
                address += 64;

                Log.d(TAG, "loading page address");
                outStream.write(0x55);
                outStream.write(laddress);
                outStream.write(haddress);
                outStream.write(0x20);
                //Thread.sleep(50);

                Log.d(TAG, "waiting for response");
                insync = inputStream.read();
                ok = inputStream.read();
                if (insync == 0x14 && ok == 0x10) {
                    Log.d(TAG, "insync");
                }

                if (compiledProgram.length - programIndex < 128) {
                    size = compiledProgram.length - programIndex;
                } else {
                    size = 128;
                }
                Log.d(TAG, "programming page size: " + size + " haddress: " + haddress + " laddress: " + laddress);
                outStream.write(0x64);
                outStream.write(0x00);
                outStream.write(size);
                outStream.write(0x46);
                for (int i = 0; i < size; i++) {
                    outStream.write(compiledProgram[programIndex++]);
                }
                outStream.write(0x20);
                //Thread.sleep(50);

                Log.d(TAG, "receiving sync ack");
                insync = inputStream.read();
                ok = inputStream.read();

                if (insync == 0x14 && ok == 0x10) {
                    Log.d(TAG, "insync");
                }

                if (size != 0x80) {
                    break;
                }
            }

            Log.d(TAG, "leaving programming mode");
            outStream.write(0x51);
            outStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "receiving sync ack");
            insync = inputStream.read();
            ok = inputStream.read();

            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "disconnect bluetooth");
            disconnect();
        } catch (Exception e) {
            Log.d(TAG, "programming error: " + e.getMessage());
        }
    }

    public void downloadFile(String remoteFile, String localFile) {  //this is the downloader method
        try {
                URL url = new URL(remoteFile);
                File file = new File(localFile);

                long startTime = System.currentTimeMillis();
                Log.d(TAG, "download begining");
                Log.d(TAG, "download url:" + url);
                Log.d(TAG, "downloaded file name:" + localFile);
                /* open a connection to that URL */
                Log.d(TAG, "opening url");
                URLConnection connection = url.openConnection();

                /* if file doesnt exists, then create it */
                if (!file.exists()) {
                    file.createNewFile();
                }
                /* define InputStreams to read from the URLConnection */
                Log.d(TAG, "getting url inputstream");
                InputStream in = connection.getInputStream();
                FileOutputStream fos = new FileOutputStream(file);

                int length = 0;
                int totalLength = 0;
                byte[] buffer = new byte[1024];//1024, 4096, 65536, 1048576
                int fileSize = connection.getContentLength();
                Log.d(TAG, "staring reading and writing file");
                /* read bytes to the buffer until there is nothing more to read (-1) */
                while ((length = in.read(buffer, 0, 65536)) != -1) {
                    fos.write(buffer, 0, length);
                    totalLength += length;
                    //progressBarStatus = (int)((totalLength / 70204818.0) * 100.0);
                    Log.d(TAG, "read bytes: " + totalLength);
                }
                fos.close();
                Log.d(TAG, "download ready in"
                                + ((System.currentTimeMillis() - startTime) / 1000)
                                + " sec");
        } catch (Exception e) {
                Log.d(TAG, "downloading file error: " + e.getMessage());
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("program")) {
            final String loopContent = args.getString(0);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    /* write everything to a Arduino sketch */
                    writeProgram(libraries + setupAndLoop + loopContent + end);
                    /* compile the Arduino sketch */
                    executeCommand("/system/bin/sh /sdcard/sumorobot/make.sh");
                    /* get the compiled program */
                    byte[] program = readProgram();
                    /* connect to the Arduino */
                    connect();
                    /* upload the program */
                    upload(program);
                }
            }).start();
            callbackContext.success("upload started");
            return true;
        } else if (action.equals("cancel")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    /* disconnect bluetooth */
                    disconnect();
                }
            }).start();
            callbackContext.success("disconnecting started");
            return true;
        } else if (action.equals("download")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    /* download busybox */
                    downloadFile("https://andavr.googlecode.com/files/busybox", "/sdcard/sumorobot/busybox");
                    /* execute install */
                    executeCommand("/system/bin/sh /sdcard/sumorobot/unpack.sh");
                }
            }).start();
            callbackContext.success("download started");
            return true;
        }
        callbackContext.error("Expected one non-empty string argument.");
        return false;
    }
}