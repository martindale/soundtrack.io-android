package io.soundtrack.activities;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

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

import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayer.ErrorReason;
import com.google.android.youtube.player.YouTubePlayer.PlaybackEventListener;
import com.google.android.youtube.player.YouTubePlayer.PlayerStateChangeListener;
import com.google.android.youtube.player.YouTubePlayerFragment;
import com.google.android.youtube.player.YouTubePlayer.PlayerStyle;

import io.soundtrack.R;
import io.soundtrack.common.ChatAdapter;
import io.soundtrack.common.ChatItem;
import io.soundtrack.common.Configuration;

import de.roderick.weberknecht.WebSocket;
import de.roderick.weberknecht.WebSocketEventHandler;
import de.roderick.weberknecht.WebSocketException;
import de.roderick.weberknecht.WebSocketMessage;

public class MainActivity extends YouTubeFailureRecoveryActivity
{
	//Misc Variables
	String TAG = "MainActivity";
	String currentTrack;
	int retryIdx;
	Boolean exiting = false;
	Boolean started = false;

	//Initialize
	YouTubePlayer ytPlayer;
	PowerManager.WakeLock wl;
	WebSocket websocket;
	URI uri;
	AsyncWebsocket mySocket;

	//UI
	static ArrayList<ChatItem> chats = new ArrayList<ChatItem>();
	static ChatAdapter adapter;
	static TextView songTitle;
	static TextView curator;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Log.d(TAG, "Oncreate called");

		// Wakelock init
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Sountrack.io");
		wl.acquire();

		// Initialize the youtube player
		YouTubePlayerFragment youTubePlayerFragment = (YouTubePlayerFragment) getFragmentManager().findFragmentById(R.id.youtube_fragment);
		youTubePlayerFragment.initialize(Configuration.DEVELOPER_KEY, this);

		// Initialize title and curator views
		songTitle = (TextView) findViewById(R.id.songTitle);
		curator = (TextView) findViewById(R.id.curator);

		// Initialize chat
		adapter = new ChatAdapter(chats);
		ListView chatview = (ListView) findViewById(R.id.list);
		chatview.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
		chatview.setStackFromBottom(true);
		chatview.setAdapter(adapter);

		// Initialize reload button
		Button btnReload = (Button) findViewById(R.id.reload);
		btnReload.setOnClickListener(new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				try
				{
					websocket.close();
				}
				catch (WebSocketException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				reconnect();
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
		try
		{
			websocket.close();
		}
		catch (WebSocketException e)
		{
			e.printStackTrace();
		}
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
			mySocket = new AsyncWebsocket();
			mySocket.execute();
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

	// This starts the websocket connection and sets up the listeners for all events, it will auto-reconnect unless we are quiting the app
	class AsyncWebsocket extends AsyncTask<Void, Void, Void>
	{

		@Override
		protected Void doInBackground(Void... params)
		{
			try
			{
				uri = new URI("wss://soundtrack.io/stream/websocket");
				websocket = new WebSocket(uri);
				final String webtag = "websocket";

				websocket.setEventHandler(new WebSocketEventHandler()
				{

					@Override
					public void onOpen()
					{
						Log.d(webtag, "socket open");
					}

					@Override
					public void onMessage(WebSocketMessage message)
					{
						retryIdx = 0; // reset our retry timeouts
						// Log.d(webtag, "message: " + message.getText());

						try
						{
							JSONObject msg = new JSONObject(message.getText());

							if (msg.get("type").toString().equals("ping"))
							{
								// Log.d(webtag, "Ping!");
								websocket.send("{\"type\":\"pong\"}");
							}
							else if (msg.get("type").toString().equals("chat"))
							{
								// Log.d(webtag, "Chat!");
								runOnUiThread(new AddChatRunnable(msg.getJSONObject("data").getString("formatted").toString()));
							}
							else if (msg.get("type").toString().equals("track"))
							{
								// Log.d(webtag, "New track!");
								String track = msg.getJSONObject("data").getJSONObject("sources").getJSONArray("youtube").getJSONObject(0)
										.getString("id");
								try {
									ytPlayer.loadVideo(track, (int) (msg.getDouble("seekTo") * 1000));
								}
								catch (Exception e) {
									e.printStackTrace();
								}

								String curator = "The Machine";
								if (msg.getJSONObject("data").has("curator")) {
									curator = msg.getJSONObject("data").getJSONObject("curator").getString("username");
								}
								
								runOnUiThread(new SongInfoRunnable(msg.getJSONObject("data").getString("title"), curator));
							}
							else
							{
								Log.w(webtag, "socket type: " + msg.get("type").toString());
							}
						}
						catch (JSONException e)
						{
							e.printStackTrace();
						}
						catch (WebSocketException e)
						{
							e.printStackTrace();
						}
					}

					@Override
					public void onClose()
					{
						Log.d(webtag, "connection closed");
						reconnect();
					}

					@Override
					public void onPing()
					{
						// Auto stub

					}

					@Override
					public void onPong()
					{
						// Auto stub

					}
				});

				websocket.connect();
			}
			catch (WebSocketException wse)
			{
				wse.printStackTrace();
				reconnect();
			}
			catch (URISyntaxException use)
			{
				use.printStackTrace();
			}

			return null;
		}
	}

	// A function we can call to restart the socket
	void reconnect()
	{
		Log.w("Socket", "Reconnecting...");
		if (!exiting)
		{
			TimerTask task = new TimerTask()
			{
				public void run()
				{
					mySocket.cancel(true);
					
					websocket = null;
					mySocket = null;
					
					mySocket = new AsyncWebsocket();
					mySocket.execute();
				}
			};
			Timer timer = new Timer();
			timer.schedule(task, 100);
		}
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

	};

}
