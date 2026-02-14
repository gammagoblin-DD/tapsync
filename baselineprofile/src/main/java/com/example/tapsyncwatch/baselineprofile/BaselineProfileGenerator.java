package com.example.tapsyncwatch.baselineprofile;

import androidx.benchmark.macro.junit4.BaselineProfileRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import kotlin.Unit;

@RunWith(AndroidJUnit4.class)
public class BaselineProfileGenerator {

    @Rule
    public BaselineProfileRule rule = new BaselineProfileRule();

    @Test
    public void generate() {
        rule.collect("com.example.tapsyncwatch", scope -> {
            scope.startActivityAndWait();
            return Unit.INSTANCE;
        });
    }
}
