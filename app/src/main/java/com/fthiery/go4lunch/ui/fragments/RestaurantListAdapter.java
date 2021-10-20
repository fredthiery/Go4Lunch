package com.fthiery.go4lunch.ui.fragments;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.fthiery.go4lunch.databinding.RestaurantViewBinding;
import com.fthiery.go4lunch.model.Restaurant;

public class RestaurantListAdapter extends ListAdapter<Restaurant,RestaurantListAdapter.RestaurantViewHolder> {

    RestaurantViewBinding binding;

    protected RestaurantListAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public RestaurantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        binding = RestaurantViewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new RestaurantViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RestaurantViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public static final DiffUtil.ItemCallback<Restaurant> DIFF_CALLBACK = new DiffUtil.ItemCallback<Restaurant>() {

        @Override
        public boolean areItemsTheSame(@NonNull Restaurant oldItem, @NonNull Restaurant newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Restaurant oldItem, @NonNull Restaurant newItem) {
            return oldItem.equals(newItem);
        }
    };

    static class RestaurantViewHolder extends RecyclerView.ViewHolder {
        RestaurantViewBinding itemBinding;

        public RestaurantViewHolder(RestaurantViewBinding itemBinding) {
            super(itemBinding.getRoot());
            this.itemBinding = itemBinding;
        }

        void bind(Restaurant restaurant) {
            itemBinding.restaurantName.setText(restaurant.getName());
            itemBinding.restaurantAddress.setText(restaurant.getAddress());
        }
    }
}