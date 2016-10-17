package com.bryansalisbury.btmeasure;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import com.bryansalisbury.btmeasure.bluno.Bluno;
import com.bryansalisbury.btmeasure.models.Sample;
import com.bryansalisbury.btmeasure.models.TestSequence;
import com.orm.SugarRecord;

import java.util.ArrayList;

public class MeasureActivity extends AppCompatActivity {
    private Bluno bluno;

    private static final String TAG = "MeasureActivity";

    public static final String ACTION_MESSAGE_AVAILABLE = "com.bryansalisbury.message.AVAILABLE";
    public static final String EXTRA_VALUE = "com.bryansalisbury.message.EXTRA_VALUE";

    private enum RemoteState {NULL, TEST, SENDBUF, MEASURE, CONTROL, TOGGLE_LED, ECHO}
    private RemoteState mState = RemoteState.NULL;

    private enum ButtonState {START, STOP}
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

        final EditText lblA0 = (EditText) findViewById(R.id.etA0);
        final EditText lblA1 = (EditText) findViewById(R.id.etA1);
        final EditText lblA2 = (EditText) findViewById(R.id.etA2);
        final EditText lblA3 = (EditText) findViewById(R.id.etA3);
        final EditText lblA4 = (EditText) findViewById(R.id.etA4);
        final EditText lblA5 = (EditText) findViewById(R.id.etA5);

        Switch switchA0 = (Switch) findViewById(R.id.switchA0);
        switchA0.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                if(isChecked){
                    lblA0.setEnabled(true);
                    lblA0.requestFocus();
                }else{
                    lblA0.clearFocus();
                    lblA0.setEnabled(false);
                }
            }
        });

        Switch switchA1 = (Switch) findViewById(R.id.switchA1);
        switchA1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                if(isChecked){
                    lblA1.setEnabled(true);
                    lblA1.requestFocus();
                }else{
                    lblA1.clearFocus();
                    lblA1.setEnabled(false);
                }
            }
        });

        Switch switchA2 = (Switch) findViewById(R.id.switchA2);
        switchA2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                if(isChecked){
                    lblA2.setEnabled(true);
                    lblA2.requestFocus();
                }else{
                    lblA2.clearFocus();
                    lblA2.setEnabled(false);
                }
            }
        });

        Switch switchA3 = (Switch) findViewById(R.id.switchA3);
        switchA3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                if(isChecked){
                    lblA3.setEnabled(true);
                    lblA3.requestFocus();
                }else{
                    lblA3.clearFocus();
                    lblA3.setEnabled(false);
                }
            }
        });

        Switch switchA4 = (Switch) findViewById(R.id.switchA4);
        switchA4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                if(isChecked){
                    lblA4.setEnabled(true);
                    lblA4.requestFocus();
                }else{
                    lblA4.clearFocus();
                    lblA4.setEnabled(false);
                }
            }
        });

        Switch switchA5 = (Switch) findViewById(R.id.switchA5);
        switchA5.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                if(isChecked){
                    lblA5.setEnabled(true);
                    lblA5.requestFocus();
                }else{
                    lblA5.clearFocus();
                    lblA5.setEnabled(false);
                }
            }
        });
    }

    private int getPrecount(){
        Spinner spinner = (Spinner) findViewById(R.id.spinner);
        return getResources().getIntArray(R.array.interrupt_values_array)[spinner.getSelectedItemPosition()] ;
    }

    private void buttonBeginStart(){
        // UI Elements
        final EditText lblA0 = (EditText) findViewById(R.id.etA0);
        final EditText lblA1 = (EditText) findViewById(R.id.etA1);
        final EditText lblA2 = (EditText) findViewById(R.id.etA2);
        final EditText lblA3 = (EditText) findViewById(R.id.etA3);
        final EditText lblA4 = (EditText) findViewById(R.id.etA4);
        final EditText lblA5 = (EditText) findViewById(R.id.etA5);
        final EditText lblTestName = (EditText) findViewById(R.id.etTestName);

        Switch switchA0 = (Switch) findViewById(R.id.switchA0);
        Switch switchA1 = (Switch) findViewById(R.id.switchA1);
        Switch switchA2 = (Switch) findViewById(R.id.switchA2);
        Switch switchA3 = (Switch) findViewById(R.id.switchA3);
        Switch switchA4 = (Switch) findViewById(R.id.switchA4);
        Switch switchA5 = (Switch) findViewById(R.id.switchA5);
        Button buttonBegin = (Button) findViewById(R.id.buttonBegin);

        // Create database test sequence
        mTestSequence = new TestSequence(lblTestName.getText().toString());
        mTestSequence.compressed = true;

        // Sample delay calculated from sampleRate(hz) value expected to be in microseconds
        mTestSequence.overflowCount = getPrecount();

        // measureMask tells the Ardiuno which values to read and send over serial.
        if(switchA0.isChecked()) {
            mTestSequence.measureMask = 1;
            mTestSequence.labelA0 = lblA0.getText().toString();
        }

        if(switchA1.isChecked()) {
            mTestSequence.measureMask += (1 << 1);
            mTestSequence.labelA1 = lblA1.getText().toString();
        }

        if(switchA2.isChecked()) {
            mTestSequence.measureMask += (1 << 2);
            mTestSequence.labelA2 = lblA2.getText().toString();
        }

        if(switchA3.isChecked()) {
            mTestSequence.measureMask += (1 << 3);
            mTestSequence.labelA3 = lblA3.getText().toString();
        }

        if(switchA4.isChecked()) {
            mTestSequence.measureMask += (1 << 4);
            mTestSequence.labelA4 = lblA4.getText().toString();
        }

        if(switchA5.isChecked()) {
            mTestSequence.measureMask += (1 << 5);
            mTestSequence.labelA5 = lblA5.getText().toString();
        }

        // Fail on no selection indicated
        if (mTestSequence.measureMask <= 0) {
            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "At least one (1) input must be selected!",
                    Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        if (Integer.bitCount(mTestSequence.measureMask) > 1){
            Toast toast = Toast.makeText(
                    getApplicationContext(),
                    "Only one (1) input supported at this time.",
                    Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        Log.i("Bluno cmdString", mTestSequence.getConfigureString());
        mTestSequence.save();

        // TODO improve handling of test start

        buttonBegin.setText("Stop");
        mStartButton = ButtonState.STOP;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String mDevice = prefs.getString("device_mac", "");

        if(!bluno.connectedTo(mDevice)){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    bluno.connect(mDevice);
                    bluno.send(mTestSequence.getConfigureString());
                    mTestSequence.startTime = System.nanoTime();
                    mTestSequence.save();
                }
            }).start();
        }else{
            bluno.send(mTestSequence.getConfigureString());
            mTestSequence.startTime = System.nanoTime();
            mTestSequence.save();
        }
    }

    private void buttonBeginStop(){
        Button buttonBegin = (Button) findViewById(R.id.buttonBegin);
        bluno.send("A");
        buttonBegin.setText("Start");
        Log.i(TAG, "Samples collected = " + mSampleBuffer.size());
        if(mSampleBuffer.size() > 0) {
            SugarRecord.saveInTx(mSampleBuffer);
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
                return RemoteState.NULL;
            case 1:
                return RemoteState.TEST;
            case 2:
                return RemoteState.SENDBUF;
            case 3:
                return RemoteState.MEASURE;
            case 4:
                return RemoteState.CONTROL;
            case 5:
                return RemoteState.TOGGLE_LED;
            case 6:
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
        ProgressBar mProgress = (ProgressBar) findViewById(R.id.progressBar);

        if(message.startsWith("STATE")){
            String[] parts = message.split("=");
            int state = Integer.parseInt(parts[parts.length - 1]);
            if(mState != StateLookup(state)){
                if(mState.equals(RemoteState.SENDBUF) || mState.equals(RemoteState.MEASURE)){
                    if(RemoteState.NULL.equals(StateLookup(state))){
                        buttonBeginStop();
                        mProgress.setProgress(0);
                    }
                }
            }
            mState = StateLookup(state);
            Log.i(TAG, mState.name());
            return; // Return on state change. done processing this message

        }else if(message.startsWith("ERROR")){
            Log.e(TAG, message);
            return; // not data to save from this message

        }else if(message.startsWith("INFO")){
            Log.i(TAG, message);
            String[] parts = message.substring(5).split("=");
            if(parts.length == 2){
                if(parts[0].equals("START")){
                    mTestSequence.startTime = Integer.parseInt(parts[1]);
                    mTestSequence.save();
                }else if(parts[0].equals("STOP")){
                    mTestSequence.finishTime = Integer.parseInt(parts[1]);
                    mTestSequence.save();
                }

            }
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
        mProgress.setProgress(mSampleBuffer.size());
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
        super.onPause();
        bluno.pause();
        getApplicationContext().unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onResume(){
        super.onResume();
        bluno.resume();
        getApplicationContext().registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }

}
