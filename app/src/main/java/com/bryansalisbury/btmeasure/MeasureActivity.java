package com.bryansalisbury.btmeasure;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
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
import com.bryansalisbury.btmeasure.bluno.RingBuffer;
import com.bryansalisbury.btmeasure.models.TestSequence;
import com.squareup.leakcanary.LeakCanary;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.UUID;

public class MeasureActivity extends AppCompatActivity {
    private Bluno bluno = null;

    private int sampleDelay;
    private int measureMask = 0;
    private boolean Compressed = false;
    private SeekBar seekSampleRate;
    private Switch switchA0;
    private Switch switchA1;
    private Switch switchA2;
    private Switch switchA3;
    private Switch switchA4;
    private Switch switchA5;
    private TextView textSampleRate;
    private Button buttonBegin;
    private Button buttonScan;
    private CheckBox checkCompress;

    // Serial Communication Variables
    private String cmdString;
    private RingBuffer<Short> inputRingBuffer = new RingBuffer<Short>(64);

    // Samples Decoded
    private ArrayList<Integer> samples = new ArrayList<Integer>();

    // Sample Statistics
    private int errorCount = 0;
    private long startTime = 0;
    private long endTime = 0;

    // Arduino Control Variables
    private static final int sampleRateMax = 4000;
    private int sampleRate;

    @Override
    public void onStart() {
        super.onStart();
    }

    // State Variables
    private enum executionState {
        isNull, isSending
    }

    private executionState mExecState = executionState.isNull;

    // Data layer variables
    TestSequence mTestSequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluno = new Bluno(this);

        setContentView(R.layout.activity_measure);
        seekSampleRate = (SeekBar) findViewById(R.id.seekBar);
        switchA0 = (Switch) findViewById(R.id.switchA0);
        switchA1 = (Switch) findViewById(R.id.switchA1);
        switchA2 = (Switch) findViewById(R.id.switchA2);
        switchA3 = (Switch) findViewById(R.id.switchA3);
        switchA4 = (Switch) findViewById(R.id.switchA4);
        switchA5 = (Switch) findViewById(R.id.switchA5);
        textSampleRate = (TextView) findViewById(R.id.textSampleRate);
        buttonBegin = (Button) findViewById(R.id.buttonBegin);
        buttonScan = (Button) findViewById(R.id.btnConnect);
        checkCompress = (CheckBox) findViewById(R.id.checkCompression);


        seekSampleRate.setMax(sampleRateMax - 1);
        sampleRate = seekSampleRate.getProgress() + 1;

        textSampleRate.setText("Sample Rate: " + sampleRate + " (hz)");

        seekSampleRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                                                      @Override
                                                      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                                                          sampleRate = progress + 1;
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

    private void buttonBeginClick(){
        // Create database test sequence
        mTestSequence = new TestSequence();
        mTestSequence.startTime = System.nanoTime();
        mTestSequence.testName = "New Collection";

        cmdString = "MP";
        startTime = 0;
        endTime = 0;

        if (checkCompress.isChecked()) {
            cmdString += "C1:";
            Compressed = true;
        } else {
            cmdString += "C0:";
            Compressed = false;
        }

        // Sample delay calculated from sampleRate(hz)
        // value expected to be in microseconds
        sampleDelay = (int) ((1.0 / (float) sampleRate) * 1000000);
        cmdString += "D" + sampleDelay + ":";

        // measureMask tells the Ardiuno which values to read and send over serial.
        measureMask = (switchA0.isChecked() ? 1 : 0);
        measureMask += (switchA1.isChecked() ? 1 : 0) << 1;
        measureMask += (switchA2.isChecked() ? 1 : 0) << 2;
        measureMask += (switchA3.isChecked() ? 1 : 0) << 3;
        measureMask += (switchA4.isChecked() ? 1 : 0) << 4;
        measureMask += (switchA5.isChecked() ? 1 : 0) << 5;

        // Fail on no selection indicated
        if (measureMask > 0) {
            cmdString += "S" + measureMask + ":";
        } else {
            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "At least one (1) input must be selected!",
                    Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        cmdString += "N2000:";

        Log.i("Bluno cmdString", cmdString);
        mExecState = executionState.isSending;
        buttonBegin.setText("Stop");
        bluno.connect("D0:39:72:C5:38:6F");

    }

    // copied from developer.android.com
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state));
    }

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

    @Override
    protected void onPause(){
        bluno.pause();
        super.onPause();
    }

    @Override
    protected void onResume(){
        bluno.resume();
        super.onResume();
    }

}
