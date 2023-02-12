package net.dstone.sample.webstress;

import net.dstone.common.queue.QueueItem;
import net.dstone.common.utils.LogUtil;

public class WebStressDbQueueItem extends QueueItem {

	@Override
	public void doTheJob() {
		
		net.dstone.sample.dept.DeptService deptService = null; 
		net.dstone.sample.dept.cud.vo.SampleDeptCudVo paramVo = null;
		
		try {
			deptService = (net.dstone.sample.dept.DeptService)this.getObj("deptService");
			paramVo = (net.dstone.sample.dept.cud.vo.SampleDeptCudVo)this.getObj("paramVo");
			Thread.sleep(500);
			deptService.insertSampleDept(paramVo);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

}
