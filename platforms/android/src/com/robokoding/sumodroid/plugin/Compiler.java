package com.robokoding.sumodroid.plugin;

import java.net.URL;
import java.io.File;
import java.util.UUID;
import java.util.Arrays;
import java.util.ArrayList;
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
import android.os.Looper;
import android.os.Environment;
import android.content.Intent;
import android.content.Context;
import android.app.AlertDialog;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.content.BroadcastReceiver;
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
 * This class compiles Arduino code and sends it to the Sumorobot.
 */
public class Compiler extends CordovaPlugin {
    /* app tag for log messages */
    private static final String TAG = "Compiler";
    /* define Arduino program layout */
    private static final String ARDUINO_END = "\n}\n";
    private static final String ARDUINO_SETUP_LOOP = "void setup(){\nSerial.begin(115200);\n}\n\nvoid loop(){\n";
    private static final String ARDUINO_LIBRARIES = "#include <Servo.h>\n#include <NewPing.h>\n#include <Sumorobot.h>\n\n";
    /* bluetooth stuff */
    private BluetoothSocket bluetoothSocket = null;
    private BluetoothDevice bluetoothDevice = null;
    private OutputStream bluetoothOutputStream = null;
    private InputStream bluetoothInputStream = null;
    private BluetoothAdapter bluetoothAdapter = null;
    private static ArrayList<BluetoothDevice> bluetoothDevices;
    private static String sumorobotAddress = "98:D3:31:B2:F4:A1";
    private static final File EXTERNAL_STORAGE = Environment.getExternalStorageDirectory();
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        try {
            bluetoothDevices = new ArrayList<BluetoothDevice>();
            /* initialize bluetooth connection */
            Log.d(TAG, "initializing bluetooth");
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            /* when bluetooth is off */
            if (!bluetoothAdapter.isEnabled()) {
                /* turn on bluetooth */
                Log.d(TAG, "turning on bluetooth");
                bluetoothAdapter.enable();
            }
            /* cancel bluetooth discovery */
            bluetoothAdapter.cancelDiscovery();
        } catch (Exception e) {
            Log.d(TAG, "bluetooth initialization error: " + e.getMessage());
        }
    }

    private void connectBluetooth() {
        try {
            Log.d(TAG, "connecting bluetooth");
            /* open a bluetooth socket */
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(sumorobotAddress);
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(MY_UUID);
            /* connect the bluetooths */
            bluetoothSocket.connect();
            /* open the input and output streams */
            bluetoothOutputStream = bluetoothSocket.getOutputStream();
            bluetoothInputStream = bluetoothSocket.getInputStream();
        } catch (Exception e) {
            Log.d(TAG, "bluetooth connecting error: " + e.getMessage());
        }
    }

    private void disconnectBluetooth() {
        try {
            Log.d(TAG, "disconnecting bluetooth");
            /* close the input and output streams */
            bluetoothOutputStream.close();
            bluetoothInputStream.close();
            /* close the bluetooth socket */
            bluetoothSocket.close();
        } catch (Exception e) {
            Log.d(TAG, "disconnecting bluetooth error: " + e.getMessage());
        }
    }

    public void writeProgram(String uncompiledProgram) {
        try {
            /* the path to the Arduino sketch */
            File file = new File(EXTERNAL_STORAGE, "sumodroid/main.ino");
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

    private byte[] readProgram(String name) {
        byte[] compiledProgram = null;
        FileInputStream fis = null;

        try {
            File file = new File(EXTERNAL_STORAGE, "sumodroid/" + name);
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
                    /* take only the actual program data from the line */
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

    private void uploadProgram(byte[] compiledProgram) {
        try {
            Log.d(TAG, "syncing");
            for (int i = 0; i < 5; i++) {
                bluetoothOutputStream.write(0x30);
                bluetoothOutputStream.write(0x20);
                Thread.sleep(50);
            }

            Log.d(TAG, "waiting for response");
            int insync = bluetoothInputStream.read();
            int ok = bluetoothInputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "reading major version");
            bluetoothOutputStream.write(0x41);
            bluetoothOutputStream.write(0x81);
            bluetoothOutputStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "waiting for response");
            insync = bluetoothInputStream.read();
            int major = bluetoothInputStream.read();
            ok = bluetoothInputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "reading minor version");
            bluetoothOutputStream.write(0x41);
            bluetoothOutputStream.write(0x82);
            bluetoothOutputStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "waiting for response");
            insync = bluetoothInputStream.read();
            int minor = bluetoothInputStream.read();
            ok = bluetoothInputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "version: " + major + "." + minor);

            Log.d(TAG, "entering programming mode");
            bluetoothOutputStream.write(0x50);
            bluetoothOutputStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "waiting for response");
            insync = bluetoothInputStream.read();
            ok = bluetoothInputStream.read();
            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }

            Log.d(TAG, "getting device signature");
            bluetoothOutputStream.write(0x75);
            bluetoothOutputStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "waiting for response");
            insync = bluetoothInputStream.read();
            byte [] signature = new byte[3];
            bluetoothInputStream.read(signature, 0, 3);
            ok = bluetoothInputStream.read();
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
                bluetoothOutputStream.write(0x55);
                bluetoothOutputStream.write(laddress);
                bluetoothOutputStream.write(haddress);
                bluetoothOutputStream.write(0x20);
                //Thread.sleep(50);

                Log.d(TAG, "waiting for response");
                insync = bluetoothInputStream.read();
                ok = bluetoothInputStream.read();
                if (insync == 0x14 && ok == 0x10) {
                    Log.d(TAG, "insync");
                }

                if (compiledProgram.length - programIndex < 128) {
                    size = compiledProgram.length - programIndex;
                } else {
                    size = 128;
                }
                Log.d(TAG, "programming page size: " + size + " haddress: " + haddress + " laddress: " + laddress);
                bluetoothOutputStream.write(0x64);
                bluetoothOutputStream.write(0x00);
                bluetoothOutputStream.write(size);
                bluetoothOutputStream.write(0x46);
                for (int i = 0; i < size; i++) {
                    bluetoothOutputStream.write(compiledProgram[programIndex++]);
                }
                bluetoothOutputStream.write(0x20);
                //Thread.sleep(50);

                Log.d(TAG, "receiving sync ack");
                insync = bluetoothInputStream.read();
                ok = bluetoothInputStream.read();

                if (insync == 0x14 && ok == 0x10) {
                    Log.d(TAG, "insync");
                }

                if (size != 0x80) {
                    break;
                }
            }

            Log.d(TAG, "leaving programming mode");
            bluetoothOutputStream.write(0x51);
            bluetoothOutputStream.write(0x20);
            Thread.sleep(50);

            Log.d(TAG, "receiving sync ack");
            insync = bluetoothInputStream.read();
            ok = bluetoothInputStream.read();

            if (insync == 0x14 && ok == 0x10) {
                Log.d(TAG, "insync");
            }
        } catch (Exception e) {
            Log.d(TAG, "programming error: " + e.getMessage());
        }
    }

    private void startBluetoothDiscovery() {
        /* clear previously found devices */
        bluetoothDevices.clear();
        /* register the BroadcastReceiver */
        //IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        //filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        //cordova.getActivity().getApplicationContext().registerReceiver(mReceiver, filter);
        Log.d(TAG, "starting to search bluetooth devices");
        //bluetoothAdapter.startDiscovery();
        /* notify frontend to stop showing activity */
        //webView.sendJavascript("app.stopShowingActivity()");
        /* add bonded devices */
        bluetoothDevices.addAll(bluetoothAdapter.getBondedDevices());
        int index = 0;
        String[] bluetoothDeviceNames = new String[bluetoothDevices.size()];
        for (BluetoothDevice device : bluetoothDevices) {
            bluetoothDeviceNames[index++] = device.getName();
        }
        /* show bluetooth devices for selection */
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(cordova.getActivity(), AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        alertDialog.setCancelable(true);
        alertDialog.setTitle("Please select your Sumorobot");
        alertDialog.setItems(bluetoothDeviceNames, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int selectedIndex) {
                sumorobotAddress = bluetoothDevices.get(selectedIndex).getAddress();
                dialog.dismiss();
            }
        });
        alertDialog.create();
        alertDialog.show();
    }

    /* create a BroadcastReceiver for ACTION_FOUND and ACTION_DISCOVERY_FINISHED */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            /* when discovery finds a device */
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                /* get the BluetoothDevice object from the Intent */
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                /* add the name and address to an array adapter to show in a ListView */
                if (device != null && device.getName() != null && device.getAddress() != null) {
                    bluetoothDevices.add(device);
                    Log.d(TAG, "found bluetooth device: " + device.getName());
                }
            /* when bluetooth discovery has finished */
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                bluetoothAdapter.cancelDiscovery();
                Log.d(TAG, "bluetooth device search finished");
                cordova.getActivity().getApplicationContext().unregisterReceiver(mReceiver);
                /* also include all paired devices */
                bluetoothDevices.addAll(bluetoothAdapter.getBondedDevices());
                /* send the bluetooth device names to the web frontend */
                int index = 0;
                String[] bluetoothDeviceNames = new String[bluetoothDevices.size()];
                for (BluetoothDevice device : bluetoothDevices) {
                    bluetoothDeviceNames[index++] = device.getName();
                }
                /* notify frontend to stop showing activity */
                webView.sendJavascript("app.stopShowingActivity()");
                /* send the sumorobot names back to the frontend */
                //webView.sendJavascript("app.showSumorobots('" + bluetoothDeviceNames + "')");
                AlertDialog.Builder alertDialog = new AlertDialog.Builder(cordova.getActivity(), AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
                alertDialog.setCancelable(true);
                alertDialog.setTitle("Please select your Sumorobot");
                alertDialog.setItems(bluetoothDeviceNames, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int selectedIndex) {
                        sumorobotAddress = bluetoothDevices.get(selectedIndex).getAddress();
                        dialog.dismiss();
                    }
                });
                alertDialog.create();
                alertDialog.show();
            }
        }
    };

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("uploadProgram")) {
            /* check if the compiler is installed */
            File compiler = new File(EXTERNAL_STORAGE, "sumodroid/compiler");
            if (!compiler.exists()) {
                callbackContext.success("Please install the compiler first");
                return true;
            }
            /* check if the user has selected a sumorobot */
            if (sumorobotAddress.equals("")) {
                callbackContext.success("Please select a sumorobot first");
                return true;
            }

            final String arduinoLoopContent = args.getString(0);
            final boolean disconnectAfterUpload = args.getBoolean(1);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    /* notify frontend to start showing activity */
                    //webView.sendJavascript("app.startShowingActivity('Uploading program')");
                    /* write everything to a Arduino sketch */
                    writeProgram(ARDUINO_LIBRARIES + ARDUINO_SETUP_LOOP + arduinoLoopContent + ARDUINO_END);
                    /* compile the Arduino sketch */
                    executeCommand("/system/bin/sh " + EXTERNAL_STORAGE.getAbsolutePath() + "/sumodroid/make.sh");
                    /* get the compiled program */
                    byte[] program = readProgram("main.hex");
                    /* connect to the Arduino */
                    connectBluetooth();
                    /* upload the program */
                    uploadProgram(program);
                    /* when disconnect after uploading */
                    if (disconnectAfterUpload) {
                        /* disconnect bluetooth */
                        disconnectBluetooth();
                    } else {
                        webView.sendJavascript("app.querySensorValues()");
                    }
                    /* notify frontend to stop showing activity */
                    //webView.sendJavascript("app.stopShowingActivity()");
                }
            }).start();
            Log.d(TAG, "uplaoding program");
            callbackContext.success("Uploading program");
            return true;
        } else if (action.equals("sendCommands")) {
            final String commands = args.getString(0);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        /* send the commands to the Sumorobot */
                        bluetoothOutputStream.write(commands.getBytes());
                        /* receive sensor values from the Sumorobot */
                        int index = 0;
                        int[] sensorValues = new int[] {0, 0, 0, 0, 0};
                        while (true) {
                            char value = (char) bluetoothInputStream.read();
                            if (value == '[') {
                                continue;
                            } else if (value == ']') {
                                break;
                            } else if (value == ',') {
                                index++;
                            } else {
                                sensorValues[index] = Integer.parseInt(String.valueOf(value)) + sensorValues[index] * 10;
                            }
                        }
                        Log.d(TAG, "received sensor values from sumorobot: " + Arrays.toString(sensorValues));
                        webView.sendJavascript("app.receiveSensorValues(" + Arrays.toString(sensorValues) + ")");
                    } catch (Exception e) {
                        Log.d(TAG, "sending commands error: " + e.getMessage());
                    }
                }
            }).start();
            Log.d(TAG, "sending commands to sumorobot");
            callbackContext.success();
            return true;
        } else if (action.equals("cancelUploadingProgram")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    /* disconnect bluetooth */
                    disconnectBluetooth();
                }
            }).start();
            callbackContext.success("Uploading program canceled");
            return true;
        } else if (action.equals("startSumorobotDiscovery")) {
            /* notify frontend to start showing activity */
            //webView.sendJavascript("app.startShowingActivity('Discovering sumorobots')");
            /* start to search for bluetooth devices */
            startBluetoothDiscovery();
            callbackContext.success();
            return true;
        } else if (action.equals("selectSumorobot")) {
            /* when no sumorobots were discovered yet */
            if (bluetoothDevices.size() == 0) {
                callbackContext.success("No sumorobots discovered yet");
                return true;
            }
            int selectedSumorobotIndex = args.getInt(0);
            /* get the selected sumorobot's bluetooth address */
            sumorobotAddress = bluetoothDevices.get(selectedSumorobotIndex).getAddress();
            Log.d(TAG, "selected sumorobot: " + bluetoothDevices.get(selectedSumorobotIndex).getName());
            callbackContext.success();
            return true;
        } else if (action.equals("extractCompiler")) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    /* notify frontend to start showing activity */
                    webView.sendJavascript("app.startShowingActivity('Extracting compiler')");
                    /* extract the compiler */
                    String external = EXTERNAL_STORAGE.getAbsolutePath();
                    executeCommand(external + "/sumodroid/busybox tar -xzvf " + external + "/sumodroid/compiler.tar.gz -C " + external + "/sumodroid/");
                    /* remove the compiler tarball */
                    executeCommand(external + "/sumodroid/busybox rm " + external + "/sumodroid/compiler.tar.gz");
                    /* notify frontend to stop showing activity */
                    webView.sendJavascript("app.stopShowingActivity()");
                    /* start sumorobot discovery */
                    webView.sendJavascript("app.startSumorobotDiscovery()");
                }
            }).start();
            Log.d(TAG, "extracting compiler");
            callbackContext.success();
            return true;
        } else if (action.equals("createFolder")) {
            final String folderName = args.getString(0);
            /* create a folder on the external storage */
            File folder = new File(EXTERNAL_STORAGE, folderName);
            if (!folder.exists()) {
                folder.mkdir();
                Log.d(TAG, "created " + folderName + " folder");
            } else {
                Log.d(TAG, "folder " + folderName + " already exists");
            }
            callbackContext.success();
            return true;
        }
        callbackContext.error("unknown action: " + action);
        return false;
    }
}