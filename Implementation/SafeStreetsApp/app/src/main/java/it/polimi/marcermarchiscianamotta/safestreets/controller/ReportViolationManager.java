package it.polimi.marcermarchiscianamotta.safestreets.controller;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.List;

import it.polimi.marcermarchiscianamotta.safestreets.model.ViolationReport;
import it.polimi.marcermarchiscianamotta.safestreets.util.AuthenticationManager;
import it.polimi.marcermarchiscianamotta.safestreets.util.DatabaseConnection;
import it.polimi.marcermarchiscianamotta.safestreets.util.GeneralUtils;
import it.polimi.marcermarchiscianamotta.safestreets.util.StorageConnection;

public class ReportViolationManager {

    private static final String TAG = "ReportViolationManager";
    private Activity activity;
    private View rootView;

    private List<Uri> selectedPictures;
    private String violationDescription;
    private double latitude;
    private double longitude;
    private List<String> picturesInUpload;
    private int numberOfUploadedPhotos = 0;
    private boolean failedUplaod = false;


    public ReportViolationManager(Activity activity, View rootView) {
        this.activity = activity;
        this.rootView = rootView;
    }


    //region Public methods
    //================================================================================
    public void onSendViolationReport(List<Uri> selectedPictures, String violationDescription, double latitude, double longitude) {
        this.selectedPictures = selectedPictures;
        this.violationDescription = violationDescription;
        this.latitude = latitude;
        this.longitude = longitude;
        numberOfUploadedPhotos = 0;
        failedUplaod = false;

        Toast.makeText(activity, "Uploading photos...", Toast.LENGTH_SHORT).show();
        uploadPhotosToCloudStorage();
    }
    //endregion


    //region Private methods
    //================================================================================
    private void uploadPhotosToCloudStorage() {
        picturesInUpload = StorageConnection.uploadPicturesToCloudStorage(selectedPictures, activity,
                taskSnapshot -> {
                    checkIfAllUploadsEnded();
                    Toast.makeText(activity, "Image uploaded", Toast.LENGTH_SHORT).show();
                },
                e -> {
                    Log.w(TAG, "uploadPhotosToCloudStorage:onError", e);
                    failedUplaod = true;
                    checkIfAllUploadsEnded();
                    Toast.makeText(activity, "Upload failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void checkIfAllUploadsEnded() {
        numberOfUploadedPhotos++;
        if(picturesInUpload != null && numberOfUploadedPhotos == picturesInUpload.size()) {
            // End upload
            if(failedUplaod) {
                GeneralUtils.showSnackbar(rootView, "Failed to send the violation report. Please try again.");
            } else {
                insertViolationReportInDatabase();
            }
        }
    }

    private void insertViolationReportInDatabase() {
        // Create ViolationReport object.
        // TODO violationType, licensePlate
        ViolationReport vr = new ViolationReport(AuthenticationManager.getUserUid(), 0, violationDescription, picturesInUpload, "AB123CD", latitude, longitude);

        // Upload object to database.
        DatabaseConnection.uploadViolationReport(vr, activity,
                document -> {
                    Toast.makeText(activity, "Violation Report sent successfully!", Toast.LENGTH_SHORT).show();
                    activity.finish();
                },
                e -> {
                    Log.e(TAG, "Failed to write message", e);
                    GeneralUtils.showSnackbar(rootView, "Failed to send the violation report. Please try again.");
                });
    }
    //endregion
}