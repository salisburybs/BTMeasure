package com.bryansalisbury.btmeasure;

import com.bryansalisbury.btmeasure.models.TestSequence;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Objects;

public class TestSequenceAdapter extends ArrayAdapter<TestSequence>{
    public TestSequenceAdapter(Context context, ArrayList<TestSequence> tests){
        super(context, 0, tests);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        TestSequence test = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_test, parent, false);
        }
        // Lookup view for data population
        TextView tvName = (TextView) convertView.findViewById(R.id.tvName);
        TextView tvTime = (TextView) convertView.findViewById(R.id.tvTime);
        // Populate the data into the template view using the data object
        tvName.setText(test.testName);
        tvTime.setText(Objects.toString(test.unixTimestamp));
        // Return the completed view to render on screen
        return convertView;
    }

}
