package com.chirag.RNMail;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.Html;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.Callback;

import java.util.List;
import java.io.File;
import android.support.v4.content.FileProvider;
import android.content.ClipData;
import android.content.pm.LabeledIntent;
import android.content.ComponentName;
import java.util.ArrayList;
import android.content.IntentFilter;

/**
 * NativeModule that allows JS to open emails sending apps chooser.
 */
public class RNMailModule extends ReactContextBaseJavaModule {

  ReactApplicationContext reactContext;

  public RNMailModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNMail";
  }

  /**
    * Converts a ReadableArray to a String array
    *
    * @param r the ReadableArray instance to convert
    *
    * @return array of strings
  */
  private String[] readableArrayToStringArray(ReadableArray r) {
    int length = r.size();
    String[] strArray = new String[length];

    for (int keyIndex = 0; keyIndex < length; keyIndex++) {
      strArray[keyIndex] = r.getString(keyIndex);
    }

    return strArray;
  }

  private void addCommonExtras(ReadableMap options, Intent i) {
    if (options.hasKey("subject") && !options.isNull("subject")) {
      i.putExtra(Intent.EXTRA_SUBJECT, options.getString("subject"));
    }

    if (options.hasKey("body") && !options.isNull("body")) {
      String body = options.getString("body");
      if (options.hasKey("isHTML") && options.getBoolean("isHTML")) {
        // i.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(body));
        i.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(body).toString());
      } else {
        i.putExtra(Intent.EXTRA_TEXT, body);
      }
    }

    if (options.hasKey("recipients") && !options.isNull("recipients")) {
      ReadableArray recipients = options.getArray("recipients");
      i.putExtra(Intent.EXTRA_EMAIL, readableArrayToStringArray(recipients));
    }

    if (options.hasKey("ccRecipients") && !options.isNull("ccRecipients")) {
      ReadableArray ccRecipients = options.getArray("ccRecipients");
      i.putExtra(Intent.EXTRA_CC, readableArrayToStringArray(ccRecipients));
    }

    if (options.hasKey("bccRecipients") && !options.isNull("bccRecipients")) {
      ReadableArray bccRecipients = options.getArray("bccRecipients");
      i.putExtra(Intent.EXTRA_BCC, readableArrayToStringArray(bccRecipients));
    }
  }

  @ReactMethod
  public void mail(ReadableMap options, Callback callback) {
    Intent i = new Intent(Intent.ACTION_SENDTO);
    i.setData(Uri.parse("mailto:"));

    addCommonExtras(options, i);

    // List<ResolveInfo> list = reactContext.getPackageManager().queryIntentActivities(intent, 0);
    // for (ResolveInfo info : list) {
    //   android.util.Log.d("ReactNative", "info1 " + info);    
    // }

    // If have attachment create a chooser with custom intents
    if (options.hasKey("attachment") && !options.isNull("attachment")
      && options.getMap("attachment").hasKey("path") 
      && !options.getMap("attachment").isNull("path")) {
      
      ReadableMap attachment = options.getMap("attachment");
      String path = attachment.getString("path");
      File file = new File(path);

      Uri fileUri = FileProvider.getUriForFile(reactContext, reactContext.getApplicationContext().getPackageName() + ".fileprovider", file);
      i.putExtra(Intent.EXTRA_STREAM, fileUri);
      ClipData clipData = ClipData.newRawUri("Image", fileUri);
      i.setClipData(clipData);
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

      ArrayList<Uri> urisList = new ArrayList<>();
      urisList.add(fileUri); 

      // Trying to get the default email app
      Intent defaultIntent = new Intent(Intent.ACTION_SENDTO);
      defaultIntent.setData(Uri.parse("mailto:"));
      //defaultIntent.addCategory(Intent.CATEGORY_APP_EMAIL);
      final ResolveInfo defaultInfo = reactContext.getPackageManager().resolveActivity(defaultIntent, 0);
      // android.util.Log.d("ReactNative", "activityInfo " + defaultInfo.activityInfo);
      // android.util.Log.d("ReactNative", "match " + defaultInfo.match); 

      if (defaultInfo != null 
        && (defaultInfo.match & IntentFilter.MATCH_CATEGORY_SCHEME) != 0  
        && (defaultInfo.match & IntentFilter.MATCH_ADJUSTMENT_NORMAL) != 0) {
        
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setComponent(new ComponentName(defaultInfo.activityInfo.packageName, defaultInfo.activityInfo.name));
        addCommonExtras(options, intent);
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisList);
        intent.setClipData(clipData);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(new ComponentName(defaultInfo.activityInfo.packageName, defaultInfo.activityInfo.name));

        try {
          reactContext.startActivity(intent);
        } catch (Exception ex) {
          callback.invoke("error");
        }
      } else {
        PackageManager manager = reactContext.getPackageManager();
        List<ResolveInfo> list = manager.queryIntentActivities(i, 0);

        if (list == null || list.size() == 0) {
          callback.invoke("not_available");
          return;
        }
      
        List<LabeledIntent> intents = new ArrayList<>();
        for (ResolveInfo info : list) {
          if (!info.activityInfo.packageName.contains("com.paypal")) {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            addCommonExtras(options, intent);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, urisList);
            intent.setClipData(clipData);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intents.add(new LabeledIntent(intent, info.activityInfo.packageName, info.loadLabel(reactContext.getPackageManager()), info.icon));
          }
        }
        Intent chooser = Intent.createChooser(intents.remove(intents.size() - 1), "Send email...");
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new LabeledIntent[intents.size()]));
        chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
          reactContext.startActivity(chooser);
        } catch (Exception ex) {
          callback.invoke("error");
        }
      }
    } else {
      // Sending normal email to default app
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try {
        reactContext.startActivity(i);
      } catch (Exception ex) {
        callback.invoke("error");
      }
    }
  }
}
