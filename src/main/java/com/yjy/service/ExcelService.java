package com.yjy.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSON;
import com.yjy.model.RecodeModel;
import com.yjy.model.User;
import com.yjy.util.ExcelTemplateExporter;

import cn.afterturn.easypoi.excel.ExcelImportUtil;
import cn.afterturn.easypoi.excel.entity.ImportParams;
import cn.afterturn.easypoi.excel.entity.result.ExcelImportResult;

@Service
public class ExcelService {
	@Autowired
	private ExcelTemplateExporter excelTemplateExporter;

	public void readText(String record, MultipartFile user, HttpServletResponse response) {
		// 1.�����û��õ��û�id���ϡ������û��в����ظ����жϡ�
		List<String> userStrList = getUserName(user);
		// 2.�����ı���¼�õ���Ч��
		List<String> recordList = getRecordSpilit(record);
		// 3����������
		List<RecodeModel> result = getResult(userStrList, recordList);
		// 4������excel
		excelTemplateExporter.exportExcel(result, "���°�-����", "����", RecodeModel.class, "���°�-����-������.xls", response);
	}


	private List<RecodeModel> getResult(List<String> userStrList, List<String> recordList) {
		// ��1�����ս��
		List<RecodeModel> result = new ArrayList<RecodeModel>();
		for (int i = 0; i < recordList.size(); i++) {
			String txtRecord = recordList.get(i);
			System.out.println("�ڡ�" + (i + 1) + "�������жϵļ�¼��" + txtRecord);
			// ��1����ȥ����ʱ�䣩�����������ƥ�����ʱ���ʽ: �ܶ���(211435812) 11:51:27
			String patternTime = ".*(\\d{1,2}:\\d{1,2}:\\d{1,2}).*";
			Matcher matcherTime = Pattern.compile(patternTime).matcher(txtRecord);
			if (matcherTime.find()) {
				System.out.println("-->��Ч���ˣ�������¼��ʱ�䣨����ð���жϣ�" + txtRecord);
				continue;
			}
			// ��1.0����ȥ���ڣ������������ƥ�����ʱ���ʽ: 2019-09-11
			String patternDate = ".*(\\d{4}-\\d{2}-\\d{2}).*";
			Matcher matchDate = Pattern.compile(patternDate).matcher(txtRecord);
			if (matchDate.find()) {
				System.out.println("-->��Ч���ˣ�������¼��ʱ�䣨���ڸ�ʽ��" + txtRecord);
				continue;
			}

			// ��2.0����ƥ��2�����֣�  ������5.5 ���819��һλ������������� 3 ��һλ/�� ��־�� ��� 917
			//�����ֲ�����Σ����ְ���С���㡣ƥ���2�����ֵļ�¼
			String patternTwoMatch = "^[^0-9\\.]*([0-9\\.]+)[^0-9\\.]+([0-9\\.]+)[^0-9\\.]*$";
			Matcher matcherTwo = Pattern.compile(patternTwoMatch).matcher(txtRecord);
			if (matcherTwo.find()) {
				System.out.println("-->��Ч��������������¼��������" + txtRecord);
				// �����Ĵ�������
				RecodeModel recodeModel = getRecodeModel(txtRecord, matcherTwo, userStrList);
				if (recodeModel != null) {
					// (1.1)������Ч����
					result.add(recodeModel);
				}else {
					throw new RuntimeException("ʶ�������"+txtRecord);
				}
				continue;
			}

			// ��2.1����ʶ���������ľ�׼ƥ�����ƥ�䲻��˵�����˽���д��пո�������ַ�(������������û�п�ʼ�ͽ�������)
			String patternTwoMatchError = "[^0-9\\.]*([0-9\\.]+)[^0-9\\.]+([0-9\\.]+)[^0-9\\.]*";
			Matcher matcherTwoError = Pattern.compile(patternTwoMatchError).matcher(txtRecord);
			if (matcherTwoError.find()) {
				throw new RuntimeException("���ʵ�����˽�������ʽ����" + txtRecord);
			}

			// ��3.0������׼ʶ��1�����֣�û�б���ֻ����ǰ��� ���ܶ��� 4.5 ��һ�� κ�壬�ܶ��� 4.5
			String patternOneMatch = "^([^0-9\\.]*)([0-9\\.]+)([^0-9\\.]*)$";
			Matcher matcherOne = Pattern.compile(patternOneMatch).matcher(txtRecord);
			if (matcherOne.find()) {
				System.out.println("-->��Ч��������ֻ��һ����û�б����ļ�¼��" + txtRecord);
				RecodeModel recodeModel = setOneMatch(matcherOne, txtRecord, userStrList);
				result.add(recodeModel);
				continue;
			}

			// ��3.1��������׼ʶ��1�����֣��ԷǾ�׼ƥ��ı���
			String patternOneMatchError = "^([^0-9\\.]*)([0-9\\.]+)([^0-9\\.]*)$";
			Matcher matcherOneError = Pattern.compile(patternOneMatchError).matcher(txtRecord);
			if (matcherOneError.find()) {
				throw new RuntimeException("���ʵ�����˽�������ʽ����" + txtRecord);
			}

			// ��4��ƥ������ ����־����һλ ���� /��һ�� �����Ҫ��3֮�󣬱���������������أ�
			String patternNext = "^(\\D*)��һ(\\D*)$";
			Matcher matcherNext = Pattern.compile(patternNext).matcher(txtRecord);
			if (matcherNext.find()) {
				// (1.2)�޸���Ч����
				// (2.2)�޸��м������
				System.out.println("-->��Ч��������������¼�ǵ����ı���һλ" + txtRecord);
				setNext(result, matcherNext);
				continue;
			}

			System.err.println("-->��Ч����,û�б��������صļ�¼:" + txtRecord);
		}

		System.out.println("----------------------�������-------------------------------");
		System.out.println("������¼����:" + result.size() + ",��������ԭ˳��������˶ԣ�");
		for (RecodeModel recodeModel : result) {
			// ��next�а����ĸ��������ݴ���
			String next = recodeModel.getNext();
			if (StringUtils.isNotBlank(next)) {
				try {
					next = subSpacialChar(next).replace("��һλ", "").replace("��һ��", "").replace("��", "").replace(",", "")
							.replace("��", "").replace("λ", "").replace(" ", "");
				} catch (Exception e) {
					next = null;
				}
				recodeModel.setNext(next);
			}
			// �������г�ʼ��
			if (recodeModel.getAllMoney() == null) {
				recodeModel.setAllMoney(new BigDecimal(0));
			}
			System.out.println(JSON.toJSONString(recodeModel));
		}
		//��������
		Collections.sort(result, new Comparator() {
				// �������ԭ�����û�о�Ĭ��0��
				public int compare(Object o1, Object o2) {
					RecodeModel b = (RecodeModel) o1;
					RecodeModel a = (RecodeModel) o2;
					return a.getAllMoney().subtract(b.getAllMoney()).intValue();
				}

		});
		return result;
	}
	
	/**
	 * ��ȡ����63���ַ�
	 * @param str
	 * @return
	 */
	private String subSpacialChar(String str) {
		byte[] bytes=null;
		try {
			bytes = str.getBytes("gbk");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		int count=0;
		for(int i=0;i<bytes.length;i++) {
			if(bytes[i]==63) {
				count++;
			}
		}
		//������63�������ַ�
		if(count==0) {
			return str;
		}
		byte[]  result=new byte[bytes.length-count];
		//����63������2��
		int x=0;
		int y=0;
		for(int i=0;i<bytes.length;i++) {
			if(bytes[i]==63) {
				y++;
				continue;
			}
			result[x]=bytes[y];
			y++;
			x++;
		}
		String resultStr=null;
		try {
			resultStr = new String(result,"gbk");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return resultStr;
	}


	private void setNext(List<RecodeModel> result, Matcher matcherNext) {
		String name = matcherNext.group(1);
		String next = matcherNext.group(2);
		String realName=null;
		for(RecodeModel recodeModel:result) {
			if(name.indexOf(recodeModel.getName())!=-1) {
				realName=recodeModel.getName();
				recodeModel.setNext(next);
				System.out.println("---->>�ɹ�������һλ��"+JSON.toJSONString(recodeModel));
			}
			
		}
		//�������޸��м���Ĵ���
		if(realName==null) {
			//˵������Ľ��û��ƥ���ϣ�������������Ч��
			return;
		}
	}
	private RecodeModel setOneMatch(Matcher matcherOne, String txtRecord,List<String> userStrList) {
		RecodeModel recodeModel=null;
		String one = matcherOne.group(1);
		String two = matcherOne.group(2);
		String three = matcherOne.group(3);
		String realName=getRealName(txtRecord,userStrList,one);
		recodeModel=new RecodeModel(realName,new BigDecimal(two),null,three);
		return recodeModel;
	}

	/**
	 * ������ȡ��name���û��б���name�ȶ�
	 * 
	 * @param txtRecord
	 * @param userStrList
	 * @param userName
	 * @return
	 */
	private String getRealName(String txtRecord, List<String> userStrList, String userName) {
		if (StringUtils.isBlank(userName)) {
			throw new RuntimeException("����δ�����������֣���ʵ�������Ѽ�¼������λ��Ա���ֶ������������³��ԣ���¼Ϊ��" + txtRecord);
		}
		String result = null;
		for (String str : userStrList) {
			if (userName.indexOf(str) != -1) {
				result = str;
				break;
			}
		}
		if (result == null) {
			throw new RuntimeException("�û��б�ȱʧ���ˣ������û��б����³���!��¼Ϊ��" + txtRecord);
		}
		return result;
	}

	/**
	 * ����������ƥ�� eg��
	 * "����?9.5?���248?��һλƤ����","Ƥ����?7.5?���241?��һλ?������","������?7.5?���?233.81?��һλ?Τ��","�޳�?7.5?��173.81?��һλ?�ܱ�","������?7.5?���181?��һλ?�޳�","�ܱ�?7.5���166?��һλ?����","����?5.5?���160?��һλ������","ͯ��?8.5?���188?��һλ������","����?14.5?���203.5?��һλ����׳","Τ��?15.5?���?218?��һλ?����","����׳??6.5??���?196?��һλ?ͯ��","��С��??7.5??���?145?��һλ?���Ԫ","���Ԫ13���132.8��һλ��Ϊ","������7.5?���153?��һλ?��С��","��Ϊ?8.5?���124.31?��һλ?ͯ��","ͯ��?9?���113?��һλ?Τ��","�ؿ���?6?���?109?��һλ?���","���������115","��Ѻ�?2.5?���?83.81?��һҳ?�����","�����?1?���?82.81?��һλ?��̳�","����?2.5?���?86.31��һλ��Ѻ�","��̳�??14??���???68.81??��һλnull","���?5?���?104?��һλ?Τ��","����֮��ȱ��һ�����˱��ˣ��м����15.5Ԫ"]
	 * 24
	 * 
	 * @param txtRecord
	 * @return
	 */
	private RecodeModel getRecodeModel(String txtRecord, Matcher matcherOne, List<String> userStrList) {
		BigDecimal money = new BigDecimal(matcherOne.group(1));
		BigDecimal allMoney = new BigDecimal(matcherOne.group(2));
		// ��2.1��ֻȡ��ͷ��ĩβ����
		String patternMatch = "([^0-9\\.]*)[0-9\\.]+([^0-9\\.]+)[0-9\\.]+([^0-9\\.]*)";
		Matcher matcher = Pattern.compile(patternMatch).matcher(txtRecord);
		if (!matcher.find()) {
			System.err.println("ȡ��ͷ�ͽ�βû��ƥ���ϣ�" + txtRecord);
			return null;
		}
		String userName = matcher.group(1);
		String nexName = matcher.group(3);
		if (matcher.group(2).contains("��һ")) {
			// ��һ�����ݷ��ں������
			nexName = matcher.group(2);
			if (nexName.contains("���")) {
				nexName = nexName.replace("���", "").replace("��", "");
			}
		}

		// ��2.2����������ݿ��û�������һ�£���������ظ�����һλ�����ȡ
		userName = getRealName(txtRecord, userStrList, userName);

		RecodeModel recodeModel = new RecodeModel(userName, money, allMoney, nexName);
		return recodeModel;
	}

	/*
	 * ��װ����
	 */
	private List<String> getUserName(MultipartFile user) {
		List<User> userList = getTxt(user, User.class);
		List<String> userStrList = new ArrayList<String>();
		for (User userModel : userList) {
			String name = userModel.getName();
			userStrList.add(name);
		}
		return userStrList;
	}

	private List<String> getRecordSpilit(String record) {
		List<String> result = new ArrayList<String>();
		if (StringUtils.isEmpty(record)) {
			throw new RuntimeException("��¼Ϊ�����飡");
		}
		String[] split = record.split("(\r?\n)");
		for (String item : split) {
			if (StringUtils.isBlank(item)) {
				continue;
			}
			result.add(item);
		}
		return result;
	}


	/**
	 * ͨ�õĶ�ȡexcle����
	 * 
	 * @param file
	 * @param cla
	 * @return
	 */
	private <T> List<T> getTxt(MultipartFile file, Class cla) {
		try {
			ImportParams params = new ImportParams();
			params.setTitleRows(0);
			params.setNeedVerfiy(false);
			ExcelImportResult<T> resul = ExcelImportUtil.importExcelMore(file.getInputStream(), cla, params);
			List<T> list = resul.getList();
			return list;
		} catch (Exception e) {
			System.out.println("����ԭ��" + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("��ȡ:" + cla.getSimpleName() + "excel����ʧ��");

		}
		// return null;
	}


	public void readUserText(String record, MultipartFile user, HttpServletResponse response) {
		// 1.�����û��õ��û�id���ϡ������û��в����ظ����жϡ�
				List<String> userStrList = getUserNameWithTxt(user);
				// 2.�����ı���¼�õ���Ч��
				List<String> recordList = getRecordSpilit(record);
				// 3����������
				List<RecodeModel> result = getResult(userStrList, recordList);
				// 4������excel
				excelTemplateExporter.exportExcel(result, "���°�-����", "����", RecodeModel.class, "���°�-����-������.xls", response);
		
	}


	private List<String> getUserNameWithTxt(MultipartFile user) {
		List<String> txtRecordList=new ArrayList<String>();	
		 try {
			InputStreamReader is = new InputStreamReader(user.getInputStream());
			BufferedReader bf = new BufferedReader(is);
			String readLine = bf.readLine();
			 while (readLine != null) {  
				 if(!StringUtils.isEmpty(readLine)) {
					 readLine=readLine.trim().replace(" ", "");
					 txtRecordList.add(readLine);
				 }
				 readLine = bf.readLine(); // һ�ζ���һ������  
	            }  
		} catch (Exception e) {
			System.out.println("����ԭ��"+e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("��ȡtxt����ʧ��");
		}
		return txtRecordList;
	}

	

}