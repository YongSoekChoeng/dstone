package net.dstone.sample.webstress;

import net.dstone.common.queue.QueueItem;

public class WebStressQueueItem extends QueueItem {

	@Override
	public void doTheJob() {
		
		String filePath = this.getProperty("filePath");
		String fileName = this.getProperty("fileName");
		String fileConts = this.getProperty("fileConts");
		String fileDelYn = this.getProperty("fileDelYn", "N");
		
		try {
			net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts);
			if("Y".equals(fileDelYn)) {
				Thread.sleep(1*1000);
				net.dstone.common.utils.FileUtil.deleteFile(filePath + "/" + fileName);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
