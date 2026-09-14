package com.alonstark.tricontroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

public final class ControllerSurface extends View {
    public enum SpecialAction { SCREENSHOT, TOUCHPAD_CLICK }

    public interface Listener {
        void onState(GamepadState state);
        void onTouchpadDelta(int dx, int dy, boolean pressed);
        void onSpecialAction(SpecialAction action);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GamepadState state = new GamepadState();
    private final Map<Integer, String> pointerControl = new HashMap<>();
    private Listener listener;
    private ControllerProfile profile = ControllerProfile.XBOX;
    private boolean haptics = true;

    private float leftCx, leftCy, rightCx, rightCy, stickR;
    private final RectF[] face = new RectF[]{new RectF(),new RectF(),new RectF(),new RectF()}; // S,E,W,N
    private final RectF lb = new RectF(), rb = new RectF(), lt = new RectF(), rt = new RectF();
    private final RectF back = new RectF(), start = new RectF(), guide = new RectF(), extra = new RectF(), touchpad = new RectF();
    private float lastTouchX, lastTouchY;

    public ControllerSurface(Context c) { super(c); setFocusable(true); setBackgroundColor(Color.rgb(18,18,20)); }
    public void setListener(Listener l) { listener = l; }
    public void setProfile(ControllerProfile p) { profile = p; state.reset(); pointerControl.clear(); invalidate(); emit(); }
    public ControllerProfile getProfile() { return profile; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w=getWidth(), h=getHeight();
        float scale=Math.min(w/1280f,h/600f);
        stickR=82*scale;
        leftCx=205*scale; leftCy=h-160*scale;
        rightCx=w-355*scale; rightCy=h-160*scale;

        setupRects(w,h,scale);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(34,34,38));
        c.drawRoundRect(8*scale,8*scale,w-8*scale,h-8*scale,30*scale,30*scale,p);

        drawTrigger(c, lt, profile.leftTriggerLabel(), state.lt>0);
        drawTrigger(c, rt, profile.rightTriggerLabel(), state.rt>0);
        drawButton(c, lb, profile.leftBumperLabel(), (state.buttons&(1<<4))!=0);
        drawButton(c, rb, profile.rightBumperLabel(), (state.buttons&(1<<5))!=0);

        drawStick(c,leftCx,leftCy,stickR,state.lx,state.ly,"L");
        drawStick(c,rightCx,rightCy,stickR,state.rx,state.ry,"R");
        drawDpad(c, w*0.12f, h*0.46f, 58*scale);

        for(int i=0;i<4;i++) drawButton(c, face[i], profile.faceLabels[i], (state.buttons&(1<<i))!=0);
        drawButton(c, back, profile.backLabel(), (state.buttons&(1<<6))!=0);
        drawButton(c, start, profile.startLabel(), (state.buttons&(1<<7))!=0);
        drawButton(c, guide, profile.guideLabel(), (state.buttons&(1<<10))!=0);
        drawButton(c, extra, profile.extraLabel(), (state.buttons&(1<<11))!=0);

        if(profile==ControllerProfile.PLAYSTATION){
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2*scale); p.setColor(Color.DKGRAY);
            c.drawRoundRect(touchpad,12*scale,12*scale,p);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.LTGRAY); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(16*scale);
            c.drawText("Touchpad → mouse",touchpad.centerX(),touchpad.centerY()+5*scale,p);
        }
    }

    private void setupRects(float w,float h,float s){
        float fx=w-175*s, fy=h*0.47f, r=27*s;
        face[0].set(fx-r,fy+54*s-r,fx+r,fy+54*s+r);
        face[1].set(fx+54*s-r,fy-r,fx+54*s+r,fy+r);
        face[2].set(fx-54*s-r,fy-r,fx-54*s+r,fy+r);
        face[3].set(fx-r,fy-54*s-r,fx+r,fy-54*s+r);
        lt.set(90*s,22*s,230*s,64*s); lb.set(90*s,72*s,230*s,112*s);
        rt.set(w-230*s,22*s,w-90*s,64*s); rb.set(w-230*s,72*s,w-90*s,112*s);
        back.set(w*0.42f-70*s,80*s,w*0.42f+10*s,118*s);
        start.set(w*0.58f-10*s,80*s,w*0.58f+70*s,118*s);
        guide.set(w*0.5f-46*s,130*s,w*0.5f+46*s,176*s);
        extra.set(w*0.5f-55*s,188*s,w*0.5f+55*s,228*s);
        touchpad.set(w*0.5f-150*s,240*s,w*0.5f+150*s,330*s);
    }

    private void drawButton(Canvas c, RectF r,String text,boolean active){
        p.setStyle(Paint.Style.FILL); p.setColor(active?Color.rgb(90,125,240):Color.rgb(55,55,62)); c.drawOval(r,p);
        p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(Math.max(12,r.height()*0.30f));
        c.drawText(text,r.centerX(),r.centerY()-((p.ascent()+p.descent())/2),p);
    }
    private void drawTrigger(Canvas c, RectF r,String text,boolean active){
        p.setStyle(Paint.Style.FILL); p.setColor(active?Color.rgb(90,125,240):Color.rgb(48,48,54)); c.drawRoundRect(r,12,12,p);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r.height()*0.35f);c.drawText(text,r.centerX(),r.centerY()-((p.ascent()+p.descent())/2),p);
    }
    private void drawStick(Canvas c,float cx,float cy,float r,int ax,int ay,String label){
        p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(46,46,52));c.drawCircle(cx,cy,r,p);
        float dx=(ax-128)/127f*r*0.55f,dy=(ay-128)/127f*r*0.55f;
        p.setColor(Color.rgb(88,88,98));c.drawCircle(cx+dx,cy+dy,r*0.46f,p);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r*0.27f);c.drawText(label,cx+dx,cy+dy-((p.ascent()+p.descent())/2),p);
    }
    private void drawDpad(Canvas c,float cx,float cy,float r){
        p.setColor(Color.rgb(60,60,68));p.setStyle(Paint.Style.FILL);
        c.drawRect(cx-r*0.35f,cy-r,cx+r*0.35f,cy+r,p);c.drawRect(cx-r,cy-r*0.35f,cx+r,cy+r*0.35f,p);
        p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r*0.35f);c.drawText("D",cx,cy-((p.ascent()+p.descent())/2),p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked(); int idx=e.getActionIndex(); int pid=e.getPointerId(idx);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){
            float x=e.getX(idx),y=e.getY(idx); String ctl=hit(x,y); if(ctl!=null){ pointerControl.put(pid,ctl); press(ctl,x,y,true); }
        } else if(action==MotionEvent.ACTION_MOVE){
            for(int i=0;i<e.getPointerCount();i++){ int id=e.getPointerId(i);String ctl=pointerControl.get(id);if(ctl!=null) press(ctl,e.getX(i),e.getY(i),false); }
        } else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL){
            String ctl=pointerControl.remove(pid);if(ctl!=null) release(ctl);
            if(action==MotionEvent.ACTION_CANCEL){ for(String c:pointerControl.values()) release(c);pointerControl.clear(); }
        }
        invalidate(); return true;
    }

    private String hit(float x,float y){
        if(dist(x,y,leftCx,leftCy)<=stickR) return "LS";
        if(dist(x,y,rightCx,rightCy)<=stickR) return "RS";
        for(int i=0;i<4;i++) if(face[i].contains(x,y)) return "B"+i;
        if(lt.contains(x,y))return "LT";if(rt.contains(x,y))return "RT";if(lb.contains(x,y))return "LB";if(rb.contains(x,y))return "RB";
        if(back.contains(x,y))return "BACK";if(start.contains(x,y))return "START";if(guide.contains(x,y))return "GUIDE";if(extra.contains(x,y))return "EXTRA";
        if(profile==ControllerProfile.PLAYSTATION&&touchpad.contains(x,y)){lastTouchX=x;lastTouchY=y;return "TOUCH";}
        float dcx=getWidth()*0.12f,dcy=getHeight()*0.46f,dr=Math.min(getWidth()/1280f,getHeight()/600f)*64;
        if(Math.abs(x-dcx)<dr&&Math.abs(y-dcy)<dr){ float dx=x-dcx,dy=y-dcy; if(Math.abs(dx)>Math.abs(dy)) return dx>0?"RIGHT":"LEFT"; else return dy>0?"DOWN":"UP"; }
        return null;
    }

    private void press(String ctl,float x,float y,boolean first){
        switch(ctl){
            case "LS": applyStick(true,x,y); break; case "RS": applyStick(false,x,y); break;
            case "LT": state.lt=255; break; case "RT": state.rt=255; break;
            case "LB": setBit(4,true); break; case "RB": setBit(5,true); break;
            case "BACK":
                setBit(6,true);
                if(first && profile==ControllerProfile.PLAYSTATION && listener!=null) listener.onSpecialAction(SpecialAction.SCREENSHOT);
                break;
            case "START": setBit(7,true); break;
            case "GUIDE": setBit(10,true); break;
            case "EXTRA":
                setBit(11,true);
                if(first && listener!=null) {
                    if(profile==ControllerProfile.PLAYSTATION) listener.onSpecialAction(SpecialAction.TOUCHPAD_CLICK);
                    else listener.onSpecialAction(SpecialAction.SCREENSHOT);
                }
                break;
            case "UP": state.hat=0; break; case "RIGHT": state.hat=2; break; case "DOWN": state.hat=4; break; case "LEFT": state.hat=6; break;
            case "TOUCH": int dx=Math.round(x-lastTouchX),dy=Math.round(y-lastTouchY);lastTouchX=x;lastTouchY=y;if(listener!=null)listener.onTouchpadDelta(dx,dy,first); break;
            default: if(ctl.startsWith("B")) setBit(Integer.parseInt(ctl.substring(1)),true);
        }
        if(first&&!ctl.equals("LS")&&!ctl.equals("RS")&&!ctl.equals("TOUCH")) vibrate();
        emit();
    }
    private void release(String ctl){
        switch(ctl){
            case "LS": state.lx=state.ly=128; break; case "RS": state.rx=state.ry=128; break;
            case "LT":state.lt=0;break;case "RT":state.rt=0;break;
            case "LB":setBit(4,false);break;case "RB":setBit(5,false);break;case "BACK":setBit(6,false);break;case "START":setBit(7,false);break;case "GUIDE":setBit(10,false);break;case "EXTRA":setBit(11,false);break;
            case "UP":case "RIGHT":case "DOWN":case "LEFT":state.hat=8;break;
            case "TOUCH":if(listener!=null)listener.onTouchpadDelta(0,0,false);break;
            default:if(ctl.startsWith("B"))setBit(Integer.parseInt(ctl.substring(1)),false);
        } emit();
    }
    private void applyStick(boolean left,float x,float y){
        float cx=left?leftCx:rightCx,cy=left?leftCy:rightCy,dx=x-cx,dy=y-cy,d=(float)Math.sqrt(dx*dx+dy*dy); if(d>stickR){dx*=stickR/d;dy*=stickR/d;}
        int ax=Math.round(128+(dx/stickR)*127),ay=Math.round(128+(dy/stickR)*127);
        if(left){state.lx=ax;state.ly=ay;}else{state.rx=ax;state.ry=ay;}
    }
    private void setBit(int bit,boolean on){if(on)state.buttons|=(1<<bit);else state.buttons&=~(1<<bit);}
    private void emit(){if(listener!=null)listener.onState(state.copy());}
    private static float dist(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return(float)Math.sqrt(dx*dx+dy*dy);}
    private void vibrate(){
        if(!haptics)return; try{Vibrator v=(Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(12,60));}catch(Exception ignored){}
    }
}
