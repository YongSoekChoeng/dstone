package net.dstone.sample.task;

import java.util.ArrayList;

import net.dstone.common.task.TaskItem;
import net.dstone.common.utils.StringUtil;

public class GenTestDataTask extends TaskItem {

	static ArrayList<String> dateList = new ArrayList<String>();
	static ArrayList<String> timeList = new ArrayList<String>();
	static ArrayList<String> custList = new ArrayList<String>();
	static ArrayList<String> evntList = new ArrayList<String>();
	static{
		String toDay = net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd");
		try {
			for(int i=0; i<100; i++){
				dateList.add(net.dstone.common.utils.DateUtil.dayCalForStr(toDay, -1*i, 1, "yyyyMMdd"));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			String HH ="";
			String mm ="";
			String ss ="";
			for(int i=0; i<24; i++){
				HH = net.dstone.common.utils.StringUtil.filler(String.valueOf(i), 2, "0");
				for(int j=0; j<60; j++){
					mm = net.dstone.common.utils.StringUtil.filler(String.valueOf(j), 2, "0");
					for(int k=0; k<60; k++){
						ss = net.dstone.common.utils.StringUtil.filler(String.valueOf(k), 2, "0");
						timeList.add(HH+mm+ss);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		for(int i=0; i<100; i++){
			evntList.add("EVNT-" + StringUtil.filler(String.valueOf(i), 3, "0"));
		}
		for(int i=0; i<100000; i++){
			custList.add("CUST-" + StringUtil.filler(String.valueOf(i), 6, "0"));
		}
	}

	
	@Override
	public TaskItem doTheTask() {
		String filePath = this.getProperty("filePath");
		String fileName = this.getId() + "-" + net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HHmmss") + ".log";
		
		String dtm = ""; 
		String event = ""; 
		
		try {
			StringBuffer fileConts = new StringBuffer();
			StringBuffer line = new StringBuffer();
			
			for(String date : dateList){
				if( !this.getProperty("date", "").equals(date) ){
					continue;
				}
				for(String cust : custList){
					// 하루에 총고객중 1/4 만 방문
					if( (new java.util.Random()).nextInt(5)!= 1 ){
						continue;
					}
					// 한 고객당 20~30 번씩 메뉴방문
					int eventNumPerCust = 20+(new java.util.Random()).nextInt(10);
					for(int i=0; i<eventNumPerCust; i++){
						
						line = new StringBuffer();
						
						dtm = date; 
						dtm += "-" + timeList.get((new java.util.Random()).nextInt(timeList.size()-1)); 
						
						event = evntList.get((new java.util.Random()).nextInt(evntList.size()-1));
						
						line.append(dtm).append("\t");
						line.append(cust).append("\t");
						line.append(event);
						
						fileConts.append(line).append("\n");
					}
				}
			}
			
			if(!"".equals(fileConts.toString())){
				net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts.toString());
			}
			/* Random Delay 시간을 줄 경우 */
			/*
			java.util.Random r = new java.util.Random();
			Thread.sleep( (new Integer(r.nextInt(1000))).longValue() );
			*/
		} catch (Exception e) {
			e.printStackTrace();
		}

		if( "Y".equals(this.getProperty("deleteFileYn")) ){
			net.dstone.common.utils.FileUtil.deleteFile(filePath + "/" + fileName);
		}
		
		debug(this.getId() + " 작업완료 !!!");

		return this;
	}

}
