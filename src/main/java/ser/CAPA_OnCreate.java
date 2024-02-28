package ser;

import com.ser.blueline.*;
import com.ser.blueline.bpm.*;
import com.ser.blueline.metaDataComponents.IStringMatrix;
import com.ser.blueline.modifiablemetadata.IStringMatrixModifiable;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class CAPA_OnCreate extends UnifiedAgent {
    private Logger log = LogManager.getLogger();
    IStringMatrix settingsMatrix = null;
    IProcessInstance proi = null;
    ProcessHelper helper;
    ITask eventTask = null;
    String nrName = "";

    @Override
    protected Object execute() {
        if (getEventTask() == null) return resultError("OBJECT CLIENT ID is NULL or not of Type ITask");

        Utils.session = getSes();
        Utils.bpm = getBpm();
        Utils.server = Utils.session.getDocumentServer();
        eventTask = getEventTask();

        try {
            settingsMatrix = getDocumentServer().getStringMatrix("SYS_NUMBER_RANGES", getSes());
            helper = new ProcessHelper(Utils.session);
            proi = eventTask.getProcessInstance();
            if (proi == null) return resultError("Process Instance is NULL");

            String number =  getNumber();
            log.info("QA_CAPA_OnChange NUMBER is:" + number);
            proi.setDescriptorValue("ObjectNumber",number);
            proi.commit();
            log.info("QA_CAPA_OnChange set number is:" + proi.getDescriptorValue("ObjectNumber"));

            log.info("QA_CAPA_OnChange Finished.");
        } catch (Exception e) {
            log.error("Exception       : " + e.getMessage());
            log.error("    Class       : " + e.getClass());
            log.error("    Stack-Trace : " + e.getStackTrace() );
            return resultRestart("Exception : " + e.getMessage(),10);
        }
        return resultSuccess("Ended successfully");
    }
    public String getNumber(){
        String rtrn = "";
        String numberPattern = getPatternFromGV("QA.CPA");
        String numberLast = getLastNumberFromGV("QA.CPA");
        if(numberPattern != null || !numberPattern.isEmpty()){
            rtrn = initPattern(proi,numberPattern,numberLast);
        }
        return rtrn;
    }
    public String initPattern(IInformationObject proi, String numberPattern, String lastNumber){
        List<String> rtrn = new ArrayList<>();
        //String text1 = "AB{Categories}%yyyy%%N5%";
        //String text = "%yyyy%%n5%";
        Pattern ptrn = Pattern.compile("[^.%/{}]+");
        Pattern ptrnAll = Pattern.compile("(\\{[a-z]+})|(\\%[a-z1-9]+%)|\\w+");
        Pattern ptrn1 = Pattern.compile("(\\{[a-z]+})");
        Pattern ptrn2 = Pattern.compile("(\\%[a-z1-9]+%)");
        Pattern ptrn3 = Pattern.compile("\\w+");
        //Pattern ptrn4 = Pattern.compile("%y+%");///%yyyy%
        Pattern ptrn4 = Pattern.compile("%y+%|%Y+%");///%yyyy%
        Pattern ptrn5 = Pattern.compile("\\{\\w+}");///{desc}
        Pattern ptrn6 = Pattern.compile("\\%\\w+%");///%yyyy% or %N5%
        //Pattern ptrn7 = Pattern.compile("%N\\d%");///%N5%
        //Pattern ptrn7 = Pattern.compile("%N[0-9]%");///%N5%
        Pattern ptrn7 = Pattern.compile("%[a-zA-Z][0-9]%");///%N5%
        Pattern ptrn8 = Pattern.compile("^[A-Z]+");///ABC

        //String[] matches = ptrn4.matcher(text).results().map(MatchResult::group).toArray(String[]::new);
        String[] matches4 = ptrn4.matcher(numberPattern).results().map(MatchResult::group).toArray(String[]::new);
        String[] matches5 = ptrn5.matcher(numberPattern).results().map(MatchResult::group).toArray(String[]::new);
        //String[] matches6 = ptrn6.matcher(text1).results().map(MatchResult::group).toArray(String[]::new);
        String[] matches7 = ptrn7.matcher(numberPattern).results().map(MatchResult::group).toArray(String[]::new);
        String[] matches8 = ptrn8.matcher(numberPattern).results().map(MatchResult::group).toArray(String[]::new);
        //int yearLength = matches4.length;
        String nYear = new SimpleDateFormat("yyyy").format(new Date());
        String joined = "";
        for(String a : matches8){
            rtrn.add(a);
        }
        for(String a : matches5){
            rtrn.add(getDescValue(proi,a));
        }
        for(String a : matches4){
            rtrn.add(getDateValue(a));
        }
        for(String a : matches7){
            rtrn.add(getSequenceValue(a,lastNumber));
        }
        joined = String.join("",rtrn);
        for(String c : new String[]{".","-"}){
            if(numberPattern.contains(c)){
                joined = String.join(c,rtrn);
                break;
            }
        }
        return joined;
    }
    public String getDescValue(IInformationObject doc, String txt){
        String rtrn = "";
        String nTxt = txt.replace("{","").replace("}","");
        rtrn = doc.getDescriptorValue(nTxt);
        rtrn = (rtrn != null ? rtrn : "");
        return rtrn;
    }
    public String getDateValue(String txt){
        String rtrn = "";
        String nYear = new SimpleDateFormat("yyyy").format(new Date());
        rtrn = txt.replaceAll("%yyyy%",nYear);
        return rtrn;
    }
    public String getSequenceValue(String txt,String lastNumber){
        Pattern ptrnNum = Pattern.compile("[0-9]");///%N%
        String rtrn = "";
        String nTxt = txt.replaceAll("%","");
        String[] matchesNum = ptrnNum.matcher(nTxt).results().map(MatchResult::group).toArray(String[]::new);
        int newNumber = Integer.parseInt(lastNumber) + 1;
        updateLastNumberGVList("QA.CPA",""+newNumber);
        int seqLen = Integer.parseInt(matchesNum[0]);
        rtrn = ("00000000000000" + newNumber).substring(("00000000000000" + newNumber).length()-seqLen);
        return rtrn;
    }
    public String getPatternFromGV(String nrName){
        String rtrn = "";
        String pattern = "";
        String nrStartS = "";
        String nrCurrentS = "0";
        int rowcount = 0;

        if(settingsMatrix != null) {
            for (int i = 0; i < settingsMatrix.getRowCount(); i++) {
                String rowID = settingsMatrix.getValue(i, 0);
                rowcount = i;
                if (rowID.equalsIgnoreCase(nrName)) {
                    pattern = settingsMatrix.getValue(i, 1);
                    break;
                }
            }
        }
        return pattern;
    }
    public String getLastNumberFromGV(String nrName){
        String rtrn = "";
        String pattern = "";
        String nrStartS = "";
        String nrCurrentS = "";
        int rowcount = 0;

        if(settingsMatrix != null) {
            for (int i = 0; i < settingsMatrix.getRowCount(); i++) {
                String rowID = settingsMatrix.getValue(i, 0);
                rowcount = i;
                if (rowID.equalsIgnoreCase(nrName)) {
                    nrCurrentS = settingsMatrix.getValue(i, 4);
                    break;
                }
            }
        }
        if(nrCurrentS == ""){

        }
        return nrCurrentS;
    }
    public void updateLastNumberGVList(String rowID, String newValue){
        String nrCurrentS = "";
        int rowCount = 0;
        if(settingsMatrix != null) {
            for (int i = 0; i < settingsMatrix.getRowCount(); i++) {
                rowCount = i;
                String rID = settingsMatrix.getValue(i, 0);
                if (rID.equalsIgnoreCase(rowID)) {
                    nrCurrentS = settingsMatrix.getValue(i, 4);
                    break;
                }
            }
            IStringMatrixModifiable srtMatrixModify = settingsMatrix.getModifiableCopy(getSes());
            srtMatrixModify.setValue(rowCount, 4, newValue, false);
            srtMatrixModify.commit();
        }
    }
    public void createLastNumberGVList(String rowID, String pattern){
        String nrCurrentS = "";
        IStringMatrixModifiable srtMatrixModify = settingsMatrix.getModifiableCopy(getSes());
        settingsMatrix.refresh();
        int rowCount = 0;
        srtMatrixModify.appendRow();
        srtMatrixModify.commit();
        srtMatrixModify.refresh();
        rowCount = srtMatrixModify.getRowCount()-1;
        srtMatrixModify.setValue(rowCount, 0, rowID, false);
        srtMatrixModify.setValue(rowCount, 1, pattern, false);
        srtMatrixModify.setValue(rowCount, 2, "0", false);
        srtMatrixModify.setValue(rowCount, 3, "", false);
        srtMatrixModify.setValue(rowCount, 4, "0", false);
        srtMatrixModify.commit();
    }
}