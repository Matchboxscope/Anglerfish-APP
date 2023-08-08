package com.example.espressoscope;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.github.chrisbanes.photoview.PhotoView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends Activity implements View.OnClickListener
{

    private static final String TAG = "MainActivity::";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private HandlerThread stream_thread;
    private Handler stream_handler;
    private PhotoView photoView;
    private EditText ip_text;

    private boolean isRecording = false;
    private final int ID_CONNECT = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.connect).setOnClickListener(this);
        ip_text = findViewById(R.id.ip);

        // Find the PhotoView by its ID
        photoView = findViewById(R.id.monitor);

        // Optionally, set some configuration options for PhotoView
        photoView.setMaximumScale(5); // Set the maximum zoom scale
        photoView.setMediumScale(3);  // Set the medium zoom scale

        // Set the double tap listener to reset zoom and pan to default
        photoView.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Handle single tap (if needed)
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Reset zoom and pan to default
                photoView.setScale(1f, true);
                photoView.setTranslationX(0f);
                photoView.setTranslationY(0f);
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                // Handle double tap event (if needed)
                return false;
            }
        });


        SeekBar focusSlider = findViewById(R.id.focusSlider);
        SeekBar resolutionSlider = findViewById(R.id.resolutionSlider);
        TextView resolutionValue = findViewById(R.id.resolutionValue);
        TextView focusValue = findViewById(R.id.focusValue);
        SeekBar lampSlider = findViewById(R.id.lampSlider);
        TextView lampValue = findViewById(R.id.lampValue);

        Button recordButton = findViewById(R.id.recordButton);
        recordButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
                recordButton.setText("Start Recording");
            } else {
                startRecording();
                recordButton.setText("Stop Recording");
            }
        });

        Button snapButton = findViewById(R.id.snapButton);
        snapButton.setOnClickListener(v -> {
            snapImage();
        });

        Button btnOpenGallery = findViewById(R.id.btnOpenGallery);
        btnOpenGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
                startActivity(intent);
            }
        });

        focusSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setFocus(progress - 100); // Assuming the SeekBar's progress is from 0 to 2000.
                focusValue.setText("Focus Value "+String.valueOf(progress - 100));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(100);
                focusValue.setText("Focus Value "+"0");
            }
        });

        resolutionSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                resolutionValue.setText("Resolution:  "+String.valueOf(progress));
                setResolution(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        lampSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lampValue.setText("Lamp Value "+String.valueOf(progress));
                if (fromUser) {
                    setLamp(progress); // Adjust this line to handle changes in the lamp slider
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Called when the user starts changing the slider position.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Called when the user stops changing the slider position.
            }
        });

        // get old IP address
        ip_text = findViewById(R.id.ip);
        String ip = loadIpAddress();
        ip_text.setText(ip);

        stream_thread = new HandlerThread("http");
        stream_thread.start();
        stream_handler = new HttpHandler(stream_thread.getLooper());

    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId())
        {
            case R.id.connect:
                String ip = ip_text.getText().toString();
                saveIpAddress(ip);

                stream_handler.sendEmptyMessage(ID_CONNECT);
                break;
            default:
                break;
        }
    }



    private void setFocus(int value) {
        String focus_url = "http://" + ip_text.getText() + ":80/control?var=focusSlider&val=" + value;
        sendMessage(focus_url);
    }

    private void setResolution(int value) {
        String focus_url = "http://" + ip_text.getText() + ":80/control?var=framesize&val=" + value;
        sendMessage(focus_url);
    }

    private void setLamp(int value){
        String lamp_url = "http://" + ip_text.getText() + ":80/control?var=lamp&val=" + value;
        sendMessage(lamp_url);
    }



    private class HttpHandler extends Handler
    {
        public HttpHandler(Looper looper)
        {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case ID_CONNECT:
                    VideoStream();
                    break;
                default:
                    break;
            }
        }
    }


    private void sendMessage(String message){
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try
                {
                    URL url = new URL(message);

                    HttpURLConnection huc = (HttpURLConnection) url.openConnection();
                    huc.setRequestMethod("GET");
                    huc.setConnectTimeout(500); // timeout in .5 seconds
                    huc.connect();
                    if (huc.getResponseCode() == 200)
                    {
                        huc.getInputStream().close();
                    }
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }


    private void VideoStream()
    {
        String stream_url = "http://" + ip_text.getText() + ":81";
        try
        {
            URL url = new URL(stream_url);
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
                    String data;

                    while ((data = br.readLine()) != null)
                    {
                        //look up for the content-type
                        if (data.contains("Content-Type:"))
                        {
                            //after that read length line, we dont need the length but it increase the buffer position about 1 line
                            data = br.readLine();
                            //after that the binary data starts and we can pass directly the inputstream because its at same position as the bufferedReader
                            final Bitmap bitmap = BitmapFactory.decodeStream(in);

                            photoView.post(() -> photoView.setImageBitmap(bitmap));
                        }
                    }
                    try
                    {
                        if (br != null)
                        {
                            br.close();
                        }
                        if(in != null)
                        {
                            in.close();
                        }
                        stream_handler.sendEmptyMessageDelayed(ID_CONNECT,3000);
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
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

        }

    }

    private void snapImage(){
        new Thread(() -> {
            // TODO: Need a callback on frames from the MJPEG stream here
                byte[] frame = getLatestFrameFromStream();
                if (frame != null) {
                    saveFrameToFile(frame);
            }
        }).start();
    }

    private void startRecording() {
        // This assumes you have a method to get the latest frame from the stream
        isRecording = true;
        new Thread(() -> {
            while (isRecording) {
                // TODO: Need a callback on frames from the MJPEG stream here
                byte[] frame = getLatestFrameFromStream();
                if (frame != null) {
                    saveFrameToFile(frame);
                }
            }
        }).start();
    }

    private void stopRecording() {
        // We're relying on the thread in startRecording() to check this variable regularly
        isRecording = false;

    }

    private void saveFrameToFile(byte[] frame) {
        // This assumes you have a folder to save the images in
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // If permission is not granted, request it
            requestStoragePermission();
            return;
        }

        // FIXME: Need to change this to DCIM, but doesnt work
        String imageFolder = getFilesDir().getAbsolutePath();

        /*
        //File imageFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "matchboxscope");
        File imageFolder Environment.getExternalStoragePublicDirectory("ESPressoscope");
        File imageFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "matchboxscope");

        if (!imageFolder.exists() && !imageFolder.mkdirs()) {

        }
        */
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File imageFile = new File(imageFolder, "IMG_" + timestamp + ".jpg");

        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(frame);
            Toast.makeText(this, "File stored: "+ "IMG_" + timestamp + ".jpg", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] getLatestFrameFromStream() {
        ImageView imageView = findViewById(R.id.monitor);
        Drawable drawable = imageView.getDrawable();

        // If the drawable is a bitmap, get the bitmap
        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            Bitmap bitmap = bitmapDrawable.getBitmap();

            // Convert the Bitmap into a byte array
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            return stream.toByteArray();
        }

        return null;
    }

    private static final int REQUEST_WRITE_STORAGE_PERMISSION = 1;

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_STORAGE_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted, proceed with saving the file
                } else {
                    // Permission was denied, inform the user or disable the feature
                    Toast.makeText(this, "Permission to write to storage was denied", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private void saveIpAddress(String ip) {
        SharedPreferences sharedPreferences = getSharedPreferences("myPreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("ipAddress", ip);
        editor.apply();
    }

    private String loadIpAddress() {
        SharedPreferences sharedPreferences = getSharedPreferences("myPreferences", MODE_PRIVATE);
        return sharedPreferences.getString("ipAddress", "192.168.4.1"); // Returning an empty string if no value found
    }







}