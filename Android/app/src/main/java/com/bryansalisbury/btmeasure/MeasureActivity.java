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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
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

    private enum RemoteState {NULL, TEST, SENDBUF, MEASURE, CONTROL, TOGGLE_LED, ECHO};
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
        bluno = new Bluno(this);

        setContentView(R.layout.activity_measure);
        final Button buttonBegin = (Button) findViewById(R.id.buttonBegin);

        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.interrupt_count_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);

        buttonBegin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonBeginClick();
            }
        });


    }

    private int getPrecount(){
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        return getResources().getIntArray(R.array.interrupt_values_array)[spinner.getSelectedItemPosition()] ;
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

        // Create database test sequence
        mTestSequence = new TestSequence("New Collection");
        mTestSequence.compressed = true;

        // Sample delay calculated from sampleRate(hz) value expected to be in microseconds
        mTestSequence.overflowCount = getPrecount();

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
                Log.i(TAG, "RemoteState.SENDBUF");
                return RemoteState.SENDBUF;
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

        if(mState.equals(RemoteState.MEASURE) || mState.equals(RemoteState.SENDBUF)){
            if(mTestSequence.compressed){
                if(data.length == 2){
                    Sample mSample = new Sample("A0", ValueUnpacker(data), mTestSequence);
                    Log.v(TAG, Integer.toString(mSample.value));
                    mSampleBuffer.add(mSample);
                    bluno.send("K"); // send the ACK command
                }
            }else{
                Sample mSample = new Sample("A0", Integer.parseInt(message), mTestSequence);
                Log.v(TAG, message);
                mSampleBuffer.add(mSample);
            }
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
