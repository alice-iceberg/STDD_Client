package com.nematjon.edd_client_season_two;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;

public class DisclosureDialog extends DialogFragment {
    private static final String TAG = "DisclosureDialog";
    String[] listItems = {"Location Permission", "Camera Usage", "Contacts Permission", "Access and  Change Wi-Fi Usage Permission", "Record audio Permission", "SMS Permission", "Package Usage  Statistics Permission"};
    ArrayList<Object> selectedItems = new ArrayList<>();

    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        selectedItems = new ArrayList<>();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        builder.setView(inflater.inflate(R.layout.activity_ema, null));
        builder.setTitle("Items").setMultiChoiceItems(listItems, null,
                (dialog, which, isChecked) -> {
                    if (isChecked) {
                        selectedItems.add(which);
                    } else if (selectedItems.contains(which)) {
                        selectedItems.remove(Integer.valueOf(which));
                    }
                }).setPositiveButton("Accept", (dialog, id) -> Log.d(TAG, "onCreateDialog: " + id)).setNegativeButton(R.string.cancel, (dialog, id) -> Log.d(TAG, "onClick: " + id));

        return builder.create();


    }
}
