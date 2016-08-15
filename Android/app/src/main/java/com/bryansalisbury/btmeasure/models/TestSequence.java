package com.bryansalisbury.btmeasure.models;
import com.orm.SugarRecord;

import java.util.List;

public class TestSequence extends SugarRecord{
    public String testName;
    public long unixTimestamp;

    public long startTime;
    public long finishTime;

    public int overflowCount;
    public boolean compressed = true;
    public int measureMask;

    // Input Labels
    public String labelA0;
    public String labelA1;
    public String labelA2;
    public String labelA3;
    public String labelA4;
    public String labelA5;

    public String getConfigureString(){
        String commandString = "AMP"; // Abort + Measure + Program

        if(this.compressed){
            commandString += "C1:"; // Compress True
        }else{
            commandString += "C0:"; // Compress False
        }

        //commandString += "D" + this.overflowCount + ":"; // Delay between measurements
        commandString += "D" + this.overflowCount + ":"; // Delay between measurements
        commandString += "S" + this.measureMask + ":"; // Selection mask (inputs)
        return  commandString;
    }

    public TestSequence(){
        this.unixTimestamp = System.currentTimeMillis() / 1000L;
    }

    public TestSequence(String testName){
        this.testName = testName;
        this.unixTimestamp = System.currentTimeMillis() / 1000L;

        this.labelA0 = "A0";
        this.labelA1 = "A1";
        this.labelA2 = "A2";
        this.labelA3 = "A3";
        this.labelA4 = "A4";
        this.labelA5 = "A5";
    }

    public List<Sample> getSamples() {
        return Sample.find(Sample.class, "test_sequence = ?", getId().toString());
    }

    public Double getSampleRate(){
        long duration = (this.finishTime - this.startTime);
        return (this.getSamples().size() / (duration * 0.000000001));
    }
}
