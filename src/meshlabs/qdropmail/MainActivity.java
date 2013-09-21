package meshlabs.qdropmail;

import meshlabs.qdropmail.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
	private final static String TAG = "QDropMain";
	
	private TextView view;
	
	public void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		view = new TextView(this);
		view.setTextSize(24);
		view.setGravity(Gravity.CENTER);
		view.setText(R.string.default_text);
		setContentView(view);
	    
	}
	
	public void onStart() {
		super.onStart();
		
	    Intent intent = getIntent();
	    String action = intent.getAction();
	    
	    if (Intent.ACTION_SEND.equals(action)) {
	    	view.setText(R.string.processing_text);
	        Thread uploader = new Thread(new BackgroundUploader(this, intent));
	    	uploader.start();
	    } else if (BackgroundUploader.UPLOAD_ERROR_ACTION.equals(action)){
	    	view.setText(R.string.upload_error_text);
	    }
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		// We got here after linking Dbx account
		if (requestCode == BackgroundUploader.DBX_ACCOUNT_LINK_REQUEST) {
			Log.i(TAG, "Dropbox account is now linked to QuickDropShare");
			view.setText(R.string.linked_account_text);
		}
	}
	

}
