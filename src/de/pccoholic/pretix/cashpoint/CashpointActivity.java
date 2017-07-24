package de.pccoholic.pretix.cashpoint;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ebanx.swipebtn.OnActiveListener;
import com.ebanx.swipebtn.SwipeButton;

import org.json.JSONObject;

import eu.pretix.pretixdroid.net.api.PretixApi;

public class CashpointActivity extends AppCompatActivity {
    private View contentView;
    private SharedPreferences prefs;
    private BluetoothDeviceManager deviceManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scannow);
        contentView = this.findViewById(android.R.id.content);
        deviceManager = new BluetoothDeviceManager(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        IntentFilter filter = new IntentFilter();
        // Broadcast sent by Lecom scanners
        filter.addAction("scan.rcv.message");
        registerReceiver(scanReceiver, filter);

        // ToDo: check if preferences are set and launch SettingsActivity if not and show information-Toast
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(scanReceiver);
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
                setContentView(R.layout.scannow);
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
                        Log.d("CashpointBluetooth", device.getName());
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
        //private SharedPreferences prefs;
        private ProgressDialog progressDialog;
        private AlertDialog.Builder alertDialog;

        public SearchTask(Context context, View contentView, String orderId) {
            this.context = context;
            this.contentView = contentView;
            this.orderId = orderId;
            //this.prefs =  PreferenceManager.getDefaultSharedPreferences(context);
        }

        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(context);
            progressDialog.setCancelable(true);
            progressDialog.setMessage("Loading...");
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
                    final ListView orderItems = contentView.findViewById(R.id.orderItems);
                    final SwipeButton markAsPaid = (SwipeButton) findViewById(R.id.markAsPaid);
                    final SwipeButton printTickets = (SwipeButton) findViewById(R.id.printButton);

                    markAsPaid.setVisibility(View.GONE);
                    printTickets.setVisibility(View.GONE);

                    switch (response.getString("status")) {
                        case "n":
                            orderStatus.setText("Pending");
                            orderStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.pretix_yellow));
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
                            orderStatus.setText("Paid");
                            orderStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.pretix_green));
                            printTickets.setVisibility(View.VISIBLE);

                            printTickets.setOnActiveListener(new OnActiveListener() {
                                @Override
                                public void onActive() {
                                    Toast.makeText(CashpointActivity.this, "ToDo: Print Tickets", Toast.LENGTH_SHORT).show();
                                }
                            });
                            break;
                        case "e":
                            orderStatus.setText("Expired");
                            orderStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.pretix_red));
                            break;
                        case "c":
                            orderStatus.setText("Canceled");
                            orderStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.pretix_red));
                            break;
                        case "r":
                            orderStatus.setText("Refunded");
                            orderStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.pretix_red));
                            break;
                    }

                    orderTotal.setText(response.getString("total"));
                    orderCode.setText(response.getString("code"));
                    paymentProvider.setText(response.getString("payment_provider"));
                    email.setText(response.getString("email"));
                    orderDate.setText(response.getString("datetime"));
                    expiryDate.setText(response.getString("expires"));
                    internalComment.setText(response.getString("comment"));

                    Toast.makeText(CashpointActivity.this, "ToDo: Populate ListView with OrderItems", Toast.LENGTH_SHORT).show();

                } else {
                    throw new Exception(response.toString());
                }
            } catch (Exception e) {
                alertDialog = new AlertDialog.Builder(context);
                alertDialog.setTitle("Error");
                alertDialog.setMessage(this.exception.getMessage().toString());
                alertDialog.setNeutralButton("Dismiss", new DialogInterface.OnClickListener() {
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
            progressDialog.setMessage("Loading...");
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
                                    throw new Exception("Order is Pending - this error should not have occured.");
                                case "p":
                                    throw new Exception("Order is already Paid!");
                                case "e":
                                    throw new Exception("Order is already Expired!");
                                case "c":
                                    throw new Exception("Order is already Canceled!");
                                case "r":
                                    throw new Exception("Order is already Refunded!");
                            }
                        default:
                            throw new Exception(response.toString());
                    }
                } else {
                    throw new Exception(response.toString());
                }
            } catch (Exception e) {
                alertDialog = new AlertDialog.Builder(context);
                alertDialog.setTitle("Error");
                alertDialog.setMessage(this.exception.getMessage().toString());
                alertDialog.setNeutralButton("Dismiss", new DialogInterface.OnClickListener() {
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
            setContentView(R.layout.scannow);

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

}
