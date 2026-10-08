package com.example.mitchellramirez2;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private EditText nameText;
    private TextView textGreeting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //initializing the views
        nameText = findViewById(R.id.nameText);
        textGreeting = findViewById(R.id.textGreeting);
    }

    public void SayHello(View view) {

        if (nameText.getText() != null && !nameText.getText().toString().isEmpty()) {
            String name = nameText.getText().toString();
            String greetingMessage = "Hello, " + name+ "!";
            textGreeting.setText(greetingMessage);
        } else {
            return;
        }
    }
}