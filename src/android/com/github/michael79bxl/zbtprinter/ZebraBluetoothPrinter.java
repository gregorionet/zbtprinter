package com.github.michael79bxl.zbtprinter;

import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import android.util.Log;
import com.zebra.android.discovery.*;
import com.zebra.sdk.graphics.internal.ZebraImageAndroid;
import com.zebra.sdk.comm.*;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.*;

public class ZebraBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "ZebraBluetoothPrinter";
    //String mac = "AC:3F:A4:1D:7A:5C";

    public ZebraBluetoothPrinter() {
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (action.equals("print")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);
                String height = args.getString(2);
                sendData(callbackContext, mac, msg, height);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        if (action.equals("find")) {
            try {
                findPrinter(callbackContext);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    public void findPrinter(final CallbackContext callbackContext) {
      try {
          BluetoothDiscoverer.findPrinters(this.cordova.getActivity().getApplicationContext(), new DiscoveryHandler() {

              public void foundPrinter(DiscoveredPrinter printer) {
                  String macAddress = printer.address;
                  //I found a printer! I can use the properties of a Discovered printer (address) to make a Bluetooth Connection
                  callbackContext.success(macAddress);
              }

              public void discoveryFinished() {
                  //Discovery is done
              }

              public void discoveryError(String message) {
                  //Error during discovery
                  callbackContext.error(message);
              }
          });
      } catch (Exception e) {
          e.printStackTrace();
      }
    }

    /*
     * This will send data to be printed by the bluetooth printer
     */
    void sendData(final CallbackContext callbackContext, final String mac, final String msg, final String height) throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Instantiate insecure connection for given Bluetooth MAC Address.
                    Connection thePrinterConn = new BluetoothConnectionInsecure(mac);

                    // Verify the printer is ready to print
                    if (isPrinterReady(thePrinterConn)) {

                        // Open the connection - physical connection is established here.
                        thePrinterConn.open();
                        ZebraPrinter printer = ZebraPrinterFactory.getInstance(thePrinterConn);
                        // Send the data to printer as a byte array.
                        String setup = "^XA^MNN,50^LL"+height+"^XZ^XA^JUS^XZ";
                        thePrinterConn.write(setup.getBytes());
                        byte[] decodedString = Base64.decode(msg, Base64.DEFAULT);
                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        printer.printImage(new ZebraImageAndroid(decodedByte), 0, 0, 0, 0, false);

                        // Make sure the data got to the printer before closing the connection
                        Thread.sleep(500);

                        // Close the insecure connection to release resources.
                        thePrinterConn.close();
                        callbackContext.success("ok");
                    } else {
						callbackContext.error("Impresora no disponible");
					}
                } catch (Exception e) {
                    // Handle communications error here.
                    callbackContext.error(e.getMessage());
                }
            }
        }).start();
    }

    private Boolean isPrinterReady(Connection connection) throws ConnectionException, ZebraPrinterLanguageUnknownException {
        Boolean isOK = false;
        connection.open();
        // Creates a ZebraPrinter object to use Zebra specific functionality like getCurrentStatus()
        ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);
        PrinterStatus printerStatus = printer.getCurrentStatus();
        if (printerStatus.isReadyToPrint) {
            isOK = true;
        } else if (printerStatus.isPaused) {
            throw new ConnectionException("No se puede imprimir, la impresora est? en pausa");
        } else if (printerStatus.isHeadOpen) {
            throw new ConnectionException("No se puede imprimir, por favor cierre la tapa de la impresora");
        } else if (printerStatus.isPaperOut) {
            throw new ConnectionException("No se puede imprimir, no hay papel en la impresora");
        } else {
            throw new ConnectionException("No se puede imprimir");
        }
        return isOK;
    }
}
