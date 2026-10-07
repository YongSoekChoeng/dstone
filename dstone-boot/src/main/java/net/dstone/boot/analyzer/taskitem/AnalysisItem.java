package net.dstone.boot.analyzer.taskitem;

import net.dstone.common.task.TaskItem;

public class AnalysisItem extends TaskItem {

	private int analyzeJobKind;
	private String configFilePath;
	
	@Override
	public TaskItem doTheTask() {
		try {
			net.dstone.boot.common.tools.analyzer.AppAnalyzer appAnalyzer = net.dstone.boot.common.tools.analyzer.AppAnalyzer.getInstance(this.configFilePath);
			appAnalyzer.analyzeApp(Integer.valueOf(analyzeJobKind), true);
		} catch (Exception e) {
            String errDetailMsg = this.getClass().getName() + ".doTheTask 수행중 예외발생. 상세사항:" + e.toString(); 
            getLogger().error(errDetailMsg); 
		}
		return this;
	}

	public int getAnalyzeJobKind() {
		return analyzeJobKind;
	}

	public void setAnalyzeJobKind(int analyzeJobKind) {
		this.analyzeJobKind = analyzeJobKind;
	}

	public String getConfigFilePath() {
		return configFilePath;
	}

	public void setConfigFilePath(String configFilePath) {
		this.configFilePath = configFilePath;
	}

}
