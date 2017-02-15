package com.bryansalisbury.btmeasure;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.bryansalisbury.btmeasure.models.Sample;
import com.bryansalisbury.btmeasure.models.TestSequence;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.orm.SugarRecord;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.support.v4.content.FileProvider.getUriForFile;

public class ResultsActivity extends AppCompatActivity {
    private Long mTestSequenceID;
    private ShareActionProvider mShareActionProvider;

    private List<Sample> mSamples = null;
    private TestSequence mTestSequence = null;
    private List<Entry> entries = new ArrayList<>();

    private Runnable sampleLoader = new Runnable() {
        @Override
        public void run() {
            mTestSequence = TestSequence.findById(TestSequence.class, mTestSequenceID);
            mSamples = Sample.findWithQuery(Sample.class,
                    "select * from Sample where test_sequence = ?",
                    mTestSequenceID.toString());

            if(mSamples.size() != 700 && mSamples.size() != 750){
                Snackbar snackbar = Snackbar
                        .make(findViewById(android.R.id.content), String.format(Locale.getDefault(), "Unexpected count (%1$d)", mSamples.size()), Snackbar.LENGTH_INDEFINITE)
                        .setAction("DISMISS", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {

                            }
                        })
                        .setActionTextColor(getResources().getColor(android.R.color.holo_red_light ));
                snackbar.show();
            }

            int i = 0;
            for (Sample theSample : mSamples) {
                // turn your data into Entry objects
                entries.add(new Entry((float) getTimeForSample(i, mTestSequence.getTImeDelta()), (float) theSample.getVolts()));
                i++;
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ScatterChart chart = (ScatterChart) findViewById(R.id.chart);
                    if(mShareActionProvider != null) {
                        mShareActionProvider.setShareIntent(createShareIntent());
                    }
                    ScatterDataSet dataSet = new ScatterDataSet(entries, "Analog Input");
                    ScatterData scatterData = new ScatterData(dataSet);
                    chart.getAxisLeft().setAxisMaxValue((float)5.00);
                    chart.getAxisLeft().setAxisMinValue((float)0.00);
                    dataSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
                    dataSet.setScatterShapeHoleRadius(0f);
                    dataSet.setScatterShapeSize(8f);

                    Legend l = chart.getLegend();
                    l.setWordWrapEnabled(true);
                    l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
                    l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
                    l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
                    l.setDrawInside(false);

                    chart.setData(scatterData);
                    chart.invalidate(); // refresh

                    setTitle(mTestSequence.testName);
                }
            });
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        Intent intent = getIntent();
        mTestSequenceID = (long) intent.getIntExtra("TEST_SEQUENCE_ID", -1);
        if (mTestSequenceID != -1) {
            new Thread(sampleLoader).start();
        }else{
            this.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.results, menu);
        MenuItem item = menu.findItem(R.id.menu_item_share);
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.menu_item_delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Permanently delete this dataset?")
                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            SugarRecord.deleteInTx(mSamples);
                            SugarRecord.delete(mTestSequence);
                            onBackPressed();
                        }})
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    });
            builder.show();
            return true;
        }else if(id == R.id.menu_item_details){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Number of samples: " + mSamples.size())
                    .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // FIRE ZE MISSILES!
                        }})
                    .setTitle("Details");
            builder.show();
        }

        return super.onOptionsItemSelected(item);
    }

    private double getTimeForSample(int i, double delta){
        return (i * delta);
    }

    private Uri writeSamplesToFile() {
        File testPath = new File(getApplicationContext().getFilesDir(), "tests");
        testPath.mkdirs();
        File newFile = new File(testPath, mTestSequence.timestamp +".csv");

        try {
            FileOutputStream f = new FileOutputStream(newFile);
            PrintWriter pw = new PrintWriter(f);
            pw.println("Start time:, " + mTestSequence.startTime);
            pw.println("End time:, " + mTestSequence.finishTime);
            long duration = (mTestSequence.finishTime - mTestSequence.startTime);
            pw.println("Duration:, " + duration);
            pw.println("Sample count:, " + mSamples.size());
            pw.println("Sample rate:, " + (1.00 / mTestSequence.getTImeDelta()));
            pw.println("");
            pw.println("num, time, value, volts");
            Integer i = 0;

            for (Sample sample : mSamples) {
                pw.println(i + "," + getTimeForSample(i, mTestSequence.getTImeDelta()) + "," + sample.value + "," + sample.getVolts());
                i = i + 1;
            }

            pw.flush();
            pw.close();
            f.close();

            return getUriForFile(getApplicationContext(), "com.bryansalisbury.fileprovider", newFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            //TAG, "File not found");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Intent createShareIntent(){
        Uri fileUri = writeSamplesToFile();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return shareIntent;
    }
}
