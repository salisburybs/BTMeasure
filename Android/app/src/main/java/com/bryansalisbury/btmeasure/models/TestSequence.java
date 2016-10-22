package com.bryansalisbury.btmeasure.models;
import com.orm.SugarRecord;

public class TestSequence extends SugarRecord {
    public String testName;
    public long timestamp;

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

    public String getConfigureString() {
        String commandString = "AMP"; // Abort + Measure + Program

        if (this.compressed) {
            commandString += "C1:"; // Compress True
        } else {
            commandString += "C0:"; // Compress False
        }

        //commandString += "D" + this.overflowCount + ":"; // Delay between measurements
        commandString += "D" + this.overflowCount + ":"; // Delay between measurements
        commandString += "S" + this.measureMask + ":"; // Selection mask (inputs)
        return commandString;
    }

    public TestSequence() { }

    public TestSequence(String testName) {
        this.testName = testName;
        this.timestamp = System.currentTimeMillis();

        this.labelA0 = "A0";
        this.labelA1 = "A1";
        this.labelA2 = "A2";
        this.labelA3 = "A3";
        this.labelA4 = "A4";
        this.labelA5 = "A5";
    }

    public double getTImeDelta(){
        return (65535-this.overflowCount) / 250000.0;
    }
}
