package info.toyonos.hfr4droid.common.activity;

import info.toyonos.hfr4droid.common.HFR4droidApplication;
import info.toyonos.hfr4droid.common.R;
import info.toyonos.hfr4droid.common.core.bean.Category;
import info.toyonos.hfr4droid.common.core.bean.Theme;
import info.toyonos.hfr4droid.common.core.bean.Topic.TopicType;
import info.toyonos.hfr4droid.common.util.asynctask.DataRetrieverAsyncTask;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * <p>Splash screen de l'application</p>
 * 
 * @author ToYonos
 *
 */
public class SplashActivity extends HFR4droidActivity
{
	private DataRetrieverAsyncTask<?, ?> task = null;
	private Thread waitingThread = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getActionBar().hide();
		setContentView(R.layout.splash);
		applyTheme(currentTheme);
		
		RotateAnimation anim = new RotateAnimation(0f, 350f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		anim.setInterpolator(new LinearInterpolator());
		anim.setRepeatCount(Animation.INFINITE);
		anim.setDuration(700);

		ImageView splash = (ImageView) findViewById(R.id.SplashAnimation);
		splash.setBackgroundResource(getDrawableKey(currentTheme.getSpinner()));
		splash.startAnimation(anim);
		
		run();
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		updateLogo();
	}
	
	public void run()
	{
		waitingThread = new Thread(new Runnable()
		{	
			public void run()
			{
				try
				{
					Thread.sleep(1000);
					runOnUiThread(new Runnable()
					{	
						public void run()
						{
							doAction();
						}
					});
				}
				catch (InterruptedException e)
				{
					Log.d(HFR4droidApplication.TAG, "Launch cancelled");
					finish();
				}
			}
		});
		waitingThread.start();
	}
	
	private void doAction()
	{
		int welcomeScreen = getWelcomeScreen();
		if (welcomeScreen > 0 && isLoggedIn())
		{
			task = loadTopics(Category.ALL_CATS, TopicType.fromInt(welcomeScreen), 1, false, false);
		}
		else
		{
			task = loadCats(false, false);
		}
	}
	
	public void retry(final Exception e)
	{
		runOnUiThread(new Runnable()
		{
			public void run()
			{
				new AlertDialog.Builder(SplashActivity.this)
				.setTitle(R.string.error_splash_title)
				.setMessage(getMessage(e, null))
				.setOnCancelListener(new OnCancelListener()
				{
					public void onCancel(DialogInterface dialog)
					{
						finish();
					}
				})
				.setPositiveButton(R.string.error_splash_retry,  new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which) 
					{
						doAction();
					}
				})
				.setNegativeButton(R.string.error_splash_cancel, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which) 
					{
						finish();
					}
				}).show();
			}
		});
	}

	@Override
	public void onConfigurationChanged(Configuration conf)
	{
		super.onConfigurationChanged(conf);
		updateLogo();
	}

	protected void updateLogo()
	{
		Display display = getWindowManager().getDefaultDisplay();
		boolean landscape = display.getWidth() > display.getHeight();
		SimpleDateFormat sdf = new SimpleDateFormat("MM");
		boolean december = sdf.format(new Date()).equals("12");
		
		ImageView logo = (ImageView) findViewById(R.id.SplashLogo);
		logo.setImageResource(landscape ? (
		december ? R.drawable.logo_medium_xmas : R.drawable.logo_medium) : (
		december ? R.drawable.logo_big_xmas : R.drawable.logo_big));
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) 
	{
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
			if (task != null)
			{
				task.cancel(true);
				task = null;
			}
			waitingThread.interrupt();
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void setTitle()
	{
		TextView title = (TextView) findViewById(R.id.VersionAndAuthor);
		title.setText(getString(R.string.splash_title, getVersionName()));
	}

	@Override
	protected void applyTheme(Theme theme)
	{
		LinearLayout container = (LinearLayout) findViewById(R.id.SplashContainer);
		container.setBackgroundColor(theme.getListBackgroundColor());
		
		TextView title = (TextView) findViewById(R.id.VersionAndAuthor);
		title.setTextColor(theme.getSplashTitleColor());
	}
}
