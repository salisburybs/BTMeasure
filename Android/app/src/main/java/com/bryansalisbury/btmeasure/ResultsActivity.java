package com.bryansalisbury.btmeasure;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.bryansalisbury.btmeasure.models.Sample;
import com.bryansalisbury.btmeasure.models.TestSequence;

import java.util.List;

public class ResultsActivity extends AppCompatActivity {
    TestSequence mTestSequence;
    List<Sample> mSamples;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            Integer mTestSequenceID = extras.getInt("TEST_SEQUENCE_ID");
            mTestSequence = TestSequence.findById(TestSequence.class, mTestSequenceID);
            mSamples = Sample.find(Sample.class, "test_sequence = ?", mTestSequenceID.toString());
            //The key argument here must match that used in the other activity
        }

        TextView testName = (TextView) findViewById(R.id.tvName);
        TextView sampleCount = (TextView) findViewById(R.id.tvSampleCount);
        TextView samples = (TextView) findViewById(R.id.tvSamples);

        testName.setText(mTestSequence.testName);
        sampleCount.setText("Count: " + mSamples.size());

    }
}
