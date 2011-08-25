package org.ebulabs.hotspot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class ChooseTechActivity extends Activity {
	
	android.net.wifi.WifiManager.MulticastLock mcastlock;
	
	private void toast(String t) {
		Toast.makeText(getApplicationContext(), t, Toast.LENGTH_SHORT).show();
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.techchoice);

        Context context = getApplicationContext();
        
        android.net.wifi.WifiManager wifi =
        	(android.net.wifi.WifiManager)context.getSystemService(android.content.Context.WIFI_SERVICE);
        
        mcastlock = wifi.createMulticastLock("HotspotDnsSDLock");
        mcastlock.setReferenceCounted(true);
        mcastlock.acquire();
        
        DiscoverHotspot dh = new DiscoverHotspot();
        String zeroconf_url;
        
        // TODO: do better
        while ((zeroconf_url = dh.getHotspotLocation()) == null) {}
        
        HotspotApplication app = ((HotspotApplication)getApplication());
        
        app.hotspotURL = zeroconf_url;
        
        Log.i(Utils.LOGTAG + "onCreate choose tech", "URL from mdns " + zeroconf_url);
        

        /* Get list of techs from daemon */
        
        URL url;
        //String url_sz = getString(R.string.daemon_url) + "/capabilities";
        String url_sz = zeroconf_url + "/capabilities";
		try {
			url = new URL(url_sz);
		} catch (MalformedURLException e) {
			toast("Malformed URL '" + url_sz + "'");
			return;
		}
		
		Log.d(Utils.LOGTAG + "onCreate choose tech", "URL defined");
		
		//ArrayAdapter<String> techListAdapter = new ArrayAdapter<String>(this, R.id.techList, )
		
		ListView techList = (ListView)findViewById(R.id.techList);
        
		techList.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                int position, long id) {
            	
            	String techname = ((TextView) view).getText().toString();
            	
            	HotspotApplication app = (HotspotApplication)getApplication();
            	
            	for (Tech t : app.allTechs) {
     	    	   if (t.name.equals(techname)) {
     	    		   app.activeTech = t;
     	    		   break;
     	    	   }
     	        }
            	
            	 
            	
            	startActivity(new Intent("org.ebulabs.hotspot.CHOOSE_PROGRAMME"));
            }
          });
		
        URLConnection conn;
		try {
			
			conn = url.openConnection();
			
			Log.d(Utils.LOGTAG + "onCreate choose tech", "Connection opened");
		
	        if (!conn.getContentType().equals("text/xml")) {
	        	toast("Error: Content-Type is not text/xml !");
	        	Log.e(Utils.LOGTAG + "onCreate choose tech", "Content type is '" + conn.getContentType() + "'");
	        }
	        
	        XMLCapabilitiesParser p;
	        
			p = new XMLCapabilitiesParser(conn.getInputStream());

	        Log.d(Utils.LOGTAG + "onCreate choose tech", "Parsed");
				        
			ArrayList<String> technames = new ArrayList<String>();
			
			((HotspotApplication)getApplication()).allTechs = p.techs;
			
	        for (Tech t : p.techs) {
	    	   technames.add(t.name);
	        }
	        
	        Log.d(Utils.LOGTAG + "onCreate choose tech", "Created & filled technames");
	        
	        techList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, technames));
		} 
		catch (HotspotException e) {
			e.printStackTrace();
			toast("XML capabilities parser exception!");
			Log.e(Utils.LOGTAG + "onCreate choose tech", "XMLCapaParser raised exception");
			return;
			
		} catch (IOException e) {
			toast("IO Exception");
			e.printStackTrace();
			return;
		}
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	if (this.mcastlock != null && this.mcastlock.isHeld())
    		this.mcastlock.release();
    }
    
}
