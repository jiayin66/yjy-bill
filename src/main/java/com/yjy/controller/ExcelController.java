package com.yjy.controller;

import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.yjy.service.ExcelService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
@RestController
@RequestMapping("/bill")
@Api(tags="yjy��������ϵͳ")
public class ExcelController {
	@Autowired
	private ExcelService excelService;
	@PostMapping("/txt")
	@ApiOperation("����˳��������������һλ || ��һλ���� "
			+ "�������Դ��������"
			+ "�������Դ��������"
			+ "ÿ��Ҫ��֮����������ı��������и���")
	public void readText(@RequestParam(value="record") String  record,@RequestParam(value="user") MultipartFile user,
			HttpServletResponse response) {
		excelService.readText(record,user,response);
	}
	
	@PostMapping("/usertxt")
	@ApiOperation("����˳��������������һλ || ��һλ���� "
			+ "�������Դ��������"
			+ "�������Դ��������"
			+ "ÿ��Ҫ��֮����������ı��������и���")
	public void readUserText(@RequestParam(value="record") String  record,@RequestParam(value="user") MultipartFile user,
			HttpServletResponse response) {
		excelService.readUserText(record,user,response);
	}
	
}
