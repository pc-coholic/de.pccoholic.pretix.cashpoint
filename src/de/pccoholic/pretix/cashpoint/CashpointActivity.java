package de.pccoholic.pretix.cashpoint;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ebanx.swipebtn.OnActiveListener;
import com.ebanx.swipebtn.SwipeButton;
import com.github.paolorotolo.expandableheightlistview.ExpandableHeightListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import eu.pretix.pretixdroid.net.api.PretixApi;
import zj.com.cn.bluetooth.sdk.BluetoothService;
import zj.com.command.sdk.PrinterCommand;

public class CashpointActivity extends AppCompatActivity {
    // ESC/POS related stuff
    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_CONNECTION_LOST = 6;
    public static final int MESSAGE_UNABLE_CONNECT = 7;
    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_CHOSE_BMP = 3;
    private static final int REQUEST_CAMER = 4;
    // QRcode
    private static final int QR_WIDTH = 350;
    private static final int QR_HEIGHT = 350;
    // Encoding
    private static final String CHINESE = "GBK";
    private static final String THAI = "CP874";
    private static final String KOREAN = "EUC-KR";
    private static final String BIG5 = "BIG5";
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the services
    private BluetoothService mService = null;

    // Non-ESC/POS related stuff
    private View contentView;
    private SharedPreferences prefs;
    private BluetoothDeviceManager deviceManager;
    public static Map<Integer,String> itemNames;
    public static Map<Integer,String> itemVariations;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        setContentView(getScanBackground());
        contentView = this.findViewById(android.R.id.content);
        deviceManager = new BluetoothDeviceManager(this);
        itemNames = new HashMap<Integer, String>();
        itemVariations = new HashMap<Integer, String>();

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, getResources().getText(R.string.bluetoothIsNotAvailable),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        // If Bluetooth is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the session
        } else {
            if (mService == null) {
                mService = new BluetoothService(this, mHandler);
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        IntentFilter filter = new IntentFilter();
        // Broadcast sent by Lecom scanners
        filter.addAction("scan.rcv.message");
        registerReceiver(scanReceiver, filter);

        if (mService != null) {
            if (mService.getState() == BluetoothService.STATE_NONE) {
                // Start the Bluetooth services
                mService.start();
            }
        }

        if (BluetoothAdapter.checkBluetoothAddress(prefs.getString("pref_printerAddr", null))) {
            BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(prefs.getString("pref_printerAddr", null));
            mService.connect(device);
        }

        if (getScanBackground() == R.layout.scannow) {
            new GetItemNamesTask(CashpointActivity.this).execute();

        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(scanReceiver);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mService != null) {
            mService.stop();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String orderId) {
                new SearchTask(CashpointActivity.this, contentView, prepOrderId(orderId)).execute();
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                setContentView(getScanBackground());
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_preferences:
                Intent intent_settings = new Intent(this, SettingsActivity.class);
                startActivity(intent_settings);
                return true;

            case R.id.action_printerPicker:
                deviceManager.pickDevice(new BluetoothDeviceManager.BluetoothDevicePickResultHandler() {
                    @Override
                    public void onDevicePicked(BluetoothDevice device) {
                        mService.connect(device);
                        prefs.edit().putString("pref_printerAddr", device.getAddress()).commit();
                    }
                });
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private class SearchTask extends AsyncTask<String, Void, JSONObject> {
        private Exception exception;
        private Context context;
        private View contentView;
        private String orderId;
        private ProgressDialog progressDialog;
        private AlertDialog.Builder alertDialog;

        public SearchTask(Context context, View contentView, String orderId) {
            this.context = context;
            this.contentView = contentView;
            this.orderId = orderId;
        }

        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(context);
            progressDialog.setCancelable(true);
            progressDialog.setMessage(getResources().getString(R.string.loading));
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setProgress(0);
            progressDialog.show();

        }
        protected JSONObject doInBackground(String... params) {
            try {
                PretixApi api = new PretixApi(
                        prefs.getString("pref_URL", ""),
                        prefs.getString("pref_APIkey", ""),
                        prefs.getString("pref_organizer", ""),
                        prefs.getString("pref_event", "")
                );

                return api.getOrder(orderId);
            } catch (Exception e) {
                this.exception = e;

                return null;
            }
        }

        protected void onPostExecute(JSONObject response) {
            progressDialog.hide();

            try {
                if (response.has("code")) {
                    setContentView(R.layout.ticket);
                    final TextView orderStatus = (TextView) findViewById(R.id.orderStatus);
                    final TextView orderTotal = contentView.findViewById(R.id.orderTotal);
                    final TextView orderCode = contentView.findViewById(R.id.orderCode);
                    final TextView paymentProvider = contentView.findViewById(R.id.paymentProvider);
                    final TextView email = contentView.findViewById(R.id.email);
                    final TextView orderDate = contentView.findViewById(R.id.orderDate);
                    final TextView expiryDate = contentView.findViewById(R.id.expiryDate);
                    final TextView internalComment = contentView.findViewById(R.id.internalComment);
                    final ExpandableHeightListView orderItems = contentView.findViewById(R.id.orderItems);
                    final SwipeButton markAsPaid = (SwipeButton) findViewById(R.id.markAsPaid);
                    final SwipeButton printTickets = (SwipeButton) findViewById(R.id.printButton);

                    markAsPaid.setVisibility(View.GONE);
                    printTickets.setVisibility(View.GONE);

                    switch (response.getString("status")) {
                        case "n":
                            orderStatus.setText(getResources().getText(R.string.status_pending));
                            orderStatus.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.pretix_yellow), PorterDuff.Mode.MULTIPLY);
                            markAsPaid.setVisibility(View.VISIBLE);

                            markAsPaid.setOnActiveListener(new OnActiveListener() {
                                @Override
                                public void onActive() {
                                    markAsPaid.setEnabled(false);
                                    new MarkAsPaidTask(CashpointActivity.this, orderId).execute();
                                }
                            });
                            break;
                        case "p":
                            orderStatus.setText(getResources().getText(R.string.status_paid));
                            orderStatus.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.pretix_green), PorterDuff.Mode.MULTIPLY);
                            printTickets.setVisibility(View.VISIBLE);

                            printTickets.setOnActiveListener(new OnActiveListener() {
                                @Override
                                public void onActive() {
                                    printOrderTicket(orderId);
                                }
                            });
                            break;
                        case "e":
                            orderStatus.setText(getResources().getText(R.string.status_expired));
                            orderStatus.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.pretix_red), PorterDuff.Mode.MULTIPLY);
                            break;
                        case "c":
                            orderStatus.setText(getResources().getText(R.string.status_canceled));
                            orderStatus.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.pretix_red), PorterDuff.Mode.MULTIPLY);
                            break;
                        case "r":
                            orderStatus.setText(getResources().getText(R.string.status_refunded));
                            orderStatus.getBackground().setColorFilter(ContextCompat.getColor(context, R.color.pretix_red), PorterDuff.Mode.MULTIPLY);
                            break;
                    }

                    orderTotal.setText(response.getString("total"));
                    orderCode.setText(response.getString("code"));
                    paymentProvider.setText(response.getString("payment_provider"));
                    email.setText(response.getString("email"));
                    orderDate.setText(response.getString("datetime").substring(0, 19));
                    expiryDate.setText(response.getString("expires").substring(0, 19));
                    internalComment.setText(response.getString("comment"));

                    JSONArray jArray = response.getJSONArray("positions");

                    JSONAdapter jSONAdapter = new JSONAdapter(CashpointActivity.this, jArray);
                    orderItems.setAdapter(jSONAdapter);
                    orderItems.setExpanded(true);
                } else {
                    throw new Exception(response.toString());
                }
            } catch (Exception e) {
                alertDialog = new AlertDialog.Builder(context);
                alertDialog.setTitle("Error");
                alertDialog.setMessage(this.exception.getMessage().toString());
                alertDialog.setNeutralButton(getResources().getText(R.string.dismiss), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                alertDialog.show();
            }
        }
    }

    private class GetItemNamesTask extends AsyncTask<String, Void, JSONObject> {
        private Exception exception;
        private Context context;
        private ProgressDialog progressDialog;
        private AlertDialog.Builder alertDialog;

        public GetItemNamesTask(Context context) {
            this.context = context;
        }

        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(context);
            progressDialog.setCancelable(true);
            progressDialog.setMessage(getResources().getText(R.string.loadingItems));
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setProgress(0);
            progressDialog.show();

        }
        protected JSONObject doInBackground(String... params) {
            try {
                PretixApi api = new PretixApi(
                        prefs.getString("pref_URL", ""),
                        prefs.getString("pref_APIkey", ""),
                        prefs.getString("pref_organizer", ""),
                        prefs.getString("pref_event", "")
                );

                return api.getItemNames();
            } catch (Exception e) {
                this.exception = e;

                return null;
            }
        }

        protected void onPostExecute(JSONObject response) {
            progressDialog.hide();

            try {
                if (response.has("results")) {
                    itemNames.clear();
                    itemVariations.clear();

                    JSONArray items = response.getJSONArray("results");

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = (JSONObject) items.get(i);
                        itemNames.put(item.getInt("id"), item.getJSONObject("name").getString(prefs.getString("pref_language", "en")));

                        JSONArray variations = item.getJSONArray("variations");
                        if (variations.length() > 0) {
                            for (int j = 0; j < variations.length(); j++) {
                                JSONObject variation = (JSONObject) variations.get(j);
                                itemVariations.put(variation.getInt("id"), variation.getJSONObject("value").getString(prefs.getString("pref_language", "en")));
                            }
                        }
                    }
                } else {
                    throw new Exception(response.toString());
                }

            } catch (Exception e) {
                alertDialog = new AlertDialog.Builder(context);
                alertDialog.setTitle(getResources().getString(R.string.errorGettingItems));
                alertDialog.setMessage(getResources().getString(R.string.errorGettingItemsMsg));
                alertDialog.setNeutralButton(getResources().getString(R.string.dismiss), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                alertDialog.show();
            }
        }
    }


    private class MarkAsPaidTask extends AsyncTask<String, Void, JSONObject> {
        private Exception exception;
        private Context context;
        private String orderId;
        private SharedPreferences prefs;
        private ProgressDialog progressDialog;
        private AlertDialog.Builder alertDialog;

        public MarkAsPaidTask(Context context, String orderId) {
            this.context = context;
            this.orderId = orderId;
            this.prefs =  PreferenceManager.getDefaultSharedPreferences(context);
        }

        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(context);
            progressDialog.setCancelable(true);
            progressDialog.setMessage(getResources().getString(R.string.loading));
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialog.setProgress(0);
            progressDialog.show();

        }
        protected JSONObject doInBackground(String... params) {
            try {
                PretixApi api = new PretixApi(
                        prefs.getString("pref_URL", ""),
                        prefs.getString("pref_APIkey", ""),
                        prefs.getString("pref_organizer", ""),
                        prefs.getString("pref_event", "")
                );

                return api.markAsPaid(orderId);
            } catch (Exception e) {
                this.exception = e;

                return null;
            }
        }

        protected void onPostExecute(JSONObject response) {
            progressDialog.hide();

            try {
                if (response.has("status")) {
                    switch(response.getString("status")) {
                        case "ok":
                            new SearchTask(CashpointActivity.this, contentView, orderId).execute();
                            break;

                        case "error":
                            switch (response.getString("reason")) {
                                case "n":
                                    throw new Exception(getResources().getString(R.string.errorOrderIsPending));
                                case "p":
                                    throw new Exception(getResources().getString(R.string.errorOrderIsPaid));
                                case "e":
                                    throw new Exception(getResources().getString(R.string.errorOrderIsExpired));
                                case "c":
                                    throw new Exception(getResources().getString(R.string.errorOrderIsCanceled));
                                case "r":
                                    throw new Exception(getResources().getString(R.string.errorOrderIsRefunded));
                            }
                        default:
                            throw new Exception(response.toString());
                    }
                } else {
                    throw new Exception(response.toString());
                }
            } catch (Exception e) {
                alertDialog = new AlertDialog.Builder(context);
                alertDialog.setTitle(getResources().getString(R.string.error));
                alertDialog.setMessage(this.exception.getMessage().toString());
                alertDialog.setNeutralButton(getResources().getString(R.string.dismiss), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                alertDialog.show();
            }
        }
    }

    private BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setContentView(getScanBackground());

            // Intent receiver for LECOM-manufactured hardware scanners
            byte[] barcode = intent.getByteArrayExtra("barocode"); // sic!
            int barocodelen = intent.getIntExtra("length", 0);
            String barcodeStr = new String(barcode, 0, barocodelen);
            new SearchTask(CashpointActivity.this, contentView, prepOrderId(barcodeStr)).execute();
        }

    };

    private String prepOrderId(String orderId) {
        if (orderId.startsWith(prefs.getString("pref_event", ""))) {
            return orderId.replace(prefs.getString("pref_event", "") + "-", "");
        } else {
            return orderId;
        }
    }

    private void printOrderTicket(String orderCode) {

        SendDataByte(PrinterCommand.POS_Set_PrtInit());
        SendDataByte(PrinterCommand.POS_S_Align(1));
        SendDataByte(PrinterCommand.POS_Set_Bold(1));
        SendDataString(prefs.getString("pref_event", "pretix Cashpoint"));
        SendDataByte(PrinterCommand.POS_Set_LF());
        SendDataByte(PrinterCommand.POS_Set_LF());
        SendDataByte(PrinterCommand.POS_S_Align(0));
        SendDataByte(PrinterCommand.POS_Set_Bold(0));
        SendDataString(getResources().getString(R.string.order) + orderCode);
        SendDataByte(PrinterCommand.POS_Set_LF());
        SendDataByte(PrinterCommand.POS_Set_LF());

        ListView orderItems = (ListView) findViewById(R.id.orderItems);
        for (int i = 0; i < orderItems.getAdapter().getCount(); i++) {
            JSONObject item = (JSONObject) orderItems.getAdapter().getItem(i);

            String secret = null;
            String price = null;
            Integer ticketType = null;
            Integer ticketVariation = null;
            String attendeeName = "";
            String attendeeEmail = "";

            try {
                secret = item.getString("secret");
                price = item.getString("price");
                ticketType = item.getInt("item");
                attendeeName = item.getString("attendee_name");
                attendeeEmail = item.getString("attendee_email");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            SendDataString(itemNames.get(ticketType) + "\n");
            SendDataString(secret + "\n");
            SendDataByte(PrinterCommand.POS_S_Align(1));
            SendDataByte(PrinterCommand.POS_Set_Bold(1));
            SendDataByte(new byte[]{0x1b, 0x61, 0x00 });
            SendDataByte(PrinterCommand.getBarCommand((String) secret, 1, 3, 8));
            SendDataByte(PrinterCommand.POS_S_Align(1));
            SendDataByte(PrinterCommand.POS_Set_Bold(1));
            SendDataString("* * * * * * *");
            SendDataByte(PrinterCommand.POS_Set_LF());
            SendDataByte(PrinterCommand.POS_Set_LF());
            SendDataByte(PrinterCommand.POS_S_Align(0));
            SendDataByte(PrinterCommand.POS_Set_Bold(0));

        }

        SendDataByte(PrinterCommand.POS_Set_LF());
        SendDataByte(PrinterCommand.POS_Set_LF());
        SendDataByte(PrinterCommand.POS_Set_LF());
    }

    private void SendDataString(String data) {

        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, getResources().getString(R.string.printerNotConnected), Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        if (data.length() > 0) {
            try {
                mService.write(data.getBytes("GBK"));
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void SendDataByte(byte[] data) {

        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, getResources().getString(R.string.printerNotConnected), Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        mService.write(data);
    }

    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            break;
                    }
                    break;
                case MESSAGE_WRITE:

                    break;
                case MESSAGE_READ:

                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(),
                            getResources().getString(R.string.connectedTo) + mConnectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
                            .show();
                    break;
                case MESSAGE_CONNECTION_LOST:
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.deviceConnectionLost),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_UNABLE_CONNECT:
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.unableToConnectDevice),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private int getScanBackground() {
        if (prefs.getString("pref_URL", "").isEmpty()
                || prefs.getString("pref_URL", "").isEmpty()
                || prefs.getString("pref_APIkey", "").isEmpty()
                || prefs.getString("pref_organizer", "").isEmpty()
                || prefs.getString("pref_event", "").isEmpty()) {
            return R.layout.mustconfigure;
        } else {
            return R.layout.scannow;
        }
    }
}
