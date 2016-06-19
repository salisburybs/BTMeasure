package com.bryansalisbury.btmeasure.models;

import com.orm.SugarRecord;

public class Sample extends SugarRecord {
    long unixTimestamp;
    String input;
    int value;

    TestSequence testSequence;

    public Sample(){
    }

    public Sample(String input, int value){
        this.unixTimestamp = System.currentTimeMillis() / 1000L;
        this.input = input;
        this.value = value;
    }

    public Sample(String input, int value, long unixTimestamp){
        this.unixTimestamp = unixTimestamp;
        this.input = input;
        this.value = value;
    }
}
