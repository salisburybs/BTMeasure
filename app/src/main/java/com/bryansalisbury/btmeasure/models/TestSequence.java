package com.bryansalisbury.btmeasure.models;
import com.orm.SugarRecord;

/**
 * Created by salis_000 on 5/31/2016.
 */
public class TestSequence extends SugarRecord{
    String testName;
    long startTime;

    int sampleDelay;

    // Input Labels
    String labelA0;
    String labelA1;
    String labelA2;
    String labelA3;
    String labelA4;
    String labelA5;

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
