package com.bryansalisbury.btmeasure.models;
import com.orm.SugarRecord;

/**
 * Created by salis_000 on 5/31/2016.
 */
public class TestSequence extends SugarRecord{
    public String testName;
    public long startTime;

    public int sampleDelay;
    public int compressed = 1;

    // Input Labels
    public String labelA0;
    public String labelA1;
    public String labelA2;
    public String labelA3;
    public String labelA4;
    public String labelA5;

    public TestSequence(){
    }

    public TestSequence(String testName, long startTime, int sampleDelay){
        this.testName = testName;
        this.startTime = startTime;
        this.sampleDelay = sampleDelay;

        this.labelA0 = "A0";
        this.labelA1 = "A1";
        this.labelA2 = "A2";
        this.labelA3 = "A3";
        this.labelA4 = "A4";
        this.labelA5 = "A5";
    }
}
