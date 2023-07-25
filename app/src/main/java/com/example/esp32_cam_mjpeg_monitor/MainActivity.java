package com.example.esp32_cam_mjpeg_monitor;

import android.app.Activity;
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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

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

public class MainActivity extends Activity implements View.OnClickListener
{

    private static final String TAG = "MainActivity::";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private HandlerThread stream_thread;
    private Handler stream_handler;
    private ImageView monitor;
    private EditText ip_text;

    private boolean isRecording = false;
    private final int ID_CONNECT = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.connect).setOnClickListener(this);
        monitor = findViewById(R.id.monitor);
        ip_text = findViewById(R.id.ip);


        SeekBar focusSlider = findViewById(R.id.focusSlider); // Replace with your SeekBar id
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
            isRecording = !isRecording;
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

        ip_text.setText("192.168.4.1");

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
                    huc.setConnectTimeout(5000); // timeout in 5 seconds
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
                                    monitor.setImageBitmap(bitmap);
                                }
                            });

                        }


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


    private void startRecording() {
        // This assumes you have a method to get the latest frame from the stream
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
    }

    private void saveFrameToFile(byte[] frame) {
        // This assumes you have a folder to save the images in
        File imageFolder = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MyRecordings");
        if (!imageFolder.exists() && !imageFolder.mkdirs()) {
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File imageFile = new File(imageFolder, "IMG_" + timestamp + ".jpg");

        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            fos.write(frame);
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


}