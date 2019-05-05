package uqac.dim.neigepal;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.GeoDataClient;
import com.google.android.gms.location.places.PlaceDetectionClient;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, NavigationView.OnNavigationItemSelectedListener {

    private static final String OPEN_WEATHER_MAP_URL =
            "http://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&units=metric&lang=fr";
    private static final String OPEN_WEATHER_MAP_API = "65af5df7d093e9b907ca2fc2eff56b22";
    // Des valeurs permettant de sauvegarder la position de la caméra et la localisation quand on met en pause l'appli
    private static final String KEY_CAMERA_POSITION = "camera_position";
    private static final String KEY_LOCATION = "location";
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int MY_PERMISSIONS_REQUEST_SEND_SMS = 0;
    // Une localisation par défaut (Sydney, Australie), au cas où la localisation est refusée par l'utilisateur
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private GoogleMap mMap;
    private CameraPosition mCameraPosition;
    // L'API de détection des Places, détermine la position d'un utilisateur par rapport à un lieu connu
    private GeoDataClient mGeoDataClient;
    private PlaceDetectionClient mPlaceDetectionClient;
    // Le fournisseur de localisation de Google
    private FusedLocationProviderClient mFusedLocationProviderClient;
    // La dernière localisation connue du téléphone
    private Location mLastKnownLocation;
    //Détermine si la localisation est permise
    private boolean mLocationPermissionGranted;

    private TextView villefield,
            temperaturefield,
            neigefield,
            description;
    private placeIdTask asyncTask;

    public static JSONObject getWeatherJSON(String lat, String lon) {
        try {
            URL url = new URL(String.format(OPEN_WEATHER_MAP_URL, lat, lon));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.addRequestProperty("x-api-key", OPEN_WEATHER_MAP_API);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            StringBuffer json = new StringBuffer(1024);
            String tmp;
            while ((tmp = reader.readLine()) != null)
                json.append(tmp).append("\n");
            reader.close();

            JSONObject data = new JSONObject(json.toString());

            // Si cod == 404, alors la requête n'est pas passée
            if (data.getInt("cod") != 200) return null;

            return data;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION);
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION);
        }


        setContentView(R.layout.activity_maps);

        FrameLayout meteo = findViewById(R.id.meteo);

        Button button = findViewById(R.id.button);


        mGeoDataClient = Places.getGeoDataClient(this);

        mPlaceDetectionClient = Places.getPlaceDetectionClient(this);

        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);


        // Construction de la map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);

        navigationView.setNavigationItemSelectedListener(this);

    }


    public void insertStations(Context context) {

        String json;

        try {
            InputStream is = context.getAssets().open("stations_ski_tbl2.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);


            JSONObject fich = new JSONObject(json);

            JSONArray stations = fich.getJSONArray("stations");

            for (int i = 0; i < stations.length(); i++) {
                JSONObject station = stations.getJSONObject(i);
                JSONObject coord = station.getJSONObject("coordonnees");

                //Log.d("CHECK", coord.toString());
                mMap.addMarker(new MarkerOptions()
                        .position(new LatLng(coord.getDouble("latitude"), coord.getDouble("longitude")))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        .title(station.getString("nom")));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    public void meteo(View v) {

        villefield = findViewById(R.id.ville);
        temperaturefield = findViewById(R.id.temperature);
        neigefield = findViewById(R.id.neige);
        description = findViewById(R.id.description);
        //updatedfield.setText(output2);
        //detailsfield.setText(output4);
        //humidityfield.setText(String.format(" Humidité : %s", output5));
        //pressurefield.setText(String.format("Pression : %s", output6));

        //updatedfield.setText(output2);
        //detailsfield.setText(output4);
        //humidityfield.setText(String.format(" Humidité : %s", output5));
        //pressurefield.setText(String.format("Pression : %s", output6));
        asyncTask = new placeIdTask(new AsyncResponse() {
            @Override
            public void processFinish(String output1, String output2, String output3, String output4) {
                villefield.setText(output1);
                //updatedfield.setText(output2);
                temperaturefield.setText(output2);
                neigefield.setText(output3);
                description.setText(output4);
                //detailsfield.setText(output4);
                //humidityfield.setText(String.format(" Humidité : %s", output5));
                //pressurefield.setText(String.format("Pression : %s", output6));
            }
        });

        asyncTask.execute(String.valueOf(mLastKnownLocation.getLatitude()), String.valueOf(mLastKnownLocation.getLongitude()));
    }


    //Après ce point, beaucoup d'expérimentations

    /**
     * Permet de manipuler la map une fois générée sur l'écran.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Demande la permission d'utiliser le GPS.
        getLocationPermission();

        // Met à jour l'interface utilisateur avec un bouton permettant de centrer la map.
        updateLocationUI();

        // Obtient la position de l'appareil.
        mLastKnownLocation = getDeviceLocation();

        insertStations(this);

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                asyncTask = new placeIdTask(new AsyncResponse() {
                    @Override
                    public void processFinish(String output1, String output2, String output3, String output4) {
                        villefield.setText(output1);
                        //updatedfield.setText(output2);
                        temperaturefield.setText(output2);
                        neigefield.setText(output3);
                        description.setText(output4);
                        //detailsfield.setText(output4);
                        //humidityfield.setText(String.format(" Humidité : %s", output5));
                        //pressurefield.setText(String.format("Pression : %s", output6));
                    }
                });
                asyncTask.execute(String.valueOf(marker.getPosition().latitude), String.valueOf(marker.getPosition().longitude));
                return true;

            }
        });

    }

    /**
     * Sauvegarde l'état de l'application quand elle est en pause
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition());
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation);
            super.onSaveInstanceState(outState);
        }
    }

    /**
     * Obtient la position de l'appareil et déplace la caméra
     */
    private Location getDeviceLocation() {

        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Si la localisation a été trouvée
                            mLastKnownLocation = task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                        } else {
                            Log.d("DIM", "Localisation introuvable.");
                            Log.e("DIM", "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });

            }


        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
        return mLastKnownLocation;
    }

    /**
     * Demande à l'utilisateur l'autorisation d'utiliser la localisation
     */
    private void getLocationPermission() {

        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        switch (id) {
            case R.id.nav_home:
                coord();
                break;
            case R.id.nav_config_urgence:
                urgence();
                break;
            default:
                return false;
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;


    }

    public void coord() {

        final AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Entrer des coordonnées");
        alert.setMessage("Entrez une latitude et une longitude avec des points, séparées par une virgule, et sans espaces au début ni à la fin de chaque valeur");

// Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

            }
        });

        alert.setNegativeButton("Annuler", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
                dialog.cancel();
            }
        });
        final AlertDialog dialog = alert.create();
        dialog.show();
        //alert.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String coord = input.getText().toString();
                if (coord.isEmpty() | coord == null) {

                } else {
                    String[] coords = coord.split(",");
                    for (String s : coords) s.trim();
                    LatLng coordinate = new LatLng(Double.parseDouble(coords[0]), Double.parseDouble(coords[1]));
                    mMap.addMarker(new MarkerOptions().position(coordinate).title("Votre position"));
                    dialog.dismiss();
                }


            }
        });

    }

    public void urgence() {
        final AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle("Entrez un numéro d'urgence");
        alert.setMessage("Entrez le numéro de quelqu'un à qui envoyer vos coordonnées en cas d'urgence");

// Set an EditText view to get user input
        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

            }
        });

        alert.setNegativeButton("Annuler", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // Canceled.
                dialog.cancel();
            }
        });
        final AlertDialog dialog = alert.create();
        dialog.show();
        //alert.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String num = input.getText().toString();
                if (num.isEmpty() | num == null) {

                } else {
                    if (ContextCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        SmsManager sm = SmsManager.getDefault();

                        String message =
                                "A l'aide ! Je suis en danger et me trouve actuellement à ces coordonnées : " + mLastKnownLocation.getLatitude() + ", " + mLastKnownLocation.getLongitude();
                        //"Comme tu m'as pas demandé, et que de toute façon c'est pas toi qui décide, voici mes coordonnées: " + + mLastKnownLocation.getLatitude() + ", " + mLastKnownLocation.getLongitude() + ". Ah oui, et ta gueule !";
                        sm.sendTextMessage(num, null,
                                message, null, null);
                        Toast.makeText(getApplicationContext(), "SMS envoyé", Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                    } else {
                        ActivityCompat.requestPermissions(MapsActivity.this, new String[]{
                                Manifest.permission.SEND_SMS
                        }, MY_PERMISSIONS_REQUEST_SEND_SMS);
                    }
                }


            }
        });
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    public interface AsyncResponse {
        void processFinish(String output1, String output2, String output3, String output4);
    }

    public static class placeIdTask extends AsyncTask<String, Void, JSONObject> {

        public AsyncResponse delegate = null;
        //Call back interface

        public placeIdTask(AsyncResponse asyncResponse) {
            delegate = asyncResponse;
            //Assigning call back interface through constructor
        }

        @Override
        protected JSONObject doInBackground(String... params) {

            JSONObject jsonWeather = null;
            try {
                jsonWeather = getWeatherJSON(params[0], params[1]);
            } catch (Exception e) {
                Log.d("Error", "Cannot process JSON results", e);
            }

            return jsonWeather;
        }

        @Override
        protected void onPostExecute(JSONObject json) {
            try {
                if (json != null) {
                    JSONObject details = json.getJSONArray("weather").getJSONObject(0);
                    JSONObject main = json.getJSONObject("main");
                    DateFormat df = DateFormat.getDateTimeInstance();

                    String city = json.getString("name").toUpperCase(Locale.FRANCE) + ", " + json.getJSONObject("sys").getString("country");
                    String description = details.getString("description").toUpperCase(Locale.FRANCE);
                    String temperature = String.format("%.2f", main.getDouble("temp")) + "°";
                    String snow = json.isNull("snow") ? "Pas de neige" : "Niveau de neige: " + json.getDouble("snow") + "cm";
                    //String humidity = main.getString("humidity") + "%";
                    //String pressure = main.getString("pressure") + " hPa";
                    //String updatedOn = df.format(new Date(json.getLong("dt") * 1000));

                    delegate.processFinish(city, temperature, snow, description);

                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }

    }
}
