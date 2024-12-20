package info.toyonos.hfr4droid.common.activity;

import info.toyonos.hfr4droid.common.HFR4droidApplication;
import info.toyonos.hfr4droid.common.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.ClipboardManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Choix d'une image, et upload sur hfr-rehost.net
 * @author fred
 *
 */
@SuppressWarnings("deprecation")
public class ImagePicker extends Activity implements Runnable{
	
	private static final String LOG_TAG = ImagePicker.class.getSimpleName();
	
	private static String ACTION_TYPE = null;
	
	private static final int DATA_READ_OK = 0;
	private static final int DATA_READ_KO = 1;
	
	//request code
	public static final int CHOOSE_PICTURE = 1;
	public static final String ACTION_HFRUPLOADER = "ACTION_HFRUPLOADER";
	public static final String ACTION_HFRUPLOADER_MP = "ACTION_HFRUPLOADER_MP";
	
	public static final String FINAL_URL = "finalUrl";
	private static final String UPLOAD_URL = "https://reho.st/upload";
	
	// Pour le thread : en entrée :
	String fichierLocal = null;
	// et en sortie :
	String url = null;

	// Dialog de progression
	ProgressDialog dialog = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.main);
        Intent intentAppel = getIntent();
        
        // On mémorise le type d'action (appel) : soit depuis hfr4droid, soit depuis la commande partager
        ACTION_TYPE = intentAppel.getAction();
        
        // Cas HFR4DROID: on appelle l'activity pour choisir l'image
        if(ACTION_TYPE.equals(ACTION_HFRUPLOADER)) {
        	Intent intent = new Intent();
        	intent.setType("image/*");
        	intent.setAction(Intent.ACTION_GET_CONTENT);
        	startActivityForResult(Intent.createChooser(intent, getString(R.string.file_hfr_rehost)), CHOOSE_PICTURE);
        }
        // Cas HFR4DROID 2: on appelle l'activity pour uploader une photo avant de l'envoyer par mp
        if(ACTION_TYPE.equals(ACTION_HFRUPLOADER_MP)) {
        	Uri uri = Uri.parse(intentAppel.getStringExtra(Intent.EXTRA_STREAM));
        	Intent intentSortie = new Intent();
        	intentSortie.setData(uri);
        	onActivityResult(CHOOSE_PICTURE, RESULT_OK, intentSortie);
        }      
        // Cas SEND : l'uri de l'image est déjà connue.
        if(ACTION_TYPE.equals(Intent.ACTION_SEND)) {
        	Bundle extras = intentAppel.getExtras();
        	Uri uri = (Uri)extras.get(Intent.EXTRA_STREAM);
        	Intent intentSortie = new Intent();
        	intentSortie.setData(uri);
        	// on appelle directement onActivityResult pour la suite du process
        	onActivityResult(CHOOSE_PICTURE, RESULT_OK, intentSortie);
        }

    }

    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
    	if(requestCode == CHOOSE_PICTURE) {
    		switch (resultCode) {
			case RESULT_OK:
				HFR4droidActivity.getConfirmDialog(
				this,
				getString(R.string.hfrrehost_title),
				getString(R.string.are_u_sure_message),
				new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface arg0, int arg1)
					{
						final boolean isKitKat = Build.VERSION.SDK_INT >= 19;
						
						try
						{
							Uri imageUri = data.getData();
							if (imageUri.getScheme().equals("content"))
							{
								if (isKitKat)
								{
									Cursor cursor = getContentResolver().query(imageUri, null, null, null, null);
									cursor.moveToFirst();
									String documentId = cursor.getString(0);
									documentId = documentId.substring(documentId.lastIndexOf(":")+1);
									cursor.close();

									cursor = getContentResolver().query( 
											android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
											null, MediaStore.Images.Media._ID + " = ? ", new String[]{documentId}, null);
									cursor.moveToFirst();
									fichierLocal = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
									cursor.close();
								}
								else
								{
									Cursor c = extractEntityCursor(imageUri);
									fichierLocal = c.getString(0);//extractValue(c, MediaStore.Images.Media.DATA);
									c.close();
								}
							}
							else
							{
								fichierLocal = imageUri.getPath();
							}
							Log.d(LOG_TAG, "Fichier local is " + fichierLocal);
						}
						catch (Exception e)
						{
							Log.d(LOG_TAG, e.getClass().getSimpleName() + (e.getMessage() != null ? " : " + e.getMessage() : ""), e);
							setResult(RESULT_CANCELED);
							finish();
							return;
						}
						
						dialog = ProgressDialog.show(ImagePicker.this, "", getString(R.string.loading_hfr_rehost), true);
						new Thread(ImagePicker.this).start();
					}
				}
				,new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface arg0, int arg1)
					{
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
		url = doUpload(fichierLocal);
		// envoie un message avec DATA_READ_OK ou DATA_READ_KO selon si l'url a été lue ou non
		handler.sendEmptyMessage(url != null ? DATA_READ_OK:DATA_READ_KO);
	}

	/**
	 * Handler pour les messages envoyés par le thread
	 */
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// On cache la fenetre de progression
			dialog.dismiss();
			
			// si upload ok
			if(msg.what == DATA_READ_OK) {
				
				// si appelé depuis l'appli, renvoi d'un intent avec l'url dans les extras
				if(ACTION_TYPE.equals(ACTION_HFRUPLOADER) || ACTION_TYPE.equals(ACTION_HFRUPLOADER_MP)) {
					getIntent().putExtra(FINAL_URL, url);
					setResult(RESULT_OK, getIntent());
				}
				// si appelé depuis le partage, on met l'url dans le presse papiers
				if(ACTION_TYPE.equals(Intent.ACTION_SEND)) {
					setClipboardText(url);
					Toast.makeText(getApplicationContext(), getString(R.string.copy_hfr_rehost) , Toast.LENGTH_LONG).show();
				}
			}
			else {
				// erreur lors de l'upload
				setResult(RESULT_CANCELED);
			}
			// on termine
            finish();
		}

	};

    private void setClipboardText(String text) {
    	ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    	Log.d(LOG_TAG, "Setting text on clipboard : " + text);
    	cm.setText(text);
    }
    
    /**
     * Renvoie la valeur qui nous intéresse depuis le cursor
     * @param c
     * @param valueColumn
     * @return
     */
    public String extractValue(Cursor c, String valueColumn) {
        int column_index = c.getColumnIndexOrThrow(valueColumn);
        Log.d(LOG_TAG, "Value for " + valueColumn + " is " + c.getString(column_index));
        return c.getString(column_index);
    }

    /**
     * Interrogation du media store
     * @param uri
     * @return
     */
    public Cursor extractEntityCursor(Uri uri) {
        //String[] projection = { MediaStore.Images.Media.DATA, MediaStore.Images.Media.MIME_TYPE };
        //Cursor cursor = managedQuery(uri, projection, null, null, null);
        Cursor cursor = getContentResolver().query(uri, new String[] {MediaStore.Images.ImageColumns.DATA }, null, null, null);
        cursor.moveToFirst();
        return cursor;
    }
    
    /**
     * Upload proprement dit
     * @param filepath chemin + fichier local
     * @param filename nom du fichier seul
     * @param contentType content type du fichier
     * @return
     */
	public String doUpload(String filepath) {
		
		String imgUrl = null;
		HttpPost post;
		HttpClient httpClient = ((HFR4droidApplication) getApplication()).getHttpClientHelper().getHttpClient();
		
		try
		{
			File fileToUpload = new File(filepath);
			try
			{
				int sampleSize = 2;
				while (fileToUpload.length() > 2097152) // 2 Mo
				{
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inSampleSize = sampleSize;
					Bitmap imgBitmap = BitmapFactory.decodeFile(fichierLocal, options);
					fileToUpload = new File(fichierLocal + ".compress.png");
					OutputStream fos = new FileOutputStream(fileToUpload);
					imgBitmap.compress(CompressFormat.PNG, 90, fos);
					fos.close();
					sampleSize += 1;
				}
			}
			catch (final IOException e)
			{
				runOnUiThread(new Runnable()
				{
					public void run()
					{
						Toast.makeText(getApplicationContext(), getString(R.string.error_hfr_rehost, e.getMessage()) , Toast.LENGTH_LONG).show();	
					}
				});
				return null;
			}

			httpClient.getParams().setParameter("http.socket.timeout", new Integer(90000)); // 90 second
			post = new HttpPost(new URI(UPLOAD_URL));
			post.setHeader("User-Agent", HFR4droidApplication.getUserAgent());
			
			MultipartEntity multipart = new MultipartEntity();
		    ContentBody cbFile = new FileBody(fileToUpload, "image/jpeg");
		    multipart.addPart("fichier", cbFile);

		    post.setEntity(multipart);

			HttpResponse response = httpClient.execute(post);
			Log.d(LOG_TAG, "Response Status line code:" + response.getStatusLine().getStatusCode());
			HttpEntity resEntity = response.getEntity();
			if (resEntity == null) {
				Log.e(LOG_TAG, "No response");
			}
			imgUrl = extractUrlFromPage(resEntity.getContent());
			
		} catch (Exception ex) {
			Log.e(LOG_TAG, "Exception : " + ex.getMessage());
			ex.printStackTrace();
		}
		return imgUrl;
	}
	
	private String extractUrlFromPage(InputStream is) {
		BufferedReader bufRead = new BufferedReader(new InputStreamReader(is));
		String line;
		String found = null;
		try {
			while((line = bufRead.readLine()) != null) {
				if(line.trim().length()>0)
					found = getUrl(line.trim());
				if(found != null)
					return found;
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}
	
	/**
	 * Regex sur les différentes lignes pour récupérer le bb code contenant l'URL. Ce serait à améliorer...
	 * @param line
	 * @return
	 */
	private String getUrl(String line) {
		Pattern p = Pattern.compile("<code>\\[img\\]https://reho.st/preview/self/.*</code>");
		Matcher m = p.matcher(line);
		String match = null;
		if(m.matches()) {
			Log.d(LOG_TAG, "** match trouve pour la ligne courante");
			match = m.group();
			match = match.substring(6, match.length() - 7);
			Log.d(LOG_TAG, match);
		}
		return match;
	}
}
