package com.cowbell.cordova.geofence;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * for send data api
 */
import java.net.URLConnection;
import java.net.URL;
import java.util.TimeZone;


public class ReceiveTransitionsIntentService extends IntentService {
    protected static final String GeofenceTransitionIntent = "com.cowbell.cordova.geofence.TRANSITION";
    protected BeepHelper beepHelper;
    protected GeoNotificationNotifier notifier;
    protected GeoNotificationStore store;
    protected URL url;

    /**
     * Sets an identifier for the service
     */
    public ReceiveTransitionsIntentService() {
        super("ReceiveTransitionsIntentService");
        beepHelper = new BeepHelper();
        store = new GeoNotificationStore(this);
        Logger.setLogger(new Logger(GeofencePlugin.TAG, this, false));
    }

    protected void asingUrl(){
      try{
        this.url = new URL("https://api.lockerroomapp.com/geofence");
        //this.url = new URL("http://192.168.8.105:8187/geofence");
      }
      catch (IOException e){
        Logger logger = Logger.getLogger();
        logger.log(Log.ERROR, e.getMessage());
      }
    }

    /**
     * Handles incoming intents
     *
     * @param intent
     *            The Intent sent by Location Services. This Intent is provided
     *            to Location Services (inside a PendingIntent) when you call
     *            addGeofences()
     */
    @Override
    protected void onHandleIntent(Intent intent) {
        Logger logger = Logger.getLogger();
        logger.log(Log.DEBUG, "ReceiveTransitionsIntentService - onHandleIntent");
        Intent broadcastIntent = new Intent(GeofenceTransitionIntent);
        notifier = new GeoNotificationNotifier(
            (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE),
            this
        );

        // TODO: refactor this, too long
        // First check for errors
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            // Get the error code with a static method
            int errorCode = geofencingEvent.getErrorCode();
            String error = "Location Services error: " + Integer.toString(errorCode);
            // Log the error
            logger.log(Log.ERROR, error);
            broadcastIntent.putExtra("error", error);
        } else {
            // Get the type of transition (entry or exit)
            int transitionType = geofencingEvent.getGeofenceTransition();
            if ((transitionType == Geofence.GEOFENCE_TRANSITION_ENTER)
                    || (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT)) {
                logger.log(Log.DEBUG, "Geofence transition detected");
                List<Geofence> triggerList = geofencingEvent.getTriggeringGeofences();
                List<GeoNotification> geoNotifications = new ArrayList<GeoNotification>();
                for (Geofence fence : triggerList) {
                    String fenceId = fence.getRequestId();
                    GeoNotification geoNotification = store
                            .getGeoNotification(fenceId);

                    if (geoNotification != null) {
                        if (geoNotification.notification != null) {
                            //notifier.notify(geoNotification.notification);
                            logger.log(Log.DEBUG, "not sent notification");
                        }
                        geoNotification.transitionType = transitionType;
                        geoNotifications.add(geoNotification);
                    }
                }

                if (geoNotifications.size() > 0) {

                    broadcastIntent.putExtra("transitionData", Gson.get().toJson(geoNotifications));
                    GeofencePlugin.onTransitionReceived(geoNotifications);

                    HttpURLConnection conn;
                    OutputStream os;
                    InputStream is;
                    try{
                      logger.log(Log.DEBUG, "send transition api");
                      this.asingUrl();
                      conn = (HttpURLConnection) this.url.openConnection();

                      conn.setReadTimeout( 10000 /*milliseconds*/ );
                      conn.setConnectTimeout( 15000 /* milliseconds */ );
                      conn.setRequestMethod("POST");
                      conn.setDoInput(true);
                      conn.setDoOutput(true);
                      conn.setFixedLengthStreamingMode(Gson.get().toJson(geoNotifications).getBytes().length);

                      //Obtener la fecha local
                      TimeZone tz = TimeZone.getTimeZone("UTC");
                      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
                      df.setTimeZone(tz);
                      String nowAsISO = df.format(new Date());

                      conn.setRequestProperty("dateTime", nowAsISO);

                      //make some HTTP header nicety
                      conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                      conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

                      //open
                      conn.connect();

                      os = new BufferedOutputStream(conn.getOutputStream());
                      os.write(Gson.get().toJson(geoNotifications).getBytes());
                      //clean up
                      os.flush();

                      //do somehting with response
                      is = conn.getInputStream();

                      BufferedReader r = new BufferedReader(new InputStreamReader(is));
                      StringBuilder total = new StringBuilder();
                      String line;
                      while ((line = r.readLine()) != null) {
                        total.append(line).append('\n');
                      }
                      logger.log(Log.DEBUG, total.toString());

                      os.close();
                      is.close();
                      conn.disconnect();
                    }
                    catch (IOException e){
                      logger.log(Log.ERROR, e.getMessage());
                    }

                }
            } else {
                String error = "Geofence transition error: " + transitionType;
                logger.log(Log.ERROR, error);
                broadcastIntent.putExtra("error", error);
            }
        }
        sendBroadcast(broadcastIntent);
    }
}
