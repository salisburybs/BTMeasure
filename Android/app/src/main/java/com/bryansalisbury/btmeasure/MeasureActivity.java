package com.bryansalisbury.btmeasure;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.bryansalisbury.btmeasure.bluno.Bluno;
import com.bryansalisbury.btmeasure.models.Sample;
import com.bryansalisbury.btmeasure.models.TestSequence;

import java.util.ArrayList;

public class MeasureActivity extends AppCompatActivity {
    private Bluno bluno;

    private static final String TAG = "MeasureActivity";

    public static final String ACTION_MESSAGE_AVAILABLE = "com.bryansalisbury.message.AVAILABLE";
    public static final String EXTRA_VALUE = "com.bryansalisbury.message.EXTRA_VALUE";

    private enum RemoteState {NULL, TEST, MAIN, MEASURE, CONTROL, TOGGLE_LED, ECHO};
    private RemoteState mState = RemoteState.NULL;

    private enum ButtonState {START, STOP};
    private ButtonState mStartButton = ButtonState.START;
    private TestSequence mTestSequence;

    private ArrayList<Sample> mSampleBuffer = new ArrayList<>();

    // Arduino Control Variables
    // TODO move to bluno.java as this is platform specific restriction
    private static final int sampleRateMax = 4000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Create bluno now to have bt service ready when needed.
        // TODO use handler to create callback and connect to service on button click
        bluno = new Bluno(this);

        setContentView(R.layout.activity_measure);
        final Button buttonBegin = (Button) findViewById(R.id.buttonBegin);
        final SeekBar seekSampleRate = (SeekBar) findViewById(R.id.seekBar);
        final TextView textSampleRate = (TextView) findViewById(R.id.textSampleRate);

        seekSampleRate.setMax(sampleRateMax - 1);
        textSampleRate.setText("Sample Rate: " + seekSampleRate.getProgress() + 1 + " (hz)");

        seekSampleRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                                                      @Override
                                                      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                                          int sampleRate = progress + 1;
                                                          textSampleRate.setText("Sample Rate: " + sampleRate + " (hz)");
                                                      }

                                                      @Override
                                                      public void onStartTrackingTouch(SeekBar seekBar) {

                                                      }

                                                      @Override
                                                      public void onStopTrackingTouch(SeekBar seekBar) {
                                                          int val = seekBar.getProgress();
                                                          if (val > 1) {
                                                              val = (Math.round((val + 1) / 100) * 100) - 1;
                                                          }
                                                          seekBar.setProgress(val);
                                                      }
                                                  }
        );

        buttonBegin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonBeginClick();
            }
        });


    }

    private int getSampleDelay(){
        final SeekBar seekSampleRate = (SeekBar) findViewById(R.id.seekBar);
        // return delay in microseconds
        return (int) ((1.0 / (float) seekSampleRate.getProgress() + 1) * 1000000);
    }

    private void buttonBeginStart(){
        // UI Elements
        Switch switchA0 = (Switch) findViewById(R.id.switchA0);
        Switch switchA1 = (Switch) findViewById(R.id.switchA1);
        Switch switchA2 = (Switch) findViewById(R.id.switchA2);
        Switch switchA3 = (Switch) findViewById(R.id.switchA3);
        Switch switchA4 = (Switch) findViewById(R.id.switchA4);
        Switch switchA5 = (Switch) findViewById(R.id.switchA5);
        Button buttonBegin = (Button) findViewById(R.id.buttonBegin);
        CheckBox checkCompress = (CheckBox) findViewById(R.id.checkCompression);

        // Create database test sequence
        mTestSequence = new TestSequence("New Collection");
        mTestSequence.compressed = checkCompress.isChecked();

        // Sample delay calculated from sampleRate(hz) value expected to be in microseconds
        mTestSequence.sampleDelay = getSampleDelay();

        // measureMask tells the Ardiuno which values to read and send over serial.
        mTestSequence.measureMask = (switchA0.isChecked() ? 1 : 0);
        mTestSequence.measureMask += (switchA1.isChecked() ? 1 : 0) << 1;
        mTestSequence.measureMask += (switchA2.isChecked() ? 1 : 0) << 2;
        mTestSequence.measureMask += (switchA3.isChecked() ? 1 : 0) << 3;
        mTestSequence.measureMask += (switchA4.isChecked() ? 1 : 0) << 4;
        mTestSequence.measureMask += (switchA5.isChecked() ? 1 : 0) << 5;

        // Fail on no selection indicated
        if (mTestSequence.measureMask <= 0) {
            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "At least one (1) input must be selected!",
                    Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        Log.i("Bluno cmdString", mTestSequence.getConfigureString());
        mTestSequence.save();

        // TODO improve handling of test start
        buttonBegin.setText("Stop");
        bluno.connect("D0:39:72:C5:38:6F");
        mStartButton = ButtonState.STOP;
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bluno.send(mTestSequence.getConfigureString());
                mTestSequence.startTime = System.nanoTime();
                mTestSequence.save();
            }
        }, 1000);
    }

    private void buttonBeginStop(){
        Button buttonBegin = (Button) findViewById(R.id.buttonBegin);
        bluno.send("A");
        buttonBegin.setText("Start");
        mTestSequence.finishTime = System.nanoTime();
        mTestSequence.save();
        Log.i(TAG, "Samples collected = " + mSampleBuffer.size());
        if(mSampleBuffer.size() > 0) {
            for (Sample sample : mSampleBuffer) {
                sample.save();
            }
            mSampleBuffer.clear();
        }else{
            mTestSequence.delete();
        }
        mStartButton = ButtonState.START;
    }

    private void buttonBeginClick(){
        if(mStartButton.equals(ButtonState.START)){
            buttonBeginStart();
        }else{
            buttonBeginStop();
        }
    }

    private RemoteState StateLookup(int code){
        switch (code){
            case 0:
                Log.i(TAG, "RemoteState.NULL");
                return RemoteState.NULL;
            case 1:
                Log.i(TAG, "RemoteState.TEST");
                return RemoteState.TEST;
            case 2:
                Log.i(TAG, "RemoteState.MAIN");
                return RemoteState.MAIN;
            case 3:
                Log.i(TAG, "RemoteState.MEASURE");
                return RemoteState.MEASURE;
            case 4:
                Log.i(TAG, "RemoteState.CONTROL");
                return RemoteState.CONTROL;
            case 5:
                Log.i(TAG, "RemoteState.TOGGLE_LED");
                return RemoteState.TOGGLE_LED;
            case 6:
                Log.i(TAG, "RemoteState.ECHO");
                return RemoteState.ECHO;
            default:
                return RemoteState.NULL;
        }
    }

    // TODO return sample object from ValueUnpacker to handle multi input record mode
    private int ValueUnpacker(byte[] data){
        if(data.length == 2){
            return ((data[0] & 0x00FF) | ((data[1] & 0x00FF) << 2));
        }
        return -1;
    }

    private void MessageHandler(byte[] data, String message) {
        if(message.startsWith("STATE")){
            String[] parts = message.split("=");
            int state = Integer.parseInt(parts[parts.length - 1]);
            mState = StateLookup(state);
            return; // Return on state change. done processing this message
        }else if(message.startsWith("ERROR")){
            Log.e(TAG, message);
            return; // not data to save from this message
        }else if(message.startsWith("INFO")){
            Log.i(TAG, message);
            return; // diagnostic message
        }

        if(mState.equals(RemoteState.MEASURE)){
            if(mTestSequence.compressed){
                if(data.length == 2){
                    Sample mSample = new Sample("A0", ValueUnpacker(data), mTestSequence);
                    Log.v(TAG, Integer.toString(mSample.value));
                    mSampleBuffer.add(mSample);
                }
            }else{
                Sample mSample = new Sample("A0", Integer.parseInt(message), mTestSequence);
                Log.v(TAG, message);
                mSampleBuffer.add(mSample);
            }
        }else if(mState.equals(RemoteState.NULL)){
            bluno.send("A");
        }
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(ACTION_MESSAGE_AVAILABLE.equals(intent.getAction())){
                byte[] data = intent.getByteArrayExtra(EXTRA_VALUE);
                if(data != null && data.length > 0) {
                    MessageHandler(data, new String(data));
                }
            }
        }
    };


    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_MESSAGE_AVAILABLE);
        return intentFilter;
    }
    /*
    public void writeSamplesToFile() {
        if (isExternalStorageWritable()) {
            File root = Environment.getExternalStorageDirectory();
            File dir = new File(root.getAbsolutePath() + "/bluno");
            dir.mkdirs();
            File file = new File(dir, "output.csv");

            try {
                FileOutputStream f = new FileOutputStream(file);
                PrintWriter pw = new PrintWriter(f);
                pw.println("Start time:, " + startTime);
                pw.println("End time:, " + endTime);
                long duration = (endTime - startTime);
                pw.println("Duration:, " + duration);
                pw.println("Sample count:, " + samples.size());
                pw.println("Error count:, " + errorCount);
                pw.println("Sample rate:, " + ((float) samples.size() / ((float) duration * 0.000000001)));
                pw.println("num, value");
                Integer i = 0;
                for (Integer sample : samples) {
                    pw.println(i + "," + sample);
                    i = i + 1;
                }

                pw.flush();
                pw.close();
                f.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                //TAG, "File not found");
            } catch (IOException e) {
                e.printStackTrace();
            }

            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));


        }
    }

    public void processBuffer() {
        // Splits inputBuffer on newline
        // newline will not be returned, but the last element in the list could be corrupted
        // depending on how Bluno handles the serial stream
        // TODO Parse the input buffer for newlines and return fragment of sample to buffer
        String sample;

        // Data in inputRingBuffer should only contain samples
        while (inputRingBuffer.size() > 3) {
            short[] temp = new short[3];
            // take 3 out of ringBuffer
            for (int i = 0; i < 3; i++) {
                temp[i] = inputRingBuffer.pop();
            }

            // Is the last character the line separator?
            if (temp[2] == 10) {
                if (startTime == 0) {
                    startTime = System.nanoTime();
                    errorCount = 0;
                }
                // TODO This should be a multiple of samples being read ie 4 when input A0 && A1
                // Sanity Check: Confirms that the separator character is in the expected position
                // This should allow for the second byte in sample to equal 10
                //int part1 = (temp[0] & 0x00FF);
                //int part2 = ((temp[1] & 0x00FF) << 2);
                int composite = (temp[0] & 0x00FF) | ((temp[1] & 0x00FF) << 2);
                //System.out.println("Data -->" + composite);
                //sample = "" + composite;  /// <--- this line may be slow. Should not be using string to store samples
                samples.add(composite);
            } else {
                errorCount += 1;
                // Try to find separator character
                // Somewhat arbitrary selection for i max
                // Should be selected as greater than number of expected bytes per sample
                for (int i = 0; i <= 3; i++) {
                    if (!inputRingBuffer.isEmpty()) {
                        if (inputRingBuffer.pop() == 10) {
                            // May have found a separator character
                            // Break loop and test sanity
                            return;
                        }
                    }else{
                        return;
                    }
                }
            }
        }
    }
    */

    @Override
    protected void onPause(){
        bluno.pause();
        getApplicationContext().unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }

    @Override
    protected void onResume(){
        bluno.resume();
        getApplicationContext().registerReceiver(mBroadcastReceiver, makeIntentFilter());
        super.onResume();
    }

}
