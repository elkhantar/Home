package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** On-demand local list, cancelled when dismissed or Home pauses. */
final class ContinueWatching {
    static final int PERMISSION_REQUEST=207;
    interface PopupFactory { PopupWindow create(View panel,int width); }
    private static final android.content.res.ColorStateList TEXT_COLORS = new android.content.res.ColorStateList(
            new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},
            new int[]{0xFF111111,0xFFFFFFFF});
    private final Activity activity;
    private final View root;
    private final PopupFactory factory;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,15,TimeUnit.SECONDS,
            new SynchronousQueue<>(),new ThreadPoolExecutor.AbortPolicy());
    private final float density;
    private PopupWindow popup;
    private Future<?> task;
    private CancellationSignal cancellation;
    private int generation;
    private boolean permissionPending;

    ContinueWatching(Activity activity,View root,PopupFactory factory) {
        this.activity=activity;this.root=root;this.factory=factory;
        density=activity.getResources().getDisplayMetrics().density;
    }
    private int dp(float n){return Math.round(n*density);}
    void open(View anchor) {
        close();
        LinearLayout panel=new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(12),dp(16),dp(12));
        panel.setBackgroundResource(R.drawable.glass_panel);
        TextView heading=text(activity.getString(R.string.continue_title),22);
        panel.addView(heading);
        TextView hint=text(activity.getString(R.string.continue_hint),12);
        hint.setTextColor(0xBFFFFFFF);hint.setPadding(0,dp(4),0,dp(10));panel.addView(hint);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);
        LinearLayout rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);panel.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        int width=Math.min(dp(560),root.getWidth()-dp(112));
        int[] at=new int[2];anchor.getLocationOnScreen(at);
        int[] origin=new int[2];root.getLocationOnScreen(origin);
        int bottom=Math.max(dp(20),root.getHeight()-(at[1]-origin[1])+dp(18));
        int available=Math.max(dp(150),root.getHeight()-bottom-dp(115));
        popup=factory.create(panel,width);
        final PopupWindow current=popup;
        current.setOnDismissListener(()-> {
            if(popup==current){popup=null;cancel();}
            if(anchor.isAttachedToWindow())anchor.requestFocus();
        });
        current.showAtLocation(root,Gravity.BOTTOM|Gravity.END,dp(56),bottom);
        panel.setPivotY(panel.getHeight());panel.setScaleY(0.96f);panel.setAlpha(0);
        panel.animate().scaleY(1f).alpha(1).setDuration(180).start();
        if(activity.checkSelfPermission(WatchNextReader.PERMISSION)!=PackageManager.PERMISSION_GRANTED) {
            TextView message=text(activity.getString(R.string.continue_permission_info),15);rows.addView(message);
            TextView allow=action(activity.getString(R.string.continue_allow));rows.addView(allow);
            allow.setOnClickListener(v->{permissionPending=true;activity.requestPermissions(
                    new String[]{WatchNextReader.PERMISSION},PERMISSION_REQUEST);});allow.requestFocus();
            return;
        }
        rows.addView(text(activity.getString(R.string.continue_loading),15));
        int ticket=++generation;
        cancellation=new CancellationSignal();final CancellationSignal signal=cancellation;
        try {
            task=worker.submit(()-> {
                List<WatchNextPolicy.Item> found=Collections.emptyList();boolean failed=false;
                try {found=WatchNextReader.read(activity.getApplicationContext(),signal);}
                catch(Exception e){failed=true;}
                final List<WatchNextPolicy.Item> result=found;final boolean error=failed;
                handler.post(()-> {
                    if(ticket!=generation || popup!=current || !current.isShowing() || activity.isFinishing())return;
                    rows.removeAllViews();
                    if(error || result.isEmpty()) {
                        TextView empty=text(activity.getString(error?R.string.continue_error:R.string.continue_empty),15);
                        empty.setPadding(0,dp(10),0,dp(14));rows.addView(empty);
                        TextView done=action(activity.getString(android.R.string.ok));rows.addView(done);
                        done.setOnClickListener(v->current.dismiss());done.requestFocus();
                    } else {
                        for(WatchNextPolicy.Item item:result)addRow(rows,item,current);
                        rows.getChildAt(0).requestFocus();
                    }
                    panel.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.UNSPECIFIED);
                    int fixed=heading.getMeasuredHeight()+hint.getMeasuredHeight()+dp(24);
                    scroll.getLayoutParams().height=Math.min(rows.getMeasuredHeight(),Math.max(dp(80),available-fixed));
                    scroll.requestLayout();current.update(width,Math.min(panel.getMeasuredHeight(),available));
                    android.util.Log.i("Home.WatchNext","Available entries: "+result.size()+"; query failed: "+error);
                });
            });
        }catch(RejectedExecutionException e){rows.removeAllViews();rows.addView(text(activity.getString(R.string.continue_busy),15));}
    }
    private void addRow(LinearLayout rows,WatchNextPolicy.Item item,PopupWindow current) {
        LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(10),dp(10));
        row.setBackgroundResource(R.drawable.input_row_bg);row.setFocusable(true);row.setClickable(true);
        ImageView icon=new ImageView(activity);String app=item.pkg;
        try {icon.setImageDrawable(activity.getPackageManager().getApplicationIcon(item.pkg));
            app=activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(item.pkg,0)).toString();
        }catch(PackageManager.NameNotFoundException ignored){}
        row.addView(icon,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout words=new LinearLayout(activity);words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(12),0,0,0);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=text(item.title,17);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTextColor(TEXT_COLORS);title.setDuplicateParentStateEnabled(true);
        words.setDuplicateParentStateEnabled(true);words.addView(title);
        String detail=app+"  \u00b7  "+activity.getString(item.kind==1?R.string.continue_next:R.string.continue_resume);
        if(!item.episode.isEmpty())detail+="  \u00b7  "+item.episode;
        TextView subtitle=text(detail,12);subtitle.setMaxLines(1);subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle.setTextColor(TEXT_COLORS);subtitle.setDuplicateParentStateEnabled(true);words.addView(subtitle);
        if(item.kind==0 && item.duration>0 && item.position>0) {
            ProgressBar progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);progress.setProgress(WatchNextPolicy.progress(item));
            LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(3));bar.topMargin=dp(6);words.addView(progress,bar);
        }
        row.setOnFocusChangeListener((view, focused) -> {
            int color = focused ? 0xFF111111 : Color.WHITE;
            title.setTextColor(color);
            subtitle.setTextColor(color);
        });
        row.setContentDescription(item.title+", "+detail);
        row.setOnClickListener(v->{
            Intent link=WatchNextReader.safeIntent(activity,item);
            if(link==null){Toast.makeText(activity,R.string.not_available,Toast.LENGTH_SHORT).show();return;}
            try {activity.startActivity(link);current.dismiss();}
            catch(ActivityNotFoundException|SecurityException e){Toast.makeText(activity,R.string.not_available,Toast.LENGTH_SHORT).show();}
        });
        int index=rows.getChildCount();
        row.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
            if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN){
                int next=index+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1);
                if(next>=0 && next<rows.getChildCount())rows.getChildAt(next).requestFocus();return true;
            }return false;
        });
        rows.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private TextView text(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setTypeface(activity.getResources().getFont(R.font.outfit));return t;}
    private TextView action(String label){TextView t=text(label,16);t.setBackgroundResource(R.drawable.input_row_bg);t.setTextColor(TEXT_COLORS);t.setPadding(dp(10),dp(12),dp(10),dp(12));t.setFocusable(true);t.setClickable(true);return t;}
    boolean permissionResult(){boolean pending=permissionPending;permissionPending=false;return pending;}
    private void cancel(){generation++;if(cancellation!=null)cancellation.cancel();if(task!=null)task.cancel(true);cancellation=null;task=null;}
    void close(){cancel();if(popup!=null){PopupWindow p=popup;popup=null;p.dismiss();}}
    void destroy(){close();worker.shutdownNow();}
}
