package meshlabs.qdropmail;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxException;
import com.dropbox.sync.android.DbxException.Unauthorized;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

public class BackgroundUploader implements Runnable {
	public final static int DBX_ACCOUNT_LINK_REQUEST = 42;
	public final static String UPLOAD_ERROR_ACTION = "meshlabs.qdropmail.UPLOAD_ERROR_ACTION";
	
	private final static String TAG = "BackgroundUploader";
	private final static String DBX_KEY = "put app key here";	
	private final static String DBX_SECRET = "put app secret here";
	
	private final Activity activity;
	private final Intent intent;
	private final DbxAccountManager mgr;

	BackgroundUploader(Activity a, Intent i) {
		this.activity = a;
		this.intent = i;
		
		mgr = DbxAccountManager.getInstance(activity.getApplicationContext(), DBX_KEY, DBX_SECRET);
	}
	
	public void run() {
		if (!mgr.hasLinkedAccount()) {
			mgr.startLink(activity, DBX_ACCOUNT_LINK_REQUEST);
			return; 
		}
		
		URL url = uploadAndGetUrl(intent);
		if (url == null) {	
			// Tell the GUI there was some sort of problem
			Log.i(TAG, "Couldn't get share URL!");
			Intent intent = new Intent();
			intent.setAction(UPLOAD_ERROR_ACTION);
			intent.setClass(activity, MainActivity.class);
			activity.startActivity(intent);
		} else {
			// Everything worked, we can close the GUI activity
			shareToGmail(url.toString());
			activity.finish();
		}
	}
	
	private URL uploadAndGetUrl(Intent intent) {
		Uri content = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
		String mimetype = intent.getType();
		
		if (content == null) {
			Log.i(TAG, "No content URI, can't process intent!");
			throw new IllegalArgumentException("No content to upload!");
		}
		
		Log.i(TAG, "Got intent with uri="+content+" type="+mimetype);

		// We don't don't know filename and extension, just a MIME type, so get the right extension and
		// generate a random filename.
		String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(intent.getType());
		String filename = UUID.randomUUID().toString();
		if (extension != null) {
			filename = filename + "." + extension;
		}
		
		DbxFileSystem dbxFs = getDropboxFileSystem();
		
		if (dbxFs == null) {
			Log.e(TAG, "Couldn't acquire Dropbox filesystem");
			return null;
		}
		
		DbxFile testFile = null;
		try {
			testFile = dbxFs.create(new DbxPath(filename));
			FileOutputStream outStream = testFile.getWriteStream();
			InputStream inStream = activity.getContentResolver().openInputStream(content);
			copyStream(inStream, outStream);
		} catch (Exception e) {
			Log.e(TAG, "Exception while copying file to Dropbox");
			e.printStackTrace();
		} finally {
		    if (testFile != null) 
		    	testFile.close();
		}
		
		URL fileLink = null;
		try {
			fileLink = dbxFs.fetchShareLink(testFile.getPath(), true);
		} catch (DbxException e) {
			Log.e(TAG, "Unable to get dbx share link");
			e.printStackTrace();
		}
		
		return fileLink;
	}
	
	private DbxFileSystem getDropboxFileSystem() {
		DbxFileSystem dbxFs = null;
		try {
			dbxFs = DbxFileSystem.forAccount(mgr.getLinkedAccount());
		} catch (Unauthorized e) {
			e.printStackTrace();
		}
		
		return dbxFs;
	}
	
	/**
	 * Launch an intent to compose an email in Gmail with given text in the body
	 */
	private void shareToGmail(String content) {
	    Intent emailIntent = new Intent(Intent.ACTION_SEND);
	    emailIntent.setType("text/plain");
	    emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, content);
	    final PackageManager pm = activity.getPackageManager();
	    final List<ResolveInfo> matches = pm.queryIntentActivities(emailIntent, 0);
	    ResolveInfo best = null;
	    for(final ResolveInfo info : matches) {
	    	//Log.i(TAG, "intentActivity: pkg="+info.activityInfo.packageName+" name="+info.activityInfo.name);
	        if (info.activityInfo.packageName.endsWith(".gm") || info.activityInfo.name.toLowerCase().contains("gmail")) {
	            best = info;
	        }
	    }
	    if (best != null) {
	        emailIntent.setClassName(best.activityInfo.packageName, best.activityInfo.name);
	    }
	    activity.startActivity(emailIntent);
	}
	
	/**
	 * Utility method to copy contents of an input stream to an outputstream.
	 */
	private static void copyStream(InputStream input, OutputStream output)
		    throws IOException {
	    byte[] buffer = new byte[1024]; 
	    int bytesRead;
	    while ((bytesRead = input.read(buffer)) != -1)
	    {
	        output.write(buffer, 0, bytesRead);
	    }
	}
}
