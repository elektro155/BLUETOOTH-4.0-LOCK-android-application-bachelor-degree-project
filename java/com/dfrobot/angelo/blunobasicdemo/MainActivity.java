package com.dfrobot.angelo.blunobasicdemo;

import android.content.Context;
import android.os.Bundle;
import android.os.AsyncTask;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.hardware.SensorManager;
import android.widget.Toast;
import android.view.MenuItem;
import android.view.Menu;


public class MainActivity  extends BlunoLibrary implements ShakeDetector.Listener {
    private Button buttonScan;
    private Button buttonSerialSend;
    private Button buttonClose;
    private Button buttonClearLog;
    private TextView serialReceivedText;

    private final int DELAY =1000; //delay after disconnecting

    private boolean scanning = false;   //flag to indicate that there is connection just to choose device, to disable accidental opening
    private boolean timeElapsed = true; //true if time of last shaking elapsed and something was received
    private boolean isVisible = false;  //if the activity is visible
    private boolean connecting = false; //true if application is attempting to connect to bluno
    private boolean toastHasBeenShown = false; //flag that toast about not enabled shaking was shown
    private boolean waitingToDisconnect = false; //it true, the application is in process of disconnecting

    private PreferencesController settings;

    private Context thisContext = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = PreferencesController.getInstance(this);

        onCreateProcess();                                                      //onCreate Process by BlunoLibrary
        serialBegin(115200);                                                    //set the Uart Baudrate on BLE chip to 115200

        serialReceivedText = (TextView) findViewById(R.id.serialReveicedText);    //initial the EditText of the received data

        /////////////////////////////////////////////button handlers//////////////////////////////////////////////////////////

        buttonSerialSend = (Button) findViewById(R.id.buttonSerialSend);        //initial the button for sending the data
        buttonSerialSend.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                if(!waitingToDisconnect) { //wait until the application disconnects completely
                    waitingToDisconnect = true; //disable calling this until the connection is finished
                    onClickInActivity(); //connect if not connected
                    scanning = false;
                    connecting = true; //app is trying to connect
                }
            }
        });

        buttonScan = (Button) findViewById(R.id.buttonScan);                    //initial the button for scanning the BLE device
        buttonScan.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                //if(!waitingToDisconnect) { //wait until the application disconnects completely
                    waitingToDisconnect = true; //disable open button and shaking
                    buttonScanOnClickProcess();                                        //Alert Dialog for selecting the BLE device
                    scanning = true; //flag to indicate that scanning is on
                //}
            }
        });

        buttonClose = (Button) findViewById(R.id.buttonClose);                    //initial the button for closing the activity
        buttonClose.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                Toast.makeText(thisContext, "App has been closed", Toast.LENGTH_SHORT).show();
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);
            }
        });

        buttonClearLog = (Button) findViewById(R.id.buttonClearLog);               //initial the button for clearing activity log
        buttonClearLog.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {

                serialReceivedText.setText(null);
            }
        });

        initShakeEventManager();
    }

    protected void onResume(){
        super.onResume();
        System.out.println("BlUNOActivity onResume");
        onResumeProcess();														//onResume Process by BlunoLibrary
        isVisible = true;
        toastHasBeenShown = false; //resetting the flag to enable showing the toast again
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        onActivityResultProcess(requestCode, resultCode, data);					//onActivityResult Process by BlunoLibrary
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        onPauseProcess();														//onPause Process by BlunoLibrary
        isVisible = false;
    }

    protected void onStop() {
        super.onStop();
        onStopProcess();														//onStop Process by BlunoLibrary
        if(!settings.getSavingPassword()){
            settings.setPassword("password"); //set default password if the password is set not to be saved
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onDestroyProcess();														//onDestroy Process by BlunoLibrary
        shakeDetector.stop();
    }

    @Override
    public void onConectionStateChange(connectionStateEnum theConnectionState) {//Once connection state changes, this function will be called
        switch (theConnectionState) {											//Four connection state
            case isConnected:
                buttonScan.setText("Connected");
                connecting = false; //app has connected to the bluno successfully
                if (!scanning) { //to prevent opening when the connection is established just to choose device
                    serialSend("pswd:"+settings.getPassword());
                    scanning = false;
                }
                break;
            case isConnecting:
                buttonScan.setText("Connecting");
                break;
            case isToScan:
                buttonScan.setText("Change Device");

                if(connecting) { //connecting has failed
                    serialReceivedText.append("CONNECTING FAILED\n");
                    timeElapsed = true; //this flag is set here in case the application failed to connect and is unable to receive anything
                    //to enable displaying message that shaking was activated ("OPENING BY SHAKING\n")
                    if(waitingToDisconnect){ //reset the flag if something went wrong (connecting failed)
                        new Waiting().execute(""); //waiting given delay then reset the waitingToDisconnect flag
                    }
                }

                if(waitingToDisconnect && scanning){ //enable shaking and open button after scanning
                    new Waiting().execute(""); //waiting given delay then reset the waitingToDisconnect flag
                }

                connecting = false;
                break;
            case isScanning:
                buttonScan.setText("Scanning");
                break;
            case isDisconnecting:
                //buttonScan.setText("isDisconnecting"); //commented to prevent the text flashing due to rapid changes of text
                break;
            default:
                break;
        }
    }

    @Override
    public void onSerialReceived(String theString) {							//Once connection data received, this function will be called
        serialReceivedText.append(theString);							        //append the text into the EditText
        timeElapsed = true; //enable message for shaking
        //The Serial data from the BLUNO may be sub-packaged, so using a buffer to hold the String is a good choice.
        ((ScrollView)serialReceivedText.getParent()).fullScroll(View.FOCUS_DOWN);

        waitingToDisconnect = true;

        try {
            synchronized (this) {
                wait(200); //wait just to let the bluno send data
            }
        } catch (InterruptedException e) {
            //nothing
        } finally {
            disconnectInActivity(); //disconnect when the acknowledgment of opening or wrong password was sent
        }

        new Waiting().execute(""); //waiting given delay then reset the waitingToDisconnect flag to unlock shaking and open button

    }

    private class Waiting extends AsyncTask<String, Void, String> {  //async task for waiting to reset the waitingToDisconnect flag

        @Override
        protected String doInBackground(String... params) {
            try {
                synchronized (this) {
                    wait(DELAY); //wait just to let bluno disconnect completely
                }
            } catch (InterruptedException e) {
                //nothing
            }
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
            waitingToDisconnect = false; //enable open button and shaking
        }

        @Override
        protected void onPreExecute() {
            waitingToDisconnect = true; //disable open button and shaking
        }

        @Override
        protected void onProgressUpdate(Void... values) {}
    }

    ////////////////////////////////////CREATING SETTINGS MENU////////////////////////////////////////////

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                startActivity(new Intent(thisContext, Main2Activity.class));
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    //////////////////////////////////////////SHAKER///////////////////////////////////////////////////////////
    private ShakeDetector shakeDetector;

    private void initShakeEventManager() {
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        shakeDetector = new ShakeDetector(this);
        shakeDetector.start(sensorManager);
    }

    @Override
    public void hearShake(){  //shaking
        if(settings.getShaking()) { //open by shaking if it was enabled in settings
            if(!waitingToDisconnect) { //wait until the application disconnects completely
                waitingToDisconnect = true; //disable calling this until the connection is finished
                if (timeElapsed && settings.getDeviceAddress().length() > 0) {  //do no let call this block unless
                    serialReceivedText.append("OPENING BY SHAKING\n");          //the response was received or there is device
                    timeElapsed = false;
                }
                onClickInActivity(); //connect if not connected
                scanning = false;
                connecting = true; //app is trying to connect
            }
        }else{
            if(isVisible) {  //show the toast only if activity is visible
                if(!toastHasBeenShown) { //show this toast only once for turned on activity instance
                    toastHasBeenShown = true; //disable the toast until getting back to activity
                    Toast.makeText(thisContext, "Shaking not enabled", Toast.LENGTH_SHORT).show();
                }

            }

        }

    }

}

