package io.soundtrack.activities;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import android.R.string;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.media.*;

import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayer.ErrorReason;
import com.google.android.youtube.player.YouTubePlayer.PlaybackEventListener;
import com.google.android.youtube.player.YouTubePlayer.PlayerStateChangeListener;
import com.google.android.youtube.player.YouTubePlayerFragment;
import com.google.android.youtube.player.YouTubePlayer.PlayerStyle;

import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;

import io.soundtrack.R;
import io.soundtrack.common.ChatAdapter;
import io.soundtrack.common.ChatItem;
import io.soundtrack.common.Configuration;

/* import de.roderick.weberknecht.WebSocket;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage; */

public class MainActivity extends YouTubeFailureRecoveryActivity implements WebSocketConnectionObserver
{
	//Misc Variables
	String TAG = "MainActivity";
	String currentTrack;
	int retryIdx;
	Boolean exiting = false;
	Boolean started = false;

	 private MediaPlayer mPlayer;
		private Uri tempUri;
		private AudioManager audiomanager;
		private float volume;

	//Initialize
	YouTubePlayer ytPlayer;
	PowerManager.WakeLock wl;
	//WebSocket websocket;
	URI uri;

	private WebSocketConnection websocket = new WebSocketConnection();
	private WebSocketConnection mConnection;
	private URI mServerURI;

	//UI
	static ArrayList<ChatItem> chats = new ArrayList<ChatItem>();
	static ChatAdapter adapter;
	static TextView songTitle;
	static TextView curator;
	static TextView progress;

	class UpdateTimerTask implements Runnable {

	  Long millis = (long) 0;
	  SimpleDateFormat df = new SimpleDateFormat("mm:ss");

	  public UpdateTimerTask(int i) {
		 millis = (long) i;
	  }

	@Override
	  public void run() {
		  	millis += 1000;
			String time = df.format(new Date( millis ));
			progress.setText( time );
	  } 
	}

	public void connect() {
		this.mConnection = new WebSocketConnection();

		try {
			this.mServerURI = new URI("wss://soundtrack.io/stream/websocket");

			mConnection.connect(mServerURI, this);

		} catch (URISyntaxException e) {
			String message = e.getLocalizedMessage();
			Log.e(TAG, message);
		} catch (WebSocketException e) {
			String message = e.getLocalizedMessage();
			Log.e(TAG, message);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Log.d(TAG, "Oncreate called");

		// Wakelock init
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "soundtrack.io");
		wl.acquire();

		// Initialize the youtube player
		YouTubePlayerFragment youTubePlayerFragment = (YouTubePlayerFragment) getFragmentManager().findFragmentById(R.id.youtube_fragment);
		youTubePlayerFragment.initialize(Configuration.DEVELOPER_KEY, this);

		audiomanager = (AudioManager) getSystemService(this.AUDIO_SERVICE);
		 // declare your mp3 player
        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(audiomanager.STREAM_MUSIC);
		
		// Initialize title and curator views
		songTitle = (TextView) findViewById(R.id.songTitle);
		curator = (TextView) findViewById(R.id.curator);
		progress = (TextView) findViewById(R.id.progress);

		// Initialize chat
		adapter = new ChatAdapter(chats);
		ListView chatview = (ListView) findViewById(R.id.list);
		chatview.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		chatview.setStackFromBottom(true);
		chatview.setAdapter(adapter);
		
		ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
		
		UpdateTimerTask myTimer = new UpdateTimerTask( 0 );
		exec.scheduleAtFixedRate(myTimer, 0, 100, TimeUnit.MICROSECONDS);

		// Initialize reload button
		Button btnReload = (Button) findViewById(R.id.reload);
		btnReload.setOnClickListener(new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				mConnection.disconnect();
				connect();
			}

		});
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if (started) {
			ytPlayer.play();
		}
	}

	// Make sure to close background tasks on back press
	public void onBackPressed()
	{
		exiting = true;
		websocket.disconnect();
		wl.release();
		finish();
	}

	// This method is from the base class and gives us access to the player
	@Override
	public void onInitializationSuccess(YouTubePlayer.Provider provider, final YouTubePlayer player, boolean wasRestored)
	{
		// set our class variable and make the player less bloaty
		ytPlayer = player;
		player.setPlayerStyle(PlayerStyle.CHROMELESS);

		// Start websocket
		if (!started) {
			started = true;
			Log.d("Socket", "Start a new socket since we dont have one yet");
			connect();
			
			/*/mySocket = new AsyncWebsocket();
			mySocket.execute();/**/
		}

		// Listen for player events so we can resume playback when the screen gets turned off
		player.setPlaybackEventListener(new PlaybackEventListener()
		{

			@Override
			public void onBuffering(boolean arg0)
			{
				Log.d("Player", "Buffering: " + arg0);
			}

			@Override
			public void onPaused()
			{
				// For now the only reason for pauses is a screen-off, we try to
				// resume after 500ms
				Log.d("Youtube Player", "Paused");
//				if (!exiting)
//				{
//					TimerTask task = new TimerTask()
//					{
//						public void run()
//						{
//							Log.d("Player", "Resume Track");
//							try {
//								player.loadVideo(currentTrack, player.getCurrentTimeMillis() + 1500);
//							}
//							catch(Exception e) {
//								e.printStackTrace();
//							}
//						}
//					};
//					Timer timer = new Timer();
//					timer.schedule(task, 100);
//				}

			}

			@Override
			public void onPlaying()
			{
				Log.d("Player", "playing");
			}

			@Override
			public void onSeekTo(int arg0)
			{
				// Auto stub
			}

			@Override
			public void onStopped()
			{
				Log.d("Youtube Player", "Stopped");
			}

		});

		// This doesn't do much right now, we will eventually use it to send errors to the server
		player.setPlayerStateChangeListener(new PlayerStateChangeListener()
		{

			@Override
			public void onAdStarted()
			{
				// Auto stub
			}

			@Override
			public void onError(ErrorReason arg0)
			{
				// TODO Intercept 'un-playable' error here and send back to the server
				Log.e("Youtube Player", "Error: " + arg0);

			}

			@Override
			public void onLoaded(String arg0)
			{
				// Auto stub
			}

			@Override
			public void onLoading()
			{
				// Auto stub
			}

			@Override
			public void onVideoEnded()
			{
				// Auto stub
			}

			@Override
			public void onVideoStarted()
			{
				// Auto stub
			}

		});
	}

	//Just a default stub from youtube API
	@Override
	protected YouTubePlayer.Provider getYouTubePlayerProvider()
	{
		return (YouTubePlayerFragment) getFragmentManager().findFragmentById(R.id.youtube_fragment);
	}

	// TODO: make an actual menu of some kind
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private static class SongInfoRunnable implements Runnable
	{
		private final String title;
		private final String curatorName;

		SongInfoRunnable(String title, String curator)
		{
			this.title = title;
			this.curatorName = curator;
		}

		@Override
		public void run()
		{
			songTitle.setText(title);
			curator.setText(curatorName);
		}

	};

	private static class AddChatRunnable implements Runnable
	{
		private final String message;

		AddChatRunnable(String message)
		{
			this.message = message;
		}

		@Override
		public void run()
		{
			Document doc = Jsoup.parse(message);
			Element content = doc.getElementsByClass("message-content").first();
			Element author = doc.getElementsByAttribute("data-user-username").first();
			chats.add(new ChatItem(author.text(), content.text()));
			adapter.notifyDataSetChanged();
		}

	}

	@Override
	public void onOpen() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onClose(WebSocketCloseNotification code, String reason) {
		// TODO Auto-generated method stub
		
	}
	
	public void send(String message) {

		mConnection.sendTextMessage(message);

	}

	@Override
	public void onTextMessage(String payload) {
		String message = payload;
		Log.d(TAG, message);

		try {
		JSONObject msg = new JSONObject(message);

		if (msg.get("type").toString().equals("ping"))
		{
			// Log.d(webtag, "Ping!");
			send("{\"type\":\"pong\"}");
		}
		else if (msg.get("type").toString().equals("chat"))
		{
			// Log.d(webtag, "Chat!");
			runOnUiThread(new AddChatRunnable(msg.getJSONObject("data").getString("formatted").toString()));
		}
		else if (msg.get("type").toString().equals("track"))
		{
			
			// Log.d(webtag, "New track!");
			mPlayer.stop();
			ytPlayer.pause();
			
			ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
			
			UpdateTimerTask myTimer = new UpdateTimerTask( (int) (msg.getDouble("seekTo") * 1000) );
			exec.scheduleAtFixedRate(myTimer, 0, 100, TimeUnit.MICROSECONDS);
			
			// TODO: make selection more robust, perhaps use _sources flat array
			// see:  https://github.com/martindale/soundtrack.io/issues/78
			JSONArray youtubeVideos = msg.getJSONObject("sources").getJSONArray("youtube");
			JSONArray soundcloudFiles = msg.getJSONObject("sources").getJSONArray("soundcloud");
			
			String firstSoundcloudID;
			String soundcloudURI = "";
			if (soundcloudFiles.length() > 0) {
				
				// TODO: loop through sources, exit loop (break) when one begins playing
				for (int i = 0; i < soundcloudFiles.length(); i++) {
					
					
				}
				firstSoundcloudID = soundcloudFiles.getJSONObject(0)
					.getString("id");
				soundcloudURI = "http://api.soundcloud.com/tracks/"+firstSoundcloudID+"/stream?client_id=7fbc3f4099d3390415d4c95f16f639ae";

				tempUri = Uri.parse(soundcloudURI);
				// provide mp3 player with file location
		        try {
		        	mPlayer.setDataSource(this,tempUri);
				}catch (IllegalArgumentException ex) {
					// TODO Auto-generated catch block
					ex.printStackTrace();
				}catch (SecurityException ex) {
					// TODO Auto-generated catch block
					ex.printStackTrace();
				}catch (IllegalStateException ex) {
					// TODO Auto-generated catch block
					ex.printStackTrace();
				}catch (IOException ex) {
					// TODO Auto-generated catch block
					ex.printStackTrace();
				}
	
				// prepare mp3 player
				try {
					mPlayer.prepare();
				}catch (IllegalStateException ex) {
					// TODO Auto-generated catch block
					ex.printStackTrace();
				}catch (IOException ex) {
					// TODO Auto-generated catch block
					ex.printStackTrace();
				} finally {
					// start mp3 player
					mPlayer.start();
					mPlayer.seekTo((int) (msg.getDouble("seekTo") * 1000));
				}
				
			}
			
			/*String firstYoutubeVideo = "";
			if (youtubeVideos.length() > 0) {
				firstYoutubeVideo = youtubeVideos.getJSONObject(0)
					.getString("id");
				try {
					ytPlayer.loadVideo(firstYoutubeVideo, (int) (msg.getDouble("seekTo") * 1000));
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}*/

			String curator = "The Machine";
			if (msg.getJSONObject("data").has("curator")) {
				curator = msg.getJSONObject("data").getJSONObject("curator").getString("username");
			}

			String extendedTitle = msg.getJSONObject("data").getJSONObject("_artist").getString("name") + " Ð " + msg.getJSONObject("data").getString("title");
			runOnUiThread(new SongInfoRunnable( extendedTitle , curator));
		}
		else
		{
			Log.w(TAG, "socket type: " + msg.get("type").toString());
		}
		} catch (JSONException e)
		{
			e.printStackTrace();
		}

	}

	@Override
	public void onRawTextMessage(byte[] payload) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onBinaryMessage(byte[] payload) {
		// TODO Auto-generated method stub
		
	};

}
