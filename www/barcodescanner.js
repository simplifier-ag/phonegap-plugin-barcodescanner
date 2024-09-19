/**
 * cordova is available under the MIT License (2008).
 * See http://opensource.org/licenses/alphabetical for full text.
 *
 * Copyright (c) Matt Kane 2010
 * Copyright (c) 2011, IBM Corporation
 * Copyright (c) 2012-2017, Adobe Systems
 */


        var exec = cordova.require("cordova/exec");

        var scanInProgress = false;

        /**
         * Constructor.
         *
         * @returns {BarcodeScanner}
         */
        function BarcodeScanner() {

            /**
             * Encoding constants.
             *
             * @type Object
             */
            this.Encode = {
                TEXT_TYPE: "TEXT_TYPE",
                EMAIL_TYPE: "EMAIL_TYPE",
                PHONE_TYPE: "PHONE_TYPE",
                SMS_TYPE: "SMS_TYPE"
                //  CONTACT_TYPE: "CONTACT_TYPE",  // TODO:  not implemented, requires passing a Bundle class from Javascript to Java
                //  LOCATION_TYPE: "LOCATION_TYPE" // TODO:  not implemented, requires passing a Bundle class from Javascript to Java
            };

    /**
     * Barcode format constants, defined in ZXing library.
     *
     * @type Object
     */
    this.format = {
        "aztec": 1,
        "codabar": 2,
        "code_128": 16,
        "code_39": 4,
        "code_93": 8,
        "data_MATRIX": 32,
        "ean_13": 128,
        "ean_8": 64,
        "itf": 256,
        "pdf_417": 1024,
        "qr_CODE": 2048,
        "upc_A": 16384,
        "upc_E": 32768
        };
  }

/**
 * Read code from scanner.
 *
 * @param {Function} successCallback This function will recieve a result object: {
         *        text : '12345-mock',    // The code that was scanned.
         *        format : 'FORMAT_NAME', // Code format.
         *        cancelled : true/false, // Was canceled.
         *    }
 * @param {Function} errorCallback
 * @param config
 */
BarcodeScanner.prototype.scan = function (successCallback, errorCallback, config) {

            if (config instanceof Array) {
                // do nothing
            } else {
                if (typeof(config) === 'object') {
                    // string spaces between formats, ZXing does not like that
                    if (config.formats) {
                        config.formats = config.formats.replace(/\s+/g, '');
                    }
                    config = [ config ];
                } else {
                    config = [];
                }
            }

            if (errorCallback == null) {
                errorCallback = function () {
                };
            }

            if (typeof errorCallback != "function") {
                console.log("BarcodeScanner.scan failure: failure parameter not a function");
                return;
            }

            if (typeof successCallback != "function") {
                console.log("BarcodeScanner.scan failure: success callback parameter must be a function");
                return;
            }

            if (scanInProgress) {
                errorCallback('Scan is already in progress');
                return;
            }

            scanInProgress = true;

            exec(
                function(result) {
                    scanInProgress = false;
                    // work around bug in ZXing library
                    if (result.format === 'UPC_A' && result.text.length === 13) {
                        result.text = result.text.substring(1);
                    }
                    successCallback(result);
                },
                function(error) {
                    scanInProgress = false;
                    errorCallback(error);
                },
                'BarcodeScanner',
                'scan',
                config
            );
        };

        var barcodeScanner = new BarcodeScanner();
        module.exports = barcodeScanner;
