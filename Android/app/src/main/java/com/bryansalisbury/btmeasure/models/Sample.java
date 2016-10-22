package com.bryansalisbury.btmeasure.models;

import com.orm.SugarRecord;

public class Sample extends SugarRecord {
    public long timestamp;
    public String input;
    public int value;

    public TestSequence testSequence;

    public Sample(){
    }

    public Sample(String input, int value, TestSequence testSequence){
        //this.timestamp = System.nanoTime();
        this.testSequence = testSequence;
        this.input = input;
        this.value = value;
    }

    public Sample(String input, int value, long timestamp, TestSequence testSequence){
        //this.timestamp = timestamp;
        this.input = input;
        this.value = value;
    }

    public double getVolts(){
        return (this.value / 1023.0) * (5.0);
    }

    public double getVolts(double aRef){
        return (this.value / 1023.0) * (aRef);
    }
}
