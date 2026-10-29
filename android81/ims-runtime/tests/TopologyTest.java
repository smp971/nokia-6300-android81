package org.codeaurora.ims;
public final class TopologyTest {
    private static int checks;
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static void invalid(Runnable call){try{call.run();throw new AssertionError("Accepted invalid slot/index");}catch(IllegalArgumentException expected){checks++;}}
    public static void main(String[] args){
        for(int selected=0;selected<2;selected++){
            final LeoImsTopology single=new LeoImsTopology(false,selected);
            check(single.count()==1,"single fallback has exactly one endpoint");
            check(single.phoneAt(0)==selected,"single endpoint retains chosen physical slot");
            check(single.indexOf(selected)==0&&single.indexOf(1-selected)==-1,"single binder rejects other SIM");
            check(single.socketAt(0).endsWith("ims"+selected),"single socket bound before reader starts");
            invalid(new Runnable(){public void run(){single.phoneAt(1);}});
            final LeoImsTopology dual=new LeoImsTopology(true,selected);
            check(dual.count()==2,"dual topology has two endpoints");
            for(int phone=0;phone<2;phone++){
                check(dual.phoneAt(dual.indexOf(phone))==phone,"Binder phone roundtrip never crosses physical slots");
                check(dual.socketAt(phone).equals("qmux_radio/rild_ims"+phone),"physical endpoint keeps own socket regardless of DDS");
            }
            check(dual.indexOf(-1)==-1&&dual.indexOf(2)==-1&&dual.indexOf(3)==-1,"subscription IDs are not valid physical slots");
            invalid(new Runnable(){public void run(){dual.phoneAt(-1);}});
            invalid(new Runnable(){public void run(){dual.phoneAt(2);}});
        }
        invalid(new Runnable(){public void run(){new LeoImsTopology(true,-1);}});
        invalid(new Runnable(){public void run(){new LeoImsTopology(true,2);}});
        for(int mask=0;mask<4;mask++){
            check(LeoImsTopology.readyMask(true,(mask&1)!=0,(mask&2)!=0)==mask,"readiness keeps each slot bit");
            check(LeoImsTopology.readyMask(false,true,true)==0,"sockets alone do not establish native readiness");
            check(!LeoImsTopology.mayConfigure(true,false,true,mask),"active calls defer configuration");
            check(!LeoImsTopology.mayConfigure(false,false,false,mask),"disabled runtime remains stopped");
            check(!LeoImsTopology.mayConfigure(true,true,false,mask),"airplane mode remains stopped");
        }
        check(!LeoImsTopology.mayConfigure(true,false,false,0),"no endpoint means no configuration");
        check(LeoImsTopology.mayConfigure(true,false,false,3),"two idle ready endpoints may be tested");
        System.out.println("PASS "+checks+" IMS topology checks");
    }
}
