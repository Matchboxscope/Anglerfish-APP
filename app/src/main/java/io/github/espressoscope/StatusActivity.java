package io.github.espressoscope;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import io.github.espressoscope.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import androidx.appcompat.app.AppCompatActivity;

public class StatusActivity extends AppCompatActivity {
    String TAG = "StatusActvity";
    RequestQueue requestQueue;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        Intent intent = this.getIntent();
        String ipAddress = intent.getExtras().get("IP").toString();
        Log.d(TAG, "onCreate: "+ipAddress);
        intent.getExtras();

        requestQueue = Volley.newRequestQueue(this);
        String mUrl = "http://"+ipAddress+"/status";
        fetchData(mUrl);
    }

    private void fetchData(String url) {
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                    (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                displayData(response);
                                Toast.makeText(StatusActivity.this, "Data fetched successfully!", Toast.LENGTH_SHORT).show();

                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(StatusActivity.this, "Data fetched not successful!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // Handle the error or show an error message to the user
                            error.printStackTrace();
                        }
                    });

            // Adding the request to the RequestQueue
            requestQueue.add(jsonObjectRequest);
        }

    private void displayData(JSONObject data) throws JSONException {
        TableLayout tableLayout = findViewById(R.id.tableLayout);

        Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = data.get(key).toString();

            TableRow tableRow = new TableRow(this);

            TextView keyTextView = new TextView(this);
            keyTextView.setText(key);
            keyTextView.setPadding(8, 8, 8, 8);

            TextView valueTextView = new TextView(this);
            valueTextView.setText(value);
            valueTextView.setPadding(8, 8, 8, 8);

            tableRow.addView(keyTextView);
            tableRow.addView(valueTextView);

            tableLayout.addView(tableRow);
        }
    }




    private String GetJson(String jsonURL)
    {
        BufferedInputStream bis = null;
        FileOutputStream fos = null;
        String OutputJson = "";
        try
        {

            URL url = new URL(jsonURL);

            try
            {
                HttpURLConnection huc = (HttpURLConnection) url.openConnection();
                huc.setRequestMethod("GET");
                huc.setConnectTimeout(1000 * 5);
                huc.setReadTimeout(1000 * 5);
                huc.setDoInput(true);
                huc.connect();

                if (huc.getResponseCode() == 200)
                {
                    InputStream in = huc.getInputStream();

                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);

                    String data = br.readLine();
                    if (data != null)
                        OutputJson = data;
                }
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        } catch (MalformedURLException e)
        {
            e.printStackTrace();
        } finally
        {
            try
            {
                if (bis != null)
                {
                    bis.close();
                }
                if (fos != null)
                {
                    fos.close();
                }


            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return OutputJson;
    }

}
