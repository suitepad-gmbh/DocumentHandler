package ch.ti8m.phonegap.plugins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

public class DocumentHandler extends CordovaPlugin {

	public static final String HANDLE_DOCUMENT_ACTION = "HandleDocumentWithURL";
	public static final int ERROR_NO_HANDLER_FOR_DATA_TYPE = 53;
	public static final int ERROR_UNKNOWN_ERROR = 1;
    private Executor asyncTasksExecutor = Executors.newFixedThreadPool(5);

	@Override
	public boolean execute(String action, JSONArray args,
			final CallbackContext callbackContext) throws JSONException {
		if (HANDLE_DOCUMENT_ACTION.equals(action)) {

			// parse arguments
			final JSONObject arg_object = args.getJSONObject(0);
			final String url = arg_object.getString("url");
            final JSONObject prefer = arg_object.getJSONObject("prefer");
			System.out.println("Found: " + url);

			// start async download task
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new FileDownloaderAsyncTask(callbackContext, url, prefer).executeOnExecutor(asyncTasksExecutor);
                }
            });

			return true;
		}
		return false;
	}

	// used for all downloaded files, so we can find and delete them again.
	private final static String FILE_PREFIX = "DH_";

	/**
	 * Returns the MIME Type of the file by looking at file name extension in
	 * the URL.
	 * 
	 * @param url
	 * @return
	 */
	private static String getMimeType(String url) {
		String mimeType = null;
		String extension = MimeTypeMap.getFileExtensionFromUrl(url);
		if (extension != null) {
			MimeTypeMap mime = MimeTypeMap.getSingleton();
			mimeType = mime.getMimeTypeFromExtension(extension);
		}

		System.out.println("Mime Type: " + mimeType);

		return mimeType;
	}

	private class FileDownloaderAsyncTask extends AsyncTask<Void, Double, File> {

		private final CallbackContext callbackContext;
		private final String url;
        private JSONObject prefer;
        private ProgressDialog pd;
        String error = null;

		public FileDownloaderAsyncTask(CallbackContext callbackContext,
				String url, JSONObject prefer) {
			super();
			this.callbackContext = callbackContext;
			this.url = url;
            this.prefer = prefer;

		}

		@Override
		protected File doInBackground(Void... arg0) {
            try {
                System.out.println("DOCUMENTHANDLER A");
                publishProgress(0.0);
                System.out.println("DOCUMENTHANDLER B");
                int totalRead = 0;
                int totalSize;
                double progress;
                // get an instance of a cookie manager since it has access to our
                // auth cookie
                CookieManager cookieManager = CookieManager.getInstance();

                System.out.println("DOCUMENTHANDLER C");
                // get the cookie string for the site.
                String auth = null;
                if (cookieManager.getCookie(url) != null) {
                    auth = cookieManager.getCookie(url).toString();
                }

                System.out.println("DOCUMENTHANDLER D");
                URL url2 = new URL(url);
                System.setProperty("http.keepAlive", "false");
                System.out.println("DOCUMENTHANDLER E");
                HttpURLConnection conn = (HttpURLConnection) url2.openConnection();

                System.out.println("DOCUMENTHANDLER F");
                conn.setRequestProperty("connection", "close");
                conn.setConnectTimeout(30 * 1000);
                conn.setReadTimeout(30 * 1000);
                if (auth != null) {
                    conn.setRequestProperty("Cookie", auth);
                }

                totalSize = conn.getContentLength();

                System.out.println("DOCUMENTHANDLER G");
                InputStream reader = conn.getInputStream();

                System.out.println("DOCUMENTHANDLER H");
                String extension = MimeTypeMap.getFileExtensionFromUrl(url);
                System.out.println("DOCUMENTHANDLER I");
                File f = File.createTempFile(FILE_PREFIX, "." + extension,
                        null);
                // make sure the receiving app can read this file
                f.setReadable(true, false);
                FileOutputStream outStream = new FileOutputStream(f);

                System.out.println("DOCUMENTHANDLER J");
                byte[] buffer = new byte[1024];
                int readBytes = reader.read(buffer);
                System.out.println("DOCUMENTHANDLER K");
                while (readBytes > 0) {
                    if(isCancelled()) {
                        System.out.println("DOCUMENTHANDLER L");
                        reader.close();
                        conn.disconnect();
                        return null;
                    }
                    totalRead += 1024;
                    if(totalSize > 0) {
                        progress = (float)totalRead / (float)totalSize;
                        publishProgress(progress);
                    }
                    outStream.write(buffer, 0, readBytes);
                    readBytes = reader.read(buffer);
                }
                System.out.println("DOCUMENTHANDLER N");
                reader.close();
                System.out.println("DOCUMENTHANDLER O");
                outStream.close();
                System.out.println("DOCUMENTHANDLER P");
                return f;

            } catch(SocketTimeoutException e) {
                System.out.println("DOCUMENTHANDLER Q");
                e.printStackTrace();
                error = e.getMessage();
                cancel(true);
            }
            catch (IOException e) {
                System.out.println("DOCUMENTHANDLER R");
                e.printStackTrace();
                error = e.getMessage();
                cancel(true);

            }
            System.out.println("DOCUMENTHANDLER S");
            return null;
		}
        @Override
        protected void onProgressUpdate(Double... values) {
            super.onProgressUpdate(values);
            if(pd.isIndeterminate()) {
                pd.hide();
                pd = new ProgressDialog(cordova.getActivity());
                pd.setIndeterminate(false);
                pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pd.show();
                pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        cancel(true);
                    }
                });
            }
            pd.setProgress((int)(values[0] * 100));
        }

        @Override
        protected void onPreExecute() {
            System.out.println("DOCUMENTHANDLER PREXEC");
            super.onPreExecute();
            pd = new ProgressDialog(cordova.getActivity());
            pd.setTitle("Processing...");
            pd.setMessage("Please wait.");
            pd.setIndeterminate(true);

            pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    cancel(true);
                }
            });
            pd.show();
        }

        @Override
        protected void onCancelled() {
            System.out.println("DOCUMENTHANDLER CANCELLED");
            super.onCancelled();
            pd.hide();
            if(error != null) {
                Toast.makeText(DocumentHandler.this.cordova.getActivity(), error, Toast.LENGTH_LONG).show();
            }
        }

        @Override
		protected void onPostExecute(File result) {
            System.out.println("DOCUMENTHANDLER POSTEXEC");
            pd.hide();

			Context context = cordova.getActivity().getApplicationContext();
            String preferredAct = null;
            String preferredPkg = null;
            String [] preferredTarget;

			// get mime type of file data
			String mimeType = getMimeType(url);


            if(this.prefer != null) {
                Iterator<String> iterator = this.prefer.keys();
                while (iterator.hasNext()) {
                    String next =  iterator.next();
                    if(MimeTypeMap.getFileExtensionFromUrl(url).equals(next)) {
                        try {
                            preferredTarget = this.prefer.getString(next).split("/");
                            preferredPkg = preferredTarget[0];
                            preferredAct = preferredTarget[1];
                            if(mimeType == null) {
                                mimeType = "application/pdf";
                            }
                            break;

                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (IndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }
                    }
                    
                }
            }
            if (mimeType == null) {
                callbackContext.error(ERROR_UNKNOWN_ERROR);
                return;
            }

			// start an intent with the file
			try {
				Intent intent = new Intent(Intent.ACTION_VIEW);

                if(preferredPkg != null
                        && preferredPkg.equals("com.adobe.reader")
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ) {
                    preferredPkg = "com.google.android.apps.docs";
                    preferredAct = "com.google.android.apps.viewer.PdfViewerActivity";
                }
                if(preferredAct != null) {
                    intent.setClassName(preferredPkg, preferredAct);
                }



				intent.setDataAndType(Uri.fromFile(result), mimeType);
//				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//				context.startActivity(intent);
                cordova.getActivity().startActivity(intent);

				callbackContext.success(); // Thread-safe.
			} catch (ActivityNotFoundException e) {
				// happens when we start intent without something that can
				// handle it
				e.printStackTrace();
				callbackContext.error(ERROR_NO_HANDLER_FOR_DATA_TYPE);
			}

		}
	}

}
