package com.yjy.model;

import javax.annotation.Generated;

import cn.afterturn.easypoi.excel.annotation.Excel;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class User {
	@Excel(name = "序号", orderNum = "0")
	private Integer id;
	
	@Excel(name = "姓名", orderNum = "1")
    private String name;
	

	public User() {
		super();
	}
	
	public User(Integer id,String name) {
		this.id=id;
		this.name=name;
	}
}
