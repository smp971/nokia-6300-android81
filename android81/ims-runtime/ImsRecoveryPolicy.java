package org.codeaurora.ims;
/** Pure bounded policy. A registration without voice is not immediately a failure. */
public final class ImsRecoveryPolicy {
    public static boolean ready(long now,long missingSince,long last,int attempts,boolean registered,boolean voice,boolean lte,boolean call){
        return !call&&lte&&registered&&!voice&&missingSince>=0&&now>=missingSince
            &&now-missingSince>=180000&&attempts<2&&(last<0||now>=last&&now-last>=1800000);
    }
}
