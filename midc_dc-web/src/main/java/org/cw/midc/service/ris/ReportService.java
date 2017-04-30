package org.cw.midc.service.ris;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cw.midc.dao.HospitalDao;
import org.cw.midc.dao.ReportDao;
import org.cw.midc.dao.StudyInfoDao;
import org.cw.midc.dto.ReportCreateDto;
import org.cw.midc.dto.ReportModifyDto;
import org.cw.midc.dto.ReportQueryDto;
import org.cw.midc.entity.Hospital;
import org.cw.midc.entity.Report;
import org.cw.midc.entity.StudyInfo;
import org.cw.midc.entity.User;
import org.cw.midc.util.CommonUtils;
import org.cw.midc.util.Constants;
import org.cw.midc.util.UserContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
	
	private static final Logger log = LoggerFactory.getLogger(ReportService.class);

	
	@Autowired
	private ReportDao reportDao;
	
	@Autowired
	private HospitalDao hospitalDao;
	
	@Autowired
	private StudyInfoDao studyInfoDao;
	
	public void createReport(ReportCreateDto reportCreateDto)
	{
		User user = (User) UserContextUtil.getAttribute("currentUser");
		
		Report report = new Report();
		report.setStudyinfoId(reportCreateDto.getStudyInfoId());
		report.setAdvice(reportCreateDto.getAdvice());
		report.setDescription(reportCreateDto.getDescription());
		report.setDiagnosis(reportCreateDto.getDiagnosis());
//		StudyInfo studyInfo = studyInfoRepository.findOne(report.getStudyInfoId());
		StudyInfo studyInfo = studyInfoDao.findUnique("getById", report.getStudyinfoId());
		
		if(studyInfo == null)
		{
			log.debug("No studyInfo found!");
			return;
		}
		
		String userId = user.getUserId();
		String rptStatus = "0";
		
		if("ROLE_SENIOR_DOC".equals(getHighestRole()))
		{
			report.setjDocId(userId);
			report.setsDocId(userId);
			rptStatus = Constants.REPORT_STATUS_APPROVED;
		}
		else if("ROLE_JUNIOR_DOC".equals(getHighestRole()))
		{
			report.setjDocId(userId);
			rptStatus = Constants.REPORT_STATUS_PRE_DIAGNOSE;
		}
		else
		{
			return;
		}
		report.setRptId((CommonUtils.generateId()));
        
		//更新 studyInfo表的报告状态
		Map<String, Object> param = new HashMap<>();
		param.put("rptStatus", rptStatus);
		param.put("studyinfoId", report.getStudyinfoId());
		param.put("rptId", report.getRptId());
		studyInfoDao.update("updateRptIdAndStatus", param);
		
		//插入报告表
		reportDao.save(report);
		
	}
	
	public void modifyReport(ReportModifyDto reportModifyDto)
	{
//		StudyInfo studyInfo = studyInfoRepository.findOne(reportModifyDto.getStudyInfoId());
		StudyInfo studyInfo = studyInfoDao.findUnique("getById", reportModifyDto.getStudyInfoId());
		if(studyInfo == null)
		{
			log.error("No studyInfo found for {}", reportModifyDto.getStudyInfoId());
			return;
		}
		User user = (User) UserContextUtil.getAttribute("currentUser");
		
		String userId = user.getUserId();
		String reportId = studyInfo.getRptId();
		
		
		
		//生成一个新的报告
		Report resultNew = reportDao.findUnique("selectByPrimaryKey", reportId);
		resultNew.setAdvice(reportModifyDto.getAdvice());
		resultNew.setDescription(reportModifyDto.getDescription());
		resultNew.setDiagnosis(reportModifyDto.getDiagnosis());
		resultNew.setRptId((CommonUtils.generateId()));
		
		//更新StudyInfo表状态
		String rptStatus = "0";		
		if("ROLE_SENIOR_DOC".equals(getHighestRole()))
		{
			resultNew.setsDocId(userId);
			rptStatus = Constants.REPORT_STATUS_APPROVED;
		}
		else if("ROLE_JUNIOR_DOC".equals(getHighestRole()))
		{
			resultNew.setjDocId(userId);
			rptStatus = Constants.REPORT_STATUS_PRE_DIAGNOSE;
		}
		else
		{
			return;
		}
		
		Map<String, Object> param = new HashMap<>();
		param.put("rptStatus", rptStatus);
		param.put("studyinfoId", reportModifyDto.getStudyInfoId());
		param.put("rptId", resultNew.getRptId());
		studyInfoDao.update("updateRptIdAndStatus", param);
		
		//原始报告置为失效
		param.clear();
		param.put("status", "0");
		param.put("rptId", reportId);
		reportDao.update("updateStatus", param);
		
		//插入新报告
		reportDao.save(resultNew);
		
		
	}
	
	public ReportQueryDto getReportByStudyInfoId(String studyInfoId)
	{
//		StudyInfo studyInfo = studyInfoRepository.findOne(studyInfoId);
		StudyInfo studyInfo = studyInfoDao.findUnique("getById", studyInfoId);
		if(studyInfo == null)
		{
			log.error("No studyInfo found for {}", studyInfoId);
			return null;
		}
		
		if(studyInfo.getRptId() == null)
		{
			log.debug("No report for studyInfoId : {}", studyInfoId);
			return null;
		}
		
		Report rport = reportDao.findUnique("selectByPrimaryKey", studyInfo.getRptId());
		ReportQueryDto result = new ReportQueryDto();
		result.setId(rport.getRptId());
		result.setStudyInfoId(rport.getStudyinfoId());
		result.setDescription(rport.getDescription());
		result.setDiagnosis(rport.getDiagnosis());
		result.setAdvice(rport.getAdvice());
		result.setJuniorDoctorId(rport.getjDocId());
		result.setSeniorDoctorId(rport.getsDocId());
		result.setDirectoDoctorId(rport.getdDocId());
		result.setCreateTime(rport.getCreateTime());
		result.setUpdateTime(rport.getUpdateTime());
		return result;		
	}
	
	public ReportQueryDto getReportByHospital(String orignalStudyInfoId, String clientId)
	{
		List<Hospital> hospitalList = hospitalDao.find("getByClientId", clientId);
		if(hospitalList == null || hospitalList.size() == 0)
		{
			return null;
		}
		String hospitalId = hospitalList.get(0).getHospId();
		Map<String, Object> param = new HashMap<>();
		param.put("hospitalId", hospitalId);
		param.put("orginalStudyInfoId", orignalStudyInfoId);
		StudyInfo studyInfo = studyInfoDao.findUnique("getByOrginalStudyInfoIdAndHospitalId", param);
		if(studyInfo == null)
		{
			log.error("No studyInfo found for originalStudyInfoId : {}, clientId : {}", orignalStudyInfoId, clientId);
			return null;
		}
		
		if(studyInfo.getRptId() == null)
		{
			log.info("No report found for originalStudyInfoId : {}, clientId : {}", orignalStudyInfoId, clientId);
			return null;
		}
		
		//查询报告		
		Report report = reportDao.findUnique("selectByPrimaryKey", studyInfo.getRptId());
		
		//更新传输状态
		param.clear();
		param.put("transStatus", Constants.STUDYINFO_TRANS_STATUS_C2B);
		param.put("studyinfoId", studyInfo.getStudyinfoId());
		studyInfoDao.update("updateTransStatus", param);
		
		//对外转换
		ReportQueryDto result = new ReportQueryDto();
		result.setId(report.getRptId());
		result.setAdvice(report.getAdvice());
		result.setDescription(report.getDescription());
		result.setDiagnosis(report.getDiagnosis());
		result.setJuniorDoctorId(report.getjDocId());
		result.setSeniorDoctorId(report.getsDocId());
		result.setDirectoDoctorId(report.getdDocId());
		result.setUpdateTime(report.getUpdateTime());
		return result;		
	}
	
	private String getHighestRole()
	{
		List<String> roles = (List<String>)UserContextUtil.getAttribute("roles");
		String doctorRoleHighest = null;
		if(roles.indexOf("ROLE_SENIOR_DOC") != -1)
		{
			return "ROLE_SENIOR_DOC";
		}
		
		if(roles.indexOf("ROLE_JUNIOR_DOC") != -1)
		{
			return "ROLE_JUNIOR_DOC";
		}		
		return null;
	}

}
