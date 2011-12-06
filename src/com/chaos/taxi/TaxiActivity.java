package com.chaos.taxi;

import org.json.JSONException;
import org.json.JSONObject;

import com.chaos.taxi.map.TaxiMapView;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class TaxiActivity extends MapActivity {
	private final static String TAG = "TaxiActivity";
	public static final int CALL_TAXI_REQUEST_CODE = 651238767;

	public static boolean sStarted = false;

	LocationManager mLocationManager = null;
	TaxiMapView mMapView = null;
	Button mCallTaxiBtn = null;
	Button mAutoLocateBtn = null;
	Button mLocateTaxiBtn = null;
	Button mFindTaxiBtn = null;
	boolean mHasTaxi = false;
	GeoPoint mTaxiPoint = null;

	LocationListener locationListener = new LocationListener() {
		public void onLocationChanged(Location location) {
			if (location != null) {
				Log.i(TAG, "Location changed : Lat: " + location.getLatitude()
						+ " Lng: " + location.getLongitude());
				GeoPoint point = getUserLastKnownGeoPoint();
				if (point == null) {
					Log.wtf(TAG, "point should not be null!");
					return;
				}
				RequestProcessor.setUserGeoPoint(point);
			}
		}

		public void onProviderDisabled(String provider) {
			Log.d(TAG, "provider: " + provider + " disabled!");
		}

		public void onProviderEnabled(String provider) {
			Log.d(TAG, "provider: " + provider + " enabled!");
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			Log.d(TAG, "provider: " + provider + " status: " + status);
		}
	};

	private GeoPoint getUserLastKnownGeoPoint() {
		Location gpsLocation = mLocationManager
				.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		if (gpsLocation != null) {
			Log.d(TAG, "gpsLocation is " + gpsLocation.getLatitude() + " "
					+ gpsLocation.getLongitude());
		}
		Location networkLocation = mLocationManager
				.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (networkLocation != null) {
			Log.d(TAG, "networkLocation is " + networkLocation.getLatitude()
					+ " " + networkLocation.getLongitude());
		}

		Location location = TaxiUtil.chooseBetterLocation(gpsLocation,
				networkLocation);

		if (location != null) {
			return TaxiUtil.locationToGeoPoint(location);
		} else {
			return null;
		}
	}

	private void setButtonListener() {
		mCallTaxiBtn = (Button) findViewById(R.id.call_taxi_btn);
		mAutoLocateBtn = (Button) findViewById(R.id.auto_locate_btn);
		mLocateTaxiBtn = (Button) findViewById(R.id.locate_taxi_btn);
		mFindTaxiBtn = (Button) findViewById(R.id.find_taxi_btn);

		// TODO: currently only support call specified taxi
		mCallTaxiBtn.setVisibility(View.INVISIBLE);
		mCallTaxiBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				RequestProcessor.callTaxi();
			}
		});

		mLocateTaxiBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				RequestProcessor.sendLocateTaxiRequest();
			}
		});

		mFindTaxiBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				RequestProcessor.sendFindTaxiRequest();
			}
		});

		mAutoLocateBtn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Log.d(TAG, "currentZoomLevel: " + mMapView.getZoomLevel());
				RequestProcessor.setUserGeoPoint(getUserLastKnownGeoPoint());
				RequestProcessor.sendLocateUserRequest();
			}
		});
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sStarted = true;
		setContentView(R.layout.main);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CALL_TAXI_REQUEST_CODE) {
			Log.d(TAG, "get call taxi result: " + resultCode);
			if (data == null) {
				Log.e(TAG, "call taxi ret data should not be null!");
				return;
			}
			int retCode = data.getIntExtra(WaitTaxiActivity.RET_CODE, -1);
			Log.d(TAG, "retCode is " + retCode);
			if (retCode == WaitTaxiActivity.CANCEL_WAIT) {
				RequestProcessor.cancelCallTaxiRequest();
			} else if (retCode == WaitTaxiActivity.SUCCEED_WAIT) {
				RequestProcessor.showCallTaxiSucceedDialog();
			} else if (retCode == WaitTaxiActivity.REJECT_WAIT) {
				AlertDialog dialog = new AlertDialog.Builder(TaxiActivity.this)
						.setIcon(android.R.drawable.ic_dialog_info)
						.setTitle("CallTaxiFail: ")
						.setMessage("Driver Reject!")
						.setNegativeButton("OK", null).create();
				dialog.show();
			} else if (retCode == WaitTaxiActivity.DRIVER_UNAVAILABLE) {
				AlertDialog dialog = new AlertDialog.Builder(TaxiActivity.this)
						.setIcon(android.R.drawable.ic_dialog_info)
						.setTitle("CallTaxiFail: ")
						.setMessage("Cannot contact the Driver!")
						.setNegativeButton("OK", null).create();
				dialog.show();
			} else {
				Log.e(TAG, "unrecognized retCode: " + retCode);
			}
		}
	}

	@Override
	public void onStart() {
		mMapView = (TaxiMapView) findViewById(R.id.google_map);
		mMapView.initTaxiMapView(this);

		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				30000, 0, locationListener);
		mLocationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER, 30000, 0, locationListener);

		RequestProcessor.initRequestProcessor(TaxiActivity.this, mMapView);
		handleLoginResponse();
		RequestProcessor.startSendRequestThread();

		setButtonListener();
		super.onStart();
	}

	private void handleLoginResponse() {
		if (RequestProcessor.mLoginResponse == null) {
			Log.w(TAG, "mLoginResponse is null!");
			return;
		}
		Log.d(TAG,
				"mLoginResponse is "
						+ RequestProcessor.mLoginResponse.toString());
		int state = RequestProcessor.mLoginResponse.optInt("state", -1);
		if (state == -1) {
			Log.w(TAG, "the login response should contain a state!");
			return;
		}
		if (state == 0) { // normal state
			return;
		}

		JSONObject driverJson = RequestProcessor.mLoginResponse
				.optJSONObject("driver");
		if (driverJson == null) {
			Log.e(TAG, "driverJson should not be null!");
			return;
		}

		try {
			String carNumber = driverJson.getString("car_number");
			String nickName = driverJson.getString("nickname");
			String phoneNumber = driverJson.getString("phone_number");
			int latitude = (int) (driverJson.optDouble("latitude", -1) * 1000000);
			int longitude = (int) (driverJson.optDouble("longitude", -1) * 1000000);

			GeoPoint point = null;
			if (latitude >= 0 && longitude >= 0) {
				point = new GeoPoint(latitude, longitude);
			}

			RequestProcessor.setMyTaxiParam(carNumber, nickName, phoneNumber,
					point);
			if (state == 1) { // calling taxi
				RequestProcessor.callTaxi(phoneNumber);
			} else if (state == 2) { // already has a taxi
				RequestProcessor.showCallTaxiSucceedDialog();
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		sStarted = false;
		mLocationManager.removeUpdates(locationListener);
		RequestProcessor.stopSendRequestThread();
		RequestProcessor.signout();
		super.onDestroy();
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
}