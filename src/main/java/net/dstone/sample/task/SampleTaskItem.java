package net.dstone.sample.task;

import net.dstone.common.task.TaskItem;

public class SampleTaskItem extends TaskItem {

	@Override
	public TaskItem doTheTask() {
		String filePath = this.getProperty("filePath");
		String fileName = this.getId() + "-" + net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HHmmss") + ".log";
		String fileConts = net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HH:mm:ss") + "에 파일내용.";
		net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts);
		
		try {
			java.util.Random r = new java.util.Random();
			Thread.sleep( (new Integer(r.nextInt(1000))).longValue() );
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
