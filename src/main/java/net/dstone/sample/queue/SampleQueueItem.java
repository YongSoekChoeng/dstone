package net.dstone.sample.queue;

import net.dstone.common.queue.QueueItem;

public class SampleQueueItem extends QueueItem {

	@Override
	public void doTheJob() {
		String filePath = "D:/Temp/task";
		String fileName = this.getId() + "-" + net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HHmmss") + ".log";
		String fileConts = net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HH:mm:ss") + "에 파일내용.";
		net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts);
		
		try {
			java.util.Random r = new java.util.Random();
			Thread.sleep( (new Integer(r.nextInt(1000))).longValue() );
			net.dstone.common.utils.FileUtil.deleteFile(filePath + "/" + fileName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		debug(this.getId() + " 작업완료 !!!");

	}

}
