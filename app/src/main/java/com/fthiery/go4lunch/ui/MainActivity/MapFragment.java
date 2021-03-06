package com.fthiery.go4lunch.ui.MainActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.fthiery.go4lunch.R;
import com.fthiery.go4lunch.databinding.FragmentMapBinding;
import com.fthiery.go4lunch.model.Restaurant;
import com.fthiery.go4lunch.ui.DetailActivity.RestaurantDetailActivity;
import com.fthiery.go4lunch.viewmodel.MainViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private MainViewModel viewModel;
    private FragmentMapBinding binding;
    private GoogleMap googleMap;
    private ClusterManager<Restaurant> clusterManager;
    private FusedLocationProviderClient fusedLocationClient;
    private boolean cameraAutomaticMode = true;
    private List<Restaurant> restaurantList = new ArrayList<>();

    // Callback for managing location permission
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    enableMyLocation();
                } else {
                    Toast.makeText(requireContext(), R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
                    requireActivity().finish();
                }
            });

    // Callback for when location is updated
    private final LocationCallback locationCallback = new LocationCallback() {
        private Location lastLocation;
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);
            Location newLocation = locationResult.getLastLocation();
            if (lastLocation == null || newLocation.distanceTo(lastLocation) > 5) {
                viewModel.setLocation(newLocation);
                lastLocation = locationResult.getLastLocation();
            }
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Initialise the ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Inflate the fragment layout
        binding = FragmentMapBinding.inflate(inflater, container, false);

        // Initialize GoogleMaps fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // Initialize the location provider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        return binding.getRoot();
    }

    private void updateMarkers(List<Restaurant> restaurants) {
        if (clusterManager != null) {
            // Clear markers
            clusterManager.clearItems();

            // Add new markers
            clusterManager.addItems(restaurants);
            clusterManager.cluster();
        }
    }

    private void centerCameraAround(Collection<Restaurant> restaurants) {
        if (restaurants.size() >= 1 && cameraAutomaticMode) {
            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (Restaurant restaurant : restaurants) {
                builder.include(restaurant.getPosition());
            }
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200), 500, null);
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        // Initialize the map
        googleMap = gMap;
        googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.style_json));
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(46.227638, 2.213749), 5.0f));
        googleMap.setMinZoomPreference(5.0f);
        googleMap.setMaxZoomPreference(20.0f);

        // Initialize the Cluster Manager
        clusterManager = new ClusterManager<>(requireContext(), googleMap);
        clusterManager.setRenderer(new CustomClusterRenderer(requireContext(), googleMap, clusterManager));
        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnMarkerClickListener(clusterManager);

        // When moving the camera manually, put it in manual mode and display the FAB
        googleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                cameraAutomaticMode = false;
                binding.floatingActionButton.setVisibility(View.VISIBLE);
            }
        });

        // When clicking on a cluster, zoom on it
        clusterManager.setOnClusterClickListener(cluster -> {
            centerCameraAround(cluster.getItems());
            return true;
        });

        // When clicking on a marker, start DetailActivity
        clusterManager.setOnClusterItemClickListener(restaurant -> {
            Intent detailActivity = new Intent(requireActivity(), RestaurantDetailActivity.class);
            detailActivity.putExtra("Id", restaurant.getId());
            requireActivity().startActivity(detailActivity);
            return true;
        });

        // When clicking on the FAB, put camera in automatic mode and hide the FAB
        binding.floatingActionButton.setOnClickListener(view -> {
            cameraAutomaticMode = true;
            binding.floatingActionButton.setVisibility(View.INVISIBLE);
            centerCameraAround(restaurantList);
        });

        // Enable the location component
        enableMyLocation();

        // Observe updates to restaurant list and update markers
        viewModel.getRestaurantsLiveData().observe(getViewLifecycleOwner(), restaurants -> {
            restaurantList = restaurants;
            updateMarkers(restaurants);
            centerCameraAround(restaurants);
        });
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (isLocationPermissionGranted() && googleMap != null) {
            googleMap.setMyLocationEnabled(true);
            startLocationUpdates();
        } else {
            // Permission to access the location is missing. Show rationale and request permission
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        // Poll location every 10 seconds
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    @Override
    public void onResume() {
        super.onResume();
        enableMyLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private boolean isLocationPermissionGranted() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private class CustomClusterRenderer extends DefaultClusterRenderer<Restaurant> {
        public CustomClusterRenderer(Context context, GoogleMap googleMap, ClusterManager<Restaurant> clusterManager) {
            super(context, googleMap, clusterManager);
        }

        @Override
        protected int getColor(int clusterSize) {
            return getResources().getColor(R.color.colorMarker);
        }

        @Override
        protected void onClusterItemUpdated(@NonNull Restaurant restaurant, @NonNull Marker marker) {
            if (restaurant.getWorkmates() != 0) {
                marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker_green));
            } else {
                marker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.marker));
            }
        }

        @Override
        protected void onBeforeClusterItemRendered(@NonNull Restaurant restaurant, @NonNull MarkerOptions markerOptions) {
            if (restaurant.getWorkmates() != 0) {
                markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_green));
            } else {
                markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.marker));
            }
        }
    }
}