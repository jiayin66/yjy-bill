package com.yjy.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
import org.springframework.core.io.ClassPathResource;
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
		// 1.解析用户拿到用户id集合。增加用户中不能重复的判断。
		List<String> userStrList = getUserName(user);
		// 2.解析文本记录拿到有效行
		List<String> recordList = getRecordSpilit(record);
		// 3、处理数据
		List<RecodeModel> result = getResult(userStrList, recordList);
		// 4、导出excel
		excelTemplateExporter.exportExcel(result, "炊事班-记账", "记账", RecodeModel.class, "炊事班-记账-不排序.xls", response);
	}


	private List<RecodeModel> getResult(List<String> userStrList, List<String> recordList) {
		// （1）最终结果
		List<RecodeModel> result = new ArrayList<RecodeModel>();
		for (int i = 0; i < recordList.size(); i++) {
			String txtRecord = recordList.get(i);
			System.out.println("第【" + (i + 1) + "】条待判断的记录：" + txtRecord);
			// 【1】（去具体时间）按照这个正则匹配过滤时间格式: 熊东飞(211435812) 11:51:27
			String patternTime = ".*(\\d{1,2}:\\d{1,2}:\\d{1,2}).*";
			Matcher matcherTime = Pattern.compile(patternTime).matcher(txtRecord);
			if (matcherTime.find()) {
				System.out.println("-->无效过滤，此条记录是时间（两个冒号判断）" + txtRecord);
				continue;
			}
			// 【1.0】（去日期）按照这个正则匹配过滤时间格式: 2019-09-11
			String patternDate = ".*(\\d{4}-\\d{2}-\\d{2}).*";
			Matcher matchDate = Pattern.compile(patternDate).matcher(txtRecord);
			if (matchDate.find()) {
				System.out.println("-->无效过滤，此条记录是时间（日期格式）" + txtRecord);
				continue;
			}

			// 【2.0】（匹配2个数字）  聂鑫勇5.5 余额819下一位王亮明，李谨延 3 下一位/个 张志龙 余额 917
			//以数字拆分三段，数字包含小数点。匹配带2份数字的记录
			String patternTwoMatch = "^[^0-9\\.]*([0-9\\.]+)[^0-9\\.]+([0-9\\.]+)[^0-9\\.]*$";
			Matcher matcherTwo = Pattern.compile(patternTwoMatch).matcher(txtRecord);
			if (matcherTwo.find()) {
				System.out.println("-->有效待处理，此条记录有两个金额：" + txtRecord);
				// 真正的处理数据
				RecodeModel recodeModel = getRecodeModel(txtRecord, matcherTwo, userStrList);
				if (recodeModel != null) {
					// (1.1)添加有效数据
					result.add(recodeModel);
				}else {
					throw new RuntimeException("识别出错："+txtRecord);
				}
				continue;
			}

			// 【2.1】（识别错误）上面的精准匹配如果匹配不上说明有人金额中带有空格等特殊字符(与上面区别是没有开始和结束限制)
			String patternTwoMatchError = "[^0-9\\.]*([0-9\\.]+)[^0-9\\.]+([0-9\\.]+)[^0-9\\.]*";
			Matcher matcherTwoError = Pattern.compile(patternTwoMatchError).matcher(txtRecord);
			if (matcherTwoError.find()) {
				throw new RuntimeException("请核实，此人金额输入格式错误：" + txtRecord);
			}

			// 【3.0】（精准识别1个数字）没有报余额，只报当前金额 ：熊东飞 4.5 下一个 魏冲，熊东飞 4.5
			String patternOneMatch = "^([^0-9\\.]*)([0-9\\.]+)([^0-9\\.]*)$";
			Matcher matcherOne = Pattern.compile(patternOneMatch).matcher(txtRecord);
			if (matcherOne.find()) {
				System.out.println("-->有效待处理，只有一个金额，没有报余额的记录：" + txtRecord);
				RecodeModel recodeModel = setOneMatch(matcherOne, txtRecord, userStrList);
				result.add(recodeModel);
				continue;
			}

			// 【3.1】（不精准识别1个数字）对非精准匹配的报错
			String patternOneMatchError = "^([^0-9\\.]*)([0-9\\.]+)([^0-9\\.]*)$";
			Matcher matcherOneError = Pattern.compile(patternOneMatchError).matcher(txtRecord);
			if (matcherOneError.find()) {
				throw new RuntimeException("请核实，此人金额输入格式错误：" + txtRecord);
			}

			// 【4】匹配这种 ：严志凌下一位 镇阳 /下一个 （这个要在3之后，避免把正常数据拦截）
			String patternNext = "^(\\D*)下一(\\D*)$";
			Matcher matcherNext = Pattern.compile(patternNext).matcher(txtRecord);
			if (matcherNext.find()) {
				// (1.2)修改有效数据
				// (2.2)修改中间表数据
				System.out.println("-->有效待处理，此条记录是单纯的报下一位" + txtRecord);
				setNext(result, matcherNext);
				continue;
			}

			System.err.println("-->无效过滤,没有被规则拦截的记录:" + txtRecord);
		}

		System.out.println("----------------------解析完成-------------------------------");
		System.out.println("完整记录个数:" + result.size() + ",所有数据原顺序如下请核对：");
		for (RecodeModel recodeModel : result) {
			// 对next中包含的各种乱数据处理
			String next = recodeModel.getNext();
			if (StringUtils.isNotBlank(next)) {
				try {
					next = subSpacialChar(next).replace("下一位", "").replace("下一个", "").replace("，", "").replace(",", "")
							.replace("个", "").replace("位", "").replace(" ", "");
				} catch (Exception e) {
					next = null;
				}
				recodeModel.setNext(next);
			}
			// 对余额进行初始化
			if (recodeModel.getAllMoney() == null) {
				recodeModel.setAllMoney(new BigDecimal(0));
			}
			System.out.println(JSON.toJSONString(recodeModel));
		}
		//余额倒序排序
		Collections.sort(result, new Comparator() {
				// 排序规则原因，如果没有就默认0把
				public int compare(Object o1, Object o2) {
					RecodeModel b = (RecodeModel) o1;
					RecodeModel a = (RecodeModel) o2;
					return a.getAllMoney().subtract(b.getAllMoney()).intValue();
				}

		});
		return result;
	}
	
	/**
	 * 截取特殊63的字符
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
		//不包含63的特殊字符
		if(count==0) {
			return str;
		}
		byte[]  result=new byte[bytes.length-count];
		//遇到63就自增2个
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
				System.out.println("---->>成功设置下一位："+JSON.toJSONString(recodeModel));
			}
			
		}
		//下面是修改中间表的代码
		if(realName==null) {
			//说明上面的结果没有匹配上，本条数据是无效的
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
	 * 根据提取的name和用户列表的name比对
	 * 
	 * @param txtRecord
	 * @param userStrList
	 * @param userName
	 * @return
	 */
	private String getRealName(String txtRecord, List<String> userStrList, String userName) {
		if (StringUtils.isBlank(userName)) {
			throw new RuntimeException("报餐未报消费者名字！核实此条消费记录归属哪位人员，手动补充姓名重新尝试！记录为：" + txtRecord);
		}
		String result = null;
		for (String str : userStrList) {
			if (userName.indexOf(str) != -1) {
				result = str;
				break;
			}
		}
		if (result == null) {
			throw new RuntimeException("用户列表缺失此人，补充用户列表重新尝试!记录为：" + txtRecord);
		}
		return result;
	}

	/**
	 * 两个参数的匹配 eg：
	 * "徐雷?9.5?余额248?下一位皮家鑫","皮家鑫?7.5?余额241?下一位?罗亚丽","罗亚丽?7.5?余额?233.81?下一位?韦聪","罗冲?7.5?余173.81?下一位?熊宾","张雅雯?7.5?余额181?下一位?罗冲","熊宾?7.5余额166?下一位?王翼","王翼?5.5?余额160?下一位聂鑫勇","童敏?8.5?余额188?下一位张雅雯","梁敏?14.5?余额203.5?下一位龚韩壮","韦聪?15.5?余额?218?下一位?梁敏","龚韩壮??6.5??余额?196?下一位?童敏","许小花??7.5??余额?145?下一位?左成元","左成元13余额132.8下一位任为","聂鑫勇7.5?余额153?下一位?许小花","任为?8.5?余额124.31?下一位?童贝","童贝?9?余额113?下一位?韦聪","贺俊凯?6?余额?109?下一位?苗刚","更正：余额115","李佳豪?2.5?余额?83.81?下一页?雷宇恒","雷宇恒?1?余额?82.81?下一位?祁程畅","雷哲?2.5?余额?86.31下一位李佳豪","祁程畅??14??余额???68.81??下一位null","苗刚?5?余额?104?下一位?韦聪","你们之间缺少一两个人报账，中间亏损15.5元"]
	 * 24
	 * 
	 * @param txtRecord
	 * @return
	 */
	private RecodeModel getRecodeModel(String txtRecord, Matcher matcherOne, List<String> userStrList) {
		BigDecimal money = new BigDecimal(matcherOne.group(1));
		BigDecimal allMoney = new BigDecimal(matcherOne.group(2));
		// 【2.1】只取开头和末尾数据
		String patternMatch = "([^0-9\\.]*)[0-9\\.]+([^0-9\\.]+)[0-9\\.]+([^0-9\\.]*)";
		Matcher matcher = Pattern.compile(patternMatch).matcher(txtRecord);
		if (!matcher.find()) {
			System.err.println("取开头和结尾没有匹配上：" + txtRecord);
			return null;
		}
		String userName = matcher.group(1);
		String nexName = matcher.group(3);
		if (matcher.group(2).contains("下一")) {
			// 下一等数据放在后面清除
			nexName = matcher.group(2);
			if (nexName.contains("余额")) {
				nexName = nexName.replace("余额", "").replace("余", "");
			}
		}

		// 【2.2】必须跟数据库用户名保持一致，否则记账重复。下一位无需截取
		userName = getRealName(txtRecord, userStrList, userName);

		RecodeModel recodeModel = new RecodeModel(userName, money, allMoney, nexName);
		return recodeModel;
	}

	/*
	 * 封装过程
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
			throw new RuntimeException("记录为空请检查！");
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
	 * 通用的读取excle方法
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
			System.out.println("错误原因：" + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("读取:" + cla.getSimpleName() + "excel数据失败");

		}
		// return null;
	}


	public void readUserText(String record, MultipartFile user, HttpServletResponse response) throws IOException {
		// 1.解析用户拿到用户id集合。增加用户中不能重复的判断。
		List<String> userStrList = null;
		if (user == null) {
			InputStream in=getLocalUserIo();
			userStrList=getUserNameWithTxt(in);
		} else {
			userStrList = getUserNameWithTxt(user.getInputStream());
		}
		// 2.解析文本记录拿到有效行
		List<String> recordList = getRecordSpilit(record);
		// 3、处理数据
		List<RecodeModel> result = getResult(userStrList, recordList);
		// 4、导出excel
		excelTemplateExporter.exportExcel(result, "炊事班-记账", "记账", RecodeModel.class, "炊事班-记账-不排序.xls", response);
		// 5、最后不报错才执行替换原来的文件的操作
		if(user!=null) {
			replaceUser(user);
		}
	}


	private void replaceUser(MultipartFile user) {
		try {
			InputStream inputStream = user.getInputStream();
			InputStreamReader ir = new InputStreamReader(inputStream, "UTF-8");
			BufferedReader br = new BufferedReader(ir);

			File file = new ClassPathResource("/全量的用户.txt").getFile();
			FileOutputStream fileOut = new FileOutputStream(file);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fileOut, "UTF-8"));

			int i = 0;
			while ((i = br.read()) != -1) {
				bw.write(i);
			}
			bw.close();
			br.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}


	private InputStream getLocalUserIo() {
		//拿到对应的数据
		InputStream resourceAsStream = this.getClass().getResourceAsStream("/全量的用户.txt");
		return resourceAsStream;
	}


	private List<String> getUserNameWithTxt(InputStream in) {
		List<String> txtRecordList=new ArrayList<String>();	
		 try {
			InputStreamReader is = new InputStreamReader(in);
			BufferedReader bf = new BufferedReader(is);
			String readLine = bf.readLine();
			 while (readLine != null) {  
				 if(!StringUtils.isEmpty(readLine)) {
					 readLine=readLine.trim().replace(" ", "");
					 txtRecordList.add(readLine);
				 }
				 readLine = bf.readLine(); // 一次读入一行数据  
	            }  
		} catch (Exception e) {
			System.out.println("错误原因："+e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("读取txt数据失败");
		}
		//替换目标文件
		return txtRecordList;
	}

	

}
