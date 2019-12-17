package it.polimi.marcermarchiscianamotta.safestreets.view;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import it.polimi.marcermarchiscianamotta.safestreets.R;
import it.polimi.marcermarchiscianamotta.safestreets.controller.ReportViolationManager;
import it.polimi.marcermarchiscianamotta.safestreets.util.GeneralUtils;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class ReportViolationActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {

	private static final String TAG = "ReportViolationActivity";

	//directory where all files are saved
	private static final String mainDirectoryPath = Environment.getExternalStorageDirectory() + "/SafeStreets/";

	//Request codes
	private static final int RC_CAMERA_PERMISSION = 201;
	private static final int RC_READ_EXT_STORAGE_PERMS = 202;
	private static final int RC_WRITE_EXT_STORAGE_PERMS = 203;
	private static final int RC_LOCATION_PERMS = 204;
	private static final int RC_IMAGE_TAKEN = 205;

	//Permissions
	private static final String READ_EXT_STORAGE_PERMS = Manifest.permission.READ_EXTERNAL_STORAGE;
	private static final String WRITE_EXT_STORAGE_PERMS = Manifest.permission.WRITE_EXTERNAL_STORAGE;
	private static final String LOCATION_PERMS = Manifest.permission.ACCESS_FINE_LOCATION;
	private static final String CAMERA_PERMS = Manifest.permission.CAMERA;
	File directory = new File(mainDirectoryPath);

	private ReportViolationManager reportViolationManager;
	Uri currentPhotoPath;

	@BindView(R.id.first_photo_view)
	ImageView firstPhotoView;

	@BindView(R.id.report_violation_root)
	View rootView;

	@BindView(R.id.description)
	EditText descriptionText;

	@BindView(R.id.report_violation_number_of_photos_added)
	TextView numberOfPhotosAddedTextView;

	//region Static methods
	//================================================================================

	/**
	 * Create intent for launching this activity.
	 *
	 * @param context context from which to launch the activity.
	 * @return intent to launch.
	 */
	@NonNull
	public static Intent createIntent(@NonNull Context context) {
		return new Intent(context, ReportViolationActivity.class);
	}
	//endregion

	//region Overridden methods
	//================================================================================
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_report_violation);
		ButterKnife.bind(this); // Needed for @BindView attributes.

		reportViolationManager = new ReportViolationManager(this, rootView);

		//Ask permissions
		if (!EasyPermissions.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
			EasyPermissions.requestPermissions(this, "Location permission", RC_LOCATION_PERMS, LOCATION_PERMS);
		}
		if (!EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
			EasyPermissions.requestPermissions(this, "Camera permission", RC_CAMERA_PERMISSION, CAMERA_PERMS);
		}
		if (!EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
			EasyPermissions.requestPermissions(this, "Camera permission", RC_READ_EXT_STORAGE_PERMS, READ_EXT_STORAGE_PERMS);
		}
		if (!EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
			EasyPermissions.requestPermissions(this, "Camera permission", RC_WRITE_EXT_STORAGE_PERMS, WRITE_EXT_STORAGE_PERMS);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
			case (RC_IMAGE_TAKEN):
				if (resultCode == RESULT_OK) {
					if (new File(URI.create(currentPhotoPath.toString())).exists()) {
						reportViolationManager.addPhotoToReport(currentPhotoPath);
						String textToDisplay = "Number of photos added:" + reportViolationManager.numberOfPhotos() + "/" + reportViolationManager.getMaxNumOfPhotos();
						numberOfPhotosAddedTextView.setText(textToDisplay);
					} else {
						GeneralUtils.showSnackbar(rootView, "Photo not found.");
						Log.e(TAG, "Photo not found at " + currentPhotoPath.toString());
					}
				}
				break;
			default:
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
	}

	@Override
	public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
		if (requestCode == RC_LOCATION_PERMS) {
			if (!EasyPermissions.hasPermissions(this, Manifest.permission.CAMERA)) {
				EasyPermissions.requestPermissions(this, "Location permission", RC_CAMERA_PERMISSION, CAMERA_PERMS);
			}
		}
		if (requestCode == RC_CAMERA_PERMISSION) {
			if (!EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
				EasyPermissions.requestPermissions(this, "Location permission", RC_READ_EXT_STORAGE_PERMS, READ_EXT_STORAGE_PERMS);
			}
		}
		if (requestCode == RC_READ_EXT_STORAGE_PERMS) {
			if (!EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				EasyPermissions.requestPermissions(this, "Location permission", RC_WRITE_EXT_STORAGE_PERMS, WRITE_EXT_STORAGE_PERMS);
			}
		}
		if (requestCode == RC_WRITE_EXT_STORAGE_PERMS) {
			//Initializes the directories
			if (!directory.exists()) {
				boolean created = directory.mkdirs();
				if (!created)
					Log.e(TAG, "Folders not created");
			}
		}
	}

	@Override
	public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
		if (EasyPermissions.somePermissionPermanentlyDenied(this, Collections.singletonList(READ_EXT_STORAGE_PERMS))) {
			new AppSettingsDialog.Builder(this).build().show();
			finish();
		} else if (EasyPermissions.somePermissionPermanentlyDenied(this, Collections.singletonList(CAMERA_PERMS))) {
			new AppSettingsDialog.Builder(this).build().show();
			finish();
		} else if (EasyPermissions.somePermissionPermanentlyDenied(this, Collections.singletonList(LOCATION_PERMS))) {
			new AppSettingsDialog.Builder(this).build().show();
			finish();
		} else if (EasyPermissions.somePermissionPermanentlyDenied(this, Collections.singletonList(WRITE_EXT_STORAGE_PERMS))) {
			new AppSettingsDialog.Builder(this).build().show();
			finish();
		}
	}
	//endregion


	//region UI methods
	//================================================================================
	@OnClick(R.id.report_violation_add_photo_temporary)
	public void onClickAddPhoto(View v) {
		if (reportViolationManager.canTakeAnotherPicture()) {
			String fileName = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss").format(new Date());
			File destination = new File(mainDirectoryPath, fileName + ".jpg");
			currentPhotoPath = Uri.parse(destination.toURI().toString());

			StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
			StrictMode.setVmPolicy(builder.build());
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(destination));
			startActivityForResult(intent, RC_IMAGE_TAKEN);
		} else
			GeneralUtils.showSnackbar(rootView, "Maximum number of pictures reached.");
	}


	@OnClick(R.id.report_violation_floating_send_button)
	public void onClickSendViolation(View v) {
		if (reportViolationManager.isReadyToSend())
			reportViolationManager.sendViolationReport(descriptionText.getText().toString());
		else
			GeneralUtils.showSnackbar(rootView, "Before reporting, please complete all mandatory fields.");
	}
	//endregion


	//region Private methods
	//================================================================================
	//endregion

}
