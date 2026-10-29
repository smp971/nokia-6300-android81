package org.codeaurora.ims;

/** Immutable physical-slot routing. Android subscription IDs never index it. */
public final class LeoImsTopology {
    private final int selected;
    private final boolean dual;
    public LeoImsTopology(boolean dual,int selected){
        if(selected<0||selected>1)throw new IllegalArgumentException("Physical slot must be 0 or 1");
        this.dual=dual;this.selected=selected;
    }
    public int count(){return dual?2:1;}
    public boolean dual(){return dual;}
    public int phoneAt(int index){
        if(index<0||index>=count())throw new IllegalArgumentException("IMS endpoint index out of range");
        return dual?index:selected;
    }
    public int indexOf(int phone){return phone<0||phone>1?-1:dual?phone:phone==selected?0:-1;}
    public String socketAt(int index){return "qmux_radio/rild_ims"+phoneAt(index);}
    public static int readyMask(boolean backend,boolean first,boolean second){
        return backend?((first?1:0)|(second?2:0)):0;
    }
    /** Teardown/configuration is deferred while any Telecom call exists. */
    public static boolean mayConfigure(boolean enabled,boolean airplane,boolean call,int readyMask){
        return enabled&&!airplane&&!call&&(readyMask&3)!=0;
    }
}
