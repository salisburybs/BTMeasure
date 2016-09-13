package com.bryansalisbury.btmeasure;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.support.v7.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.bryansalisbury.btmeasure.models.Sample;
import com.bryansalisbury.btmeasure.models.TestSequence;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
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

public class ResultsActivity extends AppCompatActivity {
    private Long mTestSequenceID;
    private ShareActionProvider mShareActionProvider;

    private List<Sample> mSamples = null;
    private TestSequence mTestSequence = null;
    private List<Entry> entries = new ArrayList<Entry>();

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private Runnable sampleLoader = new Runnable() {
        @Override
        public void run() {
            mTestSequence = TestSequence.findById(TestSequence.class, mTestSequenceID);
            mSamples = Sample.findWithQuery(Sample.class,
                    "select * from Sample where test_sequence = ?",
                    mTestSequenceID.toString());

            int i = 1;
            for (Sample theSample : mSamples) {
                // turn your data into Entry objects
                entries.add(new Entry(i, theSample.value));
                i++;
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView testName = (TextView) findViewById(R.id.tvTestName);
                    TextView sampleCount = (TextView) findViewById(R.id.tvSampleCount);
                    TextView sampleRate = (TextView) findViewById(R.id.tvSampleRate);
                    TextView samples = (TextView) findViewById(R.id.tvSamples);
                    ScatterChart chart = (ScatterChart) findViewById(R.id.chart);

                    mShareActionProvider.setShareIntent(createShareIntent());
                    ScatterDataSet dataSet = new ScatterDataSet(entries, "Analog Input");
                    ScatterData scatterData = new ScatterData(dataSet);
                    chart.setData(scatterData);
                    chart.invalidate(); // refresh

                    testName.setText(mTestSequence.testName);
                    //sampleRate.setText("Sample rate: " + mTestSequence.getSampleRate());
                    sampleCount.setText("Count: " + mSamples.size());
                }
            });
        };
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
        shareIntent.setType("text/csv");
        mShareActionProvider.setShareIntent(shareIntent);
        return true;
    }

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission

    }

    // copied from developer.android.com
    public boolean isExternalStorageWritable() {
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.menu_item_delete) {
            SugarRecord.deleteInTx(mSamples);
            SugarRecord.delete(mTestSequence);
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public Uri writeSamplesToFile() {
        if (isExternalStorageWritable()) {
            File root = Environment.getExternalStorageDirectory();
            File dir = new File(root.getAbsolutePath() + "/BTMeasure");
            dir.mkdirs();
            File file = new File(dir, mTestSequence.unixTimestamp +".csv");

            try {
                FileOutputStream f = new FileOutputStream(file);
                PrintWriter pw = new PrintWriter(f);
                pw.println("Start time:, " + mTestSequence.startTime);
                pw.println("End time:, " + mTestSequence.finishTime);
                long duration = (mTestSequence.finishTime - mTestSequence.startTime);
                pw.println("Duration:, " + duration);
                pw.println("Sample count:, " + mSamples.size());
                pw.println("Sample rate:, " + ((float) mSamples.size() / ((float) duration * 0.000000001)));
                pw.println("num, value");
                Integer i = 0;
                for (Sample sample : mSamples) {
                    pw.println(i + "," + sample.value);
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
            return Uri.fromFile(file);
        }
        return null;
    }

    private Intent createShareIntent(){
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_STREAM, writeSamplesToFile());
        return shareIntent;
    }
}
