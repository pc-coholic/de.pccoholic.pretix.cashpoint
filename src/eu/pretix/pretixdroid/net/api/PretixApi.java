package eu.pretix.pretixdroid.net.api;

import com.joshdholtz.sentry.Sentry;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import javax.net.ssl.SSLException;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PretixApi {

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    private String url;
    private String key;
    private String organizer;
    private String event;
    private OkHttpClient client;

    public PretixApi(String url, String key, String organizer, String event) {
        this.url = url;
        this.key = key;
        this.organizer = organizer;
        this.event = event;
        client = new OkHttpClient.Builder()
                    .build();
    }

    public JSONObject getOrder(String orderId) throws ApiException {
        Request request = null;

        try {
            request = new Request.Builder()
                    .url(url + "api/v1/organizers/" + organizer + "/events/" + event + "/orders/"
                            + URLEncoder.encode(orderId, "UTF-8"))
                    .addHeader("Authorization", "Token " + key)
                    .get()
                    .build();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return apiCall(request);
    }

    public JSONObject markAsPaid(String orderId) throws ApiException {
        Request request = null;

        try {
            request = new Request.Builder()
                    .url(url + "api/v1/organizers/" + organizer + "/events/" + event + "/orders/"
                            + URLEncoder.encode(orderId, "UTF-8") + "/cashpoint/")
                    .addHeader("Authorization", "Token " + key)
                    .post(new FormBody.Builder().build())
                    .build();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return apiCall(request);

    }

    private JSONObject apiCall(Request request) throws ApiException {
        Response response;
        try {
            response = client.newCall(request).execute();
        } catch (SSLException e) {
            e.printStackTrace();
            throw new ApiException("Error while creating a secure connection.", e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ApiException("Connection error.", e);
        }

        String safe_url = request.url().toString().replaceAll("^(.*)key=([0-9A-Za-z]+)([^0-9A-Za-z]*)", "$1key=redacted$3");
        Sentry.addHttpBreadcrumb(safe_url, request.method(), response.code());

        if (response.code() >= 500) {
            throw new ApiException("Server error.");
        } else if (response.code() == 404) {
            throw new ApiException("Not found.");
        } else if (response.code() == 403) {
            throw new ApiException("Permission error, please try again or reset and reconfigure.");
        }
        try {
            return new JSONObject(response.body().string());
        } catch (JSONException e) {
            e.printStackTrace();
            Sentry.captureException(e);
            throw new ApiException("Invalid JSON received.", e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ApiException("Connection error.", e);
        }
    }

}