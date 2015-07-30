/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
var app = {
    /* sumorobot code for interactive mode */
    code: "",
    /* interval for querying sensor values */
    sensorInterval: null,
    /* disable/enable interavtive mode */
    interactiveMode: false,
    /* sensor values */
    sensorValues: [0, 0, 0, 0, 0],
    /* application constructor */
    initialize: function() {
        /* add deviceready event listener */
        document.addEventListener("deviceready", app.onDeviceReady, false);
    },

    /* function that will be called when device APIs are available */
    onDeviceReady: function() {
        /* get the device language */
        navigator.globalization.getLocaleName(
            function (locale) {
                var language = locale.value.substring(0, 2);
                /* when language is not supported */
                if (language !== "en" && language !== "de" && language !== "et") {
                    /* choose english as default */
                    language = "en";
                }

                var buttons = {
                    "en": ["upload", "cancel", "interactive", "search"],
                    "de": ["upload", "abbrechen", "interactive", "suche"],
                    "et": ["upload", "katkesta", "interactive", "otsi"]
                };
                document.getElementById('upload').value = buttons[language][0];
                document.getElementById('cancel').value = buttons[language][1];
                document.getElementById('active').value = buttons[language][2];
                document.getElementById('search').value = buttons[language][3];

                /* load the language */
                var script = document.createElement('script');
                script.type = 'text/javascript';
                script.src = 'js/blockly/msg/js/' + language + '.js';
                document.head.appendChild(script);

                /* wait for script to load */
                script.onload = function() {
                    /* initialize Blockly */
                    Blockly.inject(document.body, {
                        trashcan: true,                             // show the trashcan
                        path: "js/blockly/",                        // Blockly's path
                        toolbox: document.getElementById('toolbox') // the toolbox element
                    });
                    /* remove Blockly's border */
                    var elements = document.getElementsByClassName('blocklySvg')[0].style.border = "none";
                };
            },
            function (error) { app.showMessage("Error getting language: " + error); }
        );

        /* check if compiler is installed */
        var successCallback = function(folderExists) {
            /* when the compiler does not exsist */
            if (!folderExists) {
                /* notify user about the download */
                var onSelect = function(index) {
                    /* when the user agreed */
                    if (index == 1) {
                        /* install the compiler */
                        app.installCompiler();
                    }
                };
                /* message, confirm callback, title, button names */
                navigator.notification.confirm("For installing the compiler about 20 MB will be downloaded, please check your internet connection", onSelect, "Message", "OK,Cancel");
            /* otherwise, start sumorobot discovery */
            } else {
                //app.startSumorobotDiscovery();
            }
        };
        app.pluginCallback("File", "testDirectoryExists", ["sumodroid/compiler"], successCallback);
    },

    /* function to download the compiler and extract it */
    installCompiler: function() {
        /* target and source locations */
        var sumodroidFolder = "/sdcard/sumodroid";
        var githubRelease = "https://github.com/silps/sumodroid/releases/download/v1.0";

        /* define download and extract functions */
        var downloadBusybox = function(response) {
            /* download busybox */
            app.downloadFile("busybox",
                "https://dl.dropboxusercontent.com/u/22343106/busybox", sumodroidFolder + "/busybox", downloadCompiler);
        };
        var downloadCompiler = function(response) {
            /* download the rest (Arduino, Sumorobot library, avr-gcc, Makefiles, make script) */
            app.downloadFile("compiler",
                "https://dl.dropboxusercontent.com/u/22343106/compiler.tar.gz", sumodroidFolder + "/compiler.tar.gz", extractCompiler);
        };
        var extractCompiler = function(response) {
            /* extract tarballs and when finished start sumorobot discovery */
            app.pluginCallback("Compiler", "extractCompiler", []);
        };
        /* create the sumodroid folder */
        app.pluginCallback("Compiler", "createFolder", ["sumodroid"], downloadBusybox);
    },

    /* function to download the source file to target location and show the progress */
    downloadFile: function(name, source, target, successCallback, failureCallback) {
        /* initialize filetransfer for the downlaod */
        var fileTransfer = new FileTransfer();
        var uri = encodeURI(source);

        /* start showing the progressbar for the download */
        navigator.notification.progressStart("Download", "Downloading " + name);
        /* register on progress event */
        fileTransfer.onprogress = function(progressEvent) {
            if (progressEvent.lengthComputable) {
                /* show progressbar with progress value */
                navigator.notification.progressValue(
                    /* calculate precentage and convert it to and integer */
                    parseInt((progressEvent.loaded / progressEvent.total) * 100)
                );
            } else {
                /* hide progressbar with value and show simple activity */
                navigator.notification.progressStop();
                navigator.notification.activityStart("Download", "Downloading " + name);
                /* as not progress value was available, eliminate this function */
                fileTransfer.onprogress = null;
            }
        };
        /* start downloading the compiler */
        fileTransfer.download(
            uri,
            target,
            /* download success function */
            function(response) {
                /* stop showing progress */
                navigator.notification.activityStop();
                navigator.notification.progressStop();
                if (successCallback != null) {
                    successCallback(response);
                } else {
                    app.showMessage("Downloading " + name + " successful");
                }
            },
            /* download failed function */
            function(error) {
                /* stop showing progress */
                navigator.notification.activityStop();
                navigator.notification.progressStop();
                if (failureCallback != null) {
                    failureCallback(error);
                } else {
                    app.showMessage("Downloading " + name + " failed, please check your internet connection");
                }
            }
        );
    },

    querySensorValues: function(values) {
        /* query sumorobot sensor values */
        app.pluginCallback("Compiler", "sendCommands", ["p"]);
    },

    receiveSensorValues: function(values) {
        /* save the sensor values */
        app.sensorValues = values;
        /* evaluate the sumorobot code with the new sensor values */
        app.evaluateSumorobotCode(app.code);
    },

    evaluateSumorobotCode: function(code) {
        /* define the variables and functions before evaluation */
        var ENEMY_LEFT = app.sensorValues[0] < 60;
        var ENEMY_RIGHT = app.sensorValues[1] < 60;
        var LINE_LEFT = app.sensorValues[2] > 600;
        var LINE_MIDDLE = app.sensorValues[3] > 600;
        var LINE_RIGHT = app.sensorValues[4] > 600;
        var sendCommand = function(command) {
            /* send the commands to the Sumorobot */
            app.pluginCallback("Compiler", "sendCommands", [command]);
        };
        /* evaluate the sumorobot code */
        eval(code);
    },

    onCodeChanged: function() {
        var temp = Blockly.Sumorobot.workspaceToCode();
        /* replace functions with sending command codes */
        temp = temp.replace(/stop\(\)/g, "sendCommand('x')");
        temp = temp.replace(/left\(\)/g, "sendCommand('a')");
        temp = temp.replace(/delay\(\)/g, "sendCommand('p')");
        temp = temp.replace(/right\(\)/g, "sendCommand('d')");
        temp = temp.replace(/forward\(\)/g, "sendCommand('w')");
        temp = temp.replace(/backward\(\)/g, "sendCommand('s')");
        /* when code has actually not changed */
        if (temp == app.code) {
            return;
        }
        /* save the new code */
        app.code = temp;
        /* check whether if conditions are complete */
        if (temp.indexOf("{\n}") != -1 || temp.indexOf("(false)") != -1) {
            return;
        }
        /* evaluate the new sumorobot code */
        app.evaluateSumorobotCode(app.code);
    },

    triggerInteractiveMode: function() {
        /* disable/enable interactive mode */
        app.interactiveMode = !app.interactiveMode;
        /* when in interactive mode */
        if (app.interactiveMode) {
            /* upload the interactive program to the Sumorobot and leave bluetooth connected */
            app.pluginCallback("Compiler", "uploadProgram", ["checkForCommands();", false]);
            /* start listening to change events */
            Blockly.addChangeListener(app.onCodeChanged);
            /* set a interval for getting sensor values */
            //setTimeout(function() { app.sensorInterval = setInterval(app.querySensorValues, 1000); }, 5000);
        } else {
            /* stop listening for change events */
            Blockly.removeChangeListener(app.onCodeChanged);
            /* clear the interval */
            //clearInterval(app.sensorInterval);
        }
    },

    /* function to upload program to the sumorobot and disconnect bluetooth */
    uploadProgram: function() {
        app.pluginCallback("Compiler", "uploadProgram", [Blockly.Sumorobot.workspaceToCode(), true]);
    },

    /* function to cancel uploading program to the sumorobot */
    cancelUploadingProgram: function() {
        app.pluginCallback("Compiler", "cancelUploadingProgram", []);
    },

    /* function to start sumorobot discovery */
    startSumorobotDiscovery: function() {
        app.pluginCallback("Compiler", "startSumorobotDiscovery", []);
    },

    /* function to show sumorobot names for selection */
    showSumorobots: function(devices) {
        /* will be called when a sumorobot was selected */
        var selectSumorobot = function(sumorobotIndex) {
            /* notify the compiler which sumorobot was selected */
            app.pluginCallback("Compiler", "selectSumorobot", [sumorobotIndex - 1]);
        };
        /* when at least one device was found */
        if (devices.length > 0) {
            /* message, confirm callback, title, button names */
            navigator.notification.confirm("Please select your Sumorobot", selectSumorobot, "Message", devices);
        /* otherwise notify user to check the sumorobot's bluetooth device */
        } else {
            app.showMessage("Please make sure the Sumorobot's bluetooth is switched ON");
        }
    },

    /* function to show loading activity */
    startShowingActivity: function(message) {
        navigator.notification.activityStart("Loading", message);
    },

    /* function to stop showing loading activity */
    stopShowingActivity: function() {
        navigator.notification.activityStop();
    },

    /* function to make a asynchronous call to the plugins */
    pluginCallback: function(plugin, action, args, successCallback, failureCallback) {
        cordova.exec(
            /* callback success function */
            function(response) {
                if (typeof(response) === 'string' && response != 'OK') {
                    /* show the response */
                    app.showMessage(response);
                }
                /* when a success callback function was given */
                if (typeof(successCallback) !== 'undefined') {
                    /* pass on the response */
                    successCallback(response);
                }
            },
            /* callback error fucntion */
            function(error) {
                /* when a failure callback function was given */
                if (typeof(failureCallback) !== 'undefined') {
                    /* pass on the error */
                    failureCallback(error);
                /* otherwise */
                } else {
                    /* show the error */
                    app.showMessage(error);
                }
            },
            plugin, // name of the plugin
            action, // name of the action
            args    // json arguments
        );
    },
    
    /* function to show alert messages */
    showMessage: function(message) {
        /* message, dismissed callback, button name */
        navigator.notification.alert(message, null, "Message", "OK");
    }
};
