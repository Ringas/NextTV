package com.nexttv;
import android.app.*;import android.os.*;import java.io.*;
/** Read this application's own retained ANR traces without root or other-app access. */
public class AnrTraceInstrumentation extends Instrumentation{
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){StringBuilder report=new StringBuilder();try{
 if(Build.VERSION.SDK_INT>=30){ActivityManager am=(ActivityManager)getTargetContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);int count=0;for(ApplicationExitInfo exit:am.getHistoricalProcessExitReasons("com.nexttv",0,25)){
 report.append("EXIT ").append(exit.getTimestamp()).append(" reason=").append(exit.getReason()).append(" description=").append(exit.getDescription()).append('\n');
 try(InputStream input=exit.getTraceInputStream()){if(input!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while(out.size()<1048576&&(n=input.read(b,0,Math.min(b.length,1048576-out.size())))>0)out.write(b,0,n);File file=new File(getTargetContext().getFilesDir(),"anr-trace-"+count+".txt");try(FileOutputStream f=new FileOutputStream(file)){out.writeTo(f);}report.append("TRACE ").append(file.getName()).append(" bytes=").append(out.size()).append('\n');count++;}}
 catch(IOException e){report.append("Trace unavailable: ").append(e.getMessage()).append('\n');}
 if(count>=3)break;
 }}report.append("PASS own-process exit diagnostics collected\n");Bundle result=new Bundle();result.putString("stream",report.toString());finish(Activity.RESULT_OK,result);
 }catch(Throwable t){Bundle result=new Bundle();result.putString("stream",android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
}
