package net.dstone.sample.webstress;

import net.dstone.common.queue.QueueItem;
import net.dstone.common.utils.LogUtil;

public class WebStressFileQueueItem extends QueueItem {

	@Override
	public void doTheJob() {
		
		String filePath = this.getProperty("filePath");
		String fileName = this.getProperty("fileName");
		
		try {
			//net.dstone.sample.dept.DeptService svc = (net.dstone.sample.dept.DeptService)net.dstone.common.utils.BeanUtil.getSpringBean(net.dstone.sample.dept.DeptService.class);
			

			String fileConts = net.dstone.common.utils.DateUtil.getToDate("yyyyMMdd-HH:mm:ss") + "에 파일내용.";
			net.dstone.common.utils.FileUtil.writeFile(filePath, fileName, fileConts);
			Thread.sleep(500);
			net.dstone.common.utils.FileUtil.deleteFile(filePath + "/" + fileName);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
