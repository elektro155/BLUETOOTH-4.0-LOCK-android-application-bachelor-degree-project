package com.dfrobot.angelo.blunobasicdemo;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Main2Activity extends AppCompatActivity {

    private Button buttonSave;
    private Switch switchShaking;
    private Switch switchPassword;
    private EditText passwordToSave; //password to be read or displayed
    private TextView deviceData;

    private PreferencesController settings;

    private Context thisContext=this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); //to disable rotation of screen

        settings = PreferencesController.getInstance(this);

        passwordToSave = (EditText) findViewById(R.id.serialSendText);            //initial the EditText of the password
        deviceData = (TextView) findViewById(R.id.deviceData);                    //data of connected device to be displayed here
        switchShaking = (Switch) findViewById(R.id.switchShaking);                //shaking on switch
        switchPassword = (Switch) findViewById(R.id.switchPassword);              //password remembering switch


        buttonSave = (Button) findViewById(R.id.buttonSaveSettings);                    //initial the button for scanning the BLE device
        buttonSave.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                 /*saving settings to shared preferences*/

                 if(switchShaking.isChecked()) settings.setShaking(true); else settings.setShaking(false);
                 if(switchPassword.isChecked()) settings.setSavingPassword(true); else settings.setSavingPassword(false);

                 if(passwordToSave.getText().toString().length()>8){
                     Toast.makeText(thisContext, "Password too long", Toast.LENGTH_SHORT).show();
                 }else if(passwordToSave.getText().toString().length()<1) {
                     Toast.makeText(thisContext, "Password too short", Toast.LENGTH_SHORT).show();
                 }else{
                     settings.setPassword(passwordToSave.getText().toString());
                     Toast.makeText(thisContext, "Settings have been saved", Toast.LENGTH_SHORT).show();
                     finish();
                 }

            }

        });

    }

    protected void onResume(){
        super.onResume();

        /*getting settings to be displayed*/

        if(settings.getShaking()) switchShaking.setChecked(true);
        if(settings.getSavingPassword()) switchPassword.setChecked(true);
        passwordToSave.setText(settings.getPassword());
        deviceData.setText("Chosen device:\nName: "
                + settings.getDeviceName() + "\nMAC: "
                + settings.getDeviceAddress());

    }

}
