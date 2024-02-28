package ser;

import com.ser.blueline.IDocument;
import com.ser.blueline.IInformationObject;
import com.ser.blueline.IUser;
import com.ser.blueline.bpm.IProcessInstance;
import com.ser.blueline.bpm.ITask;
import com.ser.blueline.bpm.TaskStatus;
import com.ser.blueline.metaDataComponents.IStringMatrix;
import com.ser.blueline.modifiablemetadata.IStringMatrixModifiable;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class CAPA_CancelProcess extends UnifiedAgent {
    private Logger log = LogManager.getLogger();
    IProcessInstance proi = null;
    ProcessHelper helper;
    ITask eventTask = null;
    String compCode = "";
    String docNumber = "";

    @Override
    protected Object execute() {
        if (getEventTask() == null) return resultError("OBJECT CLIENT ID is NULL or not of Type ITask");

        Utils.session = getSes();
        Utils.bpm = getBpm();
        Utils.server = Utils.session.getDocumentServer();
        eventTask = getEventTask();

        try {
            JSONObject scfg = Utils.getSystemConfig();
            if(scfg.has("LICS.SPIRE_XLS")){
                com.spire.license.LicenseProvider.setLicenseKey(scfg.getString("LICS.SPIRE_XLS"));
            }

            this.helper = new ProcessHelper(getSes());

            ITask mainTask = getEventTask();
            proi = mainTask.getProcessInstance();
            IUser processOwner = proi.getOwner();
            String uniqueId = UUID.randomUUID().toString();

            proi.setDescriptorValue("ObjectStatus","Cancelled");
            proi.commit();

            (new File(Conf.CancelProcess.MainPath)).mkdirs();

            compCode = proi.getDescriptorValue(Conf.Descriptors.CompanyNo, String.class);
            docNumber = proi.getDescriptorValue(Conf.Descriptors.DocNumber, String.class);
            if(compCode.isEmpty()){
                throw new Exception("Company no is empty.");
            }
            IInformationObject prjt = Utils.getProjectWorkspace(compCode, helper);
            if(prjt == null){
                throw new Exception("Company No not found [" + compCode + "].");
            }
            String umail = processOwner.getEMailAddress();
            List<String> mlst = new ArrayList<>();
            mlst.add(umail);
            if(mlst.size() == 0){return resultSuccess("No mail address");}

            String mtpn = "QA_CAPA_CANCEL_MAIL";
            IDocument mtpl = Utils.getTemplateDocument(prjt, mtpn);
            if(mtpl == null){
                return resultSuccess("No-Mail Template");
            }

            JSONObject dbks = new JSONObject();
            JSONObject mcfg = Utils.getMailConfig();
            dbks.put("Title", proi.getDisplayName());
            dbks.put("Task", mainTask.getName());
            dbks.put("DoxisLink", mcfg.getString("webBase") + helper.getTaskURL(proi.getID()));

            String tplMailPath = Utils.exportDocument(mtpl, Conf.CancelProcess.MainPath, mtpn + "[" + uniqueId + "]");
            String mailExcelPath = Utils.saveDocReviewExcel(tplMailPath, Conf.CancelProcessSheetIndex.Mail,
                    Conf.CancelProcess.MainPath + "/" + mtpn + "[" + uniqueId + "].xlsx", dbks
            );
            String mailHtmlPath = Utils.convertExcelToHtml(mailExcelPath, Conf.CancelProcess.MainPath + "/" + mtpn + "[" + uniqueId + "].html");

            if(mlst.size() > 0) {
                JSONObject mail = new JSONObject();

                mail.put("To", String.join(";", mlst));
                mail.put("Subject", docNumber + " nolu talep iptal edildi");
                mail.put("BodyHTMLFile", mailHtmlPath);

                try{
                    Utils.sendHTMLMail(mail);
                } catch (Exception ex){
                    log.info("EXCP [Send-Mail] : " + ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Exception Caught");
            log.error(e.getMessage());
            return resultError(e.getMessage());
        }
        return resultSuccess("Ended successfully");
    }
}