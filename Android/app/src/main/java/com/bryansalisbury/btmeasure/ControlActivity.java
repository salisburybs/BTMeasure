package com.bryansalisbury.btmeasure;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.bryansalisbury.btmeasure.bluno.Bluno;
import com.bryansalisbury.btmeasure.models.Sample;
import com.bryansalisbury.btmeasure.models.TestSequence;
import com.orm.SugarRecord;

import java.util.ArrayList;
import java.util.Locale;

public class ControlActivity extends AppCompatActivity {
    private Bluno bluno;
    private double kp=0, ki=0, kd=0;
    private int desiredPosition, outputPin, inputPin;
    private int maxOut = 255;
    private int minOut = 0;
    private TestSequence mTestSequence;
    private ArrayList<String> outputBuffer = new ArrayList<>();

    public static final String ACTION_MESSAGE_AVAILABLE = "com.bryansalisbury.message.AVAILABLE";
    public static final String EXTRA_VALUE = "com.bryansalisbury.message.EXTRA_VALUE";
    private static final String TAG = "ControlActivity";

    String mDevice;

    private enum RemoteState {NULL, SENDBUF, MEASURE, CONTROL}
    private RemoteState mState = RemoteState.NULL;

    private ArrayList<Sample> mSampleBuffer = new ArrayList<>();

    private SharedPreferences prefs;
    private ProgressBar mProgress;
    private TextView tvSensorValue;
    private boolean feedback = true;
    private boolean measure = true;


    private RemoteState StateLookup(int code){
        switch (code){
            case 0:
                return RemoteState.NULL;
            case 2:
                return RemoteState.SENDBUF;
            case 3:
                return RemoteState.MEASURE;
            case 4:
                return RemoteState.CONTROL;
            default:
                return RemoteState.NULL;
        }
    }
    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_MESSAGE_AVAILABLE);
        return intentFilter;
    }

    private int ValueUnpacker(byte[] data){
        if(data.length == 2){
            return ((data[0] & 0x00FF) | ((data[1] & 0x00FF) << 2));
        }
        return -1;
    }

    private void saveSamples(){
        bluno.send("A");
        if(mSampleBuffer.size() > 0) {
            mTestSequence.save();
            SugarRecord.saveInTx(mSampleBuffer);
            Snackbar snackbar = Snackbar
                    .make(findViewById(android.R.id.content), "Test complete", Snackbar.LENGTH_LONG)
                    .setAction("DISMISS", new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {

                        }
                    });
            snackbar.show();
            mSampleBuffer.clear();
        }else{
            mTestSequence.delete();
        }
    }

    private void MessageHandler(byte[] data, String message) {
        if(message.startsWith("STATE")){
            String[] parts = message.split("=");
            int state = Integer.parseInt(parts[parts.length - 1]);
            if(mState != StateLookup(state)) {
                if(mState.equals(RemoteState.SENDBUF) || mState.equals(RemoteState.CONTROL)){
                    if(RemoteState.NULL.equals(StateLookup(state))){
                        saveSamples();
                        mProgress.setProgress(0);
                    }
                }
            }
            mState = StateLookup(state);

        }else if(message.startsWith("ERROR")){
            Log.e(TAG, message);

        }else if(message.startsWith("INFO")){
            Log.i(TAG, message);
            String[] parts = message.substring(5).split("=");

            if(parts.length == 2){
                //confirm setting values here
                /*
                if(parts[0].equals("START")){
                    mTestSequence.startTime = Integer.parseInt(parts[1]);
                    mTestSequence.save();
                }else if(parts[0].equals("STOP")){
                    mTestSequence.finishTime = Integer.parseInt(parts[1]);
                    mTestSequence.save();
                }*/

            }

        }else if(mState.equals(RemoteState.SENDBUF)){
            if(data.length == 2){
                Sample mSample = new Sample("A0", ValueUnpacker(data), mTestSequence);
                Log.v(TAG, Integer.toString(mSample.value));
                mSampleBuffer.add(mSample);
                bluno.send("K"); // send the ACK command
                mProgress.setProgress(mSampleBuffer.size());
            }
        }else{
            tvSensorValue.setVisibility(View.VISIBLE);
            try{
                tvSensorValue.setText(String.format(Locale.getDefault(), "%1$.2fv", (5.00/1023.0)*(Integer.valueOf(message))));
            }catch (Exception ex){

            }
        }

    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(ACTION_MESSAGE_AVAILABLE.equals(intent.getAction())){
                final byte[] data = intent.getByteArrayExtra(EXTRA_VALUE);
                if(data != null && data.length > 0) {
                    MessageHandler(data, new String(data));
                }
            }
        }
    };

    private void sendConfig(){
        bluno.send("C");

        if(feedback) {
            bluno.send(String.format(Locale.getDefault(), "PA%1$.1f\nPB%2$.1f\nPC%3$.1f\n", kp, ki, kd));
            bluno.send("PF1\n");
        }else{
            bluno.send("PF0\n");
        }

        if(measure){
            bluno.send("PM1\n");
        }else{
            bluno.send("PM0\n");
        }
        bluno.send("PD" + desiredPosition + "\n");
        bluno.send("PH" + maxOut + "\nPL" + minOut + "\n");
        bluno.send("PO" + outputPin + "\nPI" + inputPin + "\n");
        bluno.send("PE" + mTestSequence.overflowCount + "\n");
        bluno.send("PS\n"); // start flag
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_control);

        //instantiate bluno
        bluno = new Bluno(this);

        final SeekBar seekKp = (SeekBar) findViewById(R.id.seekKp);
        final SeekBar seekKi = (SeekBar) findViewById(R.id.seekKi);
        final SeekBar seekKd = (SeekBar) findViewById(R.id.seekKd);
        mProgress = (ProgressBar) findViewById(R.id.progressBar);

        final SeekBar seekTarget = (SeekBar) findViewById(R.id.seekTarget);
        final SeekBar seekMaxOutput = (SeekBar) findViewById(R.id.seekMaxOutput);

        final TextView tvKp = (TextView) findViewById(R.id.tvKpValue);
        final TextView tvKi = (TextView) findViewById(R.id.tvKiValue);
        final TextView tvKd = (TextView) findViewById(R.id.tvKdValue);
        final TextView tvTarget = (TextView) findViewById(R.id.tvTargetValue);
        final TextView tvMaxOut = (TextView) findViewById(R.id.tvMaxOutVal);

        Button btnPos1 = (Button) findViewById(R.id.btnPos1);
        Button btnNeg1 = (Button) findViewById(R.id.btnNeg1);
        Button btnPos10 = (Button) findViewById(R.id.btnPos10);
        Button btnNeg10 = (Button) findViewById(R.id.btnNeg10);
        Button btnSend = (Button) findViewById(R.id.btnSend);

        ToggleButton btnFeedback = (ToggleButton) findViewById(R.id.toggleFeedback);
        ToggleButton btnTestmode = (ToggleButton) findViewById(R.id.toggleTestmode);
        tvSensorValue = (TextView) findViewById(R.id.textSensor);


        tvKp.setText(Integer.toString(seekKp.getProgress()));
        tvKi.setText(Integer.toString(seekKi.getProgress()));
        tvKd.setText(Integer.toString(seekKd.getProgress()));
        tvTarget.setText(Integer.toString(seekTarget.getProgress()));
        tvMaxOut.setText(Integer.toString(seekMaxOutput.getProgress()));

        seekKp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                kp = i / 10.0;
                tvKp.setText(String.format(Locale.getDefault(), "%1$.1f", kp));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekKi.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                ki = i / 10.0;
                tvKi.setText(String.format(Locale.getDefault(), "%1$.1f", ki));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        seekKd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                kd = i / 10.0;
                tvKd.setText(String.format(Locale.getDefault(), "%1$.1f", kd));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        seekTarget.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                desiredPosition = i;
                tvTarget.setText(String.format(Locale.getDefault(), "%1$.2fv", (5.00/1023.0)*i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        seekMaxOutput.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                maxOut = i;
                tvMaxOut.setText(String.format(Locale.getDefault(), "%1$d", i));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        btnNeg1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seekTarget.setProgress(seekTarget.getProgress() - 1);
            }
        });

        btnPos1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seekTarget.setProgress(seekTarget.getProgress() + 1);
            }
        });

        btnNeg10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seekTarget.setProgress(seekTarget.getProgress() - 10);
            }
        });

        btnPos10.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                seekTarget.setProgress(seekTarget.getProgress() + 10);
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mSampleBuffer.size() > 0){
                    mSampleBuffer.clear();
                    mProgress.setProgress(0);
                    bluno.send("A");
                    return;
                }

                mTestSequence = new TestSequence("Control Mode Run");
                prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                inputPin = Integer.parseInt(prefs.getString("control_input", "0"));
                outputPin = Integer.parseInt(prefs.getString("control_output", "9"));
                mTestSequence.overflowCount = Integer.parseInt(prefs.getString("control_precount", "65285"));
                mDevice = prefs.getString("device_mac", "");

                if(bluno.connectedTo(mDevice)){
                    sendConfig();
                }else {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            bluno.connect(mDevice);

                            sendConfig();

                        }
                    }).start();
                }
            }
        });

        btnFeedback.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                if(!isChecked){
                    seekKp.setProgress(0);
                    seekKd.setProgress(0);
                    seekKi.setProgress(0);
                    seekKp.setEnabled(false);
                    seekKd.setEnabled(false);
                    seekKi.setEnabled(false);
                    feedback = false;
                }else{
                    seekKp.setEnabled(true);
                    seekKd.setEnabled(true);
                    seekKi.setEnabled(true);
                    feedback = true;
                }
            }
        });

        btnTestmode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                measure = isChecked;
            }
        });
    }

    @Override
    protected void onPause(){
        bluno.pause();
        getApplicationContext().unregisterReceiver(mBroadcastReceiver);
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
        bluno.resume();
        getApplicationContext().registerReceiver(mBroadcastReceiver, makeIntentFilter());
    }
}
