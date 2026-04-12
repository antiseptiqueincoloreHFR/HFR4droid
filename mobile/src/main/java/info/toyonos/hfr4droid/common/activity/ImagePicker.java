package info.toyonos.hfr4droid.common.activity;

import info.toyonos.hfr4droid.common.HFR4droidApplication;
import info.toyonos.hfr4droid.common.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Choix d'une image, et upload sur rehost.diberie.com
 * @author fred
 *
 */
@SuppressWarnings("deprecation")
public class ImagePicker extends Activity implements Runnable {

	private static final String LOG_TAG = ImagePicker.class.getSimpleName();

	private static String ACTION_TYPE = null;

	private static final int DATA_READ_OK = 0;
	private static final int DATA_READ_KO = 1;

	// request code
	public static final int CHOOSE_PICTURE = 1;
	public static final String ACTION_HFRUPLOADER = "ACTION_HFRUPLOADER";
	public static final String ACTION_HFRUPLOADER_MP = "ACTION_HFRUPLOADER_MP";

	public static final String FINAL_URL = "finalUrl";
	private static final String UPLOAD_URL = "https://rehost.diberie.com/Host/UploadFiles?PrivateMode=false&SendMail=false&Comment=";
	private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10 Mo

	// Pour le thread : en entrée :
	Uri imageUri = null;
	// et en sortie :
	String uploadedPicUrl = null;
	String uploadedResizedUrl = null;

	// Dialog de progression
	ProgressDialog dialog = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intentAppel = getIntent();

		// On mémorise le type d'action (appel) : soit depuis hfr4droid, soit depuis la commande partager
		ACTION_TYPE = intentAppel.getAction();

		// Cas HFR4DROID: on appelle l'activity pour choisir l'image dans la galerie
		if (ACTION_TYPE.equals(ACTION_HFRUPLOADER)) {
			Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
			startActivityForResult(intent, CHOOSE_PICTURE);
		}
		// Cas HFR4DROID 2: on appelle l'activity pour uploader une photo avant de l'envoyer par mp
		if (ACTION_TYPE.equals(ACTION_HFRUPLOADER_MP)) {
			Uri uri = Uri.parse(intentAppel.getStringExtra(Intent.EXTRA_STREAM));
			Intent intentSortie = new Intent();
			intentSortie.setData(uri);
			onActivityResult(CHOOSE_PICTURE, RESULT_OK, intentSortie);
		}
		// Cas SEND : l'uri de l'image est déjà connue.
		if (ACTION_TYPE.equals(Intent.ACTION_SEND)) {
			Bundle extras = intentAppel.getExtras();
			Uri uri = (Uri) extras.get(Intent.EXTRA_STREAM);
			Intent intentSortie = new Intent();
			intentSortie.setData(uri);
			// on appelle directement onActivityResult pour la suite du process
			onActivityResult(CHOOSE_PICTURE, RESULT_OK, intentSortie);
		}
	}

	public void onActivityResult(int requestCode, int resultCode, final Intent data) {
		if (requestCode == CHOOSE_PICTURE) {
			switch (resultCode) {
				case RESULT_OK:
					HFR4droidActivity.getConfirmDialog(
						this,
						getString(R.string.hfrrehost_title),
						getString(R.string.are_u_sure_message),
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface arg0, int arg1) {
								imageUri = data.getData();
								dialog = HFR4droidActivity.isDarkTheme(ImagePicker.this) ?
									new ProgressDialog(ImagePicker.this, android.R.style.Theme_Holo_Dialog) :
									new ProgressDialog(ImagePicker.this);
								dialog.setMessage(getString(R.string.loading_hfr_rehost));
								dialog.setIndeterminate(true);
								dialog.show();
								new Thread(ImagePicker.this).start();
							}
						},
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface arg0, int arg1) {
								setResult(RESULT_CANCELED);
								finish();
							}
						}).show();
					break;

				case RESULT_CANCELED:
					setResult(RESULT_CANCELED);
					finish();
					break;

				default:
					break;
			}
		}
	}

	/**
	 * Pour le thread
	 */
	public void run() {
		boolean success = doUpload(imageUri);
		handler.sendEmptyMessage(success ? DATA_READ_OK : DATA_READ_KO);
	}

	/**
	 * Handler pour les messages envoyés par le thread
	 */
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// On cache la fenetre de progression
			dialog.dismiss();

			if (msg.what == DATA_READ_OK) {
				// Si pas d'URL redimensionnée, insertion directe de l'image originale
				if (uploadedResizedUrl == null) {
					sendResult("[img]" + uploadedPicUrl + "[/img]");
					return;
				}
				// Dialog de choix de la taille
				new AlertDialog.Builder(ImagePicker.this)
					.setTitle(R.string.choose_image_size_title)
					.setItems(new String[]{
						getString(R.string.image_size_reduced),
						getString(R.string.image_size_thumbnail)
					}, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface d, int which) {
							String bbcode;
							if (which == 0) {
								// Image réduite
								bbcode = "[url=" + uploadedPicUrl + "][img]" + uploadedResizedUrl + "[/img][/url]";
							} else {
								// Image miniature : Get/xxx → Get/t
								String thumbUrl = uploadedResizedUrl.replaceFirst("Get/[^/?]+", "Get/t");
								bbcode = "[url=" + uploadedPicUrl + "][img]" + thumbUrl + "[/img][/url]";
							}
							sendResult(bbcode);
						}
					})
					.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface d, int which) {
							setResult(RESULT_CANCELED);
							finish();
						}
					})
					.setCancelable(false)
					.show();
			} else {
				// erreur lors de l'upload
				Toast.makeText(getApplicationContext(), getString(R.string.error_upload_diberie), Toast.LENGTH_LONG).show();
				setResult(RESULT_CANCELED);
				finish();
			}
		}
	};

	private void sendResult(String bbcode) {
		if (ACTION_TYPE.equals(ACTION_HFRUPLOADER) || ACTION_TYPE.equals(ACTION_HFRUPLOADER_MP)) {
			getIntent().putExtra(FINAL_URL, bbcode);
			setResult(RESULT_OK, getIntent());
		}
		if (ACTION_TYPE.equals(Intent.ACTION_SEND)) {
			setClipboardText(bbcode);
			Toast.makeText(getApplicationContext(), getString(R.string.copy_hfr_rehost), Toast.LENGTH_LONG).show();
		}
		finish();
	}

	private void setClipboardText(String text) {
		ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		Log.d(LOG_TAG, "Setting text on clipboard : " + text);
		cm.setText(text);
	}

	private byte[] readStream(InputStream is) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int read;
		while ((read = is.read(chunk)) != -1) {
			buffer.write(chunk, 0, read);
		}
		return buffer.toByteArray();
	}

	private byte[] compressIfNeeded(byte[] imageBytes) {
		if (imageBytes.length <= MAX_SIZE_BYTES) {
			return imageBytes;
		}
		int sampleSize = 2;
		byte[] compressed = imageBytes;
		while (compressed.length > MAX_SIZE_BYTES) {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inSampleSize = sampleSize;
			Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos);
			compressed = bos.toByteArray();
			sampleSize++;
		}
		return compressed;
	}

	/**
	 * Upload de l'image sur rehost.diberie.com.
	 * Stocke les URLs résultantes dans uploadedPicUrl et uploadedResizedUrl.
	 * @param uri URI de l'image sélectionnée
	 * @return true si l'upload a réussi, false sinon
	 */
	public boolean doUpload(Uri uri) {
		try {
			// Lecture des octets de l'image depuis l'URI
			InputStream is = getContentResolver().openInputStream(uri);
			if (is == null) {
				Log.e(LOG_TAG, "Impossible d'ouvrir le flux pour l'URI : " + uri);
				return false;
			}
			byte[] imageBytes;
			try {
				imageBytes = readStream(is);
			} finally {
				is.close();
			}

			// Compression si nécessaire
			imageBytes = compressIfNeeded(imageBytes);

			// Construction du corps multipart
			String boundary = "----HFR4DroidBoundary" + System.currentTimeMillis();
			String CRLF = "\r\n";
			String fileName = "image.jpg";

			URL uploadUrl = new URL(UPLOAD_URL);
			HttpURLConnection conn = (HttpURLConnection) uploadUrl.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(90000);
			conn.setReadTimeout(90000);
			conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			conn.setRequestProperty("User-Agent", HFR4droidApplication.getUserAgent());

			OutputStream os = conn.getOutputStream();

			// En-tête de la partie
			String partHeader = "--" + boundary + CRLF
				+ "Content-Disposition: form-data; name=\"" + fileName + "\"; filename=\"" + fileName + "\"" + CRLF
				+ "Content-Type: image/jpeg" + CRLF + CRLF;
			os.write(partHeader.getBytes(StandardCharsets.UTF_8));
			os.write(imageBytes);

			// Frontière de clôture
			String closingBoundary = CRLF + "--" + boundary + "--" + CRLF;
			os.write(closingBoundary.getBytes(StandardCharsets.UTF_8));
			os.flush();
			os.close();

			int responseCode = conn.getResponseCode();
			Log.d(LOG_TAG, "Code de réponse : " + responseCode);

			if (responseCode != HttpURLConnection.HTTP_OK) {
				Log.e(LOG_TAG, "Échec de l'upload, code : " + responseCode);
				return false;
			}

			InputStream responseStream = conn.getInputStream();
			byte[] responseBytes;
			try {
				responseBytes = readStream(responseStream);
			} finally {
				responseStream.close();
			}
			String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
			Log.d(LOG_TAG, "Réponse : " + responseBody);

			// Analyse de la réponse JSON
			JSONObject json = new JSONObject(responseBody);
			String picUrl = json.optString("picURL", null);
			String resizedUrl = json.optString("resizedURL", null);

			if (picUrl == null || picUrl.isEmpty()) {
				Log.e(LOG_TAG, "Pas de picURL dans la réponse");
				return false;
			}

			uploadedPicUrl = picUrl;
			uploadedResizedUrl = (resizedUrl != null && !resizedUrl.isEmpty()) ? resizedUrl : null;
			return true;

		} catch (Exception e) {
			Log.e(LOG_TAG, "Exception lors de l'upload : " + e.getMessage(), e);
			return false;
		}
	}
}
