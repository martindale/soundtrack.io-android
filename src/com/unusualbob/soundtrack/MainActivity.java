package com.unusualbob.soundtrack;

import android.os.Bundle;
import android.os.PowerManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewFragment;

import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerFragment;
import com.google.android.youtube.player.YouTubePlayer.PlayerStyle;

public class MainActivity extends YouTubeFailureRecoveryActivity
{
	String TAG = "MainActivity";
	WebViewFragment webViewFragment;
	WebView webView;
	YouTubePlayer ytPlayer;
	PowerManager.WakeLock wl;

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//Wakelock init
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sountrack.io");

		//Webview init
		webViewFragment = (WebViewFragment) getFragmentManager().findFragmentById(R.id.webpage);
		webView = webViewFragment.getWebView();

		//Make sure our webview uses the chrome client so we can have awesome javascript
		webView.setWebChromeClient(new WebChromeClient());

		// requires javascript
		webView.getSettings().setJavaScriptEnabled(true);

		JsInterface jsInterface = new JsInterface();

		// provide an interface accessible to javascript
		webView.addJavascriptInterface(jsInterface, "android");

		// load the page
		webView.loadUrl("http://thegridcraft.com:13000/android");

		//Initialize the youtube player
		YouTubePlayerFragment youTubePlayerFragment = (YouTubePlayerFragment) getFragmentManager().findFragmentById(R.id.youtube_fragment);
		youTubePlayerFragment.initialize("AIzaSyA-tc5B-RQ5Vp4DtGY9vD24B-wL3pBnKf0", this);

	}
	
	//This method is from the base class and gives us access to the player
	@Override
	public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer player, boolean wasRestored)
	{
		ytPlayer = player;
		player.setPlayerStyle(PlayerStyle.CHROMELESS);
	}

	@Override
	protected YouTubePlayer.Provider getYouTubePlayerProvider()
	{
		return (YouTubePlayerFragment) getFragmentManager().findFragmentById(R.id.youtube_fragment);
	}

	//This is our really basic javascript interface
	public class JsInterface
	{
		//Shows a basic log statement
		public void showLog(String msg)
		{
			Log.d("Log from JS", msg);
		}

		//Starts a track at the specified number of milliseconds
		public void startTrack(String id, int seek)
		{
			ytPlayer.loadVideo(id, seek);
		}
	}

	//TODO: make an actual menu of some kind
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}


}
