package net.dstone.sample.queue;

import net.dstone.common.queue.QueueItem;

public class SampleQueueItem extends QueueItem {

	@Override
	public void doTheJob() {
		String filePath = this.getProperty("filePath");
		String fileName = this.getId() + "-" + (new net.dstone.common.utils.GuidUtil()).getNewGuid() + ".log";
		String fileConts = net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HH:mm:ss") + "에 파일내용.";
		
		try {
			net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts);
			if( "Y".equals(this.getProperty("deleteFileYn")) ){
				net.dstone.common.utils.FileUtil.deleteFile(filePath + "/" + fileName);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
