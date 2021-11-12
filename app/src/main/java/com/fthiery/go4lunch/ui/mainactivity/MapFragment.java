package com.fthiery.go4lunch.ui.mainactivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
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
import com.fthiery.go4lunch.ui.detailactivity.RestaurantDetailActivity;
import com.fthiery.go4lunch.viewmodel.MainViewModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private MainViewModel myViewModel;
    private FragmentMapBinding binding;
    private GoogleMap googleMap;
    private ClusterManager<Restaurant> clusterManager;
    private FusedLocationProviderClient fusedLocationClient;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    enableMyLocation();
                } else {
                    Toast.makeText(requireContext(), R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
                    requireActivity().finish();
                }
            });

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Initialise the ViewModel
        myViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        // Inflate the fragment layout
        binding = FragmentMapBinding.inflate(inflater, container, false);

        // Initialize GoogleMaps fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Initialize the location provider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        myViewModel.getRestaurantsLiveData().observe(getViewLifecycleOwner(), restaurantList -> {
            // Clear markers
            clusterManager.clearItems();
            // Add new markers
            clusterManager.addItems(restaurantList);
        });

        return binding.getRoot();
    }

    @SuppressLint("PotentialBehaviorOverride")
    @Override
    public void onMapReady(@NonNull GoogleMap gMap) {
        googleMap = gMap;
        googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.style_json));
        googleMap.setMinZoomPreference(8.0f);
        googleMap.setMaxZoomPreference(20.0f);

        // Initiate the Cluster Manager
        clusterManager = new ClusterManager<Restaurant>(requireContext(), googleMap);
        clusterManager.setRenderer(new CustomClusterRenderer(requireContext(),googleMap,clusterManager));
        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnMarkerClickListener(clusterManager);

        clusterManager.setOnClusterClickListener(cluster -> {
            // When clicking on a cluster, zoom on it
            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (ClusterItem item : cluster.getItems()) {
                builder.include(item.getPosition());
            }
            final LatLngBounds bounds = builder.build();
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
            return true;
        });

        clusterManager.setOnClusterItemClickListener(restaurant -> {
            // When clicking on a marker, start DetailActivity
            Intent detailActivity = new Intent(requireActivity(), RestaurantDetailActivity.class);
            detailActivity.putExtra("Id", restaurant.getId());
            requireActivity().startActivity(detailActivity);
            return true;
        });

        // Enable the location component
        enableMyLocation();
        googleMap.setOnMyLocationButtonClickListener(() -> {
            updateLocation();
            return false;
        });
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (isLocationPermissionGranted()) {
            if (googleMap != null) {
                googleMap.setMyLocationEnabled(true);
                updateLocation();
            }
        } else {
            // Permission to access the location is missing. Show rationale and request permission
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    public void updateLocation() {
        if (isLocationPermissionGranted()) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
                // Got last known location. In some rare situations this can be null.
                if (location != null) {
                    // Logic to handle location object
                    myViewModel.setLocation(location);

                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(location.getLatitude(), location.getLongitude()), 15));
                }
            });
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private boolean isLocationPermissionGranted() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private class CustomClusterRenderer extends DefaultClusterRenderer<Restaurant> {
        public CustomClusterRenderer(Context context, GoogleMap googleMap, ClusterManager<Restaurant> clusterManager) {
            super(context,googleMap,clusterManager);
        }

        @Override
        protected int getColor(int clusterSize) {
            return getResources().getColor(R.color.colorPrimary);
        }

        @Override
        protected void onBeforeClusterItemRendered(Restaurant restaurant, MarkerOptions markerOptions) {
            if (restaurant.isChosen()) {
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            } else {
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker());
            }
        }
    }
}