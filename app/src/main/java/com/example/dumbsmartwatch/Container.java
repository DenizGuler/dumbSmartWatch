package com.example.dumbsmartwatch;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import com.example.dumbsmartwatch.Models.Connect;

public class Container extends AppCompatActivity {

    final String watchAddress = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Connect.toWatch();


    }
}
