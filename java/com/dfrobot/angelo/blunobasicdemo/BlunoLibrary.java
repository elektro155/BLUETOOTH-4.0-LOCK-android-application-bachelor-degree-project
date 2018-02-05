package com.dfrobot.angelo.blunobasicdemo;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import android.os.IBinder;
import android.annotation.SuppressLint;
import android.support.v7.app.AppCompatActivity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

public abstract class BlunoLibrary  extends AppCompatActivity{

	private Context mainContext=this;

	private PreferencesController settings;

    private boolean changingDevice = false; //variable that indicates that "change device" button was pressed

	public abstract void onConectionStateChange(connectionStateEnum theconnectionStateEnum);
	public abstract void onSerialReceived(String theString);
	public void serialSend(String theString){
		if (mConnectionState == connectionStateEnum.isConnected) {
			mSCharacteristic.setValue(theString);
			mBluetoothLeService.writeCharacteristic(mSCharacteristic);
		}
	}

	private int mBaudrate=115200;	//set the default baud rate to 115200
	private String mPassword="AT+PASSWOR=DFRobot\r\n";

	private String mBaudrateBuffer = "AT+CURRUART="+mBaudrate+"\r\n";

	public void serialBegin(int baud){
		mBaudrate=baud;
		mBaudrateBuffer = "AT+CURRUART="+mBaudrate+"\r\n";
	}

	static class ViewHolder {
		TextView deviceName;
		TextView deviceAddress;
	}

	private static BluetoothGattCharacteristic mSCharacteristic, mModelNumberCharacteristic, mSerialPortCharacteristic, mCommandCharacteristic;
	BluetoothLeService mBluetoothLeService;
	private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
			new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
	private LeDeviceListAdapter mLeDeviceListAdapter=null;
	private BluetoothAdapter mBluetoothAdapter;
	private boolean mScanning =false;
	AlertDialog mScanDeviceDialog;
	private String mDeviceName;
	private String mDeviceAddress;
	public enum connectionStateEnum{isNull, isScanning, isToScan, isConnecting , isConnected, isDisconnecting}
	public connectionStateEnum mConnectionState = connectionStateEnum.isNull;
	private static final int REQUEST_ENABLE_BT = 1;

	protected String deviceAdressToSave=null; //string for saving address of chosen device

	private Handler mHandler= new Handler();

	public boolean mConnected = false;

	private final static String TAG = BlunoLibrary.class.getSimpleName();

	private Runnable mConnectingOverTimeRunnable=new Runnable(){

		@Override
		public void run() {
			if(mConnectionState==connectionStateEnum.isConnecting)
				mConnectionState=connectionStateEnum.isToScan;
			onConectionStateChange(mConnectionState);
			mBluetoothLeService.close();
		}};

	private Runnable mDisonnectingOverTimeRunnable=new Runnable(){

		@Override
		public void run() {
			if(mConnectionState==connectionStateEnum.isDisconnecting)
				mConnectionState=connectionStateEnum.isToScan;
			onConectionStateChange(mConnectionState);
			mBluetoothLeService.close();
		}};

	public static final String SerialPortUUID="0000dfb1-0000-1000-8000-00805f9b34fb";
	public static final String CommandUUID="0000dfb2-0000-1000-8000-00805f9b34fb";
	public static final String ModelNumberStringUUID="00002a24-0000-1000-8000-00805f9b34fb";

	////////////////////////////////////////scanning without list//////////////////////////////////////////////

	public void onClickInActivity(){
		changingDevice = false;  //false because the button "change device" was not pressed
		scanLeDevice(true); //it must be here to provide proper connection
		if(deviceAdressToSave==null&&settings.getDeviceAddress().length()>0) { //get address if there was something saved and app started
			deviceAdressToSave = settings.getDeviceAddress(); //it is so that the connection could be established to the saved device
		}
		if(deviceAdressToSave!=null&&(mConnectionState==connectionStateEnum.isToScan||mConnectionState==connectionStateEnum.isNull)) {
			/*start scanning if application is disconnected*/
			scanLeDevice(false); //it must be here to provide proper connection

			if (mBluetoothLeService.connect(deviceAdressToSave)) {
				Log.d(TAG, "Connect request success");
				mConnectionState = connectionStateEnum.isConnecting;
				onConectionStateChange(mConnectionState);
				mHandler.postDelayed(mConnectingOverTimeRunnable, 10000);
			} else {
				Log.d(TAG, "Connect request fail");
				mConnectionState = connectionStateEnum.isToScan;
				onConectionStateChange(mConnectionState);
			}
		}
		scanLeDevice(false); //it must be here to provide proper connection
		if(deviceAdressToSave==null){
			Toast.makeText(mainContext, "Choose the device", Toast.LENGTH_SHORT).show();
		}
	}

	///////////////////////////////disconnecting when the message was received//////////////////////////////////

	public void disconnectInActivity(){
		if(mConnectionState==connectionStateEnum.isConnected) {
			mBluetoothLeService.disconnect();
			mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
			mConnectionState = connectionStateEnum.isDisconnecting;
			onConectionStateChange(mConnectionState);
		}
	}

	public void onCreateProcess()
	{
		settings = PreferencesController.getInstance(this);

		if(!initiate())
		{
			Toast.makeText(mainContext, R.string.error_bluetooth_not_supported,
					Toast.LENGTH_SHORT).show();
			(( AppCompatActivity) mainContext).finish();
		}


		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

		// Initializes list view adapter.
		mLeDeviceListAdapter = new LeDeviceListAdapter();
		// Initializes and show the scan Device Dialog
		mScanDeviceDialog = new AlertDialog.Builder(mainContext)
				.setTitle("BLE Device Scan...").setAdapter(mLeDeviceListAdapter, new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						final BluetoothDevice device = mLeDeviceListAdapter.getDevice(which);
						if (device == null)
							return;
						scanLeDevice(false);

						if(device.getName()==null || device.getAddress()==null)
						{
							mConnectionState=connectionStateEnum.isToScan;
							onConectionStateChange(mConnectionState);
						}
						else{ //if device is ok

							System.out.println("onListItemClick " + device.getName().toString());

							System.out.println("Device Name:"+device.getName() + "   " + "Device Name:" + device.getAddress());

							mDeviceName=device.getName().toString();
							mDeviceAddress=device.getAddress().toString();

							//////////////////MAC and name of device are saved automatically////////////////////////
							settings.setDeviceName(mDeviceName);  //saving name to preferences controller
							settings.setDeviceAddress(mDeviceAddress); //saving address


							if (mBluetoothLeService.connect(mDeviceAddress)) {
								Log.d(TAG, "Connect request success");
								mConnectionState=connectionStateEnum.isConnecting;
								onConectionStateChange(mConnectionState);
								mHandler.postDelayed(mConnectingOverTimeRunnable, 10000);
							}
							else {
								Log.d(TAG, "Connect request fail");
								mConnectionState=connectionStateEnum.isToScan;
								onConectionStateChange(mConnectionState);
							}
						}
					}
				})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {

					@Override
					public void onCancel(DialogInterface arg0) {
						System.out.println("mBluetoothAdapter.stopLeScan");

						mConnectionState = connectionStateEnum.isToScan;
						onConectionStateChange(mConnectionState);
						mScanDeviceDialog.dismiss();

						scanLeDevice(false);
					}
				}).create();

	}



	public void onResumeProcess() {
		System.out.println("BlUNOActivity onResume");
		// Ensures Bluetooth is enabled on the device. If Bluetooth is not
		// currently enabled,
		// fire an intent to display a dialog asking the user to grant
		// permission to enable it.
		if (!mBluetoothAdapter.isEnabled()) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				(( AppCompatActivity) mainContext).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			}
		}

		mainContext.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}


	public void onPauseProcess() {
		System.out.println("BLUNOActivity onPause");

	}


	public void onStopProcess() {
		System.out.println("MiUnoActivity onStop");

	}

	public void onDestroyProcess() {
		scanLeDevice(false);
		mainContext.unregisterReceiver(mGattUpdateReceiver);
		mLeDeviceListAdapter.clear();
		mConnectionState=connectionStateEnum.isToScan;
		mScanDeviceDialog.dismiss();
		if(mBluetoothLeService!=null)
		{
			mBluetoothLeService.disconnect();
			mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
			mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
			mBluetoothLeService.close();

		}
		mSCharacteristic=null;

		mainContext.unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	public void onActivityResultProcess(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_ENABLE_BT
				&& resultCode ==  AppCompatActivity.RESULT_CANCELED) {
			(( AppCompatActivity) mainContext).finish();
			return;
		}
	}

	boolean initiate()
	{
		// Use this check to determine whether BLE is supported on the device.
		// Then you can
		// selectively disable BLE-related features.
		if (!mainContext.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			return false;
		}

		// Initializes a Bluetooth adapter. For API level 18 and above, get a
		// reference to
		// BluetoothAdapter through BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) mainContext.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();

		// Checks if Bluetooth is supported on the device.
		if (mBluetoothAdapter == null) {
			return false;
		}
		return true;
	}

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
	//                        or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@SuppressLint("DefaultLocale")
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			System.out.println("mGattUpdateReceiver->onReceive->action="+action);
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				mConnected = true;
				mHandler.removeCallbacks(mConnectingOverTimeRunnable);

			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				mConnected = false;
				mConnectionState = connectionStateEnum.isToScan;
				onConectionStateChange(mConnectionState);
				mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
				mBluetoothLeService.close();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				// Show all the supported services and characteristics on the user interface.
				for (BluetoothGattService gattService : mBluetoothLeService.getSupportedGattServices()) {
					System.out.println("ACTION_GATT_SERVICES_DISCOVERED  "+
							gattService.getUuid().toString());
				}
				getGattServices(mBluetoothLeService.getSupportedGattServices());
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				if(mSCharacteristic==mModelNumberCharacteristic)
				{
					if (intent.getStringExtra(BluetoothLeService.EXTRA_DATA).toUpperCase().startsWith("DF BLUNO")) {
						mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, false);
						mSCharacteristic=mCommandCharacteristic;
						mSCharacteristic.setValue(mPassword);
						mBluetoothLeService.writeCharacteristic(mSCharacteristic);
						mSCharacteristic.setValue(mBaudrateBuffer);
						mBluetoothLeService.writeCharacteristic(mSCharacteristic);
						mSCharacteristic=mSerialPortCharacteristic;
						mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);
						mConnectionState = connectionStateEnum.isConnected;
						onConectionStateChange(mConnectionState);

						if(changingDevice){ // if the "change device" button was pressed then display this toast
                            Toast.makeText(mainContext, "Device has been changed", Toast.LENGTH_SHORT).show();
                            changingDevice = false;
							try {
								synchronized (this) {
									wait(300); //wait just to let the bluno do what it is supposed to do
								}
							} catch (InterruptedException e) {
								//nothing
							} finally {
								disconnectInActivity(); //disconnect when the device was chosen successfully
							}
                        }
					}
					else {
						Toast.makeText(mainContext, "Please select DFRobot devices",Toast.LENGTH_SHORT).show();
						mConnectionState = connectionStateEnum.isToScan;
						onConectionStateChange(mConnectionState);
					}
				}
				else if (mSCharacteristic==mSerialPortCharacteristic) {
					onSerialReceived(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
				}


				System.out.println("displayData "+intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
			}
		}
	};

	void buttonScanOnClickProcess()
	{
        changingDevice = true;  //true because the button "change device" was pressed
		switch (mConnectionState) {
			case isNull:
				mConnectionState=connectionStateEnum.isScanning;
				onConectionStateChange(mConnectionState);
				scanLeDevice(true);
				mScanDeviceDialog.show();
				break;
			case isToScan:
				mConnectionState=connectionStateEnum.isScanning;
				onConectionStateChange(mConnectionState);
				scanLeDevice(true);
				mScanDeviceDialog.show();
				break;
			case isScanning:

				break;
			case isConnecting:

				break;
			case isConnected:
				mBluetoothLeService.disconnect();
				mHandler.postDelayed(mDisonnectingOverTimeRunnable, 10000);
				mConnectionState=connectionStateEnum.isDisconnecting;
				onConectionStateChange(mConnectionState);
				break;
			case isDisconnecting:

				break;

			default:
				break;
		}

	}

	void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.

			System.out.println("mBluetoothAdapter.startLeScan");

			if(mLeDeviceListAdapter != null)
			{
				mLeDeviceListAdapter.clear();
				mLeDeviceListAdapter.notifyDataSetChanged();
			}

			if(!mScanning)
			{
				mScanning = true;
				mBluetoothAdapter.startLeScan(mLeScanCallback);
			}
		} else {
			if(mScanning)
			{
				mScanning = false;
				mBluetoothAdapter.stopLeScan(mLeScanCallback);
			}
		}
	}

	// Code to manage Service lifecycle.
	ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			System.out.println("mServiceConnection onServiceConnected");
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				(( AppCompatActivity) mainContext).finish();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			System.out.println("mServiceConnection onServiceDisconnected");
			mBluetoothLeService = null;
		}
	};

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, int rssi,
							 byte[] scanRecord) {
			(( AppCompatActivity) mainContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					System.out.println("mLeScanCallback onLeScan run ");
					mLeDeviceListAdapter.addDevice(device);
					mLeDeviceListAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	private void getGattServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null) return;
		String uuid = null;
		mModelNumberCharacteristic=null;
		mSerialPortCharacteristic=null;
		mCommandCharacteristic=null;
		mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices) {
			uuid = gattService.getUuid().toString();
			System.out.println("displayGattServices + uuid="+uuid);

			List<BluetoothGattCharacteristic> gattCharacteristics =
					gattService.getCharacteristics();
			ArrayList<BluetoothGattCharacteristic> charas =
					new ArrayList<BluetoothGattCharacteristic>();

			// Loops through available Characteristics.
			for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
				charas.add(gattCharacteristic);
				uuid = gattCharacteristic.getUuid().toString();
				if(uuid.equals(ModelNumberStringUUID)){
					mModelNumberCharacteristic=gattCharacteristic;
					System.out.println("mModelNumberCharacteristic  "+mModelNumberCharacteristic.getUuid().toString());
				}
				else if(uuid.equals(SerialPortUUID)){
					mSerialPortCharacteristic = gattCharacteristic;
					System.out.println("mSerialPortCharacteristic  "+mSerialPortCharacteristic.getUuid().toString());
//                    updateConnectionState(R.string.comm_establish);
				}
				else if(uuid.equals(CommandUUID)){
					mCommandCharacteristic = gattCharacteristic;
					System.out.println("mSerialPortCharacteristic  "+mSerialPortCharacteristic.getUuid().toString());
//                    updateConnectionState(R.string.comm_establish);
				}
			}
			mGattCharacteristics.add(charas);
		}

		if (mModelNumberCharacteristic==null || mSerialPortCharacteristic==null || mCommandCharacteristic==null) {
			Toast.makeText(mainContext, "Please select DFRobot devices",Toast.LENGTH_SHORT).show();
			mConnectionState = connectionStateEnum.isToScan;
			onConectionStateChange(mConnectionState);
		}
		else {
			mSCharacteristic=mModelNumberCharacteristic;
			mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);
			mBluetoothLeService.readCharacteristic(mSCharacteristic);
		}

	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}

	private class LeDeviceListAdapter extends BaseAdapter {
		private ArrayList<BluetoothDevice> mLeDevices;
		private LayoutInflater mInflator;

		public LeDeviceListAdapter() {
			super();
			mLeDevices = new ArrayList<BluetoothDevice>();
			mInflator =  (( AppCompatActivity) mainContext).getLayoutInflater();
		}

		public void addDevice(BluetoothDevice device) {
			if (!mLeDevices.contains(device)) {
				mLeDevices.add(device);
			}
		}

		public BluetoothDevice getDevice(int position) {
			return mLeDevices.get(position);
		}

		public void clear() {
			mLeDevices.clear();
		}

		@Override
		public int getCount() {
			return mLeDevices.size();
		}

		@Override
		public Object getItem(int i) {
			return mLeDevices.get(i);
		}

		@Override
		public long getItemId(int i) {
			return i;
		}

		@Override
		public View getView(int i, View view, ViewGroup viewGroup) {
			ViewHolder viewHolder;
			// General ListView optimization code.
			if (view == null) {
				view = mInflator.inflate(R.layout.listitem_device, null);
				viewHolder = new ViewHolder();
				viewHolder.deviceAddress = (TextView) view
						.findViewById(R.id.device_address);
				viewHolder.deviceName = (TextView) view
						.findViewById(R.id.device_name);
				System.out.println("mInflator.inflate  getView");
				view.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) view.getTag();
			}

			BluetoothDevice device = mLeDevices.get(i);
			final String deviceName = device.getName();
			if (deviceName != null && deviceName.length() > 0)
				viewHolder.deviceName.setText(deviceName);
			else
				viewHolder.deviceName.setText(R.string.unknown_device);
			viewHolder.deviceAddress.setText(device.getAddress());

			return view;

		}

	}

}
