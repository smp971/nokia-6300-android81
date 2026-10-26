package org.codeaurora.ims;
public final class RecoveryPolicyTest {
    static int count;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);count++;}
    public static void main(String[] args){
        check(ImsRecoveryPolicy.ready(180000,0,-1,0,true,false,true,false),"eligible idle recovery");
        check(!ImsRecoveryPolicy.ready(179999,0,-1,0,true,false,true,false),"initial grace");
        check(!ImsRecoveryPolicy.ready(180000,0,-1,0,true,false,true,true),"active call protected");
        check(!ImsRecoveryPolicy.ready(180000,0,-1,0,false,false,true,false),"registration required");
        check(!ImsRecoveryPolicy.ready(180000,0,-1,0,true,true,true,false),"healthy service untouched");
        check(!ImsRecoveryPolicy.ready(180000,0,-1,0,true,false,false,false),"LTE required");
        check(!ImsRecoveryPolicy.ready(360000,0,180000,1,true,false,true,false),"cooldown");
        check(ImsRecoveryPolicy.ready(1980000,0,180000,1,true,false,true,false),"second bounded recovery");
        check(!ImsRecoveryPolicy.ready(4000000,0,180000,2,true,false,true,false),"two-per-boot cap");
        check(!ImsRecoveryPolicy.ready(0,180000,-1,0,true,false,true,false),"clock reversal");
        check(!ImsRecoveryPolicy.ready(180000,-1,-1,0,true,false,true,false),"uninitialized timestamp");
        System.out.println("PASS "+count+" IMS recovery policy checks");
    }
}
