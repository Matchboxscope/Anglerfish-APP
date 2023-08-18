package io.github.espressoscope;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
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
import com.google.android.material.chip.Chip;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;


public class MainActivity extends Activity implements View.OnClickListener
{

    private static final String TAG = "MainActivity::";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private HandlerThread stream_thread;
    private Handler stream_handler;
    private PhotoView photoView;
    private EditText ip_text;

    private boolean isRecording = false;

    private boolean isTimelapse = false;
    private final int ID_CONNECT = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Setting an onClickListener to a button (assuming you have one) to launch the new Activity
        Button button = findViewById(R.id.statusButton);
        button.setOnClickListener(view -> {
            Intent intent = new Intent(this, StatusActivity.class);
            intent.putExtra("IP", ip_text.getText());
            startActivity(intent);
        });

        Button btnOpenLocation = findViewById(R.id.btnOpenFilelocation);
        btnOpenLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFolder();
            }
        });



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
        SeekBar timelapseSlider = findViewById(R.id.timelapseSlider);
        TextView timelapseValue = findViewById(R.id.timelapseValue);
        Button timelapseSwitch = findViewById(R.id.timelapseSwitch);
        TextView aecValue = findViewById(R.id.aecValue);
        SeekBar aecSlider = findViewById(R.id.aecSlider);

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

        aecSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                setAEC(progress); // Assuming the SeekBar's progress is from 0 to 2000.
                aecValue.setText("AEC: "+String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
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



        timelapseSwitch.setOnClickListener(v -> {
            if (isTimelapse) {
                timelapseSwitch.setBackgroundColor(Color.GREEN);
                setTimelapseActive(1);
                timelapseSwitch.setText("Start");
                isTimelapse = false;
            } else {
                isTimelapse = true;
                timelapseSwitch.setBackgroundColor(Color.RED);
                setTimelapseActive(1);
                timelapseSwitch.setText("Stop");
            }
        });

        timelapseSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                timelapseValue.setText("Timelpase:  "+String.valueOf(progress));
                setTimelapse(progress);
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

    private void setAEC(int value) {
        String focus_url = "http://" + ip_text.getText() + ":80/control?var=ae_level&val=" + value;
        sendMessage(focus_url);
    }

    private void setResolution(int value) {
        String focus_url = "http://" + ip_text.getText() + ":80/control?var=framesize&val=" + value;
        sendMessage(focus_url);
    }

    private void setTimelapse(int value) {
        String focus_url = "http://" + ip_text.getText() + ":80/control?var=timelapseInterval&val=" + value;
        sendMessage(focus_url);
    }

    private void setTimelapseActive(int value) {
        String focus_url = "http://" + ip_text.getText() + ":80/control?var=isTimelapse&val=" + value;
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
                        InputStream in = huc.getInputStream();
                        BufferedReader br = new BufferedReader(new InputStreamReader(in));
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

        BufferedInputStream bis = null;
        FileOutputStream fos = null;
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

                    int len;
                    byte[] buffer;

                    // IDentify that we are connected
                    Toast.makeText(MainActivity.this, "We are connected!", Toast.LENGTH_SHORT).show();

                    while ((data = br.readLine()) != null)
                    {
                        if (data.contains("Content-Type:"))
                        {
                            data = br.readLine();

                            len = Integer.parseInt(data.split(":")[1].trim());

                            bis = new BufferedInputStream(in);
                            buffer = new byte[len];

                            int t = 0;
                            while (t < len)
                            {
                                t += bis.read(buffer, t, len - t);
                            }

                            Bytes2ImageFile(buffer, getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/0A.jpg");

                            final Bitmap bitmap = BitmapFactory.decodeFile(getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/0A.jpg");

                            runOnUiThread(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    photoView.setImageBitmap(bitmap);
                                }
                            });

                        }


                    }
                }

            } catch (IOException e)
            {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "We are not connected. Wrong URL?", Toast.LENGTH_SHORT).show();
            }
        } catch (MalformedURLException e)
        {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "We are not connected. Wrong URL?", Toast.LENGTH_SHORT).show();
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

                stream_handler.sendEmptyMessageDelayed(ID_CONNECT,3000);
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

    }

    private void Bytes2ImageFile(byte[] bytes, String fileName)
    {
        try
        {
            File file = new File(fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes, 0, bytes.length);
            fos.flush();
            fos.close();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void snapImage(){
        new Thread(() -> {
            // TODO: Need a callback on frames from the MJPEG stream here
                byte[] frame = getLatestFrameFromStream();
                if (frame != null) {
                    saveFrameToFile(frame);
                }
                else{
                    Toast("Please first start the stream!");
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
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        long timeMillis = System.currentTimeMillis();
        File imageFile = new File(imageFolder, "IMG_" + timestamp  +"_"+timeMillis + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(frame);
            Log.i(TAG, "File stored: "+ "IMG_" + timestamp +"_"+timeMillis+ ".jpg");
            //Toast.makeText(this, "File stored: "+ "IMG_" + timestamp + ".jpg", Toast.LENGTH_SHORT).show();
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

    private void Toast(String mMessage){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, mMessage, Toast.LENGTH_LONG).show();
            }
        });
    }


    private void openFolder() {

        File folderLocation = new File(getFilesDir().getAbsolutePath(), "");  // Adjust the path to your specific folder if it's in a different location
        Uri folderUri = FileProvider.getUriForFile(this, "io.github.espressoscope.fileprovider", folderLocation);

        try{
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(folderUri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No suitable app found to open the folder.", Toast.LENGTH_LONG).show();
            }
        }
        catch (Exception e){
            Toast("File location is: "+String.valueOf(folderLocation));
        }
    }
}