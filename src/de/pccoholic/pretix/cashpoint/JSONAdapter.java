package de.pccoholic.pretix.cashpoint;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class JSONAdapter extends BaseAdapter implements ListAdapter {
    // From https://stackoverflow.com/questions/10879592/how-to-load-data-to-custom-listview-from-json-array

    private final Activity activity;
    private final JSONArray jsonArray;
    JSONAdapter(Activity activity, JSONArray jsonArray) {
        assert activity != null;
        assert jsonArray != null;

        this.jsonArray = jsonArray;
        this.activity = activity;
    }

    @Override
    public int getCount() {
        if (jsonArray == null) {
            return 0;
        } else {
            return jsonArray.length();
        }
    }

    @Override
    public JSONObject getItem(int position) {
        if (jsonArray == null){
            return null;
        } else {
            return jsonArray.optJSONObject(position);
        }
    }

    @Override
    public long getItemId(int position) {
        JSONObject jsonObject = getItem(position);

        return jsonObject.optLong("id");
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = activity.getLayoutInflater().inflate(R.layout.itemrow, null);

        TextView tvSecret = (TextView) convertView.findViewById(R.id.tvSecret);
        TextView tvPrice = (TextView) convertView.findViewById(R.id.tvPrice);
        TextView tvTicketName = (TextView) convertView.findViewById(R.id.tvTicketName);
        TextView tvAttendeeName = (TextView) convertView.findViewById(R.id.tvAttendeeName);
        TextView tvAttendeeEmail = (TextView) convertView.findViewById(R.id.tvAttendeeEmail);


        JSONObject json_data = getItem(position);
        if(json_data != null) {
            String secret = null;
            String price = null;
            Integer ticketType = null;
            Integer ticketVariation = null;
            String attendeeName = "";
            String attendeeEmail = "";

            try {
                secret = json_data.getString("secret");
                price = json_data.getString("price");
                ticketType = json_data.getInt("item");
                ticketVariation = json_data.getInt("variation");
                attendeeName = json_data.getString("attendee_name");
                attendeeEmail = json_data.getString("attendee_email");
            } catch (JSONException e) {
                e.printStackTrace();
            }


            tvSecret.setText(secret);
            tvPrice.setText(price);
            tvAttendeeName.setText(attendeeName);
            tvAttendeeEmail.setText(attendeeEmail);
            if (ticketVariation != null) {
                tvTicketName.setText(CashpointActivity.itemNames.get(ticketType) + " - " + CashpointActivity.itemVariations.get(ticketVariation));
            } else {
                tvTicketName.setText(CashpointActivity.itemNames.get(ticketType));
            }
        }

        return convertView;
    }
}