package org.ebulabs.hotspot;

import java.util.Map;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class PlayProgrammeActivity extends Activity {
	
	private MediaPlayer mediaPlayer;

	private void toast(String t) {
		Toast.makeText(getApplicationContext(), t, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.playprogramme);
		TextView v = (TextView)findViewById(R.id.playText);
		HotspotApplication app = (HotspotApplication)getApplication();

		if (app.pi == null) {
			toast("App.pi is null !");
			Log.e(Utils.LOGTAG + "PlayProgrammeActivity", "app.pi null");
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(app.pi.name + "\n" + app.pi.url + "\n\n");
		Map<String,String> info = app.pi.getInfo();

		for (Map.Entry<String, String> e : info.entrySet()) {
			sb.append(e.getKey() + ": " + e.getValue() + "\n");
		}
		
		v.setText(sb.toString());
				
		this.mediaPlayer = new MediaPlayer();
		try {
			Log.d(Utils.LOGTAG + "PlayProgrammeActivity", "startAudioStream");
			Utils.startAudioStream(this.mediaPlayer, app.pi.url);
		} catch (HotspotException e) {
			toast("App.pi is null !");
		
		}
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		Utils.stopAudioStream(this.mediaPlayer);
		this.mediaPlayer.release();
	}

}