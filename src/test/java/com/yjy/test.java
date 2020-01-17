package com.yjy;

import java.io.File;
import java.io.IOException;

public class test {
	public static void main(String[] args) throws IOException {
		String path="C:\\Users\\yjy\\Desktop\\记账2\\新建文件夹\\";
		File file=new File(path+"a.txt");
		file.createNewFile();
	}
}
