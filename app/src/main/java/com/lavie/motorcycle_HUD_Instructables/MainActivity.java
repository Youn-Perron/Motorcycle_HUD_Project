package com.lavie.motorcycle_HUD_Instructables;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;

import com.mapbox.services.android.navigation.ui.v5.NavigationView;
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions;
import com.mapbox.services.android.navigation.ui.v5.OnNavigationReadyCallback;
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.offroute.OffRouteListener;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements PermissionsListener, OnNavigationReadyCallback,
        OffRouteListener, ProgressChangeListener, NavigationListener, OnMapReadyCallback, MapboxMap.OnMapClickListener {

    //  User Interaction
    private Button startButton;
    private Button connectBTButton;
    private Button closeBTButton;

    //  maps related
    private PermissionsManager permissionsManager;
    private NavigationView navigationView;
    private MapboxMap mapboxMap;
    private MapView mapView;
    LocationComponent locationComponent;
    //  Navigation related
    private DirectionsRoute directionsRoute;
    private NavigationMapRoute navigationMapRoute;
    private MapboxNavigation navigation;
    //  Origin & Destination points with defaults being Nantes, France -> Paris, France
    private Point ORIGIN = Point.fromLngLat(-1.5534, 47.2173);       // Nantes, France
    private Point DESTINATION = Point.fromLngLat(-2.2488, 48.8534);  // Paris, France

    static BluetoothManagement bluetoothManagement = new BluetoothManagement();

    public static Context context;
    private static int OnProgressChange_thirdCall = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();

        // Mapbox access token is configured here. This needs to be called either in your application object or in the same activity which contains the mapview
        Mapbox.getInstance(this, getString(R.string.access_token));
        // This contains the MapView in XML and needs to be called after the access token is configured.
        setContentView(R.layout.activity_main);

        mapView = (MapView) findViewById(R.id.mapView);
        startButton = findViewById(R.id.buttonStart);
        startButton.setEnabled(false);  // Cannot launch navigation before route has been fetched
        connectBTButton = findViewById(R.id.buttonConnectBT);
        connectBTButton.setEnabled(false); // Cannot attempt to connect before checking if bluetooth is on
        closeBTButton = findViewById(R.id.buttonCloseBT);
        closeBTButton.setEnabled(false);   // Cannot attempt to close bluetooth connection if it hasn't been established yet

        // Setup serial bluetooth management
        if (bluetoothManagement.setupBT_noErrors()){
            connectBTButton.setEnabled(true);
        }

        // Setup test buttons
        connectBTButton.setOnClickListener(v -> {
            if (bluetoothManagement.connectBT_noErrors()) {
                closeBTButton.setEnabled(true);
            }
        });
        closeBTButton.setOnClickListener(v -> {
            bluetoothManagement.closeBT();   //Close all Bluetooth devices
        });

        startButton.setOnClickListener(v -> {
            setTheme(R.style.Theme_AppCompat_Light_NoActionBar);
            setContentView(R.layout.activity_navigation);
            navigationView = findViewById(R.id.navigationView);
            navigationView.onCreate(savedInstanceState);
            navigationView.initialize(this);
        });
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        MainActivity.this.mapboxMap = mapboxMap;

        mapboxMap.setStyle(new Style.Builder().fromUri("mapbox://styles/mapbox/dark-v10"),
                new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        mapboxMap.addOnMapClickListener(MainActivity.this);
                        enableLocationComponent(style);
                    }
                });
    }

    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        Toast.makeText(this,"map click detected", Toast.LENGTH_LONG).show();
        DESTINATION = Point.fromLngLat(point.getLongitude(), point.getLatitude());
        fetchRoute();
        return true;
    }

    private void fetchRoute() {
        Toast.makeText(this, "Calculating route...", Toast.LENGTH_SHORT).show();

        // Set current ( or last known ) phone location as the origin of the route
        Location location = locationComponent.getLastKnownLocation();
        ORIGIN = Point.fromLngLat(location.getLongitude(), location.getLatitude());

        // build the navigation route from ORIGIN to DESTINATION
        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(ORIGIN)
                .destination(DESTINATION)
                .profile(DirectionsCriteria.PROFILE_DRIVING)
                .voiceUnits("metric")
                .language(Locale.ENGLISH)   // English required for REGEXs to function on ESP32
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        //  Checking if calculated route is valid
                        if (response.body() == null) {
                            Toast.makeText(getApplicationContext(), "couldn't fetch valid route, check API key/permissions", Toast.LENGTH_LONG).show();
                            return;
                        } else if (response.body().routes().size() < 1) {
                            Toast.makeText(getApplicationContext(), "No routes found.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        Toast.makeText(getApplicationContext(), "Valid route fetched", Toast.LENGTH_LONG).show();

                        directionsRoute = response.body().routes().get(0);

                        // Draw the route on the map
                        if (navigationMapRoute != null) {
                            navigationMapRoute.removeRoute();
                        } else {
                            navigationMapRoute = new NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute);
                            startButton.setBackgroundResource(R.color.colorPrimary);
                        }
                        navigationMapRoute.addRoute(directionsRoute);
                        startButton.setEnabled(true);
                }
                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                        Toast.makeText(getApplicationContext(), "Fatal error fetching route", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    /*
    NAVIGATION RELATED
    */
    @Override
    public void onNavigationReady(boolean isRunning) {
        startNavigation();
    }

    private void startNavigation() {
        if (directionsRoute == null) {
            return;
        }
        NavigationViewOptions options = NavigationViewOptions.builder()
                .directionsRoute(directionsRoute)
                .shouldSimulateRoute(true)
                .navigationListener(MainActivity.this)
                .progressChangeListener(this)
                .build();
        navigationView.startNavigation(options);
    }

    @Override
    public void onCancelNavigation() {
        navigationView.stopNavigation();
        finish();
    }

    @Override
    public void onNavigationFinished() {
        //do nothing
    }

    @Override
    public void onNavigationRunning() {
        //do nothing
    }

    /*
        LOCATION COMPONENT RELATED
     */
    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
        // Check if permissions are enabled and if not request them
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            locationComponent = mapboxMap.getLocationComponent();   // Get an instance of the component
            // Activate with options
            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, loadedMapStyle).build());
            locationComponent.setLocationComponentEnabled(true);    // Enable to make component visible
            locationComponent.setCameraMode(CameraMode.TRACKING);   // Set the component's camera mode
            locationComponent.setRenderMode(RenderMode.COMPASS);    // Set the component's render mode
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationComponent(style);
                }
            });
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    /*
        PROGRESS CHANGE ( GPS update ) : BLUETOOTH COMM WITH ESP32
     */
    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {
        String BTmsg = "";
        if (OnProgressChange_thirdCall < 3) {
            OnProgressChange_thirdCall ++;
            return;
        }
        OnProgressChange_thirdCall = 0;

        String nextManeuver = routeProgress.currentLegProgress().upComingStep().maneuver().instruction();
        int distToNextManeuver = (int) routeProgress.currentLegProgress().currentStepProgress().distanceRemaining();

        String arr[] = nextManeuver.split(" ");
        if (arr[0].equals("Turn")) {        // Turn instruction detected
            BTmsg = ":turn.";
            if (arr[1].equals("left"))      BTmsg = BTmsg + "left,";   // Left turn detected
            else if (arr[1].equals("right"))BTmsg = BTmsg + "right,";  // Right turn detected
            else                            BTmsg = BTmsg + "err,";    //Unknown case
            BTmsg = BTmsg + distToNextManeuver + ";\n";
            //Toast.makeText(this, BTmsg, Toast.LENGTH_SHORT).show();  // for testing purpose, display sent message
            if (bluetoothManagement.isBTSet) bluetoothManagement.sendMsg(BTmsg);
            else ;
            BTmsg = " ";
        }
    }

    /*
        OFF ROUTE RELATED : does nothing for now, will have to readjust current route if necessary in the future
     */
    @Override
    public void userOffRoute(Location location) {
        Toast.makeText(this, "offRoute", Toast.LENGTH_LONG).show();
    }

    /*
        LIFECYCLE RELATED OVERRIDES (NECESSARY)
     */
    @Override
    @SuppressWarnings( {"MissingPermission"})
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        navigationView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
        navigationView.onStop();
        bluetoothManagement.closeBT();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
        navigationView.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        navigationView.onDestroy();
        bluetoothManagement.closeBT();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
        navigationView.onLowMemory();
    }
}
