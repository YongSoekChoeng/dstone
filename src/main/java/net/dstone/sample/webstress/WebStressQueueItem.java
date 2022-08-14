package net.dstone.sample.webstress;

import net.dstone.common.queue.QueueItem;

public class WebStressQueueItem extends QueueItem {

	@Override
	public void doTheJob() {
		String filePath = this.getProperty("filePath");
		String fileName = this.getProperty("fileName");
		
		try {
			net.dstone.common.utils.FileUtil.deleteFile(filePath + "/" + fileName);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
