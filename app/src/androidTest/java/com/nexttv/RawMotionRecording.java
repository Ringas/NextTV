// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv;
import android.app.Instrumentation;import android.os.*;
/** Start only after the app is in landscape; avoid the launcher's portrait encoder canvas. */
public final class RawMotionRecording {
 private final ParcelFileDescriptor pipe;private final long until;
 private RawMotionRecording(ParcelFileDescriptor pipe,long until){this.pipe=pipe;this.until=until;}
 public static RawMotionRecording start(Instrumentation owner,String filename){
  android.graphics.Bitmap frame=owner.getUiAutomation().takeScreenshot();int w=frame.getWidth(),h=frame.getHeight();frame.recycle();
  if(w<h)throw new IllegalStateException("Recording must start in landscape");
  long now=SystemClock.uptimeMillis();ParcelFileDescriptor fd=owner.getUiAutomation().executeShellCommand("screenrecord --size "+w+"x"+h+" --bit-rate 5000000 --time-limit 25 /sdcard/Download/NextTVAudit/"+filename);
  SystemClock.sleep(300);return new RawMotionRecording(fd,now+27000);
 }
 public void close(){long left=until-SystemClock.uptimeMillis();if(left>0)SystemClock.sleep(left);try{pipe.close();}catch(Exception ignored){}}
}
