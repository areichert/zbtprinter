package com.github.areichert.zbtprinter;

import java.io.IOException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.Manifest;
import android.util.Log;
import android.content.pm.PackageManager;
import com.zebra.android.discovery.*;
import com.zebra.sdk.comm.*;
import com.zebra.sdk.printer.*;

public class ZebraBluetoothPrinter extends CordovaPlugin {

    private static final String LOG_TAG = "ZebraBluetoothPrinter";
    private final String REQUEST_ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static final int FIND_REQ_CODE = 0;
    public static final int PERMISSION_DENIED_ERROR = 20;
    private CallbackContext callbackContext;

    public ZebraBluetoothPrinter() {

    }


    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;

        if (action.equals("print")) {
            try {
                String mac = args.getString(0);
                String msg = args.getString(1);
                sendData(callbackContext, mac, msg);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
                e.printStackTrace();
            }
            return true;
        }

        if (action.equals("find")) {
          if(hasPermission())
          {
              try {
                  findPrinter();
              } catch (Exception e) {
                  Log.e(LOG_TAG, "An error occured: \n" + e.getMessage());
                  e.printStackTrace();
              }
              return true;
          } else {
              requestPermission();
          }
        }
        else {
            Log.e(LOG_TAG, "permission not found: " + android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        return false;
    }

    private boolean hasPermission() {
    		return cordova.hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private void requestPermission() {
    		cordova.requestPermission(this, FIND_REQ_CODE, android.Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
    		for (int r : grantResults) {
    			if (r == PackageManager.PERMISSION_DENIED) {
    				callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "User has denied permission"));
    				return;
    			}
    		}
    		try{
    		  findPrinter();
    		} catch(Exception e) {

    		}

    }


    protected void getReadPermission(int requestCode)
    {
        cordova.requestPermission(this, requestCode, android.Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public void findPrinter() {
      try {
          BluetoothDiscoverer.findPrinters(this.cordova.getActivity().getApplicationContext(), new DiscoveryHandler() {
              JSONArray discoveredDevices = new JSONArray();

              public void foundPrinter(DiscoveredPrinter printer) {
                  //I found a printer! I can use the properties of a Discovered printer (address) to make a Bluetooth Connection
                  if(printer instanceof DiscoveredPrinterBluetooth) {
                    JSONObject printerObj = new JSONObject();
                    try {
                      printerObj.put("address", printer.address);
                      printerObj.put("friendlyName", ((DiscoveredPrinterBluetooth) printer).friendlyName);
                      discoveredDevices.put(printerObj);
                    } catch (JSONException e) {
                    }
                  }
              }

              public void discoveryFinished() {
                  //Discovery is done
                  callbackContext.success(discoveredDevices);
              }

              public void discoveryError(String message) {
                  //Error during discovery
                  callbackContext.error("Unable to fulfill your request: " + message);
              }
        });
      } catch (Exception e) {
          e.printStackTrace();
      }
    }

    /*
     * This will send data to be printed by the bluetooth printer
     */
    void sendData(final CallbackContext callbackContext, final String mac, final String msg) throws IOException {
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

                        // Send the data to printer as a byte array.
//                        thePrinterConn.write("^XA^FO0,20^FD^FS^XZ".getBytes());
                        thePrinterConn.write(msg.getBytes());


                        // Make sure the data got to the printer before closing the connection
                        Thread.sleep(500);

                        // Close the insecure connection to release resources.
                        thePrinterConn.close();
                        callbackContext.success("Done");
                    } else {
						callbackContext.error("Printer is not ready");
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
            throw new ConnectionException("Cannot print because the printer is paused");
        } else if (printerStatus.isHeadOpen) {
            throw new ConnectionException("Cannot print because the printer media door is open");
        } else if (printerStatus.isPaperOut) {
            throw new ConnectionException("Cannot print because the paper is out");
        } else {
            throw new ConnectionException("Cannot print");
        }
        return isOK;
    }
}

