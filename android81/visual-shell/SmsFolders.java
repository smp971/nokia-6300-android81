package ro.leo.visual;
import android.provider.Telephony;
final class SmsFolders {
    static final String[] LABELS={"Toate","Primite","Trimise","În așteptare","Eșuate"};
    static boolean accepts(int folder,int type){return folder==0||folder==1&&type==Telephony.Sms.MESSAGE_TYPE_INBOX||folder==2&&type==Telephony.Sms.MESSAGE_TYPE_SENT||folder==3&&(type==Telephony.Sms.MESSAGE_TYPE_OUTBOX||type==Telephony.Sms.MESSAGE_TYPE_QUEUED)||folder==4&&type==Telephony.Sms.MESSAGE_TYPE_FAILED;}
}
