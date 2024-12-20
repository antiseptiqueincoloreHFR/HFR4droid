package info.toyonos.hfr4droid.common.activity;

import info.toyonos.hfr4droid.common.R;
import info.toyonos.hfr4droid.common.core.bean.Topic;
import info.toyonos.hfr4droid.common.core.bean.Topic.TopicType;
import info.toyonos.hfr4droid.common.core.data.DataRetrieverException;
import info.toyonos.hfr4droid.common.core.message.HFRMessageSender.ResponseCode;
import info.toyonos.hfr4droid.common.core.message.MessageSenderException;
import info.toyonos.hfr4droid.common.util.asynctask.ValidateMessageAsynckTask;
import info.toyonos.hfr4droid.common.util.helper.NewPostUIHelper;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.view.Menu;

/**
 * <p>Activity permettant d'ajouter un post (classique ou MP)</p>
 * 
 * @author ToYonos
 *
 */
public class NewPostActivity extends NewPostGenericActivity
{
	private Topic topic = null;
	private TopicType fromType = TopicType.ALL;
	private boolean fromAllCats;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
		ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.new_post, null);
		setContentView(layout);
		
		Intent intent = getIntent();
		Bundle bundle = intent.getExtras();
		if (bundle != null)
		{
			if(bundle.getSerializable("topic") != null)
			{
				topic = (Topic) bundle.getSerializable("topic");
			}
			if(bundle.getSerializable("fromTopicType") != null)
			{
				fromType = (TopicType) bundle.getSerializable("fromTopicType");
			}
			fromAllCats =  bundle.getBoolean("fromAllCats", false);
		}

		if (topic == null)
		{
			finish();
			return;
		}

		uiHelper.addPostButtons(this, layout);
		applyTheme(currentTheme);
		((EditText) layout.findViewById(R.id.InputPostContent)).setTextSize(getTextSize(14));
		((EditText) layout.findViewById(R.id.InputSmileyTag)).setTextSize(getTextSize(14));
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.common, menu);
		getMenuInflater().inflate(R.menu.misc, menu);
		menu.removeItem(R.id.MenuRefresh);
		return true;
	}
	
	@Override
	protected void setTitle()
	{
		getActionBar().setTitle(topic.getName());
	}
		
	@Override
	protected void setOkButtonClickListener(Button okButton)
	{
		okButton.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				final EditText postContent = (EditText) findViewById(R.id.InputPostContent);
				new ValidateMessageAsynckTask(NewPostActivity.this, -1)
				{
					@Override
					protected boolean canExecute()
					{
						if (postContent.getText().length() == 0)
						{
							Toast.makeText(NewPostActivity.this, R.string.missing_post_content, Toast.LENGTH_SHORT).show();
							return false;
						}

						return true;
					}

					@Override
					protected ResponseCode validateMessage() throws MessageSenderException, DataRetrieverException
					{
						return getMessageSender().postMessage(topic, getDataRetriever().getHashCheck(), postContent.getText().toString(), isSignatureEnable());
					}

					@Override
					protected boolean handleCodeResponse(ResponseCode code)
					{
						if (!super.handleCodeResponse(code))
						{
							switch (code)
							{		
								case POST_ADD_OK: // New post ok
									topic.setLastReadPost(NewPostUIHelper.BOTTOM_PAGE_ID);
									loadPosts(topic, topic.getNbPages(), false);
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
	
	protected TopicType getFromType() 
	{
		return fromType;
	}

	protected boolean isFromAllCats()
	{
		return fromAllCats;
	}
}