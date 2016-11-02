package com.bryansalisbury.btmeasure;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.bryansalisbury.btmeasure.bluno.Bluno;

import java.util.ArrayList;
import java.util.Locale;

public class ControlActivity extends AppCompatActivity {
    private Bluno bluno;
    private double kp=0, ki=0, kd=0;
    private int desiredPosition, outputPin, inputPin;
    private int maxOut = 255;
    private int minOut = 0;

    private ArrayList<String> outputBuffer = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        //instantiate bluno
        bluno = new Bluno(this);

        SeekBar seekKp = (SeekBar) findViewById(R.id.seekKp);
        SeekBar seekKi = (SeekBar) findViewById(R.id.seekKi);
        SeekBar seekKd = (SeekBar) findViewById(R.id.seekKd);
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

        tvKp.setText(Integer.toString(seekKp.getProgress()));
        tvKi.setText(Integer.toString(seekKi.getProgress()));
        tvKd.setText(Integer.toString(seekKd.getProgress()));
        tvTarget.setText(Integer.toString(seekTarget.getProgress()));
        tvMaxOut.setText(Integer.toString(seekMaxOutput.getProgress()));

        seekKp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                double kp = i / 10.0;
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
                double ki = i / 10.0;
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
                double kd = i / 10.0;
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
                tvTarget.setText(Integer.toString(i));
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


        final Spinner spinInput = (Spinner) findViewById(R.id.spinInput);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapterInput = ArrayAdapter.createFromResource(this,
                R.array.inputs_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapterInput.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinInput.setAdapter(adapterInput);


        final Spinner spinOutput = (Spinner) findViewById(R.id.spinOutput);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapterOutput = ArrayAdapter.createFromResource(this,
                R.array.outputs_array, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapterOutput.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinOutput.setAdapter(adapterOutput);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                inputPin = getResources().getIntArray(R.array.inputs_values_array)[spinInput.getSelectedItemPosition()];
                outputPin = getResources().getIntArray(R.array.outputs_values_array)[spinOutput.getSelectedItemPosition()];

                outputBuffer.add("C");
                outputBuffer.add(String.format(Locale.getDefault(), "PKP%1$.1f:KI%2$.1f:KI%3$.1f", kp, ki, kd));
                outputBuffer.add("PD" + desiredPosition);
                outputBuffer.add("PH" + maxOut + ":L" + minOut);
                outputBuffer.add("PO" + outputPin + ":I" + inputPin);
            }
        });
    }
}
