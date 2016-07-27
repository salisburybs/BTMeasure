package com.bryansalisbury.btmeasure;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.bryansalisbury.btmeasure.models.Sample;
import com.bryansalisbury.btmeasure.models.TestSequence;

import java.util.List;

public class ResultsActivity extends AppCompatActivity {
    TestSequence mTestSequence = new TestSequence();
    List<Sample> mSamples;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        TextView testName = (TextView) findViewById(R.id.tvTestName);
        TextView sampleCount = (TextView) findViewById(R.id.tvSampleCount);
        TextView samples = (TextView) findViewById(R.id.tvSamples);

        Intent intent = getIntent();
        Integer mTestSequenceID = intent.getIntExtra("TEST_SEQUENCE_ID", -1);
        if (mTestSequenceID != -1) {
            mTestSequence = TestSequence.findById(TestSequence.class, mTestSequenceID);
            //mSamples = Sample.find(Sample.class, "test_sequence = ?", mTestSequenceID.toString());
            mSamples = Sample.find(Sample.class, "");
            //The key argument here must match that used in the other activity

            testName.setText(mTestSequence.testName);
            sampleCount.setText("Count: " + mSamples.size());
        }

    }
}
