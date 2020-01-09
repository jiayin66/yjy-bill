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
@Api(tags="yjy饭卡管理系统")
public class ExcelController {
	@Autowired
	private ExcelService excelService;
	@PostMapping("/txt")
	@ApiOperation("核心顺序：姓名、金额（余额、下一位 || 下一位、余额） "
			+ "两个可以带点的数字"
			+ "三个可以带点的数字"
			+ "每个要素之间可以任意文本但必须有隔开")
	public void readText(@RequestParam(value="record") String  record,@RequestParam(value="user") MultipartFile user,
			HttpServletResponse response) {
		excelService.readText(record,user,response);
	}
	
	@PostMapping("/usertxt")
	@ApiOperation("核心顺序：姓名、金额（余额、下一位 || 下一位、余额） "
			+ "两个可以带点的数字"
			+ "三个可以带点的数字"
			+ "每个要素之间可以任意文本但必须有隔开")
	public void readUserText(@RequestParam(value="record") String  record,@RequestParam(value="user") MultipartFile user,
			HttpServletResponse response) {
		excelService.readUserText(record,user,response);
	}
	
}
