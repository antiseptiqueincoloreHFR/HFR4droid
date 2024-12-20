package info.toyonos.hfr4droid.common.activity;

import info.toyonos.hfr4droid.common.R;
import info.toyonos.hfr4droid.common.core.bean.Category;
import info.toyonos.hfr4droid.common.core.bean.Theme;
import info.toyonos.hfr4droid.common.core.bean.Topic.TopicType;
import info.toyonos.hfr4droid.common.core.data.DataRetrieverException;
import info.toyonos.hfr4droid.common.core.message.HFRMessageSender.ResponseCode;
import info.toyonos.hfr4droid.common.core.message.MessageSenderException;
import info.toyonos.hfr4droid.common.util.asynctask.ValidateMessageAsynckTask;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.view.Menu;

/**
 * <p>Activity permettant d'ajouter un topic (classique ou MP)</p>
 * 
 * @author ToYonos
 *
 */
public class NewTopicActivity extends NewPostGenericActivity
{
	private Category cat = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
		ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.new_topic, null);
		setContentView(layout);
		
		Intent intent = getIntent();
		Bundle bundle = intent.getExtras();
		String action = intent.getAction();
		
		if (bundle != null && bundle.getString("pseudo") != null)
		{
			((TextView) findViewById(R.id.inputMpTo)).setText(bundle.getString("pseudo"));
		}
		
		if (bundle != null && bundle.getSerializable("cat") != null)
		{
			cat = (Category) bundle.getSerializable("cat");
		}
		else if (action.equals(Intent.ACTION_SEND))
		{
			// Envoie de données par message privé
			cat = Category.MPS_CAT;
			if (bundle.get(Intent.EXTRA_TEXT) != null)
			{
				((TextView) findViewById(R.id.InputPostContent)).setText(bundle.get(Intent.EXTRA_TEXT).toString());
			}
			if (bundle.get(Intent.EXTRA_SUBJECT) != null)
			{
				((TextView) findViewById(R.id.inputTopicSubject)).setText(bundle.get(Intent.EXTRA_SUBJECT).toString());
			}
			if (bundle.get(Intent.EXTRA_STREAM) != null)
			{
				Uri uri = (Uri) bundle.get(Intent.EXTRA_STREAM);
				Intent intentRehost = new Intent(this, ImagePicker.class);
				intentRehost.setAction(ImagePicker.ACTION_HFRUPLOADER_MP);
				intentRehost.putExtra(Intent.EXTRA_STREAM, uri.toString());
				startActivityForResult(intentRehost, ImagePicker.CHOOSE_PICTURE);
			}
		}
		
		if (cat == null)
		{
			finish();
			return;
		}

		uiHelper.addPostButtons(this, layout);
		applyTheme(currentTheme);
		((EditText) layout.findViewById(R.id.InputPostContent)).setTextSize(getTextSize(14));
		((EditText) layout.findViewById(R.id.InputSmileyTag)).setTextSize(getTextSize(14));
		((EditText) layout.findViewById(R.id.inputMpTo)).setTextSize(getTextSize(14));
		((EditText) layout.findViewById(R.id.inputTopicSubject)).setTextSize(getTextSize(14));
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.common, menu);
		getMenuInflater().inflate(R.menu.misc, menu);
		menu.removeItem(R.id.MenuMps);
		menu.removeItem(R.id.MenuRefresh);
		return true;
	}
	
	@Override
	protected void setTitle()
	{
		getActionBar().setTitle(isMpsCat(cat) ? getString(R.string.new_mp) : cat.getName());
	}
		
	@Override
	protected void setOkButtonClickListener(Button okButton)
	{
		okButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				final EditText postRecipient = (EditText) findViewById(R.id.inputMpTo);
				final EditText postSubject = (EditText) findViewById(R.id.inputTopicSubject);
				final EditText postContent = (EditText) findViewById(R.id.InputPostContent);
				new ValidateMessageAsynckTask(NewTopicActivity.this, -1)
				{
					@Override
					protected boolean canExecute()
					{
						if (postRecipient.getText().length() == 0)
						{
							Toast.makeText(NewTopicActivity.this, R.string.missing_recipient, Toast.LENGTH_SHORT).show();
							return false;
						}

						if (postSubject.getText().length() == 0)
						{
							Toast.makeText(NewTopicActivity.this, R.string.missing_subject, Toast.LENGTH_SHORT).show();
							return false;
						}

						if (postContent.getText().length() == 0)
						{
							Toast.makeText(NewTopicActivity.this, R.string.missing_post_content, Toast.LENGTH_SHORT).show();
							return false;
						}						

						return true;
					}

					@Override
					protected ResponseCode validateMessage() throws MessageSenderException, DataRetrieverException
					{
						return getMessageSender().newTopic(Category.MPS_CAT, getDataRetriever().getHashCheck(), postRecipient.getText().toString(), postSubject.getText().toString(), postContent.getText().toString(), isSignatureEnable());
					}

					@Override
					protected boolean handleCodeResponse(ResponseCode code)
					{
						if (!super.handleCodeResponse(code))
						{
							switch (code)
							{	
								case TOPIC_NEW_OK: // New topic ok
									loadTopics(Category.MPS_CAT, TopicType.ALL, 1, false);
									return true;
								
								case TOPIC_FLOOD: // Flood
									Toast.makeText(NewTopicActivity.this, getString(R.string.topic_flood), Toast.LENGTH_SHORT).show();
									return true;
									
								case MP_INVALID_RECIPIENT: // Invalid recipient
									Toast.makeText(NewTopicActivity.this, getString(R.string.mp_invalid_recipient), Toast.LENGTH_SHORT).show();
									return true;									
								
								default:
									return false;
							}							
						}
						else
						{
							return true;
						}
					}
				}.execute();
			}
		});
	}
	
	@Override
	protected void applyTheme(Theme theme)
	{
		super.applyTheme(theme);
		
		EditText inputMpTo = (EditText) findViewById(R.id.inputMpTo);
		inputMpTo.setTextColor(theme.getPostTextColor());
		inputMpTo.setBackgroundResource(getKeyByTheme(theme.getKey(), R.drawable.class, "input_background"));

		EditText inputTopicSubject = (EditText) findViewById(R.id.inputTopicSubject);
		inputTopicSubject.setTextColor(theme.getPostTextColor());
		inputTopicSubject.setBackgroundResource(getKeyByTheme(theme.getKey(), R.drawable.class, "input_background"));
	}
}